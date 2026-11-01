// Pure model for the Inventory & Procurement workspace. No React or browser imports so it can be tested with node --test.

export type ScreenKey =
  | 'dashboard' | 'inventory' | 'product-stock' | 'warehouses' | 'store-inventory' | 'transfers' | 'adjustments' | 'reservations'
  | 'movements' | 'low-stock' | 'out-of-stock' | 'forecasting' | 'suppliers' | 'purchase-orders' | 'receiving' | 'supplier-returns'
  | 'audits' | 'cycle-counting' | 'valuation' | 'procurement-analytics' | 'configuration';

export interface ScreenDef { key: ScreenKey; label: string; group: 'Overview' | 'Inventory' | 'Procurement' | 'Control'; permissions: string[] }

/** The 21 navigation entries in display order. `permissions` is any-of and only controls visibility; the backend enforces access. */
export const SCREENS: ScreenDef[] = [
  { key: 'dashboard', label: 'Inventory Dashboard', group: 'Overview', permissions: ['inventory:view'] },
  { key: 'inventory', label: 'All Inventory', group: 'Inventory', permissions: ['inventory:view'] },
  { key: 'product-stock', label: 'Product Stock', group: 'Inventory', permissions: ['inventory:view'] },
  { key: 'warehouses', label: 'Warehouse Management', group: 'Inventory', permissions: ['warehouse:view'] },
  { key: 'store-inventory', label: 'Store Inventory', group: 'Inventory', permissions: ['warehouse:view', 'inventory:view'] },
  { key: 'transfers', label: 'Stock Transfers', group: 'Inventory', permissions: ['inventory:view'] },
  { key: 'adjustments', label: 'Stock Adjustments', group: 'Inventory', permissions: ['inventory:view'] },
  { key: 'reservations', label: 'Stock Reservations', group: 'Inventory', permissions: ['inventory:view'] },
  { key: 'movements', label: 'Stock Movement History', group: 'Inventory', permissions: ['inventory:view'] },
  { key: 'low-stock', label: 'Low Stock Alerts', group: 'Inventory', permissions: ['inventory:view'] },
  { key: 'out-of-stock', label: 'Out of Stock Products', group: 'Inventory', permissions: ['inventory:view'] },
  { key: 'forecasting', label: 'Inventory Forecasting', group: 'Inventory', permissions: ['inventory:view'] },
  { key: 'suppliers', label: 'Suppliers', group: 'Procurement', permissions: ['supplier:view'] },
  { key: 'purchase-orders', label: 'Purchase Orders', group: 'Procurement', permissions: ['purchase-order:view'] },
  { key: 'receiving', label: 'Goods Receiving', group: 'Procurement', permissions: ['goods-receipt:manage', 'purchase-order:view'] },
  { key: 'supplier-returns', label: 'Supplier Returns', group: 'Procurement', permissions: ['supplier-return:manage', 'purchase-order:view'] },
  { key: 'audits', label: 'Stock Audits', group: 'Control', permissions: ['inventory:audit'] },
  { key: 'cycle-counting', label: 'Cycle Counting', group: 'Control', permissions: ['inventory:adjust'] },
  { key: 'valuation', label: 'Inventory Valuation', group: 'Control', permissions: ['inventory:view'] },
  { key: 'procurement-analytics', label: 'Procurement Analytics', group: 'Control', permissions: ['purchase-order:view'] },
  { key: 'configuration', label: 'Inventory Configuration', group: 'Control', permissions: ['inventory:config', 'inventory:view'] },
];

export function hasAny(granted: readonly string[], required: readonly string[]): boolean {
  return granted.includes('*') || required.some(r => granted.includes(r));
}

export function visibleScreens(granted: readonly string[]): ScreenDef[] {
  return SCREENS.filter(s => hasAny(granted, s.permissions));
}

/** available = onHand - reserved - unavailable; incoming never counts. Mirrors the backend rule for display only. */
export function availableQuantity(onHand: number, reserved: number, unavailable: number, _incoming = 0): number {
  return Math.max(0, onHand - reserved - unavailable);
}

export function stockStatus(available: number, reorderPoint: number): 'OUT_OF_STOCK' | 'LOW_STOCK' | 'IN_STOCK' {
  if (available <= 0) return 'OUT_OF_STOCK';
  return available <= reorderPoint ? 'LOW_STOCK' : 'IN_STOCK';
}

// ---- transfers ----
export type TransferStatus = 'DRAFT' | 'REQUESTED' | 'APPROVED' | 'PICKING' | 'DISPATCHED' | 'IN_TRANSIT' | 'RECEIVED' | 'COMPLETED'
  | 'REJECTED' | 'CANCELLED' | 'PARTIALLY_RECEIVED' | 'LOST' | 'DAMAGED';

