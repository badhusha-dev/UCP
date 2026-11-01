// Pure logic for the Order Management Control Tower screens (no React; unit-tested with node --test).
// The backend validates and authorises everything again; this module only shapes requests and drives the UI.

export type ScreenId =
  | 'dashboard' | 'all' | 'new' | 'pending' | 'processing' | 'ready' | 'partial' | 'shipped' | 'out' | 'delivered' | 'cancelled' | 'failed' | 'hold'
  | 'exceptions' | 'bulk' | 'returns' | 'reports' | 'settings' | 'audit';

export interface MenuItem { id: ScreenId; label: string; view?: string; anyOf: string[] }

export const MENU: readonly MenuItem[] = [
  { id: 'dashboard', label: 'Order Dashboard', anyOf: ['order:view'] },
  { id: 'all', label: 'All Orders', anyOf: ['order:view'] },
  { id: 'new', label: 'New Orders', view: 'NEW', anyOf: ['order:view'] },
  { id: 'pending', label: 'Pending Confirmation', view: 'PENDING_CONFIRMATION', anyOf: ['order:view'] },
  { id: 'processing', label: 'Processing Orders', view: 'PROCESSING', anyOf: ['order:view'] },
  { id: 'ready', label: 'Ready for Fulfillment', view: 'READY_FOR_FULFILLMENT', anyOf: ['order:view'] },
  { id: 'partial', label: 'Partially Fulfilled', view: 'PARTIALLY_FULFILLED', anyOf: ['order:view'] },
  { id: 'shipped', label: 'Shipped Orders', view: 'SHIPPED', anyOf: ['order:view'] },
  { id: 'out', label: 'Out for Delivery', view: 'OUT_FOR_DELIVERY', anyOf: ['order:view'] },
  { id: 'delivered', label: 'Delivered Orders', view: 'DELIVERED', anyOf: ['order:view'] },
  { id: 'cancelled', label: 'Cancelled Orders', view: 'CANCELLED', anyOf: ['order:view'] },
  { id: 'failed', label: 'Failed Orders', view: 'FAILED', anyOf: ['order:view'] },
  { id: 'hold', label: 'On-Hold Orders', view: 'ON_HOLD', anyOf: ['order:view'] },
  { id: 'exceptions', label: 'Order Exceptions', anyOf: ['order:view'] },
  { id: 'bulk', label: 'Bulk Order Operations', anyOf: ['order:bulk'] },
  { id: 'returns', label: 'Returns & Refund Requests', anyOf: ['order:view'] },
  { id: 'reports', label: 'Order Reports', anyOf: ['order:report:view'] },
  { id: 'settings', label: 'Order Settings', anyOf: ['order:view'] },
  { id: 'audit', label: 'Order Audit History', anyOf: ['order:audit:view'] },
];

export function can(role: string | undefined, permissions: readonly string[] | undefined, wanted: string): boolean {
  if (role === 'SUPER_ADMIN') return true;
  return !!permissions && (permissions.includes('*') || permissions.includes(wanted));
}

export function visibleMenu(role: string | undefined, permissions: readonly string[] | undefined): MenuItem[] {
  return MENU.filter((m) => m.anyOf.some((p) => can(role, permissions, p)));
}

// ---- filters & query string -------------------------------------------------------------------------------------------

export interface OrderFilter {
  q: string; view: string; status: string[]; orderType: string; storeId: string; vendorId: string; paymentStatus: string; fulfillmentStatus: string;
  deliveryStatus: string; from: string; to: string; minAmount: string; maxAmount: string; operatorId: string; slaBreached: '' | 'true' | 'false';
}

export const emptyFilter = (view = ''): OrderFilter => ({ q: '', view, status: [], orderType: '', storeId: '', vendorId: '', paymentStatus: '', fulfillmentStatus: '',
  deliveryStatus: '', from: '', to: '', minAmount: '', maxAmount: '', operatorId: '', slaBreached: '' });

export type SortSpec = { field: string; dir: 'asc' | 'desc' };
export const SORTABLE = ['orderNumber', 'createdAt', 'customerName', 'storeId', 'orderType', 'totalAmount', 'paymentStatus', 'fulfillmentStatus', 'deliveryStatus', 'status'] as const;

/** Validates user input before it is sent, so obvious mistakes are explained instantly instead of bouncing off the API. */
export function filterProblems(f: OrderFilter): string[] {
  const out: string[] = [];
  if (f.from && f.to && f.to < f.from) out.push('The end date is before the start date.');
  if (f.minAmount && !(Number(f.minAmount) >= 0)) out.push('Minimum amount must be zero or more.');
  if (f.maxAmount && !(Number(f.maxAmount) >= 0)) out.push('Maximum amount must be zero or more.');
  if (f.minAmount && f.maxAmount && Number(f.minAmount) > Number(f.maxAmount)) out.push('Minimum amount is above the maximum.');
  if (f.operatorId && !/^\d+$/.test(f.operatorId)) out.push('Operator must be a numeric user id.');
  return out;
}

