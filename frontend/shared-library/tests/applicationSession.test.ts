import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  ApplicationSignInError,
  clearApplicationSession,
  decodeTokenClaims,
  isUsableApplicationToken,
  readApplicationSession,
  readPlatformSession,
  sessionStorageKey,
  signInToApplication,
  signInToPlatform,
  storeApplicationSession,
  storePlatformSession,
  type ApplicationSession,
} from '../src/auth/applicationSession.ts';

const TENANT = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const NOW = Date.UTC(2026, 9, 31, 12, 0, 0);

function token(claims: Record<string, unknown>): string {
  const encode = (value: unknown) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode(claims)}.signature`;
}

function inventoryToken(overrides: Record<string, unknown> = {}): string {
  return token({ sub: 'employee@example.test', application_code: 'INVENTORY', tenant_id: TENANT,
    tenant_role: 'WAREHOUSE_OPERATOR', exp: NOW / 1000 + 900, ...overrides });
}

function memoryStorage() {
  const values = new Map<string, string>();
  return {
    values,
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => { values.set(key, value); },
    removeItem: (key: string) => { values.delete(key); },
  };
}

function session(overrides: Partial<ApplicationSession> = {}): ApplicationSession {
  return { token: inventoryToken(), applicationCode: 'INVENTORY', tenantId: TENANT,
    tenantSlug: 'client-a', tenantName: 'Client A', role: 'WAREHOUSE_OPERATOR', ...overrides };
}

function fetchReturning(status: number, body: unknown, capture?: (url: string, init: RequestInit) => void): typeof fetch {
  return (async (url: string, init: RequestInit) => {
    capture?.(url, init);
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
  }) as unknown as typeof fetch;
}

describe('decodeTokenClaims', () => {
  it('decodes base64url payloads including non-ASCII values', () => {
    assert.equal(decodeTokenClaims(token({ name: 'José' }))?.name, 'José');
  });

  it('returns null for malformed tokens', () => {
    assert.equal(decodeTokenClaims('not-a-token'), null);
    assert.equal(decodeTokenClaims('a.%%%.c'), null);
  });
});

describe('isUsableApplicationToken', () => {
  it('accepts an unexpired token for the same application and tenant', () => {
    assert.equal(isUsableApplicationToken(inventoryToken(), 'INVENTORY', TENANT, NOW), true);
  });

  it('rejects another application, another tenant, expiry and missing exp', () => {
    assert.equal(isUsableApplicationToken(inventoryToken(), 'DELIVERY_OPS', TENANT, NOW), false);
    assert.equal(isUsableApplicationToken(inventoryToken(), 'INVENTORY', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', NOW), false);
    assert.equal(isUsableApplicationToken(inventoryToken({ exp: NOW / 1000 - 1 }), 'INVENTORY', TENANT, NOW), false);
    assert.equal(isUsableApplicationToken(inventoryToken({ exp: undefined }), 'INVENTORY', TENANT, NOW), false);
  });
});

describe('signInToApplication', () => {
  it('posts the scoped login request and returns the session', async () => {
    let sentUrl = '';
    let sentBody: Record<string, unknown> = {};
    const fresh = inventoryToken({ exp: Date.now() / 1000 + 900 });
    const result = await signInToApplication(
      { applicationCode: 'INVENTORY', tenantSlug: ' Client-A ', email: ' employee@example.test ', password: 'pw' },
      fetchReturning(200, { ...session(), token: fresh }, (url, init) => {
        sentUrl = url;
        sentBody = JSON.parse(String(init.body));
      }),
      'https://api.example.com',
    );

    assert.equal(sentUrl, 'https://api.example.com/api/users/login/application');
    assert.deepEqual(sentBody, { email: 'employee@example.test', password: 'pw', applicationCode: 'INVENTORY', tenantSlug: 'client-a' });
    assert.equal(result.tenantId, TENANT);
  });

  it('maps 401 and 403 to clear messages without echoing server details', async () => {
    const request = { applicationCode: 'INVENTORY' as const, tenantSlug: 'client-b', email: 'e@example.test', password: 'pw' };
    await assert.rejects(signInToApplication(request, fetchReturning(403, { error: 'internal detail' })),
      (error: unknown) => error instanceof ApplicationSignInError && error.status === 403 && !error.message.includes('internal'));
    await assert.rejects(signInToApplication(request, fetchReturning(401, {})),
      (error: unknown) => error instanceof ApplicationSignInError && error.status === 401);
  });

  it('rejects a response for a different application', async () => {
    const deliveryToken = token({ application_code: 'DELIVERY_OPS', tenant_id: TENANT, exp: Date.now() / 1000 + 900 });
    await assert.rejects(
      signInToApplication({ applicationCode: 'INVENTORY', tenantSlug: 'client-a', email: 'e@example.test', password: 'pw' },
        fetchReturning(200, { ...session(), applicationCode: 'DELIVERY_OPS', token: deliveryToken })),
      ApplicationSignInError,
    );
  });

  it('reports an unreachable service as status 0', async () => {
    const failing = (async () => { throw new TypeError('network'); }) as unknown as typeof fetch;
    await assert.rejects(
      signInToApplication({ applicationCode: 'INVENTORY', tenantSlug: 'client-a', email: 'e@example.test', password: 'pw' }, failing),
      (error: unknown) => error instanceof ApplicationSignInError && error.status === 0,
    );
  });
});

describe('stored sessions', () => {
  it('stores sessions per application and reads them back', () => {
    const storage = memoryStorage();
    storeApplicationSession(session(), storage);

    assert.ok(storage.values.has(sessionStorageKey('INVENTORY')));
    assert.equal(readApplicationSession('INVENTORY', storage, NOW)?.tenantSlug, 'client-a');
    assert.equal(readApplicationSession('DELIVERY_OPS', storage, NOW), null);
  });

  it('clears expired, foreign and malformed sessions on read', () => {
    const storage = memoryStorage();
    storeApplicationSession(session({ token: inventoryToken({ exp: NOW / 1000 - 1 }) }), storage);
    assert.equal(readApplicationSession('INVENTORY', storage, NOW), null);
    assert.equal(storage.values.size, 0);

    storage.setItem(sessionStorageKey('INVENTORY'), JSON.stringify(session({ applicationCode: 'CRM' as never })));
    assert.equal(readApplicationSession('INVENTORY', storage, NOW), null);
    assert.equal(storage.values.size, 0);

    storage.setItem(sessionStorageKey('INVENTORY'), '{not json');
    assert.equal(readApplicationSession('INVENTORY', storage, NOW), null);
    assert.equal(storage.values.size, 0);
  });

  it('clears a session on sign out', () => {
    const storage = memoryStorage();
    storeApplicationSession(session(), storage);
    clearApplicationSession('INVENTORY', storage);
    assert.equal(storage.values.size, 0);
  });
});

describe('platform sessions', () => {
  const platformToken = (overrides: Record<string, unknown> = {}) =>
    token({ sub: 'owner@example.test', role: 'SUPER_ADMIN', exp: Date.now() / 1000 + 900, ...overrides });

  it('signs in and returns the role from the token', async () => {
    const result = await signInToPlatform({ email: 'owner@example.test', password: 'pw' }, 'SUPER_ADMIN',
      fetchReturning(200, { token: platformToken() }));
    assert.equal(result.role, 'SUPER_ADMIN');
    assert.equal(result.email, 'owner@example.test');
  });

  it('rejects another role when a role is required', async () => {
    await assert.rejects(
      signInToPlatform({ email: 'm@example.test', password: 'pw' }, 'SUPER_ADMIN',
        fetchReturning(200, { token: platformToken({ role: 'MANAGER' }) })),
      (error: unknown) => error instanceof ApplicationSignInError && error.status === 403,
    );
  });

  it('rejects a tenant-scoped application token as a platform session', async () => {
    await assert.rejects(
      signInToPlatform({ email: 'e@example.test', password: 'pw' }, undefined,
        fetchReturning(200, { token: inventoryToken({ exp: Date.now() / 1000 + 900 }) })),
      ApplicationSignInError,
    );
  });

  it('stores, reads and expires platform sessions', () => {
    const storage = memoryStorage();
    storePlatformSession({ token: platformToken({ exp: NOW / 1000 + 60 }), email: '', role: '' }, storage);
    assert.equal(readPlatformSession(storage, NOW)?.role, 'SUPER_ADMIN');
    assert.equal(readPlatformSession(storage, NOW + 120_000), null);
    assert.equal(storage.values.size, 0);
  });
});
