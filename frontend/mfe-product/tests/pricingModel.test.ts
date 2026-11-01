import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  buildCampaignRequest, campaignActions, emptyCampaignForm, formatMoney, hasPermission, parseIds, precheckPriceCsv, statusTone,
  validateCampaignForm, validatePriceForm, visibleSections,
} from '../src/pricing/pricingModel.ts';

const CURRENCIES = ['USD', 'EUR'];
const future = (days: number) => new Date(Date.now() + days * 86_400_000).toISOString();

test('navigation shows only the sections a user may use', () => {
  assert.deepEqual(visibleSections('USER', undefined), []);
  const analyst = visibleSections('PRICING_ANALYST', ['pricing:read', 'promotion:read', 'pricing:audit']).map((s) => s.id);
  assert.ok(analyst.includes('audit') && analyst.includes('dashboard') && !analyst.includes('bulk'));
  const readOnly = visibleSections('X', ['pricing:read']).map((s) => s.id);
  assert.ok(!readOnly.includes('audit') && !readOnly.includes('campaigns') && readOnly.includes('prices'));
  assert.equal(visibleSections('SUPER_ADMIN', undefined).length, 15);
  assert.equal(hasPermission('X', ['*'], 'anything'), true);
});

test('price form rejects zero, negative, over-precise, unsupported currency and sale above base', () => {
  const ok = { productId: '5', currency: 'usd', basePrice: '10.50', salePrice: '9.99', costPrice: '' };
  assert.deepEqual(validatePriceForm(ok, CURRENCIES, false), []);
  assert.ok(validatePriceForm({ ...ok, basePrice: '0' }, CURRENCIES, false).length > 0);
  assert.ok(validatePriceForm({ ...ok, basePrice: '-5' }, CURRENCIES, false).length > 0);
  assert.ok(validatePriceForm({ ...ok, basePrice: '10.123' }, CURRENCIES, false).length > 0);
  assert.ok(validatePriceForm({ ...ok, currency: 'JPY' }, CURRENCIES, false).length > 0);
  assert.ok(validatePriceForm({ ...ok, salePrice: '11' }, CURRENCIES, false).some((e) => e.includes('exceed')));
  assert.ok(validatePriceForm({ ...ok, productId: '' }, CURRENCIES, false).length > 0);
});

test('cost price needs the restricted permission', () => {
  const form = { productId: '5', currency: 'USD', basePrice: '10', salePrice: '', costPrice: '4' };
  assert.ok(validatePriceForm(form, CURRENCIES, false).some((e) => e.includes('permission')));
  assert.deepEqual(validatePriceForm(form, CURRENCIES, true), []);
});

test('campaign form validation covers dates, percent limit, targets, bundles and limits', () => {
  const base = emptyCampaignForm('PERCENTAGE', { name: 'Sale', startsAt: future(1), endsAt: future(5), targetIds: '1, 2', value: '10' });
  assert.deepEqual(validateCampaignForm(base, 50, CURRENCIES), []);
  assert.ok(validateCampaignForm({ ...base, value: '60' }, 50, CURRENCIES).some((e) => e.includes('maximum')));
  assert.ok(validateCampaignForm({ ...base, endsAt: future(-1) }, 50, CURRENCIES).length > 0);
  assert.ok(validateCampaignForm({ ...base, startsAt: future(6) }, 50, CURRENCIES).some((e) => e.includes('after')));
  assert.ok(validateCampaignForm({ ...base, targetIds: '' }, 50, CURRENCIES).length > 0);
  assert.deepEqual(validateCampaignForm({ ...base, scope: 'STORE_WIDE', targetIds: '' }, 50, CURRENCIES), []);
  assert.ok(validateCampaignForm({ ...emptyCampaignForm('BUNDLE', { name: 'B', startsAt: future(1), targetIds: '1', value: '10' }) }, 50, CURRENCIES).some((e) => e.includes('two')));
  assert.ok(validateCampaignForm({ ...base, usageLimitTotal: '0' }, 50, CURRENCIES).length > 0);
  assert.ok(validateCampaignForm({ ...base, automatic: true, usageLimitPerCustomer: '1' }, 50, CURRENCIES).length > 0);
  assert.ok(validateCampaignForm({ ...base, minOrderValue: '-1' }, 50, CURRENCIES).length > 0);
});

