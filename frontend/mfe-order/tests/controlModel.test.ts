import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  MENU, activeFilterCount, bulkActionsFor, bulkParamProblems, buildQuery, emptyFilter, filterProblems, formatMinutes, idempotencyKey, nextSort, previewSummary,
  restaurantActions, sanitizeColumns, slaTone, statusTone, toggleSelection, togglePage, transitionDialog, transitionsForMenu, visibleMenu, BULK_ACTIONS,
} from '../src/control/controlModel.ts';

test('menu has all 19 entries and respects permissions', () => {
  assert.equal(MENU.length, 19);
  assert.equal(visibleMenu('SUPER_ADMIN', undefined).length, 19);
  assert.equal(visibleMenu('X', undefined).length, 0);
  const viewer = visibleMenu('X', ['order:view']).map((m) => m.id);
  assert.ok(viewer.includes('all') && viewer.includes('exceptions') && viewer.includes('settings'));
  assert.ok(!viewer.includes('bulk') && !viewer.includes('reports') && !viewer.includes('audit'));
  const analyst = visibleMenu('X', ['order:view', 'order:report:view', 'order:audit:view']).map((m) => m.id);
  assert.ok(analyst.includes('reports') && analyst.includes('audit'));
  assert.deepEqual(MENU.filter((m) => m.view).map((m) => m.view), ['NEW', 'PENDING_CONFIRMATION', 'PROCESSING', 'READY_FOR_FULFILLMENT', 'PARTIALLY_FULFILLED', 'SHIPPED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED', 'FAILED', 'ON_HOLD']);
});

test('query strings carry only meaningful filters, whitelisted sorts and clamped paging', () => {
  const f = { ...emptyFilter('NEW'), q: ' ORD-000012 ', paymentStatus: 'PAID', status: ['CREATED', 'PLACED'], from: '2026-10-01', to: '2026-10-31' };
  const q = new URLSearchParams(buildQuery(f, [{ field: 'totalAmount', dir: 'desc' }, { field: 'password', dir: 'asc' }, { field: 'status', dir: 'asc' }], -3, 5000));
  assert.equal(q.get('q'), 'ORD-000012');
  assert.equal(q.get('view'), 'NEW');
  assert.deepEqual(q.getAll('status'), ['CREATED', 'PLACED']);
  assert.deepEqual(q.getAll('sort'), ['totalAmount:desc', 'status:asc']);
  assert.equal(q.get('page'), '0');
  assert.equal(q.get('size'), '100');
  assert.equal(q.has('vendorId'), false);
  assert.equal(new URLSearchParams(buildQuery(emptyFilter(), [], 2, 25)).toString(), 'page=2&size=25');
});

test('filter validation explains mistakes', () => {
  assert.deepEqual(filterProblems(emptyFilter()), []);
  assert.equal(filterProblems({ ...emptyFilter(), from: '2026-10-31', to: '2026-10-01' }).length, 1);
  assert.equal(filterProblems({ ...emptyFilter(), minAmount: '10', maxAmount: '5' }).length, 1);
  assert.equal(filterProblems({ ...emptyFilter(), minAmount: '-1' }).length, 1);
  assert.equal(filterProblems({ ...emptyFilter(), operatorId: 'bob' }).length, 1);
  assert.equal(activeFilterCount({ ...emptyFilter('NEW'), q: 'x', paymentStatus: 'PAID' }, 'NEW'), 2);
  assert.equal(activeFilterCount(emptyFilter('NEW'), 'NEW'), 0);
});

test('multi-column sorting: click cycles a single sort, shift-click adds secondary sorts', () => {
  let s = nextSort([], 'totalAmount', false);
  assert.deepEqual(s, [{ field: 'totalAmount', dir: 'asc' }]);
  s = nextSort(s, 'totalAmount', false);
  assert.deepEqual(s, [{ field: 'totalAmount', dir: 'desc' }]);
  s = nextSort(s, 'totalAmount', false);
  assert.deepEqual(s, []);
  s = nextSort([{ field: 'status', dir: 'asc' }], 'totalAmount', true);
  assert.deepEqual(s, [{ field: 'status', dir: 'asc' }, { field: 'totalAmount', dir: 'asc' }]);
  s = nextSort(s, 'totalAmount', true);
  assert.deepEqual(s[1], { field: 'totalAmount', dir: 'desc' });
  assert.deepEqual(nextSort(s, 'createdAt', false), [{ field: 'createdAt', dir: 'asc' }]);
});

