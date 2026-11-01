import { useState } from 'react';
import {
  Alert, Box, Button, Checkbox, FormControlLabel, Grid, MenuItem, Paper, Select, Stack, TextField, Typography,
} from '@mui/material';
import { api, errorMessage } from '../pricingApi';
import type { Row } from '../pricingApi';
import { DataTable, Loading, StatusChip, shortDate, useLoad } from '../common';
import { buildPriceRequest, formatMoney, precheckPriceCsv, validatePriceForm } from '../pricingModel';
import type { PriceForm } from '../pricingModel';

export interface SectionProps { can: (permission: string) => boolean; settings: Row | null }

function useAction() {
  const [message, setMessage] = useState<{ severity: 'success' | 'error'; text: string } | null>(null);
  const run = async (work: () => Promise<unknown>, success: string, after?: () => void) => {
    try { await work(); setMessage({ severity: 'success', text: success }); after?.(); } catch (e) { setMessage({ severity: 'error', text: errorMessage(e) }); }
  };
  return { message, run, clear: () => setMessage(null) };
}

const supported = (settings: Row | null): string[] => (settings?.supportedCurrencies as string[] | undefined) ?? ['USD'];

export function DashboardSection() {
  const { data, loading, error } = useLoad(() => api.dashboard());
  const tiles: [string, string][] = [
    ['pricedProducts', 'Priced products'], ['totalProducts', 'Catalog products'], ['scheduledPrices', 'Scheduled price changes'],
    ['pendingApprovals', 'Pending approvals'], ['activeCampaigns', 'Active campaigns'], ['upcomingCampaigns', 'Upcoming campaigns'],
    ['expiredCampaigns', 'Expired promotions'], ['productsWithSalePrice', 'Products with a sale price'],
    ['activeCoupons', 'Active coupons'], ['redemptions', 'Coupon redemptions'], ['recentPriceChanges', 'Price changes, last 7 days'],
  ];
  return (
    <Box>
      <Loading loading={loading} error={error} />
      {data && <Grid container spacing={2}>{tiles.map(([key, label]) => (
        <Grid item xs={6} md={3} key={key}><Paper variant="outlined" sx={{ p: 2 }}>
          <Typography variant="caption" color="text.secondary">{label}</Typography>
          <Typography variant="h5">{String(data[key] ?? 0)}</Typography>
        </Paper></Grid>
      ))}</Grid>}
      {data && (
        <Box sx={{ mt: 3 }}>
          <Typography variant="subtitle2" gutterBottom>Promotion-generated orders (by currency)</Typography>
          <DataTable rows={(data.promotionOrdersByCurrency as Row[]) ?? []} empty="No orders have used a promotion yet." columns={[
            { header: 'Currency', render: (r) => r.currency }, { header: 'Orders', align: 'right', render: (r) => String(r.orders) },
            { header: 'Total discount value', align: 'right', render: (r) => formatMoney(r.discountTotal, r.currency) },
            { header: 'Promotion-generated revenue', align: 'right', render: (r) => formatMoney(r.revenue, r.currency) },
          ]} />
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>Conversion rate: {data.conversionRate == null ? String(data.conversionRateNote) : `${data.conversionRate}`}</Typography>
        </Box>
      )}
    </Box>
  );
}

