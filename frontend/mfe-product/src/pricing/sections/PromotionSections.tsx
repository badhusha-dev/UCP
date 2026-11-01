import { useState } from 'react';
import {
  Alert, Button, Checkbox, FormControlLabel, MenuItem, Paper, Select, Stack, TextField, Typography,
} from '@mui/material';
import { api, errorMessage } from '../pricingApi';
import type { Row } from '../pricingApi';
import { DataTable, Loading, StatusChip, shortDate, useLoad } from '../common';
import { buildCampaignRequest, campaignActions, emptyCampaignForm, validateCampaignForm } from '../pricingModel';
import type { CampaignForm, PromotionType } from '../pricingModel';
import type { SectionProps } from './PriceSections';

function useAction() {
  const [message, setMessage] = useState<{ severity: 'success' | 'error'; text: string } | null>(null);
  const run = async (work: () => Promise<unknown>, success: string, after?: () => void) => {
    try { await work(); setMessage({ severity: 'success', text: success }); after?.(); } catch (e) { setMessage({ severity: 'error', text: errorMessage(e) }); }
  };
  return { message, run };
}

export type CampaignKind = 'campaigns' | 'automatic' | 'bundles' | 'bxgy' | 'segments';

const KIND: Record<CampaignKind, { title: string; types: PromotionType[]; query: Row; defaults: Partial<CampaignForm> }> = {
  campaigns: { title: 'Discount campaign', types: ['PERCENTAGE', 'FIXED_AMOUNT', 'QUANTITY_TIER'], query: {}, defaults: {} },
  automatic: { title: 'Automatic discount', types: ['PERCENTAGE', 'FIXED_AMOUNT', 'QUANTITY_TIER'], query: { automatic: true }, defaults: { automatic: true, scope: 'STORE_WIDE' } },
  bundles: { title: 'Bundle offer', types: ['BUNDLE'], query: { type: 'BUNDLE' }, defaults: {} },
  bxgy: { title: 'Buy X Get Y offer', types: ['BUY_X_GET_Y'], query: { type: 'BUY_X_GET_Y' }, defaults: {} },
  segments: { title: 'Customer segment price', types: ['PERCENTAGE', 'FIXED_AMOUNT'], query: { segmentOnly: true }, defaults: { automatic: true, scope: 'STORE_WIDE', customerSegment: '' } },
};