test('status, SLA and duration helpers', () => {
  assert.equal(statusTone('DELIVERED'), 'success');
  assert.equal(statusTone('PAYMENT_FAILED'), 'error');
  assert.equal(statusTone('ON_HOLD'), 'warning');
  assert.equal(statusTone('PROCESSING'), 'info');
  assert.equal(slaTone('BREACHED'), 'error');
  assert.equal(slaTone('AT_RISK'), 'warning');
  assert.equal(slaTone('NONE'), 'default');
  assert.equal(formatMinutes(null), '—');
  assert.equal(formatMinutes(45), '45 min');
  assert.equal(formatMinutes(125), '2 h 5 min');
  assert.equal(formatMinutes(180), '3 h');
  assert.equal(formatMinutes(3000), '2 d 2 h');
});

test('transition confirmation requires a reason where the backend does, and blocked ones are explained not offered', () => {
  const hold = { to: 'ON_HOLD', allowed: true, reasonRequired: true };
  assert.equal(transitionDialog(hold).needsReason, true);
  assert.equal(transitionDialog({ to: 'CANCELLED', allowed: true, reasonRequired: true }).destructive, true);
  assert.equal(transitionDialog({ to: 'PROCESSING', allowed: true, reasonRequired: false }).needsReason, false);
  assert.equal(transitionDialog({ to: 'REFUNDED', allowed: false, reasonRequired: false, blockedReason: 'Set by Payment' }).canSubmit, false);
  const split = transitionsForMenu([hold, { to: 'FULFILLED', allowed: false, reasonRequired: false, blockedReason: 'x' }]);
  assert.equal(split.allowed.length, 1);
  assert.equal(split.blocked[0].to, 'FULFILLED');
});

test('bulk actions follow permissions; parameters are validated; there is no raw status change', () => {
  assert.deepEqual(bulkActionsFor('X', ['order:view']), []);
  assert.deepEqual(bulkActionsFor('X', ['order:bulk']), []);
  assert.deepEqual(bulkActionsFor('X', ['order:bulk', 'order:hold']).map((a) => a.action), ['HOLD']);
  assert.equal(bulkActionsFor('SUPER_ADMIN', undefined).length, BULK_ACTIONS.length);
  assert.ok(!BULK_ACTIONS.some((a) => /STATUS/.test(a.action)));
  const hold = BULK_ACTIONS.find((a) => a.action === 'HOLD')!;
  assert.equal(bulkParamProblems(hold, {}).length, 1);
  assert.equal(bulkParamProblems(hold, { reason: '<b>x</b>' }).length, 1);
  assert.deepEqual(bulkParamProblems(hold, { reason: 'Audit' }), []);
  const assign = BULK_ACTIONS.find((a) => a.action === 'ASSIGN_OPERATOR')!;
  assert.equal(bulkParamProblems(assign, { operatorId: 'abc' }).length, 1);
  assert.equal(bulkParamProblems(assign, { operatorId: '0' }).length, 1);
  assert.deepEqual(bulkParamProblems(assign, { operatorId: '42' }), []);
  assert.deepEqual(previewSummary({ totalCount: 5, eligibleCount: 3 }), { skipped: 2, canExecute: true, text: '3 of 5 order(s) can be changed; 2 will be skipped.' });
  assert.equal(previewSummary({ totalCount: 2, eligibleCount: 0 }).canExecute, false);
});

test('selection helpers and column settings', () => {
  assert.deepEqual(toggleSelection([1, 2], 2), [1]);
  assert.deepEqual(toggleSelection([1], 3), [1, 3]);
  assert.deepEqual(togglePage([5], [1, 2, 3]), [5, 1, 2, 3]);
  assert.deepEqual(togglePage([1, 2, 3, 5], [1, 2, 3]), [5]);
  assert.equal(sanitizeColumns(['status', 'evil', 'orderNumber']).join(), 'orderNumber,status');
  assert.equal(sanitizeColumns('nope').length, 14);
  assert.equal(sanitizeColumns([]).length, 14);
  assert.notEqual(idempotencyKey(), idempotencyKey());
  assert.match(idempotencyKey(), /^[A-Za-z0-9_.:-]{8,80}$/);
});

test('restaurant kitchen actions follow the restaurant lifecycle only', () => {
  assert.deepEqual(restaurantActions('RECEIVED').map((a) => a.action), ['accept', 'reject']);
  assert.equal(restaurantActions('RECEIVED')[0].needsPrep, true);
  assert.deepEqual(restaurantActions('PREPARING').map((a) => a.action), ['ready', 'delay']);
  assert.deepEqual(restaurantActions('DELIVERED'), []);
  assert.deepEqual(restaurantActions('PROCESSING'), []); // retail status: no kitchen actions
});