export function PricesSection({ can, settings }: SectionProps) {
  const [filter, setFilter] = useState('');
  const { data, loading, error, reload } = useLoad(() => api.prices(filter || undefined), [filter]);
  const history = useLoad(() => api.history(filter || undefined), [filter]);
  const { message, run } = useAction();
  const canCost = can('pricing:cost:read');
  const [form, setForm] = useState<PriceForm>({ productId: '', currency: (settings?.defaultCurrency as string) ?? 'USD', basePrice: '', salePrice: '', costPrice: '',
    minSellingPrice: '', maxDiscountPercent: '', taxInclusive: false, effectiveFrom: '', effectiveTo: '' });
  const [csvErrors, setCsvErrors] = useState<string[]>([]);

  const submit = (existing: boolean) => {
    const errors = validatePriceForm(form, supported(settings), canCost);
    if (errors.length) { setCsvErrors(errors); return; }
    setCsvErrors([]);
    const body: Row = buildPriceRequest(form);
    void run(() => (existing ? api.updatePrice(body) : api.createPrice(body)), existing ? 'Price updated.' : 'Price created.', () => { void reload(); void history.reload(); });
  };

  const download = () => run(async () => {
    const csv = await api.exportCsv();
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
    const a = document.createElement('a'); a.href = url; a.download = 'prices.csv'; a.click(); URL.revokeObjectURL(url);
  }, 'Export downloaded.');

  const upload = async (file: File | undefined) => {
    if (!file) return;
    const text = await file.text();
    const errors = precheckPriceCsv(text, supported(settings));
    setCsvErrors(errors);
    if (errors.length === 0) void run(() => api.importCsv(text), 'Import applied.', () => { void reload(); void history.reload(); });
  };

  return (
    <Stack spacing={2}>
      {message && <Alert severity={message.severity}>{message.text}</Alert>}
      {csvErrors.length > 0 && <Alert severity="warning">{csvErrors.map((e) => <div key={e}>{e}</div>)}</Alert>}
      {(can('pricing:create') || can('pricing:update')) && (
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Typography variant="subtitle2" gutterBottom>Set a product price</Typography>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={1}>
            <TextField size="small" label="Product ID" value={form.productId} onChange={(e) => setForm({ ...form, productId: e.target.value })} />
            <Select size="small" value={form.currency} onChange={(e) => setForm({ ...form, currency: e.target.value })}>
              {supported(settings).map((c) => <MenuItem key={c} value={c}>{c}</MenuItem>)}
            </Select>
            <TextField size="small" label="Base price" value={form.basePrice} onChange={(e) => setForm({ ...form, basePrice: e.target.value })} />
            <TextField size="small" label="Sale price" value={form.salePrice} onChange={(e) => setForm({ ...form, salePrice: e.target.value })} />
            {canCost && <TextField size="small" label="Cost price" value={form.costPrice} onChange={(e) => setForm({ ...form, costPrice: e.target.value })} />}
            <TextField size="small" label="Min selling price" value={form.minSellingPrice} onChange={(e) => setForm({ ...form, minSellingPrice: e.target.value })} />
            <TextField size="small" label="Max discount %" value={form.maxDiscountPercent} onChange={(e) => setForm({ ...form, maxDiscountPercent: e.target.value })} />
            <FormControlLabel control={<Checkbox checked={!!form.taxInclusive} onChange={(e) => setForm({ ...form, taxInclusive: e.target.checked })} />} label="Tax inclusive" />
            <TextField size="small" type="datetime-local" label="Effective from" InputLabelProps={{ shrink: true }} value={form.effectiveFrom} onChange={(e) => setForm({ ...form, effectiveFrom: e.target.value })} />
            <TextField size="small" type="datetime-local" label="Effective to" InputLabelProps={{ shrink: true }} value={form.effectiveTo} onChange={(e) => setForm({ ...form, effectiveTo: e.target.value })} />
            {can('pricing:create') && <Button variant="contained" onClick={() => submit(false)}>Create</Button>}
            {can('pricing:update') && <Button variant="outlined" onClick={() => submit(true)}>Update</Button>}
          </Stack>
        </Paper>
      )}
      <Stack direction="row" spacing={1} alignItems="center">
        <TextField size="small" label="Filter by product ID" value={filter} onChange={(e) => setFilter(e.target.value.replace(/\D/g, ''))} />
        {can('pricing:export') && <Button onClick={download}>Export CSV</Button>}
        {can('pricing:update') && can('pricing:create') && <Button component="label">Import CSV<input hidden type="file" accept=".csv,text/csv" onChange={(e) => { void upload(e.target.files?.[0]); e.target.value = ''; }} /></Button>}
      </Stack>
      <Loading loading={loading} error={error} />
      {data && <DataTable rows={data} empty="No prices yet." columns={[
        { header: 'Product', render: (r) => `${r.productName ?? ''} (#${r.productId})` },
        { header: 'Currency', render: (r) => r.currency },
        { header: 'Base', align: 'right', render: (r) => formatMoney(r.basePrice, r.currency) },
        { header: 'Sale', align: 'right', render: (r) => formatMoney(r.salePrice, r.currency) },
        { header: 'Min selling', align: 'right', render: (r) => formatMoney(r.minSellingPrice, r.currency) },
        { header: 'Max disc.', align: 'right', render: (r) => (r.maxDiscountPercent ? `${r.maxDiscountPercent}%` : '—') },
        { header: 'Tax', render: (r) => (r.taxInclusive ? 'incl.' : 'excl.') },
        { header: 'Effective', render: (r) => `${shortDate(r.effectiveFrom)}${r.effectiveTo ? ' → ' + shortDate(r.effectiveTo) : ''}` },
        ...(canCost ? [{ header: 'Cost', align: 'right' as const, render: (r: Row) => formatMoney(r.costPrice, r.currency) }] : []),
        { header: 'Updated', render: (r) => shortDate(r.updatedAt) },
      ]} />}
      <Typography variant="subtitle2">Price history</Typography>
      <Loading loading={history.loading} error={history.error} />
      {history.data && <DataTable rows={history.data} empty="No history yet." columns={[
        { header: 'When', render: (r) => shortDate(r.changedAt) },
        { header: 'Product', render: (r) => `#${r.productId}` },
        { header: 'Change', render: (r) => `${r.oldBasePrice ?? '—'} → ${r.newBasePrice}` },
        { header: 'Source', render: (r) => r.changeSource },
        { header: 'By', render: (r) => `user ${r.changedBy}` },
        { header: '', render: (r) => can('pricing:publish') && r.oldBasePrice != null
          ? <Button size="small" onClick={() => run(() => api.rollback(r.id), 'Rolled back.', () => { void reload(); void history.reload(); })}>Roll back</Button> : null },
      ]} />}
    </Stack>
  );
}