export function CampaignSection({ kind, can, settings }: SectionProps & { kind: CampaignKind }) {
  const cfg = KIND[kind];
  const { data, loading, error, reload } = useLoad(() => api.campaigns(cfg.query), [kind]);
  const { message, run } = useAction();
  const [form, setForm] = useState<CampaignForm>(emptyCampaignForm(cfg.types[0], { currency: (settings?.defaultCurrency as string) ?? 'USD', ...cfg.defaults }));
  const [show, setShow] = useState(false);
  const maxPct = Number(settings?.maxDiscountPercent ?? 50);
  const currencies = (settings?.supportedCurrencies as string[] | undefined) ?? ['USD'];
  const errors = validateCampaignForm(form, maxPct, currencies).concat(kind === 'segments' && form.customerSegment.trim() === '' ? ['Segment is required.'] : []);
  const set = <K extends keyof CampaignForm>(k: K, v: CampaignForm[K]) => setForm({ ...form, [k]: v });

  const act = (r: Row, action: string) => {
    if (action === 'cancel' && !window.confirm(`Cancel "${r.name}"? Its coupons will be revoked.`)) return;
    void run(() => (action === 'cancel' ? api.campaignAction(r.id, 'cancel', { note: 'Cancelled from admin' }) : api.campaignAction(r.id, action)), `Campaign ${action} done.`, () => { void reload(); });
  };
  const allowed = (action: string) => (action === 'publish' ? can('pricing:publish') : can('promotion:update'));

  return (
    <Stack spacing={2}>
      {message && <Alert severity={message.severity}>{message.text}</Alert>}
      {can('promotion:create') && <Button sx={{ alignSelf: 'flex-start' }} variant="contained" onClick={() => setShow(!show)}>{show ? 'Close form' : `New ${cfg.title.toLowerCase()}`}</Button>}
      {show && (
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Stack spacing={1.5}>
            {errors.length > 0 && form.name !== '' && <Alert severity="warning">{errors.map((e) => <div key={e}>{e}</div>)}</Alert>}
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={1}>
              <TextField size="small" fullWidth label="Name" value={form.name} onChange={(e) => set('name', e.target.value)} />
              {cfg.types.length > 1 && <Select size="small" value={form.promotionType} onChange={(e) => set('promotionType', e.target.value as PromotionType)}>{cfg.types.map((t) => <MenuItem key={t} value={t}>{t.replace(/_/g, ' ')}</MenuItem>)}</Select>}
              <Select size="small" value={form.scope} onChange={(e) => set('scope', e.target.value as CampaignForm['scope'])}>
                <MenuItem value="PRODUCT">Products</MenuItem><MenuItem value="CATEGORY">Categories</MenuItem><MenuItem value="STORE_WIDE">Store-wide</MenuItem>
              </Select>
              {form.scope !== 'STORE_WIDE' && <TextField size="small" label="Target IDs" value={form.targetIds} onChange={(e) => set('targetIds', e.target.value)} />}
            </Stack>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={1}>
              <TextField size="small" label={form.promotionType === 'FIXED_AMOUNT' ? 'Amount off' : form.promotionType === 'BUNDLE' ? 'Bundle price' : form.promotionType === 'BUY_X_GET_Y' ? 'Percent off the free units (blank = free)' : 'Percent off'} value={form.value} onChange={(e) => set('value', e.target.value)} />
              {form.promotionType === 'BUY_X_GET_Y' && <><TextField size="small" label="Buy" value={form.buyQuantity} onChange={(e) => set('buyQuantity', e.target.value)} /><TextField size="small" label="Get" value={form.getQuantity} onChange={(e) => set('getQuantity', e.target.value)} /></>}
              {form.promotionType === 'QUANTITY_TIER' && <TextField size="small" label="Minimum quantity" value={form.minQuantity} onChange={(e) => set('minQuantity', e.target.value)} />}
              <TextField size="small" type="datetime-local" label="Starts" InputLabelProps={{ shrink: true }} value={form.startsAt} onChange={(e) => set('startsAt', e.target.value)} />
              <TextField size="small" type="datetime-local" label="Ends (optional)" InputLabelProps={{ shrink: true }} value={form.endsAt} onChange={(e) => set('endsAt', e.target.value)} />
            </Stack>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={1}>
              <TextField size="small" label="Min order value" value={form.minOrderValue} onChange={(e) => set('minOrderValue', e.target.value)} />
              <TextField size="small" label="Max discount amount" value={form.maxDiscountAmount} onChange={(e) => set('maxDiscountAmount', e.target.value)} />
              <TextField size="small" label="Total usage limit" value={form.usageLimitTotal} onChange={(e) => set('usageLimitTotal', e.target.value)} />
              {!form.automatic && <TextField size="small" label="Per-customer limit" value={form.usageLimitPerCustomer} onChange={(e) => set('usageLimitPerCustomer', e.target.value)} />}
              <TextField size="small" label="Priority" value={form.priority} onChange={(e) => set('priority', e.target.value)} />
              {(kind === 'segments' || form.customerSegment !== '') && <TextField size="small" label="Customer segment" value={form.customerSegment} onChange={(e) => set('customerSegment', e.target.value)} />}
            </Stack>
            <Stack direction="row" spacing={2}>
              <FormControlLabel control={<Checkbox checked={form.stackable} onChange={(e) => set('stackable', e.target.checked)} />} label="Can stack with other promotions" />
              <FormControlLabel control={<Checkbox checked={form.automatic} onChange={(e) => set('automatic', e.target.checked)} />} label="Applies automatically (no coupon)" />
              <FormControlLabel control={<Checkbox checked={form.firstOrderOnly} onChange={(e) => set('firstOrderOnly', e.target.checked)} />} label="First order only" />
            </Stack>
            <Button variant="contained" sx={{ alignSelf: 'flex-start' }} disabled={errors.length > 0}
              onClick={() => run(() => api.createCampaign(buildCampaignRequest(form)), 'Draft created. Submit it for approval to publish.', () => { setShow(false); void reload(); })}>Save draft</Button>
          </Stack>
        </Paper>
      )}
      <Loading loading={loading} error={error} />
      {data && <DataTable rows={data} empty="Nothing here yet." columns={[
        { header: 'Name', render: (r) => r.name },
        { header: 'Type', render: (r) => String(r.promotionType).replace(/_/g, ' ') },
        { header: 'Window', render: (r) => `${shortDate(r.startsAt)} – ${shortDate(r.endsAt)}` },
        { header: 'Priority', render: (r) => `${r.priority}${r.stackable ? ' · stackable' : ''}` },
        { header: 'Used', render: (r) => `${r.usageCount}${r.usageLimitTotal ? ` / ${r.usageLimitTotal}` : ''}` },
        { header: 'Status', render: (r) => <StatusChip status={r.status} /> },
        { header: '', render: (r) => (
          <Stack direction="row" spacing={1}>
            {campaignActions(r.status).filter((a) => a !== 'edit' && allowed(a)).map((a) => <Button key={a} size="small" color={a === 'cancel' ? 'error' : 'primary'} onClick={() => act(r, a)}>{a}</Button>)}
          </Stack>) },
      ]} />}
    </Stack>
  );
}

