// Pure logic for the Pricing & Promotions screens (no React, so it can be unit-tested with node --test).

export type SectionId =
  | 'dashboard' | 'prices' | 'bulk' | 'scheduled' | 'campaigns' | 'coupons' | 'automatic' | 'bundles' | 'bxgy'
  | 'segments' | 'calendar' | 'approvals' | 'performance' | 'rules' | 'audit';

export interface SectionDefinition {
  id: SectionId;
  label: string;
  /** Any one of these permissions makes the section visible. The backend re-checks every call. */
  anyOf: string[];
}

export const SECTIONS: readonly SectionDefinition[] = [
  { id: 'dashboard', label: 'Pricing Dashboard', anyOf: ['pricing:read', 'promotion:read'] },
  { id: 'prices', label: 'Product Price Management', anyOf: ['pricing:read'] },
  { id: 'bulk', label: 'Bulk Price Updates', anyOf: ['pricing:update'] },
  { id: 'scheduled', label: 'Scheduled Pricing', anyOf: ['pricing:read'] },
  { id: 'campaigns', label: 'Discount Campaigns', anyOf: ['promotion:read'] },
  { id: 'coupons', label: 'Coupon Management', anyOf: ['coupon:manage', 'promotion:read'] },
  { id: 'automatic', label: 'Automatic Discounts', anyOf: ['promotion:read'] },
  { id: 'bundles', label: 'Bundle Offers', anyOf: ['promotion:read'] },
  { id: 'bxgy', label: 'Buy X Get Y Offers', anyOf: ['promotion:read'] },
  { id: 'segments', label: 'Customer Segment Pricing', anyOf: ['promotion:read'] },
  { id: 'calendar', label: 'Promotion Calendar', anyOf: ['promotion:read'] },
  { id: 'approvals', label: 'Promotion Approvals', anyOf: ['promotion:read', 'pricing:approve'] },
  { id: 'performance', label: 'Promotion Performance', anyOf: ['promotion:read'] },
  { id: 'rules', label: 'Pricing Rules', anyOf: ['pricing:read', 'promotion:read'] },
  { id: 'audit', label: 'Audit History', anyOf: ['pricing:audit'] },
];

export function hasPermission(role: string | undefined, permissions: readonly string[] | undefined, wanted: string): boolean {
  if (role === 'SUPER_ADMIN') return true;
  if (!permissions) return false;
  return permissions.includes('*') || permissions.includes(wanted);
}

export function visibleSections(role: string | undefined, permissions: readonly string[] | undefined): SectionDefinition[] {
  return SECTIONS.filter((s) => s.anyOf.some((p) => hasPermission(role, permissions, p)));
}

// ---- price form --------------------------------------------------------------------------------------------------

export interface PriceForm {
  productId: string; currency: string; basePrice: string; salePrice: string; costPrice: string;
  minSellingPrice?: string; maxDiscountPercent?: string; taxInclusive?: boolean; effectiveFrom?: string; effectiveTo?: string;
}

const MONEY = /^\d{1,11}(\.\d{1,2})?$/;

export function validatePriceForm(form: PriceForm, supportedCurrencies: readonly string[], canEditCost: boolean): string[] {
  const errors: string[] = [];
  if (!/^\d+$/.test(form.productId) || Number(form.productId) <= 0) errors.push('Choose a product.');
  if (!/^[A-Za-z]{3}$/.test(form.currency) || !supportedCurrencies.includes(form.currency.toUpperCase())) errors.push('Currency is not enabled for this tenant.');
  if (!MONEY.test(form.basePrice) || Number(form.basePrice) <= 0) errors.push('Base price must be greater than zero with at most 2 decimals.');
  if (form.salePrice !== '') {
    if (!MONEY.test(form.salePrice) || Number(form.salePrice) <= 0) errors.push('Sale price must be greater than zero with at most 2 decimals.');
    else if (Number(form.salePrice) > Number(form.basePrice)) errors.push('Sale price cannot exceed the base price.');
  }
  if (form.costPrice !== '') {
    if (!canEditCost) errors.push('You do not have permission to set cost price.');
    else if (!/^\d{1,11}(\.\d{1,2})?$/.test(form.costPrice)) errors.push('Cost price must be zero or more with at most 2 decimals.');
  }
  const effective = form.salePrice !== '' ? Number(form.salePrice) : Number(form.basePrice);
  if (form.minSellingPrice) {
    if (!MONEY.test(form.minSellingPrice) || Number(form.minSellingPrice) <= 0) errors.push('Minimum selling price must be greater than zero.');
    else if (Number(form.minSellingPrice) > effective) errors.push('Minimum selling price cannot exceed the selling price.');
  }
  if (form.maxDiscountPercent) {
    const pct = Number(form.maxDiscountPercent);
    if (!(pct > 0 && pct <= 100)) errors.push('Maximum discount must be between 0 and 100.');
  }
  if (form.effectiveFrom && form.effectiveTo && new Date(form.effectiveTo) <= new Date(form.effectiveFrom)) errors.push('Effective end must be after the start.');
  return errors;
}

