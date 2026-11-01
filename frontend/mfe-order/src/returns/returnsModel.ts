// Pure logic for the Returns & Refund Management screens (no React; unit-tested with node --test).
// The backend validates and authorises everything again; this module only shapes requests and decides what the UI offers.

export type ScreenId =
  | 'dashboard' | 'all' | 'new' | 'pending' | 'approved' | 'rejected' | 'pickup' | 'transit' | 'received' | 'inspectionPending' | 'inspectionDone'
  | 'refundPending' | 'refundProcessing' | 'refundDone' | 'exchanges' | 'exceptions' | 'policies' | 'config' | 'analytics' | 'audit';

export interface MenuItem { id: ScreenId; label: string; view?: string; anyOf: string[] }

export const MENU: readonly MenuItem[] = [
  { id: 'dashboard', label: 'Returns Dashboard', anyOf: ['return:view'] },
  { id: 'all', label: 'All Return Requests', anyOf: ['return:view'] },
  { id: 'new', label: 'New Requests', view: 'NEW', anyOf: ['return:view'] },
  { id: 'pending', label: 'Pending Approval', view: 'PENDING_APPROVAL', anyOf: ['return:view'] },
  { id: 'approved', label: 'Approved', view: 'APPROVED', anyOf: ['return:view'] },
  { id: 'rejected', label: 'Rejected', view: 'REJECTED', anyOf: ['return:view'] },
  { id: 'pickup', label: 'Pickup Scheduled', view: 'PICKUP_SCHEDULED', anyOf: ['return:view'] },
  { id: 'transit', label: 'In Transit', view: 'IN_TRANSIT', anyOf: ['return:view'] },
  { id: 'received', label: 'Received', view: 'RECEIVED', anyOf: ['return:view'] },
  { id: 'inspectionPending', label: 'Inspection Pending', view: 'INSPECTION_PENDING', anyOf: ['return:view'] },
  { id: 'inspectionDone', label: 'Inspection Completed', view: 'INSPECTION_COMPLETED', anyOf: ['return:view'] },
  { id: 'refundPending', label: 'Refund Pending', view: 'REFUND_PENDING', anyOf: ['return:view'] },
  { id: 'refundProcessing', label: 'Refund Processing', view: 'REFUND_PROCESSING', anyOf: ['return:view'] },
  { id: 'refundDone', label: 'Refund Completed', view: 'REFUND_COMPLETED', anyOf: ['return:view'] },
  { id: 'exchanges', label: 'Exchange Requests', anyOf: ['return:view'] },
  { id: 'exceptions', label: 'Return Exceptions', anyOf: ['return:view'] },
  { id: 'policies', label: 'Return Policies', anyOf: ['return:view'] },
  { id: 'config', label: 'Refund Configuration', anyOf: ['return:view'] },
  { id: 'analytics', label: 'Return Analytics', anyOf: ['return:analytics-view'] },
  { id: 'audit', label: 'Return Audit History', anyOf: ['return:audit-view'] },
];

export function can(role: string | undefined, permissions: readonly string[] | undefined, wanted: string): boolean {
  if (role === 'SUPER_ADMIN') return true;
  return !!permissions && (permissions.includes('*') || permissions.includes(wanted));
}

export function visibleMenu(role: string | undefined, permissions: readonly string[] | undefined): MenuItem[] {
  return MENU.filter((m) => m.anyOf.some((p) => can(role, permissions, p)));
}

export type Tone = 'default' | 'success' | 'warning' | 'error' | 'info';

