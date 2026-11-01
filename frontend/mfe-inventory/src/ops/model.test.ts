import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  SCREENS, visibleScreens, availableQuantity, stockStatus, transferActions, poTotals, validatePoForm, poActions, acceptedQuantity,
  validateReceipt, newIdempotencyKey, adjustmentNeedsApproval, validateAdjustment, validateWarehouse, dashboardCards, toCsv, parseCsv, csvCell,
} from './model.ts';

test('navigation has the 21 required screens', () => {
  assert.equal(SCREENS.length, 21);
  assert.equal(new Set(SCREENS.map(s => s.key)).size, 21);
  assert.equal(SCREENS[0].label, 'Inventory Dashboard');
});

test('screens are filtered by granted permissions and wildcard shows all', () => {
  assert.equal(visibleScreens(['*']).length, 21);
  const keys = visibleScreens(['supplier:view']).map(s => s.key);
  assert.deepEqual(keys, ['suppliers']);
  assert.equal(visibleScreens([]).length, 0);
});

test('listing: available excludes reserved/unavailable and never counts incoming', () => {
  assert.equal(availableQuantity(100, 20, 10, 500), 70);
  assert.equal(availableQuantity(5, 9, 0), 0);
  assert.equal(stockStatus(0, 5), 'OUT_OF_STOCK');
  assert.equal(stockStatus(5, 5), 'LOW_STOCK');
  assert.equal(stockStatus(6, 5), 'IN_STOCK');
});

test('warehouse form validation', () => {
  assert.deepEqual(validateWarehouse({ code: 'MAIN-1', name: 'Main', capacityUnits: 10 }), []);
  assert.equal(validateWarehouse({ code: 'a b', name: ' ' }).length, 2);
  assert.equal(validateWarehouse({ code: 'OK', name: 'x', capacityUnits: -1 }).length, 1);
});

test('transfer workflow: actions follow status and permissions', () => {
  const op = ['inventory:transfer'];
  assert.deepEqual(transferActions('DRAFT', op).map(a => a.action), ['request', 'cancel']);
  assert.deepEqual(transferActions('REQUESTED', op).map(a => a.action), ['cancel']); // approval needs transfer-approve
  assert.deepEqual(transferActions('REQUESTED', [...op, 'inventory:transfer-approve']).map(a => a.action), ['approve', 'reject', 'cancel']);
  assert.deepEqual(transferActions('IN_TRANSIT', op).map(a => a.action), ['receive', 'lost', 'damaged']);
  assert.deepEqual(transferActions('COMPLETED', ['*']), []);
  assert.deepEqual(transferActions('REJECTED', ['*']), []);
});

test('PO form: totals use discount, tax on discounted amount, and shipping', () => {
  const t = poTotals([{ quantity: 100, unitCost: 10 }], 100, 18, 2);
  assert.deepEqual(t, { subtotal: 1000, tax: 162, total: 1064 });
  assert.throws(() => poTotals([{ quantity: 1, unitCost: 5 }], 10, 0, 0));
});

test('PO form validation catches missing supplier, empty and duplicate lines', () => {
  assert.equal(validatePoForm({ lines: [] }).length, 3);
  const errs = validatePoForm({ supplierId: 1, warehouseId: 1, lines: [{ productId: 5, quantity: 2 }, { productId: 5, quantity: 0 }] });
  assert.ok(errs.some(e => e.includes('duplicate')));
  assert.ok(errs.some(e => e.includes('quantity')));
  assert.deepEqual(validatePoForm({ supplierId: 1, warehouseId: 2, lines: [{ productId: 5, quantity: 2, unitCost: 3 }] }), []);
});

test('PO actions respect approver permission', () => {
  assert.deepEqual(poActions('PENDING_APPROVAL', ['purchase-order:manage']), []);
  assert.deepEqual(poActions('PENDING_APPROVAL', ['purchase-order:approve']).map(a => a.action), ['approve', 'reject']);
  assert.deepEqual(poActions('RECEIVED', ['*']), []);
});

test('receiving: only accepted quantity counts and invalid splits are rejected', () => {
  assert.equal(acceptedQuantity(10, 1, 2), 7);
  assert.throws(() => acceptedQuantity(5, 3, 3));
  assert.deepEqual(validateReceipt([{ poLineId: 1, received: 0, rejected: 0, damaged: 0 }]).length, 1);
  assert.deepEqual(validateReceipt([{ poLineId: 1, received: 5, rejected: 3, damaged: 3 }]).length, 1);
  assert.deepEqual(validateReceipt([{ poLineId: 1, received: 5, rejected: 1, damaged: 1 }]), []);
  assert.equal(newIdempotencyKey(7, 'abc'), 'ui-7-abc');
});

test('adjustments need a reason and approval above the threshold', () => {
  assert.equal(adjustmentNeedsApproval(-21, 20), true);
  assert.equal(adjustmentNeedsApproval(20, 20), false);
  assert.equal(validateAdjustment({ warehouseId: 1, productId: 2, newQuantity: 3, reason: '  ' }).length, 1);
  assert.deepEqual(validateAdjustment({ warehouseId: 1, productId: 2, newQuantity: 3, reason: 'Count' }), []);
});

test('dashboard cards show backend metrics and flag problems', () => {
  const cards = dashboardCards({ skus: 3, onHandUnits: 100, reservedUnits: 10, availableUnits: 90, incomingUnits: 40, lowStock: 2, outOfStock: 0, valuation: 1234.5, pendingAdjustments: 1, openTransfers: 0, warehouses: 2 });
  assert.equal(cards.find(c => c.label === 'Low stock')?.tone, 'warn');
  assert.equal(cards.find(c => c.label === 'Out of stock')?.tone, 'ok');
  assert.equal(cards.find(c => c.label === 'Stock value')?.value, '$1,234.50');
  assert.ok(cards.some(c => c.label.startsWith('Incoming')));
});

test('csv export neutralises formulas and import round-trips', () => {
  assert.equal(csvCell('=SUM(A1)'), `"'=SUM(A1)"`);
  assert.equal(csvCell('say "hi"'), '"say ""hi"""');
  const csv = toCsv([{ sku: 'A,1', qty: 3 }], ['sku', 'qty']);
  assert.deepEqual(parseCsv(csv), [{ sku: 'A,1', qty: '3' }]);
  assert.deepEqual(parseCsv('only-header'), []);
});