export function CouponSection({ can }: SectionProps) {
  const [campaignId, setCampaignId] = useState('');
  const { data, loading, error, reload } = useLoad(() => api.coupons(campaignId ? { campaignId } : {}), [campaignId]);
  const redemptions = useLoad(() => api.redemptions(), []);
  const { message, run } = useAction();
  const [form, setForm] = useState({ campaignId: '', code: '', count: '', prefix: '', usageLimit: '', perCustomerLimit: '', expiresAt: '' });
  const bulk = form.count !== '' && Number(form.count) > 1;
  const problems = [
    !/^\d+$/.test(form.campaignId) ? 'Campaign ID is required.' : '',
    !bulk && !/^[A-Za-z0-9_-]{3,40}$/.test(form.code) ? 'Enter a code (3-40 letters, digits, _ or -) or a bulk count.' : '',
    bulk && form.code !== '' ? 'Use either a code or a count.' : '',
    form.usageLimit !== '' && !(Number(form.usageLimit) > 0) ? 'Usage limit must be positive.' : '',
    form.expiresAt !== '' && new Date(form.expiresAt) <= new Date() ? 'Expiry must be in the future.' : '',
  ].filter(Boolean);
  return (
    <Stack spacing={2}>
      {message && <Alert severity={message.severity}>{message.text}</Alert>}
      {can('coupon:manage') && (
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Typography variant="subtitle2" gutterBottom>Create coupon(s)</Typography>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={1}>
            <TextField size="small" label="Campaign ID" value={form.campaignId} onChange={(e) => setForm({ ...form, campaignId: e.target.value })} />
            <TextField size="small" label="Code (single)" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} />
            <TextField size="small" label="Bulk count" value={form.count} onChange={(e) => setForm({ ...form, count: e.target.value })} />
            <TextField size="small" label="Bulk prefix" value={form.prefix} onChange={(e) => setForm({ ...form, prefix: e.target.value })} />
            <TextField size="small" label="Usage limit" value={form.usageLimit} onChange={(e) => setForm({ ...form, usageLimit: e.target.value })} />
            <TextField size="small" label="Per customer" value={form.perCustomerLimit} onChange={(e) => setForm({ ...form, perCustomerLimit: e.target.value })} />
            <TextField size="small" type="datetime-local" label="Expires" InputLabelProps={{ shrink: true }} value={form.expiresAt} onChange={(e) => setForm({ ...form, expiresAt: e.target.value })} />
            <Button variant="contained" disabled={problems.length > 0} title={problems.join(' ')} onClick={() => run(() => api.createCoupons({
              campaignId: Number(form.campaignId), code: form.code || null, count: form.count ? Number(form.count) : null, prefix: form.prefix || null,
              usageLimit: form.usageLimit ? Number(form.usageLimit) : null, perCustomerLimit: form.perCustomerLimit ? Number(form.perCustomerLimit) : null,
              expiresAt: form.expiresAt ? new Date(form.expiresAt).toISOString() : null }), 'Coupon(s) created.', () => { void reload(); })}>Create</Button>
          </Stack>
        </Paper>
      )}
      <TextField size="small" label="Filter by campaign ID" sx={{ maxWidth: 240 }} value={campaignId} onChange={(e) => setCampaignId(e.target.value.replace(/\D/g, ''))} />
      <Loading loading={loading} error={error} />
      {data && <DataTable rows={data} empty="No coupons." columns={[
        { header: 'Code', render: (r) => <code>{r.code}</code> }, { header: 'Campaign', render: (r) => `#${r.campaignId}` },
        { header: 'Redeemed', render: (r) => `${r.redeemedCount}${r.usageLimit ? ` / ${r.usageLimit}` : ''}` },
        { header: 'Expires', render: (r) => shortDate(r.expiresAt) }, { header: 'Status', render: (r) => <StatusChip status={r.status} /> },
        { header: '', render: (r) => can('coupon:manage') && (
          <Stack direction="row" spacing={1}>
            {r.status === 'ACTIVE' && <Button size="small" onClick={() => run(() => api.couponAction(r.id, 'pause'), 'Paused.', () => { void reload(); })}>Pause</Button>}
            {r.status === 'PAUSED' && <Button size="small" onClick={() => run(() => api.couponAction(r.id, 'activate'), 'Activated.', () => { void reload(); })}>Activate</Button>}
            {r.status !== 'REVOKED' && <Button size="small" color="error" onClick={() => { if (window.confirm(`Revoke ${r.code}? This cannot be undone.`)) void run(() => api.couponAction(r.id, 'revoke'), 'Revoked.', () => { void reload(); }); }}>Revoke</Button>}
          </Stack>) },
      ]} />}
      <Typography variant="subtitle2">Redemption history</Typography>
      <Loading loading={redemptions.loading} error={redemptions.error} />
      {redemptions.data && <DataTable rows={redemptions.data} empty="No redemptions yet." columns={[
        { header: 'When', render: (r) => shortDate(r.redeemedAt) }, { header: 'Code', render: (r) => r.code }, { header: 'Customer', render: (r) => r.customerId },
        { header: 'Order', render: (r) => r.orderRef }, { header: 'Discount', align: 'right', render: (r) => `${r.discountAmount} ${r.currency}` }, { header: 'Status', render: (r) => <StatusChip status={r.status} /> },
      ]} />}
    </Stack>
  );
}