export function statusTone(status: string): Tone {
  switch (status) {
    case 'REFUNDED': case 'CLOSED': case 'EXCHANGED': case 'COMPLETED': case 'APPROVED': case 'SUCCEEDED': case 'PUBLISHED': return 'success';
    case 'REJECTED': case 'CUSTOMER_CANCELLED': case 'PICKUP_FAILED': case 'RETURN_LOST': case 'INSPECTION_FAILED': case 'REFUND_FAILED': case 'FAILED': case 'CANCELLED': case 'CRITICAL': return 'error';
    case 'REQUESTED': case 'UNDER_REVIEW': case 'INSPECTION_PENDING': case 'REFUND_PENDING': case 'EXCHANGE_PENDING': case 'PENDING_APPROVAL': case 'IN_REVIEW': case 'HIGH': case 'OPEN': case 'CALCULATED': return 'warning';
    case 'PICKUP_SCHEDULED': case 'PICKED_UP': case 'IN_TRANSIT': case 'RECEIVED': case 'INSPECTION_COMPLETED': case 'REFUND_PROCESSING': case 'PROCESSING': case 'FULFILLMENT_CREATED': case 'ASSIGNED': case 'ESCALATED': return 'info';
    default: return 'default';
  }
}

export function idempotencyKey(): string {
  const bytes = new Uint8Array(12);
  globalThis.crypto.getRandomValues(bytes);
  return 'rma-' + Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}

// ---- lists ---------------------------------------------------------------------------------------------------------

export interface ListFilter { q: string; status: string; kind: string; reasonCode: string; resolution: string; storeId: string; vendorId: string; from: string; to: string; flagged: boolean }

export const emptyFilter = (): ListFilter => ({ q: '', status: '', kind: '', reasonCode: '', resolution: '', storeId: '', vendorId: '', from: '', to: '', flagged: false });

export function filterProblems(f: ListFilter): string[] {
  const out: string[] = [];
  const iso = /^\d{4}-\d{2}-\d{2}$/;
  if (f.from && !iso.test(f.from)) out.push('From must be a valid date.');
  if (f.to && !iso.test(f.to)) out.push('To must be a valid date.');
  if (f.from && f.to && iso.test(f.from) && iso.test(f.to) && f.from > f.to) out.push('From must not be after To.');
  if (f.q.length > 100) out.push('Search text is too long.');
  return out;
}

/** Only meaningful filters are sent; paging is clamped to what the API accepts. */
export function buildQuery(view: string | undefined, f: ListFilter, page: number, size: number): string {
  const p = new URLSearchParams();
  if (view) p.set('view', view);
  const q = f.q.trim();
  if (q) p.set('q', q);
  for (const k of ['status', 'kind', 'reasonCode', 'resolution', 'storeId', 'vendorId', 'from', 'to'] as const) if (f[k]) p.set(k, f[k]);
  if (f.flagged) p.set('flagged', 'true');
  p.set('page', String(Math.max(0, Math.floor(page) || 0)));
  p.set('size', String(Math.min(100, Math.max(1, Math.floor(size) || 25))));
  return p.toString();
}

export function activeFilterCount(f: ListFilter): number {
  return (Object.keys(f) as (keyof ListFilter)[]).filter((k) => (k === 'flagged' ? f.flagged : !!f[k])).length;
}

export const REASON_LABELS: Record<string, string> = {
  DEFECTIVE: 'Defective', WRONG_ITEM: 'Wrong item', DAMAGED_ON_ARRIVAL: 'Damaged on arrival', NOT_AS_DESCRIBED: 'Not as described', NO_LONGER_NEEDED: 'No longer needed',
  SIZE_FIT: 'Size or fit', OTHER: 'Other', ORDER_REJECTED: 'Order rejected', RESTAURANT_UNAVAILABLE: 'Restaurant unavailable', INCORRECT_ORDER: 'Incorrect order',
  MISSING_ITEMS: 'Missing items', DAMAGED_FOOD: 'Damaged food', LATE_DELIVERY: 'Late delivery', PAYMENT_NOT_ACCEPTED: 'Payment not accepted',
};
export const reasonLabel = (code: string | null | undefined): string => (code ? REASON_LABELS[code] ?? code.replace(/_/g, ' ').toLowerCase() : '—');

