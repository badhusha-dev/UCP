import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  StorefrontLookupError,
  isPlatformHost,
  readActiveStorefront,
  resolveActiveStorefront,
  storeActiveStorefront,
  storefrontFromQuery,
  storefrontParams,
} from '../src/storefront/activeStorefront.ts';

function memoryStorage() {
  const values = new Map<string, string>();
  return {
    values,
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => { values.set(key, value); },
    removeItem: (key: string) => { values.delete(key); },
  };
}

/** A fake public storefront API: `hosts` maps hostnames to slugs, `sites` maps active slugs to names. */
function storefrontApi(hosts: Record<string, string>, sites: Record<string, string>, calls: string[] = []): typeof fetch {
  return (async (url: string) => {
    calls.push(url);
    const json = (status: number, body: unknown) =>
      new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
    const path = url.replace('/api/products/public/storefront', '');
    if (path.startsWith('/resolve?host=')) {
      const slug = hosts[decodeURIComponent(path.slice('/resolve?host='.length))];
      return slug ? json(200, { slug, hostname: 'x', primary: true }) : json(404, { error: 'Storefront not found' });
    }
    const name = sites[decodeURIComponent(path.slice(1))];
    return name ? json(200, { name, slug: path.slice(1) }) : json(404, { error: 'Storefront not found' });
  }) as unknown as typeof fetch;
}

const LOCAL = { hostname: 'localhost', search: '' };

describe('address parsing', () => {
  it('treats localhost and IP literals as platform hosts, and storefront subdomains as storefront hosts', () => {
    for (const host of ['localhost', '127.0.0.1', '[::1]', '::1', '']) assert.equal(isPlatformHost(host), true, host);
    for (const host of ['acme.storefront.localhost', 'shop.acme.com']) assert.equal(isPlatformHost(host), false, host);
  });

  it('reads a well-formed ?storefront= slug and ignores malformed ones', () => {
    assert.equal(storefrontFromQuery('?storefront=Acme'), 'acme');
    assert.equal(storefrontFromQuery('?q=1&storefront=acme-shop'), 'acme-shop');
    for (const search of ['', '?storefront=', '?storefront=-acme', '?storefront=acme/../x', '?storefront=a%20b']) {
      assert.equal(storefrontFromQuery(search), null, search);
    }
  });
});

describe('resolveActiveStorefront', () => {
  it('uses a ?storefront= slug once the API confirms it, and remembers it for the session', async () => {
    const storage = memoryStorage();
    const result = await resolveActiveStorefront({ hostname: 'localhost', search: '?storefront=acme' }, storage,
      storefrontApi({}, { acme: 'Acme Hardware' }));
    assert.deepEqual(result, { slug: 'acme', name: 'Acme Hardware' });
    assert.deepEqual(readActiveStorefront(storage), { slug: 'acme', name: 'Acme Hardware' });

    // Later pages of the visit no longer carry the parameter and need no request.
    const calls: string[] = [];
    assert.deepEqual(await resolveActiveStorefront(LOCAL, storage, storefrontApi({}, {}, calls)), result);
    assert.equal(calls.length, 0);
  });

  it('prefers the storefront the address belongs to over a ?storefront= parameter', async () => {
    const storage = memoryStorage();
    const result = await resolveActiveStorefront({ hostname: 'shop.acme.com', search: '?storefront=bistro' }, storage,
      storefrontApi({ 'shop.acme.com': 'acme' }, { acme: 'Acme', bistro: 'Bistro' }));
    assert.equal(result?.slug, 'acme');
  });

  it('falls back to the default catalog, and forgets the old storefront, when the named one is unknown or inactive', async () => {
    const storage = memoryStorage();
    storeActiveStorefront({ slug: 'acme', name: 'Acme' }, storage);
    const result = await resolveActiveStorefront({ hostname: 'localhost', search: '?storefront=closed' }, storage,
      storefrontApi({}, { acme: 'Acme' }));
    assert.equal(result, null);
    assert.equal(storage.values.size, 0);
  });

  it('keeps the remembered storefront on a host that is not a storefront address', async () => {
    const storage = memoryStorage();
    storeActiveStorefront({ slug: 'acme', name: 'Acme' }, storage);
    const result = await resolveActiveStorefront({ hostname: 'shop.platform.example', search: '' }, storage,
      storefrontApi({}, { acme: 'Acme' }));
    assert.equal(result?.slug, 'acme');
  });

  it('throws StorefrontLookupError instead of guessing when the storefront service is unreachable or failing', async () => {
    const unreachable = (async () => { throw new TypeError('network'); }) as unknown as typeof fetch;
    const failing = (async () => new Response('oops', { status: 503 })) as unknown as typeof fetch;
    await assert.rejects(resolveActiveStorefront({ hostname: 'shop.acme.com', search: '' }, memoryStorage(), unreachable), StorefrontLookupError);
    await assert.rejects(resolveActiveStorefront({ hostname: 'localhost', search: '?storefront=acme' }, memoryStorage(), failing), StorefrontLookupError);
  });

  it('drops a malformed remembered entry', () => {
    const storage = memoryStorage();
    storage.setItem('ucp.storefront', '{"slug":"../admin"}');
    assert.equal(readActiveStorefront(storage), null);
    storage.setItem('ucp.storefront', '{not json');
    assert.equal(readActiveStorefront(storage), null);
    assert.equal(storage.values.size, 0);
  });
});

describe('storefrontParams', () => {
  it('adds the slug only when a storefront is active', () => {
    assert.deepEqual(storefrontParams({ slug: 'acme', name: 'Acme' }), { storefront: 'acme' });
    assert.deepEqual(storefrontParams(null), {});
  });
});