export interface TransferAction { action: string; label: string; needsApproval?: boolean }

const TRANSFER_ACTIONS: Record<string, TransferAction[]> = {
  DRAFT: [{ action: 'request', label: 'Request' }, { action: 'cancel', label: 'Cancel' }],
  REQUESTED: [{ action: 'approve', label: 'Approve', needsApproval: true }, { action: 'reject', label: 'Reject', needsApproval: true }, { action: 'cancel', label: 'Cancel' }],
  APPROVED: [{ action: 'pick', label: 'Start picking' }, { action: 'cancel', label: 'Cancel' }],
  PICKING: [{ action: 'dispatch', label: 'Dispatch' }, { action: 'cancel', label: 'Cancel' }],
  DISPATCHED: [{ action: 'receive', label: 'Receive' }, { action: 'lost', label: 'Mark lost' }],
  IN_TRANSIT: [{ action: 'receive', label: 'Receive' }, { action: 'lost', label: 'Mark lost' }, { action: 'damaged', label: 'Mark damaged' }],
  PARTIALLY_RECEIVED: [{ action: 'receive', label: 'Receive rest' }, { action: 'lost', label: 'Mark lost' }, { action: 'damaged', label: 'Mark damaged' }],
};

/** Actions the UI may offer for a status. The backend remains the authority and rejects illegal moves. */
export function transferActions(status: string, granted: readonly string[]): TransferAction[] {
  return (TRANSFER_ACTIONS[status] ?? []).filter(a => a.needsApproval ? hasAny(granted, ['inventory:transfer-approve']) : hasAny(granted, ['inventory:transfer']));
}

// ---- purchase orders ----
export function poTotals(lines: { quantity: number; unitCost: number }[], discount: number, taxRatePercent: number, shipping: number) {
  const subtotal = round4(lines.reduce((s, l) => s + l.quantity * l.unitCost, 0));
  const net = round4(subtotal - discount);
  if (net < 0) throw new Error('Discount exceeds subtotal');
  const tax = round4(net * taxRatePercent / 100);
  return { subtotal, tax, total: round4(net + tax + shipping) };
}

export function validatePoForm(f: { supplierId?: number; warehouseId?: number; lines: { productId?: number; quantity?: number; unitCost?: number }[] }): string[] {
  const errors: string[] = [];
  if (!f.supplierId) errors.push('Choose a supplier.');
  if (!f.warehouseId) errors.push('Choose a receiving warehouse.');
  if (f.lines.length === 0) errors.push('Add at least one line.');
  const seen = new Set<number>();
  f.lines.forEach((l, i) => {
    if (!l.productId) errors.push(`Line ${i + 1}: product is required.`);
    else if (seen.has(l.productId)) errors.push(`Line ${i + 1}: duplicate product.`);
    else seen.add(l.productId);
    if (!l.quantity || l.quantity <= 0 || !Number.isInteger(l.quantity)) errors.push(`Line ${i + 1}: quantity must be a positive whole number.`);
    if (l.unitCost !== undefined && l.unitCost < 0) errors.push(`Line ${i + 1}: unit cost cannot be negative.`);
  });
  return errors;
}

export const PO_ACTIONS: Record<string, { action: string; label: string; permission: string }[]> = {
  DRAFT: [{ action: 'submit', label: 'Submit for approval', permission: 'purchase-order:manage' }, { action: 'cancel', label: 'Cancel', permission: 'purchase-order:manage' }],
  PENDING_APPROVAL: [{ action: 'approve', label: 'Approve', permission: 'purchase-order:approve' }, { action: 'reject', label: 'Reject', permission: 'purchase-order:approve' }],
  REJECTED: [{ action: 'reopen', label: 'Reopen as draft', permission: 'purchase-order:manage' }],
  APPROVED: [{ action: 'issue', label: 'Issue to supplier', permission: 'purchase-order:manage' }, { action: 'cancel', label: 'Cancel', permission: 'purchase-order:manage' }],
  ISSUED: [{ action: 'cancel', label: 'Cancel', permission: 'purchase-order:manage' }],
};

export function poActions(status: string, granted: readonly string[]) {
  return (PO_ACTIONS[status] ?? []).filter(a => hasAny(granted, [a.permission]));
}

// ---- receiving ----
export interface ReceiptLineInput { poLineId: number; received: number; rejected: number; damaged: number }

export function acceptedQuantity(received: number, rejected: number, damaged: number): number {
  if (received < 0 || rejected < 0 || damaged < 0 || rejected + damaged > received) throw new Error('Rejected plus damaged cannot exceed received');
  return received - rejected - damaged;
}