export const FLAG_LABELS: Record<string, string> = { DUPLICATE_ATTEMPT: 'Duplicate attempt', REPEAT_RETURNER: 'Frequent returner', HIGH_VALUE: 'High value' };
export const flagLabel = (f: string): string => FLAG_LABELS[f] ?? f.replace(/_/g, ' ').toLowerCase();

export const money = (v: unknown): string => (v === null || v === undefined || v === '' || Number.isNaN(Number(v)) ? '—' : Number(v).toFixed(2));

// ---- approval --------------------------------------------------------------------------------------------------------

export interface ReturnItem { id: number; orderItemId: number; productName?: string; quantity: number; approvedQuantity?: number | null; receivedQuantity?: number | null; acceptedQuantity?: number | null; unitPrice?: number | string }

/** Mirrors the API's checks so mistakes are caught before a request is sent. */
export function approvalProblems(items: readonly ReturnItem[], quantities: Record<number, number>): string[] {
  const out: string[] = [];
  let total = 0;
  for (const it of items) {
    const q = quantities[it.orderItemId] ?? it.quantity;
    if (!Number.isInteger(q) || q < 0) out.push(`${it.productName ?? 'Item'}: enter a whole number.`);
    else if (q > it.quantity) out.push(`${it.productName ?? 'Item'}: at most ${it.quantity} can be approved.`);
    else total += q;
  }
  if (out.length === 0 && total === 0) out.push('Approve at least one unit, or reject the request.');
  return out;
}

export const isPartial = (items: readonly ReturnItem[], quantities: Record<number, number>): boolean => items.some((it) => (quantities[it.orderItemId] ?? it.quantity) < it.quantity);

// ---- inspection ------------------------------------------------------------------------------------------------------

export const CONDITIONS = ['NEW', 'LIKE_NEW', 'USED', 'DAMAGED', 'DEFECTIVE', 'WRONG_ITEM'] as const;
export const OUTCOMES = ['ACCEPTED_FOR_REFUND', 'ACCEPTED_FOR_EXCHANGE', 'PARTIALLY_ACCEPTED', 'REJECTED', 'REQUIRES_MANUAL_REVIEW'] as const;
export const PACKAGING = ['INTACT', 'OPENED', 'DAMAGED', 'MISSING'] as const;
const RESELLABLE = new Set<string>(['NEW', 'LIKE_NEW']);

export interface InspectionRow { returnItemId: number; approved: number; receivedQuantity: number; acceptedQuantity: number; condition: string; restockable: boolean; note: string }

export function inspectionProblems(rows: readonly InspectionRow[], outcome: string, resolution: string): string[] {
  const out: string[] = [];
  let approved = 0, accepted = 0;
  for (const r of rows) {
    if (!Number.isInteger(r.receivedQuantity) || !Number.isInteger(r.acceptedQuantity) || r.receivedQuantity < 0 || r.acceptedQuantity < 0) { out.push('Quantities must be whole numbers, zero or more.'); continue; }
    if (r.receivedQuantity > r.approved) out.push(`Received quantity cannot exceed the approved ${r.approved}; note any discrepancy instead.`);
    if (r.acceptedQuantity > r.receivedQuantity) out.push('Accepted quantity cannot exceed the received quantity.');
    if (r.restockable && (!RESELLABLE.has(r.condition) || r.acceptedQuantity === 0)) out.push('Only accepted units in NEW or LIKE_NEW condition can be restocked.');
    approved += r.approved; accepted += r.acceptedQuantity;
  }
  if (out.length) return [...new Set(out)];
  if ((outcome === 'ACCEPTED_FOR_REFUND' || outcome === 'ACCEPTED_FOR_EXCHANGE') && accepted === 0) out.push('An accepting outcome needs at least one accepted unit.');
  if (outcome === 'PARTIALLY_ACCEPTED' && (accepted === 0 || accepted >= approved)) out.push('Partially accepted means some, but not all, approved units were accepted.');
  if (outcome === 'REJECTED' && accepted !== 0) out.push('A rejected inspection cannot accept units.');
  if (outcome === 'ACCEPTED_FOR_EXCHANGE' && resolution !== 'EXCHANGE') out.push('The customer did not ask for an exchange.');
  if (outcome === 'ACCEPTED_FOR_REFUND' && resolution === 'EXCHANGE') out.push('This is an exchange; choose Accepted for exchange.');
  return out;
}