test('campaign request is shaped for each promotion type', () => {
  const start = future(1);
  const pct = buildCampaignRequest(emptyCampaignForm('PERCENTAGE', { name: ' A ', startsAt: start, targetIds: '7', value: '15' }));
  assert.equal(pct.name, 'A');
  assert.deepEqual(pct.rewards, [{ type: 'PERCENT_OFF', percent: 15 }]);
  assert.deepEqual(pct.rules, [{ role: 'TRIGGER', effect: 'INCLUDE', targetType: 'PRODUCT', targetId: 7 }]);
  assert.equal(pct.endsAt, null);

  const cat = buildCampaignRequest(emptyCampaignForm('FIXED_AMOUNT', { name: 'C', scope: 'CATEGORY', startsAt: start, targetIds: '3', value: '5' }));
  assert.equal(cat.rules[0].targetType, 'CATEGORY');
  assert.deepEqual(cat.rewards, [{ type: 'AMOUNT_OFF', amount: 5 }]);

  const wide = buildCampaignRequest(emptyCampaignForm('PERCENTAGE', { name: 'W', scope: 'STORE_WIDE', startsAt: start, value: '5' }));
  assert.deepEqual(wide.rules, []);

  const bxgy = buildCampaignRequest(emptyCampaignForm('BUY_X_GET_Y', { name: 'X', startsAt: start, targetIds: '1', buyQuantity: '2', getQuantity: '1' }));
  assert.deepEqual(bxgy.rewards, [{ type: 'BUY_X_GET_Y', buyQuantity: 2, getQuantity: 1, percent: null }]);
});

test('parseIds ignores junk, zero, negatives and decimals', () => {
  assert.deepEqual(parseIds('1, 2;3 x 0 -4 5.5'), [1, 2, 3]);
});

test('CSV precheck reports row errors and missing columns', () => {
  assert.deepEqual(precheckPriceCsv('productId,currency,basePrice\n1,USD,10.00\n2,EUR,5', CURRENCIES), []);
  assert.ok(precheckPriceCsv('productId,currency\n1,USD', CURRENCIES).some((e) => e.includes('basePrice')));
  const errors = precheckPriceCsv('productId,currency,basePrice,salePrice\nabc,USD,10,\n2,JPY,5,\n3,USD,-1,\n4,USD,5,6', CURRENCIES);
  assert.equal(errors.length, 4);
  assert.ok(precheckPriceCsv('productId,currency,basePrice', CURRENCIES).length > 0);
});

test('money formatting and status helpers', () => {
  assert.equal(formatMoney('12.5', 'USD'), '$12.50');
  assert.equal(formatMoney(null, 'USD'), '—');
  assert.equal(formatMoney('abc', 'USD'), '—');
  assert.equal(statusTone('PUBLISHED'), 'success');
  assert.equal(statusTone('REVOKED'), 'error');
  assert.deepEqual(campaignActions('APPROVED'), ['publish', 'cancel']);
  assert.deepEqual(campaignActions('CANCELLED'), []);
});

test('price guard rails: minimum selling price, max discount and effective window', async () => {
  const { buildPriceRequest } = await import('../src/pricing/pricingModel.ts');
  const ok = { productId: '5', currency: 'USD', basePrice: '100', salePrice: '95', costPrice: '', minSellingPrice: '90', maxDiscountPercent: '20' };
  assert.deepEqual(validatePriceForm(ok, CURRENCIES, false), []);
  assert.ok(validatePriceForm({ ...ok, minSellingPrice: '96' }, CURRENCIES, false).some((e) => e.includes('Minimum selling')));
  assert.ok(validatePriceForm({ ...ok, maxDiscountPercent: '120' }, CURRENCIES, false).length > 0);
  assert.ok(validatePriceForm({ ...ok, effectiveFrom: '2030-01-02T00:00', effectiveTo: '2030-01-01T00:00' }, CURRENCIES, false).length > 0);
  const body = buildPriceRequest({ ...ok, taxInclusive: true, effectiveFrom: '2030-01-01T00:00' });
  assert.equal(body.minSellingPrice, 90);
  assert.equal(body.taxInclusive, true);
  assert.equal(body.effectiveTo, null);
  assert.equal(buildPriceRequest({ ...ok, minSellingPrice: '' }).minSellingPrice, null);
});