export function buildQuery(f: OrderFilter, sort: SortSpec[], page: number, size: number): string {
  const p = new URLSearchParams();
  const put = (k: string, v: string) => { if (v.trim() !== '') p.append(k, v.trim()); };
  put('q', f.q); put('view', f.view); f.status.forEach((s) => p.append('status', s)); put('orderType', f.orderType);
  put('storeId', f.storeId); put('vendorId', f.vendorId); put('paymentStatus', f.paymentStatus); put('fulfillmentStatus', f.fulfillmentStatus);
  put('deliveryStatus', f.deliveryStatus); put('from', f.from); put('to', f.to); put('minAmount', f.minAmount); put('maxAmount', f.maxAmount);
  put('operatorId', f.operatorId); put('slaBreached', f.slaBreached);
  sort.filter((s) => (SORTABLE as readonly string[]).includes(s.field)).forEach((s) => p.append('sort', `${s.field}:${s.dir}`));
  p.set('page', String(Math.max(0, page))); p.set('size', String(Math.min(100, Math.max(1, size))));
  return p.toString();
}

/** Click toggles a single sort (asc → desc → off); shift-click adds or flips a secondary sort. */
export function nextSort(current: SortSpec[], field: string, additive: boolean): SortSpec[] {
  const existing = current.find((s) => s.field === field);
  if (!additive) {
    if (!existing) return [{ field, dir: 'asc' }];
    return existing.dir === 'asc' && current.length === 1 ? [{ field, dir: 'desc' }] : existing.dir === 'desc' && current.length === 1 ? [] : [{ field, dir: 'asc' }];
  }
  if (!existing) return [...current, { field, dir: 'asc' }];
  return current.map((s) => (s.field === field ? { field, dir: s.dir === 'asc' ? 'desc' : 'asc' } : s));
}

export function activeFilterCount(f: OrderFilter, baseView: string): number {
  const e = emptyFilter(baseView);
  return (Object.keys(e) as (keyof OrderFilter)[]).filter((k) => JSON.stringify(f[k]) !== JSON.stringify(e[k]) && k !== 'view').length;
}

// ---- statuses, SLA, transitions ----------------------------------------------------------------------------------------

export type Tone = 'default' | 'success' | 'warning' | 'error' | 'info';

export function statusTone(status: string): Tone {
  if (['DELIVERED', 'FULFILLED', 'PAID', 'REFUNDED', 'PICKED_UP', 'READY_FOR_PICKUP'].includes(status)) return 'success';
  if (['CANCELLED', 'REJECTED', 'PAYMENT_FAILED', 'FULFILLMENT_FAILED', 'DELIVERY_FAILED', 'PICKUP_FAILED', 'FAILED'].includes(status)) return 'error';
  if (['ON_HOLD', 'CANCELLATION_REQUESTED', 'PREPARATION_DELAYED', 'PENDING', 'PAYMENT_PENDING', 'REFUND_PENDING', 'PARTIAL', 'PARTIALLY_FULFILLED'].includes(status)) return 'warning';
  if (['CREATED', 'PLACED', 'RECEIVED', 'UNKNOWN', 'UNFULFILLED', 'NONE'].includes(status)) return 'default';
  return 'info';
}

export function slaTone(indicator: string): Tone { return indicator === 'BREACHED' ? 'error' : indicator === 'AT_RISK' ? 'warning' : indicator === 'ON_TRACK' ? 'success' : 'default'; }

export function formatMinutes(m: number | null | undefined): string {
  if (m === null || m === undefined || !Number.isFinite(m)) return '—';
  if (m < 60) return `${Math.round(m)} min`;
  const h = Math.floor(m / 60), r = Math.round(m % 60);
  if (h < 24) return r ? `${h} h ${r} min` : `${h} h`;
  const d = Math.floor(h / 24), hr = h % 24;
  return hr ? `${d} d ${hr} h` : `${d} d`;
}

export const REASON_REQUIRED = ['ON_HOLD', 'CANCELLED', 'CANCELLATION_REQUESTED', 'REJECTED', 'PAYMENT_FAILED', 'FULFILLMENT_FAILED', 'DELIVERY_FAILED', 'PICKUP_FAILED', 'PREPARATION_DELAYED', 'RETURN_REQUESTED'];

export interface Transition { to: string; allowed: boolean; blockedReason?: string | null; reasonRequired: boolean }

/** Describes what the confirmation dialog for a transition must collect; blocked transitions are explained, never offered. */
export function transitionDialog(t: Transition) {
  return {
    needsReason: t.reasonRequired || REASON_REQUIRED.includes(t.to),
    destructive: ['CANCELLED', 'REJECTED'].includes(t.to),
    title: `Move order to ${t.to.replace(/_/g, ' ').toLowerCase()}`,
    canSubmit: t.allowed,
  };
}

export function transitionsForMenu(transitions: Transition[]): { allowed: Transition[]; blocked: Transition[] } {
  return { allowed: transitions.filter((t) => t.allowed), blocked: transitions.filter((t) => !t.allowed) };
}