/** A starting suggestion only; the inspector always chooses. */
export function suggestOutcome(rows: readonly InspectionRow[], resolution: string): string {
  const approved = rows.reduce((s, r) => s + r.approved, 0), accepted = rows.reduce((s, r) => s + r.acceptedQuantity, 0);
  if (accepted === 0) return 'REJECTED';
  if (accepted < approved) return 'PARTIALLY_ACCEPTED';
  return resolution === 'EXCHANGE' ? 'ACCEPTED_FOR_EXCHANGE' : 'ACCEPTED_FOR_REFUND';
}

// ---- actions offered on a return -----------------------------------------------------------------------------------------

export interface ReturnLike { status: string; returnKind: string; resolution: string; evidenceRequired?: boolean; allowedTransitions?: string[] }
export interface ActionDef { id: string; label: string; permission: string; reasonRequired: boolean; tone: 'primary' | 'error' | 'inherit' }

/** The API enforces all of this too; here it only decides which buttons are shown. */
export function availableActions(r: ReturnLike, allowed: (p: string) => boolean, hasEvidence: boolean): ActionDef[] {
  const out: ActionDef[] = [];
  const claim = r.returnKind === 'RESTAURANT_CLAIM';
  const next = new Set(r.allowedTransitions ?? []);
  const add = (a: ActionDef) => { if (allowed(a.permission)) out.push(a); };
  if (['REQUESTED', 'UNDER_REVIEW'].includes(r.status)) {
    if (r.evidenceRequired && !hasEvidence) add({ id: 'request-info', label: 'Request evidence', permission: 'return:review', reasonRequired: true, tone: 'primary' });
    else add({ id: 'approve', label: 'Approve', permission: 'return:approve', reasonRequired: false, tone: 'primary' });
    add({ id: 'reject', label: 'Reject', permission: 'return:reject', reasonRequired: true, tone: 'error' });
    if (!(r.evidenceRequired && !hasEvidence)) add({ id: 'request-info', label: 'Request information', permission: 'return:review', reasonRequired: true, tone: 'inherit' });
    if (r.status === 'REQUESTED' && next.has('UNDER_REVIEW')) add({ id: 'to:UNDER_REVIEW', label: 'Start review', permission: 'return:review', reasonRequired: false, tone: 'inherit' });
  }
  if (!claim && r.status === 'APPROVED') {
    add({ id: 'pickup', label: 'Schedule pickup', permission: 'return:pickup', reasonRequired: false, tone: 'primary' });
    add({ id: 'to:RECEIVED', label: 'Mark received (drop-off)', permission: 'return:receive', reasonRequired: false, tone: 'inherit' });
  }
  if (!claim && r.status === 'PICKUP_FAILED') add({ id: 'pickup', label: 'Reschedule pickup', permission: 'return:pickup', reasonRequired: false, tone: 'primary' });
  if (!claim && r.status === 'PICKUP_SCHEDULED') add({ id: 'pickup-cancel', label: 'Cancel pickup', permission: 'return:pickup', reasonRequired: true, tone: 'inherit' });
  if (!claim && r.status === 'RETURN_LOST') add({ id: 'to:RECEIVED', label: 'Mark received (found)', permission: 'return:receive', reasonRequired: false, tone: 'inherit' });
  if (!claim && r.status === 'RECEIVED') add({ id: 'to:INSPECTION_PENDING', label: 'Send to inspection', permission: 'return:receive', reasonRequired: false, tone: 'primary' });
  if (!claim && ['INSPECTION_PENDING', 'INSPECTION_FAILED'].includes(r.status)) {
    if (r.status === 'INSPECTION_PENDING') add({ id: 'inspect', label: 'Record inspection', permission: 'return:inspect', reasonRequired: false, tone: 'primary' });
    else add({ id: 'to:INSPECTION_PENDING', label: 'Re-inspect', permission: 'return:receive', reasonRequired: false, tone: 'primary' });
  }
  if (r.resolution === 'EXCHANGE' && !claim && ['APPROVED', 'RECEIVED', 'INSPECTION_COMPLETED', 'EXCHANGE_PENDING', 'PICKUP_SCHEDULED', 'PICKED_UP', 'IN_TRANSIT'].includes(r.status))
    add({ id: 'exchange', label: 'Propose exchange', permission: 'return:exchange', reasonRequired: false, tone: 'inherit' });
  if (r.resolution !== 'EXCHANGE' && ['REFUND_PENDING', 'INSPECTION_COMPLETED', 'APPROVED', 'RECEIVED'].includes(r.status) && (claim || r.status !== 'APPROVED'))
    add({ id: 'refund', label: r.status === 'REFUND_PENDING' ? 'Calculate refund' : 'Prepare refund', permission: 'return:refund-process', reasonRequired: false, tone: 'primary' });
  if (['REQUESTED', 'UNDER_REVIEW', 'APPROVED', 'PICKUP_SCHEDULED'].includes(r.status)) add({ id: 'to:CUSTOMER_CANCELLED', label: 'Cancel request', permission: 'return:review', reasonRequired: true, tone: 'error' });
  if (['REFUNDED', 'EXCHANGED', 'REFUND_FAILED', 'INSPECTION_FAILED', 'PICKUP_FAILED', 'RETURN_LOST'].includes(r.status) && next.has('CLOSED'))
    add({ id: 'to:CLOSED', label: 'Close', permission: 'return:review', reasonRequired: true, tone: 'inherit' });
  return out;
}