export function BulkSection({ settings }: SectionProps) {
  const { message, run } = useAction();
  const [ids, setIds] = useState('');
  const [mode, setMode] = useState('PERCENT');
  const [value, setValue] = useState('');
  const [preview, setPreview] = useState<Row[] | null>(null);
  const body = (dryRun: boolean): Row => ({ productIds: ids.split(/[\s,;]+/).filter(Boolean).map(Number), mode, value: Number(value), currency: (settings?.defaultCurrency as string) ?? 'USD', dryRun, reason: 'Bulk update' });
  const valid = /^[\d\s,;]+$/.test(ids) && value !== '' && Number(value) !== 0 && Number.isFinite(Number(value));
  return (
    <Stack spacing={2}>
      {message && <Alert severity={message.severity}>{message.text}</Alert>}
      <Alert severity="info">Bulk updates are all-or-nothing. Preview first; changes beyond the approval threshold need the publish permission.</Alert>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1}>
        <TextField size="small" fullWidth label="Product IDs (comma separated)" value={ids} onChange={(e) => setIds(e.target.value)} />
        <Select size="small" value={mode} onChange={(e) => setMode(e.target.value)}><MenuItem value="PERCENT">Percent change</MenuItem><MenuItem value="AMOUNT">Amount change</MenuItem></Select>
        <TextField size="small" label="Value (+/−)" value={value} onChange={(e) => setValue(e.target.value)} />
        <Button disabled={!valid} onClick={() => run(async () => setPreview(await api.bulk(body(true))), 'Preview ready.')}>Preview</Button>
        <Button variant="contained" disabled={!valid || preview === null} onClick={() => run(() => api.bulk(body(false)), 'Prices updated.', () => setPreview(null))}>Apply</Button>
      </Stack>
      {preview && <DataTable rows={preview} empty="" columns={[
        { header: 'Product', render: (r) => `#${r.productId}` }, { header: 'Old base', align: 'right', render: (r) => String(r.oldBasePrice) }, { header: 'New base', align: 'right', render: (r) => String(r.newBasePrice) },
      ]} />}
    </Stack>
  );
}