/** Request body for create/update; optional guard rails are omitted when blank so the server keeps stored values. */
export function buildPriceRequest(form: PriceForm) {
  const num = (v?: string) => (v === undefined || v === '' ? null : Number(v));
  return {
    productId: Number(form.productId), currency: form.currency.toUpperCase(), basePrice: Number(form.basePrice), salePrice: num(form.salePrice),
    costPrice: num(form.costPrice), minSellingPrice: num(form.minSellingPrice), maxDiscountPercent: num(form.maxDiscountPercent),
    taxInclusive: form.taxInclusive ?? null,
    effectiveFrom: form.effectiveFrom ? new Date(form.effectiveFrom).toISOString() : null,
    effectiveTo: form.effectiveTo ? new Date(form.effectiveTo).toISOString() : null,
  };
}

// ---- campaign form -----------------------------------------------------------------------------------------------

export type PromotionType = 'PERCENTAGE' | 'FIXED_AMOUNT' | 'BUY_X_GET_Y' | 'BUNDLE' | 'QUANTITY_TIER';

export interface CampaignForm {
  name: string; promotionType: PromotionType; scope: 'PRODUCT' | 'CATEGORY' | 'STORE_WIDE'; currency: string;
  startsAt: string; endsAt: string; targetIds: string; value: string; buyQuantity: string; getQuantity: string; minQuantity: string;
  minOrderValue: string; maxDiscountAmount: string; usageLimitTotal: string; usageLimitPerCustomer: string;
  priority: string; stackable: boolean; automatic: boolean; customerSegment: string; firstOrderOnly: boolean;
}

export function emptyCampaignForm(type: PromotionType, defaults: Partial<CampaignForm> = {}): CampaignForm {
  return {
    name: '', promotionType: type, scope: 'PRODUCT', currency: 'USD', startsAt: '', endsAt: '', targetIds: '', value: '', buyQuantity: '2',
    getQuantity: '1', minQuantity: '2', minOrderValue: '', maxDiscountAmount: '', usageLimitTotal: '', usageLimitPerCustomer: '', priority: '100',
    stackable: false, automatic: false, customerSegment: '', firstOrderOnly: false, ...defaults,
  };
}

export function parseIds(text: string): number[] {
  return text.split(/[\s,;]+/).filter(Boolean).map(Number).filter((n) => Number.isInteger(n) && n > 0);
}

export function validateCampaignForm(f: CampaignForm, maxDiscountPercent: number, supportedCurrencies: readonly string[]): string[] {
  const errors: string[] = [];
  if (f.name.trim() === '') errors.push('Name is required.');
  if (!supportedCurrencies.includes(f.currency.toUpperCase())) errors.push('Currency is not enabled for this tenant.');
  if (f.startsAt === '') errors.push('Start date is required.');
  if (f.endsAt !== '' && f.startsAt !== '' && new Date(f.endsAt) <= new Date(f.startsAt)) errors.push('End date must be after the start date.');
  if (f.endsAt !== '' && new Date(f.endsAt) <= new Date()) errors.push('End date must be in the future.');
  const targets = parseIds(f.targetIds);
  if (f.scope !== 'STORE_WIDE' && targets.length === 0) errors.push('Enter at least one target id.');
  if (f.promotionType === 'BUNDLE' && targets.length < 2) errors.push('A bundle needs at least two products.');
  const value = Number(f.value);
  if (['PERCENTAGE', 'QUANTITY_TIER'].includes(f.promotionType)) {
    if (!(value > 0 && value <= 100)) errors.push('Percent must be between 0 and 100.');
    else if (value > maxDiscountPercent) errors.push(`Percent exceeds the tenant maximum discount of ${maxDiscountPercent}%.`);
  }
  if (['FIXED_AMOUNT', 'BUNDLE'].includes(f.promotionType) && !(value > 0)) errors.push('Amount must be greater than zero.');
  if (f.promotionType === 'BUY_X_GET_Y' && (!(Number(f.buyQuantity) >= 1) || !(Number(f.getQuantity) >= 1))) errors.push('Buy and get quantities must be at least 1.');
  if (f.promotionType === 'QUANTITY_TIER' && !(Number(f.minQuantity) >= 1)) errors.push('Tier quantity must be at least 1.');
  for (const [label, v] of [['Minimum order value', f.minOrderValue], ['Max discount amount', f.maxDiscountAmount]] as const) {
    if (v !== '' && !(Number(v) >= 0)) errors.push(`${label} cannot be negative.`);
  }
  for (const [label, v] of [['Total usage limit', f.usageLimitTotal], ['Per-customer limit', f.usageLimitPerCustomer]] as const) {
    if (v !== '' && !(Number.isInteger(Number(v)) && Number(v) > 0)) errors.push(`${label} must be a positive whole number.`);
  }
  if (f.automatic && f.usageLimitPerCustomer !== '') errors.push('Per-customer limits apply to coupon campaigns only.');
  return errors;
}