// ---- lifecycle display ------------------------------------------------------------------------------------------------

export function lifecycleSteps(kind: string, resolution: string): string[] {
  if (kind === 'RESTAURANT_CLAIM') return ['REQUESTED', 'UNDER_REVIEW', 'APPROVED', 'REFUND_PENDING', 'REFUND_PROCESSING', 'REFUNDED', 'CLOSED'];
  const base = ['REQUESTED', 'UNDER_REVIEW', 'APPROVED', 'PICKUP_SCHEDULED', 'PICKED_UP', 'IN_TRANSIT', 'RECEIVED', 'INSPECTION_PENDING', 'INSPECTION_COMPLETED'];
  return resolution === 'EXCHANGE' ? [...base, 'EXCHANGE_PENDING', 'EXCHANGED', 'CLOSED'] : [...base, 'REFUND_PENDING', 'REFUND_PROCESSING', 'REFUNDED', 'CLOSED'];
}

/** Index of the current status in the happy path, or -1 when it is an exception state (rejected, failed, lost...). */
export function lifecycleIndex(steps: readonly string[], status: string): number { return steps.indexOf(status); }

export const TERMINAL = new Set(['CLOSED', 'REJECTED', 'CUSTOMER_CANCELLED']);

// ---- refunds / pickups / policies -------------------------------------------------------------------------------------------

export interface RefundLike { originalAmount?: unknown; previouslyRefunded?: unknown; itemsAmount?: unknown; discountAllocation?: unknown; taxAdjustment?: unknown; deliveryFeeRefund?: unknown; fees?: unknown; finalAmount?: unknown }

export function refundRows(r: RefundLike): { label: string; value: string; strong?: boolean }[] {
  return [
    { label: 'Original order amount', value: money(r.originalAmount) },
    { label: 'Previously refunded', value: money(r.previouslyRefunded) },
    { label: 'Returned items', value: money(r.itemsAmount) },
    { label: 'Discount allocation', value: `-${money(r.discountAllocation)}` },
    { label: 'Tax adjustment', value: money(r.taxAdjustment) },
    { label: 'Delivery fee refund', value: money(r.deliveryFeeRefund) },
    { label: 'Fees', value: `-${money(r.fees)}` },
    { label: 'Final refund', value: money(r.finalAmount), strong: true },
  ];
}