export function CalendarSection() {
  const [range] = useState(() => ({ from: new Date(Date.now() - 7 * 86_400_000).toISOString(), to: new Date(Date.now() + 90 * 86_400_000).toISOString() }));
  const { data, loading, error } = useLoad(() => api.calendar(range.from, range.to));
  return (
    <Stack spacing={1}>
      <Typography variant="body2" color="text.secondary">Next 90 days. Campaigns flagged "conflict" share priority, dates and targets with another non-stackable campaign.</Typography>
      <Loading loading={loading} error={error} />
      {data && <DataTable rows={data} empty="No campaigns in this window." columns={[
        { header: 'Campaign', render: (r) => r.name }, { header: 'Starts', render: (r) => shortDate(r.startsAt) }, { header: 'Ends', render: (r) => shortDate(r.endsAt) },
        { header: 'Status', render: (r) => <StatusChip status={r.status} /> },
        { header: 'Conflicts', render: (r) => (r.conflictsWith?.length ? <StatusChip status={`CONFLICT ${r.conflictsWith.join(',')}`} /> : '—') },
      ]} />}
    </Stack>
  );
}

export function ApprovalSection({ can }: SectionProps) {
  const { data, loading, error, reload } = useLoad(() => api.approvals('PENDING'));
  const { message, run } = useAction();
  const [note, setNote] = useState('');
  return (
    <Stack spacing={1}>
      {message && <Alert severity={message.severity}>{message.text}</Alert>}
      {can('promotion:approve') && <TextField size="small" label="Decision note (required to reject)" value={note} onChange={(e) => setNote(e.target.value)} />}
      <Loading loading={loading} error={error} />
      {data && <DataTable rows={data} empty="No pending approvals." columns={[
        { header: 'Subject', render: (r) => `${r.subjectType} #${r.subjectId}` }, { header: 'Reason', render: (r) => r.reason ?? '—' },
        { header: 'Requested', render: (r) => shortDate(r.requestedAt) }, { header: 'By', render: (r) => `user ${r.requestedBy}` },
        { header: '', render: (r) => {
          const decide = (approve: boolean) => run(() => (r.subjectType === 'CAMPAIGN' ? api.decideCampaign(r.subjectId, approve, note) : api.decideScheduled(r.subjectId, approve, note)), approve ? 'Approved.' : 'Rejected.', () => { setNote(''); void reload(); });
          const allowed = r.subjectType === 'CAMPAIGN' ? can('promotion:approve') : can('pricing:approve');
          return allowed ? <Stack direction="row" spacing={1}><Button size="small" onClick={() => decide(true)}>Approve</Button><Button size="small" color="error" disabled={note.trim() === ''} onClick={() => decide(false)}>Reject</Button></Stack> : null;
        } },
      ]} />}
    </Stack>
  );
}

export function PerformanceSection() {
  const { data, loading, error } = useLoad(() => api.performance());
  return (
    <>
      <Loading loading={loading} error={error} />
      {data && <DataTable rows={data} empty="No campaigns yet." columns={[
        { header: 'Campaign', render: (r) => r.name }, { header: 'Status', render: (r) => <StatusChip status={r.status} /> },
        { header: 'Orders using it', align: 'right', render: (r) => String(r.usageCount) },
        { header: 'Coupon redemptions', align: 'right', render: (r) => String(r.redemptions) },
        { header: 'Coupon discount given', align: 'right', render: (r) => String(r.discountGiven) },
        { header: 'Unique coupon customers', align: 'right', render: (r) => String(r.uniqueCustomers) },
      ]} />}
    </>
  );
}
