import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  MENU, activeFilterCount, age, approvalProblems, availableActions, buildQuery, emptyFilter, filterProblems, flagLabel, idempotencyKey, inspectionProblems, isPartial,
  lifecycleIndex, lifecycleSteps, money, pickupProblems, policyProblems, reasonLabel, refundRows, settingsProblems, statusTone, suggestOutcome, visibleMenu,
} from '../src/returns/returnsModel.ts';

const everything = ['return:view', 'return:approve', 'return:reject', 'return:review', 'return:pickup', 'return:receive', 'return:inspect', 'return:refund-process', 'return:exchange'];
const all = (p: string) => everything.includes(p);

test('the menu has the 20 Returns & Refunds entries and respects permissions', () => {
  assert.equal(MENU.length, 20);
  assert.equal(new Set(MENU.map((m) => m.id)).size, 20);
  assert.equal(visibleMenu('SUPER_ADMIN', undefined).length, 20);
  assert.equal(visibleMenu('X', undefined).length, 0);
  assert.equal(visibleMenu('X', ['order:view']).length, 0, 'order permissions do not open the returns area');
  const viewer = visibleMenu('X', ['return:view']).map((m) => m.id);
  assert.equal(viewer.length, 18);
  assert.ok(!viewer.includes('analytics') && !viewer.includes('audit'));
  const analyst = visibleMenu('X', ['return:view', 'return:analytics-view', 'return:audit-view']).map((m) => m.id);
  assert.ok(analyst.includes('analytics') && analyst.includes('audit'));
  assert.deepEqual(MENU.filter((m) => m.view).map((m) => m.view), ['NEW', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'PICKUP_SCHEDULED', 'IN_TRANSIT', 'RECEIVED', 'INSPECTION_PENDING', 'INSPECTION_COMPLETED', 'REFUND_PENDING', 'REFUND_PROCESSING', 'REFUND_COMPLETED']);
});

test('query strings carry only meaningful filters and clamped paging', () => {
  const f = { ...emptyFilter(), q: ' RMA-000012 ', status: 'APPROVED', from: '2026-10-01', to: '2026-10-31', flagged: true };
  const q = new URLSearchParams(buildQuery('NEW', f, -3, 5000));
  assert.equal(q.get('view'), 'NEW');
  assert.equal(q.get('q'), 'RMA-000012');
  assert.equal(q.get('flagged'), 'true');
  assert.equal(q.get('page'), '0');
  assert.equal(q.get('size'), '100');
  assert.equal(q.has('kind'), false);
  assert.equal(buildQuery(undefined, emptyFilter(), 2, 25), 'page=2&size=25');
  assert.equal(activeFilterCount(f), 5);
  assert.deepEqual(filterProblems({ ...emptyFilter(), from: '2026-10-31', to: '2026-10-01' }), ['From must not be after To.']);
  assert.equal(filterProblems({ ...emptyFilter(), from: 'yesterday' }).length, 1);
});

test('status colours separate success, waiting, in-flight and failure', () => {
  assert.equal(statusTone('REFUNDED'), 'success');
  assert.equal(statusTone('REJECTED'), 'error');
  assert.equal(statusTone('REFUND_FAILED'), 'error');
  assert.equal(statusTone('INSPECTION_PENDING'), 'warning');
  assert.equal(statusTone('IN_TRANSIT'), 'info');
  assert.equal(statusTone('SOMETHING_ELSE'), 'default');
  assert.equal(reasonLabel('SIZE_FIT'), 'Size or fit');
  assert.equal(reasonLabel(null), '—');
  assert.equal(flagLabel('HIGH_VALUE'), 'High value');
  assert.equal(money('12.5'), '12.50');
  assert.equal(money(null), '—');
});

test('approval: quantity limits, partial detection, at least one unit', () => {
  const items = [{ id: 1, orderItemId: 11, productName: 'Widget', quantity: 3 }, { id: 2, orderItemId: 12, productName: 'Gadget', quantity: 1 }];
  assert.deepEqual(approvalProblems(items, {}), []);
  assert.deepEqual(approvalProblems(items, { 11: 2 }), []);
  assert.equal(isPartial(items, { 11: 2 }), true);
  assert.equal(isPartial(items, {}), false);
  assert.match(approvalProblems(items, { 11: 4 })[0], /at most 3/);
  assert.match(approvalProblems(items, { 12: -1 })[0], /whole number/);
  assert.match(approvalProblems(items, { 11: 1.5 })[0], /whole number/);
  assert.deepEqual(approvalProblems(items, { 11: 0, 12: 0 }), ['Approve at least one unit, or reject the request.']);
});