// ---- bulk -------------------------------------------------------------------------------------------------------------

export interface BulkMeta { action: string; label: string; permission: string; params: ('operatorId' | 'reason' | 'note' | 'carrier')[] }
export const BULK_ACTIONS: readonly BulkMeta[] = [
  { action: 'ASSIGN_OPERATOR', label: 'Assign processing operator', permission: 'order:process', params: ['operatorId'] },
  { action: 'HOLD', label: 'Put on hold', permission: 'order:hold', params: ['reason'] },
  { action: 'RELEASE', label: 'Release from hold', permission: 'order:release', params: [] },
  { action: 'ADD_NOTE', label: 'Add internal note', permission: 'order:note:create', params: ['note'] },
  { action: 'REQUEST_CANCELLATION', label: 'Request cancellation', permission: 'order:cancel', params: ['reason'] },
  { action: 'SET_CARRIER', label: 'Set carrier', permission: 'order:process', params: ['carrier'] },
  { action: 'EXPORT', label: 'Export selected', permission: 'order:export', params: [] },
];

export function bulkActionsFor(role: string | undefined, permissions: readonly string[] | undefined): BulkMeta[] {
  if (!can(role, permissions, 'order:bulk')) return [];
  return BULK_ACTIONS.filter((a) => can(role, permissions, a.permission));
}

export function bulkParamProblems(meta: BulkMeta, params: Record<string, string>): string[] {
  const out: string[] = [];
  for (const p of meta.params) {
    const v = (params[p] ?? '').trim();
    if (v === '') out.push(`${p === 'operatorId' ? 'Operator' : p[0].toUpperCase() + p.slice(1)} is required.`);
    else if (p === 'operatorId' && !/^[1-9]\d*$/.test(v)) out.push('Operator must be a numeric user id.');
    else if (p !== 'operatorId' && /[<>]/.test(v)) out.push(`${p[0].toUpperCase() + p.slice(1)} cannot contain markup.`);
  }
  return out;
}

export function previewSummary(job: { totalCount: number; eligibleCount: number }) {
  const skipped = job.totalCount - job.eligibleCount;
  return { skipped, canExecute: job.eligibleCount > 0, text: `${job.eligibleCount} of ${job.totalCount} order(s) can be changed${skipped > 0 ? `; ${skipped} will be skipped` : ''}.` };
}

// ---- selection, columns, saved views ---------------------------------------------------------------------------------------

export const ALL_COLUMNS = ['orderNumber', 'createdAt', 'customer', 'store', 'vendor', 'type', 'items', 'amount', 'payment', 'fulfillment', 'delivery', 'status', 'partner', 'sla'] as const;
export const DEFAULT_COLUMNS: string[] = [...ALL_COLUMNS];

export function sanitizeColumns(saved: unknown): string[] {
  if (!Array.isArray(saved)) return DEFAULT_COLUMNS;
  const keep = saved.filter((c): c is string => typeof c === 'string' && (ALL_COLUMNS as readonly string[]).includes(c));
  return keep.length === 0 ? DEFAULT_COLUMNS : ALL_COLUMNS.filter((c) => keep.includes(c));
}

export function toggleSelection(selected: number[], id: number): number[] { return selected.includes(id) ? selected.filter((x) => x !== id) : [...selected, id]; }
export function togglePage(selected: number[], pageIds: number[]): number[] {
  return pageIds.every((id) => selected.includes(id)) ? selected.filter((id) => !pageIds.includes(id)) : [...new Set([...selected, ...pageIds])];
}

export function idempotencyKey(): string {
  const c = (globalThis as { crypto?: { randomUUID?: () => string } }).crypto;
  return c?.randomUUID ? c.randomUUID() : `k-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}

export function exceptionTone(priority: string): Tone { return priority === 'CRITICAL' ? 'error' : priority === 'HIGH' ? 'warning' : priority === 'MEDIUM' ? 'info' : 'default'; }

/** The restaurant kitchen actions available from the current status. */
export function restaurantActions(status: string): { action: string; label: string; needsPrep?: boolean; needsReason?: boolean }[] {
  switch (status) {
    case 'RECEIVED': return [{ action: 'accept', label: 'Accept', needsPrep: true }, { action: 'reject', label: 'Reject', needsReason: true }];
    case 'ACCEPTED': return [{ action: 'start', label: 'Start preparing' }];
    case 'PREPARING': return [{ action: 'ready', label: 'Ready for pickup' }, { action: 'delay', label: 'Report delay', needsReason: true, needsPrep: true }];
    case 'PREPARATION_DELAYED': return [{ action: 'start', label: 'Resume preparing' }, { action: 'ready', label: 'Ready for pickup' }];
    case 'READY_FOR_PICKUP': return [{ action: 'picked-up', label: 'Picked up' }, { action: 'pickup-failed', label: 'Pickup failed', needsReason: true }];
    case 'PICKUP_FAILED': return [{ action: 'ready', label: 'Ready again' }];
    default: return [];
  }
}