export function validateReceipt(lines: ReceiptLineInput[]): string[] {
  const errors: string[] = [];
  const active = lines.filter(l => l.received > 0);
  if (active.length === 0) errors.push('Enter a received quantity for at least one line.');
  for (const l of lines) {
    if (!Number.isInteger(l.received) || !Number.isInteger(l.rejected) || !Number.isInteger(l.damaged)) errors.push(`Line ${l.poLineId}: quantities must be whole numbers.`);
    else if (l.rejected + l.damaged > l.received) errors.push(`Line ${l.poLineId}: rejected plus damaged exceeds received.`);
  }
  return errors;
}

/** One key per submit attempt: a double click or network retry replays the same receipt instead of adding stock twice. */
export function newIdempotencyKey(poId: number, attemptSeed: string): string { return `ui-${poId}-${attemptSeed}`; }

// ---- adjustments & misc ----
export function adjustmentNeedsApproval(delta: number, threshold: number): boolean { return Math.abs(delta) > threshold; }

export function validateAdjustment(f: { warehouseId?: number; productId?: number; newQuantity?: number; reason?: string }): string[] {
  const e: string[] = [];
  if (!f.warehouseId) e.push('Choose a warehouse.');
  if (!f.productId) e.push('Choose a product.');
  if (f.newQuantity === undefined || !Number.isInteger(f.newQuantity) || f.newQuantity < 0) e.push('New quantity must be a whole number of at least 0.');
  if (!f.reason || f.reason.trim().length < 3) e.push('A reason is required.');
  return e;
}

export function validateWarehouse(f: { code?: string; name?: string; capacityUnits?: number }): string[] {
  const e: string[] = [];
  if (!f.code || !/^[A-Za-z0-9_-]{2,40}$/.test(f.code)) e.push('Code must be 2-40 letters, digits, dash or underscore.');
  if (!f.name || !f.name.trim()) e.push('Name is required.');
  if (f.capacityUnits !== undefined && f.capacityUnits < 0) e.push('Capacity cannot be negative.');
  return e;
}

export interface DashboardMetrics { skus: number; onHandUnits: number; reservedUnits: number; availableUnits: number; incomingUnits: number; lowStock: number; outOfStock: number; valuation: number; pendingAdjustments: number; openTransfers: number; warehouses: number }

export function dashboardCards(m: DashboardMetrics): { label: string; value: string; tone: 'ok' | 'warn' | 'bad' | 'info' }[] {
  const money = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 2 });
  return [
    { label: 'SKUs tracked', value: String(m.skus), tone: 'info' },
    { label: 'On hand', value: String(m.onHandUnits), tone: 'info' },
    { label: 'Reserved', value: String(m.reservedUnits), tone: 'info' },
    { label: 'Available', value: String(m.availableUnits), tone: 'ok' },
    { label: 'Incoming (not available)', value: String(m.incomingUnits), tone: 'info' },
    { label: 'Low stock', value: String(m.lowStock), tone: m.lowStock > 0 ? 'warn' : 'ok' },
    { label: 'Out of stock', value: String(m.outOfStock), tone: m.outOfStock > 0 ? 'bad' : 'ok' },
    { label: 'Stock value', value: money.format(Number(m.valuation) || 0), tone: 'info' },
    { label: 'Pending adjustments', value: String(m.pendingAdjustments), tone: m.pendingAdjustments > 0 ? 'warn' : 'ok' },
    { label: 'Open transfers', value: String(m.openTransfers), tone: 'info' },
    { label: 'Warehouses', value: String(m.warehouses), tone: 'info' },
  ];
}

/** Neutralises spreadsheet formula injection when exporting cells. */
export function csvCell(value: unknown): string {
  let s = value === null || value === undefined ? '' : String(value);
  if (/^[=+\-@]/.test(s)) s = `'${s}`;
  return `"${s.replace(/"/g, '""')}"`;
}

export function toCsv(rows: Record<string, unknown>[], columns: string[]): string {
  return [columns.join(','), ...rows.map(r => columns.map(c => csvCell(r[c])).join(','))].join('\n');
}

export function parseCsv(text: string): Record<string, string>[] {
  const lines = text.split(/\r?\n/).filter(l => l.trim().length > 0);
  if (lines.length < 2) return [];
  const split = (line: string) => { const out: string[] = []; let cur = ''; let q = false;
    for (let i = 0; i < line.length; i++) { const c = line[i];
      if (c === '"') { if (q && line[i + 1] === '"') { cur += '"'; i++; } else q = !q; }
      else if (c === ',' && !q) { out.push(cur); cur = ''; } else cur += c; }
    out.push(cur); return out; };
  const head = split(lines[0]).map(h => h.trim());
  return lines.slice(1).map(l => { const cells = split(l); return Object.fromEntries(head.map((h, i) => [h, (cells[i] ?? '').trim()])); });
}

function round4(n: number): number { return Math.round((n + Number.EPSILON) * 10000) / 10000; }