test('inspection rules mirror the API', () => {
  const row = (o: object) => ({ returnItemId: 1, approved: 3, receivedQuantity: 3, acceptedQuantity: 3, condition: 'NEW', restockable: true, note: '', ...o });
  assert.deepEqual(inspectionProblems([row({})], 'ACCEPTED_FOR_REFUND', 'REFUND'), []);
  assert.match(inspectionProblems([row({ receivedQuantity: 4 })], 'ACCEPTED_FOR_REFUND', 'REFUND')[0], /exceed the approved 3/);
  assert.match(inspectionProblems([row({ receivedQuantity: 2 })], 'ACCEPTED_FOR_REFUND', 'REFUND')[0], /Accepted quantity cannot exceed/);
  assert.match(inspectionProblems([row({ condition: 'DAMAGED' })], 'ACCEPTED_FOR_REFUND', 'REFUND')[0], /NEW or LIKE_NEW/);
  assert.match(inspectionProblems([row({ acceptedQuantity: 0, restockable: true })], 'REJECTED', 'REFUND')[0], /NEW or LIKE_NEW/);
  assert.deepEqual(inspectionProblems([row({ acceptedQuantity: 0, restockable: false })], 'REJECTED', 'REFUND'), []);
  assert.match(inspectionProblems([row({ acceptedQuantity: 1, restockable: false })], 'REJECTED', 'REFUND')[0], /cannot accept/);
  assert.match(inspectionProblems([row({ restockable: false })], 'PARTIALLY_ACCEPTED', 'REFUND')[0], /some, but not all/);
  assert.deepEqual(inspectionProblems([row({ acceptedQuantity: 2, restockable: false })], 'PARTIALLY_ACCEPTED', 'REFUND'), []);
  assert.match(inspectionProblems([row({})], 'ACCEPTED_FOR_EXCHANGE', 'REFUND')[0], /did not ask for an exchange/);
  assert.match(inspectionProblems([row({})], 'ACCEPTED_FOR_REFUND', 'EXCHANGE')[0], /exchange/);
  assert.match(inspectionProblems([row({ acceptedQuantity: 0, restockable: false })], 'ACCEPTED_FOR_REFUND', 'REFUND')[0], /at least one accepted unit/);
  assert.equal(suggestOutcome([row({ acceptedQuantity: 0 })], 'REFUND'), 'REJECTED');
  assert.equal(suggestOutcome([row({ acceptedQuantity: 1 })], 'REFUND'), 'PARTIALLY_ACCEPTED');
  assert.equal(suggestOutcome([row({})], 'EXCHANGE'), 'ACCEPTED_FOR_EXCHANGE');
});

test('actions offered follow status, kind and permissions, and never offer money or carrier states', () => {
  const ids = (r: object, can = all, evidence = true) => availableActions({ status: 'REQUESTED', returnKind: 'RETAIL_RETURN', resolution: 'REFUND', ...r }, can, evidence).map((a) => a.id);
  assert.deepEqual(ids({}), ['approve', 'reject', 'request-info', 'to:CUSTOMER_CANCELLED'], 'Start review is only offered when the API lists UNDER_REVIEW as allowed');
  assert.ok(ids({ allowedTransitions: ['UNDER_REVIEW'] }).includes('to:UNDER_REVIEW'));
  assert.ok(!ids({ evidenceRequired: true }, all, false).includes('approve'), 'evidence-required cases cannot be approved without evidence');
  assert.ok(ids({ evidenceRequired: true }, all, false).includes('request-info'));
  assert.ok(ids({ evidenceRequired: true }, all, true).includes('approve'));
  assert.ok(!ids({}, (p) => p === 'return:view').includes('approve'));
  assert.deepEqual(ids({}, (p) => p === 'return:view'), []);
  assert.deepEqual(ids({ status: 'APPROVED' }).filter((x) => x === 'pickup' || x === 'to:RECEIVED'), ['pickup', 'to:RECEIVED']);
  assert.ok(!ids({ status: 'APPROVED', returnKind: 'RESTAURANT_CLAIM' }).includes('pickup'), 'restaurant claims have no pickup');
  assert.ok(ids({ status: 'APPROVED', returnKind: 'RESTAURANT_CLAIM' }).includes('refund'), 'claims refund straight from approval');
  assert.ok(!ids({ status: 'APPROVED' }).includes('refund'), 'retail needs the goods back first');
  assert.ok(ids({ status: 'RECEIVED' }).includes('to:INSPECTION_PENDING'));
  assert.ok(ids({ status: 'INSPECTION_PENDING' }).includes('inspect'));
  assert.ok(ids({ status: 'INSPECTION_COMPLETED' }).includes('refund'));
  assert.ok(!ids({ status: 'INSPECTION_COMPLETED', resolution: 'EXCHANGE' }).includes('refund'));
  assert.ok(ids({ status: 'INSPECTION_COMPLETED', resolution: 'EXCHANGE' }).includes('exchange'));
  assert.ok(ids({ status: 'PICKUP_FAILED' }).includes('pickup'));
  assert.ok(ids({ status: 'PICKUP_SCHEDULED' }).includes('pickup-cancel'));
  const allIds = ['REQUESTED', 'APPROVED', 'RECEIVED', 'REFUND_PENDING', 'REFUND_PROCESSING', 'REFUNDED', 'IN_TRANSIT'].flatMap((s) => ids({ status: s }));
  for (const forbidden of ['to:REFUNDED', 'to:REFUND_FAILED', 'to:PICKED_UP', 'to:IN_TRANSIT', 'to:REFUND_PROCESSING', 'to:EXCHANGED', 'to:APPROVED', 'to:REJECTED']) assert.ok(!allIds.includes(forbidden), forbidden);
  assert.deepEqual(ids({ status: 'REFUND_PROCESSING' }), [], 'nothing can be done by hand while Payment is working');
  assert.deepEqual(ids({ status: 'REFUNDED', allowedTransitions: ['CLOSED'] }), ['to:CLOSED']);
  assert.deepEqual(ids({ status: 'CLOSED' }), []);
});

