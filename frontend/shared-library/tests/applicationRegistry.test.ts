import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  APPLICATION_REGISTRY,
  buildApplicationRegistry,
  normalizeOrigin,
  resolveSafeRedirect,
} from '../src/config/applicationRegistry.ts';

const origins = {
  SUPER_ADMIN: 'https://platform.example.com',
  INVENTORY: 'https://inventory.example.com',
  DELIVERY_OPS: 'https://delivery.example.com',
  QUALITY_CENTER: 'https://quality.example.com',
};

describe('normalizeOrigin', () => {
  it('accepts HTTPS origins and strips a trailing slash', () => {
    assert.equal(normalizeOrigin('https://inventory.example.com/'), 'https://inventory.example.com');
  });

  it('accepts plain HTTP only for localhost', () => {
    assert.equal(normalizeOrigin('http://localhost:3004'), 'http://localhost:3004');
    assert.equal(normalizeOrigin('http://inventory.example.com'), null);
  });

  it('rejects paths, credentials, queries and malformed values', () => {
    assert.equal(normalizeOrigin('https://inventory.example.com/app'), null);
    assert.equal(normalizeOrigin('https://user:pw@inventory.example.com'), null);
    assert.equal(normalizeOrigin('https://inventory.example.com?next=/'), null);
    assert.equal(normalizeOrigin('javascript:alert(1)'), null);
    assert.equal(normalizeOrigin('not a url'), null);
  });
});

describe('buildApplicationRegistry', () => {
  it('requires an origin for every ACTIVE application', () => {
    assert.throws(() => buildApplicationRegistry({ ...origins, INVENTORY: undefined }), /INVENTORY/);
  });

  it('fills origins for active applications and leaves planned ones empty', () => {
    const registry = buildApplicationRegistry(origins);
    assert.equal(registry.INVENTORY.origin, 'https://inventory.example.com');
    assert.equal(registry.CRM.origin, '');
    assert.equal(APPLICATION_REGISTRY.INVENTORY.origin, '');
  });

  it('rejects an invalid origin for an active application', () => {
    assert.throws(
      () => buildApplicationRegistry({ ...origins, INVENTORY: 'http://inventory.example.com' }),
      /Invalid origin for application INVENTORY/,
    );
  });
});

describe('resolveSafeRedirect', () => {
  const registry = buildApplicationRegistry(origins);

  it('allows relative paths on the application origin', () => {
    assert.equal(
      resolveSafeRedirect('/stock/transfers', 'INVENTORY', registry),
      'https://inventory.example.com/stock/transfers',
    );
  });

  it('allows absolute URLs only on the same registered origin', () => {
    assert.equal(
      resolveSafeRedirect('https://inventory.example.com/x', 'INVENTORY', registry),
      'https://inventory.example.com/x',
    );
    assert.equal(resolveSafeRedirect('https://evil.example.net/x', 'INVENTORY', registry), null);
  });

  it('rejects protocol-relative, backslash and javascript targets', () => {
    assert.equal(resolveSafeRedirect('//evil.example.net', 'INVENTORY', registry), null);
    assert.equal(resolveSafeRedirect('/\\evil.example.net', 'INVENTORY', registry), null);
    assert.equal(resolveSafeRedirect('javascript:alert(1)', 'INVENTORY', registry), null);
  });

  it('returns null for missing targets and planned applications', () => {
    assert.equal(resolveSafeRedirect(undefined, 'INVENTORY', registry), null);
    assert.equal(resolveSafeRedirect('/x', 'CRM', registry), null);
  });
});
