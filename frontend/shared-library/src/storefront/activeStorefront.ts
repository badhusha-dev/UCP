// Which storefront a shopper is buying from. The browser only ever names a public storefront slug;
// product-service maps it to a tenant for the catalog and order-service does the same at checkout,
// so a forged slug can only reach another public storefront, never a tenant of the caller's choice.

import type { SessionStorageLike } from '../auth/applicationSession';

export interface ActiveStorefront {
  slug: string;
  name: string;
}

/** Thrown when the storefront service cannot be reached, so a page can say so instead of showing another catalog. */
export class StorefrontLookupError extends Error {
  constructor(message = 'The store could not be reached. Please try again shortly.') {
    super(message);
    this.name = 'StorefrontLookupError';
  }
}

const STOREFRONT_KEY = 'ucp.storefront';
// Same rule as product-service: 1-63 lowercase letters, digits or hyphens, because the slug is also a subdomain.
const SLUG_PATTERN = /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/;
const PUBLIC_API = '/api/products/public/storefront';

/** Hosts that can never be a storefront address: bare localhost and IP literals. */
export function isPlatformHost(hostname: string): boolean {
  const host = hostname.trim().toLowerCase();
  return host === '' || host === 'localhost' || /^\d{1,3}(\.\d{1,3}){3}$/.test(host) || host.includes(':');
}

/** The slug named in the address (`?storefront=acme`), lower-cased, or null when absent or malformed. */
export function storefrontFromQuery(search: string): string | null {
  const value = new URLSearchParams(search).get('storefront');
  const slug = value ? value.trim().toLowerCase() : '';
  return SLUG_PATTERN.test(slug) ? slug : null;
}

export function readActiveStorefront(storage: SessionStorageLike): ActiveStorefront | null {
  try {
    const raw = storage.getItem(STOREFRONT_KEY);
    const value = raw ? JSON.parse(raw) : null;
    if (value && typeof value.slug === 'string' && SLUG_PATTERN.test(value.slug)) {
      return { slug: value.slug, name: typeof value.name === 'string' ? value.name : value.slug };
    }
  } catch {
    // A malformed entry is dropped below.
  }
  storage.removeItem(STOREFRONT_KEY);
  return null;
}

export function storeActiveStorefront(storefront: ActiveStorefront, storage: SessionStorageLike): void {
  storage.setItem(STOREFRONT_KEY, JSON.stringify(storefront));
}

export function clearActiveStorefront(storage: SessionStorageLike): void {
  storage.removeItem(STOREFRONT_KEY);
}

/** GETs a public storefront resource: the body on 200, null on 404, StorefrontLookupError on anything else. */
async function getPublic(path: string, fetchImpl: typeof fetch, apiBaseUrl: string): Promise<Record<string, unknown> | null> {
  let response: Response;
  try {
    response = await fetchImpl(`${apiBaseUrl}${PUBLIC_API}${path}`, { headers: { Accept: 'application/json' } });
  } catch {
    throw new StorefrontLookupError();
  }
  if (response.status === 404) return null;
  const body = response.ok ? await response.json().catch(() => null) : null;
  if (!body || typeof body !== 'object') throw new StorefrontLookupError();
  return body as Record<string, unknown>;
}

/**
 * Works out the storefront for this visit and remembers it for the browser session:
 * 1. the address itself, when it is a verified custom domain or `<slug>.<platform storefront domain>`;
 * 2. otherwise a `?storefront=` parameter;
 * 3. otherwise the storefront remembered from earlier in the session.
 * A named storefront that is unknown or inactive is forgotten, and the shopper gets the platform's default
 * catalog (null). An unreachable storefront service throws StorefrontLookupError rather than guessing.
 */
export async function resolveActiveStorefront(
  location: { hostname: string; search: string },
  storage: SessionStorageLike,
  fetchImpl: typeof fetch = fetch,
  apiBaseUrl = '',
): Promise<ActiveStorefront | null> {
  const remembered = readActiveStorefront(storage);

  let requested: string | null = null;
  if (!isPlatformHost(location.hostname)) {
    const host = await getPublic(`/resolve?host=${encodeURIComponent(location.hostname.toLowerCase())}`, fetchImpl, apiBaseUrl);
    requested = host && typeof host.slug === 'string' ? host.slug : null;
  }
  requested = requested ?? storefrontFromQuery(location.search);

  if (!requested) return remembered;
  if (remembered && remembered.slug === requested) return remembered;

  const site = await getPublic(`/${encodeURIComponent(requested)}`, fetchImpl, apiBaseUrl);
  if (!site) {
    clearActiveStorefront(storage);
    return null;
  }
  const storefront = { slug: requested, name: typeof site.name === 'string' && site.name ? site.name : requested };
  storeActiveStorefront(storefront, storage);
  return storefront;
}

/** The `?storefront=` query a storefront-scoped API call needs, or an empty object for the default catalog. */
export function storefrontParams(storefront: ActiveStorefront | null): { storefront?: string } {
  return storefront ? { storefront: storefront.slug } : {};
}