export function ScheduledSection({ can, settings }: SectionProps) {
  const { data, loading, error, reload } = useLoad(() => api.scheduled());
  const { message, run } = useAction();
  const [form, setForm] = useState({ productId: '', basePrice: '', salePrice: '', effectiveFrom: '' });
  const formErrors = [
    !/^\d+$/.test(form.productId) ? 'Product is required.' : '',
    !(Number(form.basePrice) > 0) ? 'Base price must be greater than zero.' : '',
    form.salePrice !== '' && Number(form.salePrice) > Number(form.basePrice) ? 'Sale price cannot exceed base.' : '',
    form.effectiveFrom === '' || new Date(form.effectiveFrom) <= new Date() ? 'Pick a future date and time.' : '',
  ].filter(Boolean);
  return (
    <Stack spacing={2}>
      {message && <Alert severity={message.severity}>{message.text}</Alert>}
      {can('pricing:create') && (
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={1}>
            <TextField size="small" label="Product ID" value={form.productId} onChange={(e) => setForm({ ...form, productId: e.target.value })} />
            <TextField size="small" label="New base price" value={form.basePrice} onChange={(e) => setForm({ ...form, basePrice: e.target.value })} />
            <TextField size="small" label="New sale price" value={form.salePrice} onChange={(e) => setForm({ ...form, salePrice: e.target.value })} />
            <TextField size="small" type="datetime-local" label="Effective from" InputLabelProps={{ shrink: true }} value={form.effectiveFrom} onChange={(e) => setForm({ ...form, effectiveFrom: e.target.value })} />
            <Button variant="contained" disabled={formErrors.length > 0} title={formErrors.join(' ')} onClick={() => run(() => api.schedule({
              productId: Number(form.productId), currency: (settings?.defaultCurrency as string) ?? 'USD', basePrice: Number(form.basePrice),
              salePrice: form.salePrice === '' ? null : Number(form.salePrice), effectiveFrom: new Date(form.effectiveFrom).toISOString(),
            }), 'Scheduled; awaiting approval.', () => { void reload(); })}>Schedule</Button>
          </Stack>
        </Paper>
      )}
      <Loading loading={loading} error={error} />
      {data && <DataTable rows={data} empty="Nothing scheduled." columns={[
        { header: 'Product', render: (r) => `${r.productName ?? ''} (#${r.productId})` },
        { header: 'New price', align: 'right', render: (r) => formatMoney(r.newBasePrice, r.currency) },
        { header: 'Effective', render: (r) => shortDate(r.effectiveFrom) },
        { header: 'Status', render: (r) => <StatusChip status={r.status} /> },
        { header: '', render: (r) => (
          <Stack direction="row" spacing={1}>
            {r.status === 'PENDING_APPROVAL' && can('pricing:approve') && <>
              <Button size="small" onClick={() => run(() => api.decideScheduled(r.id, true, ''), 'Approved.', () => { void reload(); })}>Approve</Button>
              <Button size="small" color="error" onClick={() => run(() => api.decideScheduled(r.id, false, 'Rejected'), 'Rejected.', () => { void reload(); })}>Reject</Button></>}
            {['PENDING_APPROVAL', 'SCHEDULED'].includes(r.status) && can('pricing:update') && <Button size="small" onClick={() => run(() => api.cancelScheduled(r.id), 'Cancelled.', () => { void reload(); })}>Cancel</Button>}
          </Stack>) },
      ]} />}
    </Stack>
  );
}