export function pickupProblems(from: string, to: string, now: Date = new Date()): string[] {
  const out: string[] = [];
  const f = Date.parse(from), t = Date.parse(to);
  if (!from || Number.isNaN(f)) out.push('Choose when the pickup window starts.');
  if (!to || Number.isNaN(t)) out.push('Choose when the pickup window ends.');
  if (out.length) return out;
  if (t <= f) out.push('The pickup window must end after it starts.');
  if (f < now.getTime() - 5 * 60_000) out.push('The pickup window cannot start in the past.');
  return out;
}

export interface PolicyDraft { returnWindowDays: number; maxQuantityPercent: number; restockingFeePercent: number; claimWindowHours: number; reasons: { code: string; label: string }[] }

/** Quick checks before saving a draft; the API is the authority and normalises the document. */
export function policyProblems(c: PolicyDraft): string[] {
  const out: string[] = [];
  if (!Number.isInteger(c.returnWindowDays) || c.returnWindowDays < 0 || c.returnWindowDays > 3650) out.push('Return window must be 0-3650 days.');
  if (!Number.isInteger(c.maxQuantityPercent) || c.maxQuantityPercent < 1 || c.maxQuantityPercent > 100) out.push('Maximum quantity must be 1-100 percent.');
  if (!(c.restockingFeePercent >= 0 && c.restockingFeePercent <= 50)) out.push('Restocking fee must be 0-50 percent.');
  if (!Number.isInteger(c.claimWindowHours) || c.claimWindowHours < 1 || c.claimWindowHours > 720) out.push('Claim window must be 1-720 hours.');
  if (c.reasons.length === 0) out.push('Add at least one return reason.');
  const codes = c.reasons.map((r) => r.code);
  if (codes.some((x) => !/^[A-Z0-9_]{2,40}$/.test(x))) out.push('Reason codes use capital letters, digits and underscores.');
  if (new Set(codes).size !== codes.length) out.push('Reason codes must be unique.');
  if (c.reasons.some((r) => !r.label.trim() || /[<>]/.test(r.label))) out.push('Every reason needs a plain-text label.');
  return out;
}

export interface SettingsDraft { highValueRefundThreshold: number; refundApprovalThreshold: number; repeatReturnCount: number; repeatReturnDays: number; pickupFailureLimit: number; lostShipmentDays: number; reviewSlaHours: number; refundSlaHours: number }

export function settingsProblems(s: SettingsDraft): string[] {
  const out: string[] = [];
  const whole = (v: number, lo: number, hi: number, name: string) => { if (!Number.isInteger(v) || v < lo || v > hi) out.push(`${name} must be a whole number from ${lo} to ${hi}.`); };
  if (!(s.highValueRefundThreshold > 0)) out.push('High-value threshold must be more than zero.');
  if (!(s.refundApprovalThreshold >= 0)) out.push('Approval threshold cannot be negative.');
  whole(s.repeatReturnCount, 2, 100, 'Repeat-return count'); whole(s.repeatReturnDays, 1, 365, 'Repeat-return window');
  whole(s.pickupFailureLimit, 1, 20, 'Pickup failure limit'); whole(s.lostShipmentDays, 1, 365, 'Lost-shipment days');
  whole(s.reviewSlaHours, 1, 2000, 'Review SLA'); whole(s.refundSlaHours, 1, 2000, 'Refund SLA');
  return out;
}

export const age = (iso: string | null | undefined, now: number = Date.now()): string => {
  if (!iso) return '—';
  const m = Math.max(0, Math.round((now - Date.parse(iso)) / 60_000));
  if (Number.isNaN(m)) return '—';
  if (m < 60) return `${m}m`;
  if (m < 1440) return `${Math.floor(m / 60)}h`;
  return `${Math.floor(m / 1440)}d`;
};