/** Builds the exact request body the API expects. Never includes anything the server computes. */
export function buildCampaignRequest(f: CampaignForm) {
  const targets = parseIds(f.targetIds);
  const targetType = f.scope === 'CATEGORY' ? 'CATEGORY' : 'PRODUCT';
  const num = (v: string) => (v === '' ? null : Number(v));
  const reward = (() => {
    switch (f.promotionType) {
      case 'PERCENTAGE': return { type: 'PERCENT_OFF', percent: Number(f.value) };
      case 'FIXED_AMOUNT': return { type: 'AMOUNT_OFF', amount: Number(f.value) };
      case 'BUNDLE': return { type: 'BUNDLE_PRICE', amount: Number(f.value) };
      case 'QUANTITY_TIER': return { type: 'TIER_PERCENT', percent: Number(f.value), minQuantity: Number(f.minQuantity) };
      case 'BUY_X_GET_Y': return { type: 'BUY_X_GET_Y', buyQuantity: Number(f.buyQuantity), getQuantity: Number(f.getQuantity), percent: f.value === '' ? null : Number(f.value) };
    }
  })();
  return {
    name: f.name.trim(), promotionType: f.promotionType, scope: f.scope, currency: f.currency.toUpperCase(),
    startsAt: new Date(f.startsAt).toISOString(), endsAt: f.endsAt === '' ? null : new Date(f.endsAt).toISOString(),
    automatic: f.automatic, stackable: f.stackable, priority: Number(f.priority),
    minOrderValue: num(f.minOrderValue), maxDiscountAmount: num(f.maxDiscountAmount),
    usageLimitTotal: num(f.usageLimitTotal), usageLimitPerCustomer: num(f.usageLimitPerCustomer),
    customerSegment: f.customerSegment.trim() === '' ? null : f.customerSegment.trim(), firstOrderOnly: f.firstOrderOnly,
    rules: f.scope === 'STORE_WIDE' ? [] : targets.map((id) => ({ role: 'TRIGGER', effect: 'INCLUDE', targetType, targetId: id })),
    rewards: [reward],
  };
}

// ---- CSV ------------------------------------------------------------------------------------------------------

/** Client-side pre-check so users see row errors before upload. The server validates again, all-or-nothing. */
export function precheckPriceCsv(csv: string, supportedCurrencies: readonly string[]): string[] {
  const lines = csv.split(/\r?\n/).filter((l) => l.trim() !== '');
  if (lines.length < 2) return ['The file needs a header row and at least one data row.'];
  const header = lines[0].split(',').map((h) => h.trim());
  const errors: string[] = [];
  for (const required of ['productId', 'currency', 'basePrice']) if (!header.includes(required)) errors.push(`Missing column ${required}.`);
  if (errors.length) return errors;
  lines.slice(1).forEach((line, i) => {
    const cells = line.split(',');
    const row = Object.fromEntries(header.map((h, j) => [h, (cells[j] ?? '').trim()]));
    const n = i + 1;
    if (!/^\d+$/.test(row.productId)) errors.push(`Row ${n}: productId must be a number.`);
    if (!supportedCurrencies.includes((row.currency ?? '').toUpperCase())) errors.push(`Row ${n}: unsupported currency ${row.currency}.`);
    if (!MONEY.test(row.basePrice) || Number(row.basePrice) <= 0) errors.push(`Row ${n}: basePrice must be greater than zero.`);
    if (row.salePrice && (!MONEY.test(row.salePrice) || Number(row.salePrice) > Number(row.basePrice))) errors.push(`Row ${n}: salePrice is invalid or above basePrice.`);
  });
  return errors.slice(0, 20);
}

export function formatMoney(value: number | string | null | undefined, currency: string): string {
  if (value === null || value === undefined || value === '') return '—';
  const n = Number(value);
  if (!Number.isFinite(n)) return '—';
  try { return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(n); } catch { return `${n.toFixed(2)} ${currency}`; }
}

export type StatusTone = 'default' | 'success' | 'warning' | 'error' | 'info';
export function statusTone(status: string): StatusTone {
  switch (status) {
    case 'PUBLISHED': case 'ACTIVE': case 'APPLIED': case 'APPROVED': case 'REDEEMED': return 'success';
    case 'PENDING_APPROVAL': case 'PENDING': case 'PAUSED': case 'SCHEDULED': return 'warning';
    case 'REJECTED': case 'CANCELLED': case 'REVOKED': case 'REVERSED': return 'error';
    case 'DRAFT': return 'default';
    default: return 'info';
  }
}

/** Which lifecycle actions make sense for a campaign status, in display order. */
export function campaignActions(status: string): string[] {
  switch (status) {
    case 'DRAFT': case 'REJECTED': return ['edit', 'submit', 'cancel'];
    case 'PENDING_APPROVAL': return ['cancel'];
    case 'APPROVED': return ['publish', 'cancel'];
    case 'PUBLISHED': return ['pause', 'cancel'];
    case 'PAUSED': return ['resume', 'cancel'];
    default: return [];
  }
}