test('lifecycle steps differ for claims and exchanges', () => {
  assert.ok(!lifecycleSteps('RESTAURANT_CLAIM', 'REFUND').includes('INSPECTION_PENDING'));
  assert.ok(lifecycleSteps('RETAIL_RETURN', 'EXCHANGE').includes('EXCHANGED'));
  assert.ok(!lifecycleSteps('RETAIL_RETURN', 'EXCHANGE').includes('REFUNDED'));
  assert.equal(lifecycleIndex(lifecycleSteps('RETAIL_RETURN', 'REFUND'), 'RECEIVED'), 6);
  assert.equal(lifecycleIndex(lifecycleSteps('RETAIL_RETURN', 'REFUND'), 'REJECTED'), -1);
});

test('refund breakdown shows every component and a final amount', () => {
  const rows = refundRows({ originalAmount: 54.5, previouslyRefunded: 0, itemsAmount: 50, discountAllocation: 5, taxAdjustment: 4.5, deliveryFeeRefund: 5, fees: 0, finalAmount: 54.5 });
  assert.equal(rows.length, 8);
  assert.deepEqual(rows[7], { label: 'Final refund', value: '54.50', strong: true });
  assert.equal(rows[3].value, '-5.00');
});

test('pickup windows must be sensible', () => {
  const now = new Date('2026-11-01T10:00:00Z');
  assert.deepEqual(pickupProblems('2026-11-01T12:00:00Z', '2026-11-01T14:00:00Z', now), []);
  assert.match(pickupProblems('', '2026-11-01T14:00:00Z', now)[0], /starts/);
  assert.match(pickupProblems('2026-11-01T14:00:00Z', '2026-11-01T12:00:00Z', now)[0], /end after/);
  assert.match(pickupProblems('2026-10-31T10:00:00Z', '2026-11-01T12:00:00Z', now)[0], /past/);
});

test('policy and settings drafts are checked before they are sent', () => {
  const ok = { returnWindowDays: 30, maxQuantityPercent: 100, restockingFeePercent: 10, claimWindowHours: 48, reasons: [{ code: 'DEFECTIVE', label: 'Defective' }] };
  assert.deepEqual(policyProblems(ok), []);
  assert.equal(policyProblems({ ...ok, returnWindowDays: 5000 }).length, 1);
  assert.equal(policyProblems({ ...ok, restockingFeePercent: 80 }).length, 1);
  assert.equal(policyProblems({ ...ok, reasons: [] }).length, 1);
  assert.equal(policyProblems({ ...ok, reasons: [{ code: 'bad code', label: 'x' }] }).length, 1);
  assert.equal(policyProblems({ ...ok, reasons: [{ code: 'A_B', label: '<b>' }] }).length, 1);
  assert.equal(policyProblems({ ...ok, reasons: [{ code: 'A_B', label: 'a' }, { code: 'A_B', label: 'b' }] }).length, 1);
  const s = { highValueRefundThreshold: 500, refundApprovalThreshold: 100, repeatReturnCount: 3, repeatReturnDays: 30, pickupFailureLimit: 2, lostShipmentDays: 14, reviewSlaHours: 48, refundSlaHours: 72 };
  assert.deepEqual(settingsProblems(s), []);
  assert.equal(settingsProblems({ ...s, highValueRefundThreshold: 0 }).length, 1);
  assert.equal(settingsProblems({ ...s, repeatReturnCount: 1 }).length, 1);
  assert.equal(settingsProblems({ ...s, refundSlaHours: 3000, reviewSlaHours: 0.5 }).length, 2);
});

test('keys and ages', () => {
  assert.match(idempotencyKey(), /^rma-[0-9a-f]{24}$/);
  assert.notEqual(idempotencyKey(), idempotencyKey());
  const now = Date.parse('2026-11-01T10:00:00Z');
  assert.equal(age('2026-11-01T09:30:00Z', now), '30m');
  assert.equal(age('2026-11-01T05:00:00Z', now), '5h');
  assert.equal(age('2026-10-29T10:00:00Z', now), '3d');
  assert.equal(age(null, now), '—');
});
