import { useState } from 'react';
import { Alert, Box, Button, Checkbox, Chip, FormControlLabel, Grid, MenuItem, Paper, Select, Stack, TextField, Typography } from '@mui/material';
import { DataTable, Loading, StatusChip, useLoad, useNotice, when } from '../control/common';
import { returnsApi } from './returnsApi';
import type { Row } from './returnsApi';
import { activeFilterCount, age, buildQuery, emptyFilter, filterProblems, flagLabel, money, reasonLabel, statusTone } from './returnsModel';
import type { ListFilter } from './returnsModel';
import { ReasonDialog } from './returnWidgets';

// ---- dashboard ------------------------------------------------------------------------------------------------------------------

function useScope() {
  const [f, setF] = useState({ from: '', to: '', storeId: '', vendorId: '', kind: '' });
  const [applied, setApplied] = useState(f);
  const query = new URLSearchParams(Object.entries(applied).filter(([, v]) => v !== '') as [string, string][]).toString();
  const bad = f.from && f.to && f.to < f.from ? 'The end date is before the start date.' : '';
  const bar = (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap alignItems="center">
        <TextField size="small" type="date" label="From" value={f.from} onChange={(e) => setF({ ...f, from: e.target.value })} slotProps={{ inputLabel: { shrink: true } }} />
        <TextField size="small" type="date" label="To" value={f.to} onChange={(e) => setF({ ...f, to: e.target.value })} slotProps={{ inputLabel: { shrink: true } }} />
        <TextField size="small" label="Store" value={f.storeId} onChange={(e) => setF({ ...f, storeId: e.target.value })} />
        <TextField size="small" label="Vendor" value={f.vendorId} onChange={(e) => setF({ ...f, vendorId: e.target.value })} />
        <Select size="small" displayEmpty value={f.kind} onChange={(e) => setF({ ...f, kind: e.target.value })}><MenuItem value="">Retail and restaurant</MenuItem><MenuItem value="RETAIL_RETURN">Retail returns</MenuItem><MenuItem value="RESTAURANT_CLAIM">Restaurant claims</MenuItem></Select>
        <Button variant="contained" disabled={!!bad} onClick={() => setApplied(f)}>Apply</Button>
        {bad && <Typography color="error" variant="caption">{bad}</Typography>}
      </Stack>
    </Paper>
  );
  return { query, bar };
}

const TILES: [string, string, (v: any) => string][] = [
  ['Total returns', 'totalReturns', String], ['Pending approvals', 'pendingApprovals', String], ['Approved', 'approved', String], ['Rejected', 'rejected', String],
  ['Awaiting pickup', 'awaitingPickup', String], ['In transit', 'inTransit', String], ['Awaiting inspection', 'awaitingInspection', String], ['Refunds pending', 'refundsPending', String],
  ['Refunds completed', 'refundsCompleted', String], ['Total refunded', 'totalRefundAmount', money], ['Return rate', 'returnRatePercent', (v) => `${v}%`],
  ['Avg processing time', 'avgProcessingHours', (v) => `${v} h`], ['Avg refund time', 'avgRefundHours', (v) => `${v} h`], ['Open review items', 'exceptionsOpen', String],
];

export function ReturnsDashboard() {
  const scope = useScope();
  const m = useLoad(() => returnsApi.dashboard(scope.query), [scope.query]);
  const d = m.data;
  return (
    <Stack spacing={2}>
      {scope.bar}
      <Loading loading={m.loading} error={m.error} />
      {d && <>
        <Grid container spacing={2}>{TILES.map(([label, key, fmt]) => (
          <Grid item xs={6} md={3} key={key}><Paper variant="outlined" sx={{ p: 2, height: '100%' }}><Typography variant="caption" color="text.secondary">{label}</Typography><Typography variant="h5">{d[key] === null || d[key] === undefined ? '—' : fmt(d[key])}</Typography></Paper></Grid>
        ))}</Grid>
        <Typography variant="caption" color="text.secondary">Return rate = orders with a return ÷ delivered orders in the same scope ({d.deliveredOrders} delivered). Times are averaged over closed returns and completed refunds. Amounts have no currency symbol: orders do not store a currency code.</Typography>
        <Grid container spacing={2}>
          <Grid item xs={12} md={6}><Typography variant="subtitle1" gutterBottom>Top return reasons</Typography>
            <DataTable rows={d.topReasons ?? []} empty="No returns in this scope." columns={[{ header: 'Reason', render: (x) => reasonLabel(x.reasonCode) }, { header: 'Returns', align: 'right', render: (x) => x.returns }]} /></Grid>
          <Grid item xs={12} md={6}><Typography variant="subtitle1" gutterBottom>Products returned most</Typography>
            <DataTable rows={d.highReturnProducts ?? []} empty="No returned products." columns={[{ header: 'Product', render: (x) => x.productName }, { header: 'Units', align: 'right', render: (x) => x.returnedQuantity }, { header: 'Returns', align: 'right', render: (x) => x.returns }]} /></Grid>
        </Grid>
      </>}
    </Stack>
  );
}

// ---- queues -----------------------------------------------------------------------------------------------------------------------

export function ReturnsQueue({ view, can, onOpen }: { view?: string; can: (p: string) => boolean; onOpen: (id: number) => void }) {
  const [f, setF] = useState<ListFilter>(emptyFilter());
  const [applied, setApplied] = useState<ListFilter>(emptyFilter());
  const [page, setPage] = useState(0);
  const problems = filterProblems(f);
  const query = buildQuery(view, applied, page, 25);
  const list = useLoad(() => returnsApi.list(query), [query]);
  const note = useNotice();
  const exportCsv = () => note.run(async () => {
    const csv = await returnsApi.exportCsv(buildQuery(view, applied, 0, 100));
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
    const a = document.createElement('a'); a.href = url; a.download = 'returns.csv'; a.click(); setTimeout(() => URL.revokeObjectURL(url), 10_000);
  }, 'Export downloaded.');
  const d = list.data;
  return (
    <Stack spacing={2}>
      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap alignItems="center">
          <TextField size="small" label="Search RMA, order, customer" value={f.q} onChange={(e) => setF({ ...f, q: e.target.value })} sx={{ minWidth: 240 }} onKeyDown={(e) => { if (e.key === 'Enter' && !problems.length) { setApplied(f); setPage(0); } }} />
          {!view && <Select size="small" displayEmpty value={f.status} onChange={(e) => setF({ ...f, status: e.target.value })}><MenuItem value="">Any status</MenuItem>
            {['REQUESTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'PICKUP_SCHEDULED', 'PICKED_UP', 'IN_TRANSIT', 'RECEIVED', 'INSPECTION_PENDING', 'INSPECTION_COMPLETED', 'REFUND_PENDING', 'REFUND_PROCESSING', 'REFUNDED', 'EXCHANGE_PENDING', 'EXCHANGED', 'CLOSED', 'CUSTOMER_CANCELLED'].map((s) => <MenuItem key={s} value={s}>{s.replace(/_/g, ' ').toLowerCase()}</MenuItem>)}</Select>}
          <Select size="small" displayEmpty value={f.kind} onChange={(e) => setF({ ...f, kind: e.target.value })}><MenuItem value="">Retail and restaurant</MenuItem><MenuItem value="RETAIL_RETURN">Retail returns</MenuItem><MenuItem value="RESTAURANT_CLAIM">Restaurant claims</MenuItem></Select>
          <Select size="small" displayEmpty value={f.resolution} onChange={(e) => setF({ ...f, resolution: e.target.value })}><MenuItem value="">Any resolution</MenuItem><MenuItem value="REFUND">Refund</MenuItem><MenuItem value="EXCHANGE">Exchange</MenuItem><MenuItem value="STORE_CREDIT">Store credit</MenuItem></Select>
          <TextField size="small" label="Store" value={f.storeId} onChange={(e) => setF({ ...f, storeId: e.target.value })} sx={{ width: 120 }} />
          <TextField size="small" type="date" label="From" value={f.from} onChange={(e) => setF({ ...f, from: e.target.value })} slotProps={{ inputLabel: { shrink: true } }} />
          <TextField size="small" type="date" label="To" value={f.to} onChange={(e) => setF({ ...f, to: e.target.value })} slotProps={{ inputLabel: { shrink: true } }} />
          <FormControlLabel control={<Checkbox checked={f.flagged} onChange={(e) => setF({ ...f, flagged: e.target.checked })} />} label="Flagged for review" />
          <Button variant="contained" disabled={problems.length > 0} onClick={() => { setApplied(f); setPage(0); }}>Apply</Button>
          <Button onClick={() => { setF(emptyFilter()); setApplied(emptyFilter()); setPage(0); }} disabled={activeFilterCount(applied) === 0}>Clear</Button>
          {can('return:export') && <Button onClick={() => void exportCsv()}>Export CSV</Button>}
        </Stack>
        {problems.map((p) => <Typography key={p} color="error" variant="caption" display="block">{p}</Typography>)}
      </Paper>
      {note.view}
      <Loading loading={list.loading} error={list.error} />
      {d && <DataTable rows={d.items} empty="No return requests match." columns={[
        { header: 'RMA', render: (x) => <Button size="small" onClick={() => onOpen(x.id)}>{x.rmaNumber ?? `#${x.id}`}</Button> }, { header: 'Order', render: (x) => x.orderNumber },
        { header: 'Customer', render: (x) => x.customerName ?? '—' }, { header: 'Type', render: (x) => `${x.returnKind === 'RESTAURANT_CLAIM' ? 'claim' : 'return'} · ${String(x.resolution).toLowerCase().replace('_', ' ')}` },
        { header: 'Reason', render: (x) => reasonLabel(x.reasonCode) }, { header: 'Items', align: 'right', render: (x) => x.itemQuantity }, { header: 'Amount', align: 'right', render: (x) => money(x.refundAmount) },
        { header: 'Status', render: (x) => <StatusChip status={x.status} /> }, { header: 'Flags', render: (x) => (x.riskFlags ?? []).map((fl: string) => <Chip key={fl} size="small" color="warning" variant="outlined" label={flagLabel(fl)} sx={{ mr: 0.5 }} />) },
        { header: 'Age', render: (x) => age(x.createdAt) }, { header: 'Store', render: (x) => x.storeId },
      ]} />}
      {d && <Stack direction="row" spacing={1} alignItems="center">
        <Button disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button>
        <Typography variant="body2">{d.total === 0 ? '0' : `${page * 25 + 1}–${Math.min(d.total, (page + 1) * 25)}`} of {d.total}</Typography>
        <Button disabled={(page + 1) * 25 >= d.total} onClick={() => setPage(page + 1)}>Next</Button>
      </Stack>}
    </Stack>
  );
}

// ---- exchanges ----------------------------------------------------------------------------------------------------------------------

export function ExchangesScreen({ onOpen }: { onOpen: (id: number) => void }) {
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const list = useLoad(() => returnsApi.exchanges(new URLSearchParams({ ...(status ? { status } : {}), page: String(page), size: '25' }).toString()), [status, page]);
  const d = list.data;
  return (
    <Stack spacing={2}>
      <Select size="small" displayEmpty value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }} sx={{ maxWidth: 260 }}><MenuItem value="">All exchanges</MenuItem>
        {['PENDING_APPROVAL', 'APPROVED', 'FULFILLMENT_CREATED', 'DISPATCHED', 'REJECTED', 'CANCELLED'].map((s) => <MenuItem key={s} value={s}>{s.replace(/_/g, ' ').toLowerCase()}</MenuItem>)}</Select>
      <Loading loading={list.loading} error={list.error} />
      {d && <DataTable rows={d.items} empty="No exchange requests." columns={[
        { header: 'RMA', render: (x) => <Button size="small" onClick={() => onOpen(x.returnId)}>{x.rmaNumber}</Button> }, { header: 'Replacement', render: (x) => `${x.newProductName ?? '#' + x.newProductId}${x.newVariant ? ` (${x.newVariant})` : ''} × ${x.quantity}` },
        { header: 'Price difference', align: 'right', render: (x) => money(x.priceDifference) }, { header: 'Exchange', render: (x) => <StatusChip status={x.status} /> }, { header: 'Return', render: (x) => <StatusChip status={x.returnStatus} /> },
        { header: 'Replacement order', render: (x) => x.replacementOrderId ?? '—' }, { header: 'Created', render: (x) => when(x.createdAt) },
      ]} />}
      {d && d.total > 25 && <Stack direction="row" spacing={1}><Button disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button><Button disabled={(page + 1) * 25 >= d.total} onClick={() => setPage(page + 1)}>Next</Button></Stack>}
    </Stack>
  );
}

// ---- exceptions --------------------------------------------------------------------------------------------------------------------

export function ExceptionsScreen({ can, onOpen }: { can: (p: string) => boolean; onOpen: (id: number) => void }) {
  const [scope, setScope] = useState('open');
  const [category, setCategory] = useState('');
  const [page, setPage] = useState(0);
  const list = useLoad(() => returnsApi.exceptions(new URLSearchParams({ ...(scope ? { scope } : {}), ...(category ? { category } : {}), page: String(page), size: '25' }).toString()), [scope, category, page]);
  const note = useNotice();
  const [target, setTarget] = useState<{ row: Row; action: string } | null>(null);
  const d = list.data;
  const act = (row: Row, action: string) => (action === 'ESCALATE' ? void note.run(() => returnsApi.exceptionAction(row.id, action, null, ''), 'Escalated.', () => void list.reload()) : setTarget({ row, action }));
  return (
    <Stack spacing={2}>
      <Alert severity="info">These are signals for a human reviewer — duplicates, repeated or high-value returns, inspection mismatches, failed pickups, lost parcels, refund failures. Nothing here blocks a customer or accuses anyone.</Alert>
      <Stack direction="row" spacing={1}>
        <Select size="small" value={scope} onChange={(e) => { setScope(e.target.value); setPage(0); }}><MenuItem value="open">Open</MenuItem><MenuItem value="">All</MenuItem></Select>
        <Select size="small" displayEmpty value={category} onChange={(e) => { setCategory(e.target.value); setPage(0); }}><MenuItem value="">Any category</MenuItem>
          {['DUPLICATE_ATTEMPT', 'REPEAT_RETURNER', 'HIGH_VALUE', 'MISSING_ITEMS', 'INSPECTION_MISMATCH', 'PICKUP_FAILURE', 'LOST_SHIPMENT', 'REFUND_FAILURE', 'REFUND_AMOUNT_MISMATCH', 'THRESHOLD_EXCEEDED', 'SUSPICIOUS_PATTERN', 'MANUAL_REVIEW'].map((c) => <MenuItem key={c} value={c}>{c.replace(/_/g, ' ').toLowerCase()}</MenuItem>)}</Select>
      </Stack>
      {note.view}
      <Loading loading={list.loading} error={list.error} />
      {d && <DataTable rows={d.items} empty="Nothing needs review." columns={[
        { header: 'Priority', render: (x) => <StatusChip status={x.priority} tone={statusTone(x.priority)} /> }, { header: 'Category', render: (x) => x.category.replace(/_/g, ' ').toLowerCase() },
        { header: 'RMA', render: (x) => <Button size="small" onClick={() => onOpen(x.returnId)}>{x.rmaNumber}</Button> }, { header: 'Summary', render: (x) => x.summary },
        { header: 'Status', render: (x) => <StatusChip status={x.status} /> }, { header: 'Raised', render: (x) => when(x.createdAt) },
        { header: '', render: (x) => can('return:exception-manage') && !['RESOLVED', 'DISMISSED'].includes(x.status) ? <Stack direction="row" spacing={0.5}>
          <Button size="small" onClick={() => act(x, 'ASSIGN')}>Assign</Button><Button size="small" onClick={() => act(x, 'ESCALATE')}>Escalate</Button>
          <Button size="small" onClick={() => act(x, 'RESOLVE')}>Resolve</Button><Button size="small" color="inherit" onClick={() => act(x, 'DISMISS')}>Dismiss</Button></Stack> : null },
      ]} />}
      {d && d.total > 25 && <Stack direction="row" spacing={1}><Button disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button><Button disabled={(page + 1) * 25 >= d.total} onClick={() => setPage(page + 1)}>Next</Button></Stack>}
      {target && <ReasonDialog title={`${target.action[0] + target.action.slice(1).toLowerCase()} review item`} required={target.action !== 'ASSIGN'} label={target.action === 'ASSIGN' ? 'Assignee user ID' : 'Note (required)'} confirmLabel="Save" onClose={() => setTarget(null)}
        onConfirm={async (text) => {
          if (target.action === 'ASSIGN' && !/^\d+$/.test(text)) { note.fail('Enter the numeric user ID of the assignee.'); return; }
          await note.run(() => returnsApi.exceptionAction(target.row.id, target.action, target.action === 'ASSIGN' ? Number(text) : null, target.action === 'ASSIGN' ? '' : text), 'Saved.', () => { setTarget(null); void list.reload(); });
        }} />}
    </Stack>
  );
}

// ---- analytics and audit --------------------------------------------------------------------------------------------------------------

function Bars({ rows, label, value }: { rows: Row[]; label: (r: Row) => string; value: (r: Row) => number }) {
  const max = Math.max(1, ...rows.map(value));
  if (rows.length === 0) return <Typography color="text.secondary">No data for this scope.</Typography>;
  return <Stack spacing={0.5}>{rows.map((r, i) => (
    <Stack key={i} direction="row" spacing={1} alignItems="center"><Typography variant="caption" sx={{ width: 170, flexShrink: 0 }}>{label(r)}</Typography>
      <Box sx={{ flex: 1, bgcolor: 'action.hover', borderRadius: 0.5 }}><Box sx={{ width: `${(value(r) / max) * 100}%`, minWidth: 2, height: 14, bgcolor: 'primary.main', borderRadius: 0.5 }} /></Box>
      <Typography variant="caption" sx={{ width: 60, textAlign: 'right' }}>{value(r)}</Typography></Stack>
  ))}</Stack>;
}

export function ReturnsAnalytics() {
  const scope = useScope();
  const a = useLoad(() => returnsApi.analytics(scope.query), [scope.query]);
  const d = a.data;
  return (
    <Stack spacing={2}>
      {scope.bar}
      <Loading loading={a.loading} error={a.error} />
      {d && <Grid container spacing={3}>
        <Grid item xs={12} md={6}><Typography variant="subtitle1" gutterBottom>Returns by reason</Typography><Bars rows={d.byReason} label={(r) => reasonLabel(r.reasonCode)} value={(r) => Number(r.returns)} /></Grid>
        <Grid item xs={12} md={6}><Typography variant="subtitle1" gutterBottom>Returns by status</Typography><Bars rows={d.byStatus} label={(r) => r.status.replace(/_/g, ' ').toLowerCase()} value={(r) => Number(r.returns)} /></Grid>
        <Grid item xs={12} md={6}><Typography variant="subtitle1" gutterBottom>Returns by store</Typography><Bars rows={d.byStore} label={(r) => r.storeId} value={(r) => Number(r.returns)} /></Grid>
        <Grid item xs={12} md={6}><Typography variant="subtitle1" gutterBottom>Returns per day</Typography><Bars rows={d.byDay} label={(r) => String(r.bucketDay)} value={(r) => Number(r.returns)} /></Grid>
        <Grid item xs={12} md={6}><Typography variant="subtitle1" gutterBottom>Inspection outcomes</Typography><Bars rows={d.inspectionOutcomes} label={(r) => r.outcome.replace(/_/g, ' ').toLowerCase()} value={(r) => Number(r.inspections)} /></Grid>
        <Grid item xs={12} md={6}><Typography variant="subtitle1" gutterBottom>Refunds</Typography>
          <DataTable rows={d.refundsByMethod} empty="No refunds yet." columns={[{ header: 'Method', render: (r) => r.method.replace('_', ' ').toLowerCase() }, { header: 'Status', render: (r) => <StatusChip status={r.status} /> }, { header: 'Count', align: 'right', render: (r) => r.refunds }, { header: 'Amount', align: 'right', render: (r) => money(r.amount) }]} /></Grid>
        <Grid item xs={12} md={6}><Typography variant="subtitle1" gutterBottom>Exchanges</Typography>
          <DataTable rows={d.exchangeStatus} empty="No exchanges yet." columns={[{ header: 'Status', render: (r) => <StatusChip status={r.status} /> }, { header: 'Count', align: 'right', render: (r) => r.exchanges }, { header: 'Net price difference', align: 'right', render: (r) => money(r.priceDifference) }]} /></Grid>
        <Grid item xs={12} md={6}><Typography variant="body2">Flagged for review: <b>{d.flagged}</b> · Auto-approved by policy: <b>{d.autoApproved}</b></Typography></Grid>
      </Grid>}
    </Stack>
  );
}

export function ReturnsAudit({ onOpen }: { onOpen: (id: number) => void }) {
  const [f, setF] = useState({ returnId: '', entityType: '', action: '' });
  const [applied, setApplied] = useState(f);
  const [page, setPage] = useState(0);
  const query = new URLSearchParams({ ...Object.fromEntries(Object.entries(applied).filter(([, v]) => v)), page: String(page), size: '50' }).toString();
  const list = useLoad(() => returnsApi.audit(query), [query]);
  const d = list.data;
  const bad = f.returnId && !/^\d+$/.test(f.returnId) ? 'Return ID must be a number.' : '';
  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
        <TextField size="small" label="Return ID" value={f.returnId} onChange={(e) => setF({ ...f, returnId: e.target.value })} sx={{ width: 120 }} error={!!bad} helperText={bad} />
        <Select size="small" displayEmpty value={f.entityType} onChange={(e) => setF({ ...f, entityType: e.target.value })}><MenuItem value="">Any record</MenuItem>{['RETURN', 'REFUND', 'EXCHANGE', 'PICKUP', 'INSPECTION', 'ATTACHMENT', 'EXCEPTION', 'POLICY', 'SETTINGS', 'EXPORT', 'NOTE'].map((t) => <MenuItem key={t} value={t}>{t.toLowerCase()}</MenuItem>)}</Select>
        <TextField size="small" label="Action" value={f.action} onChange={(e) => setF({ ...f, action: e.target.value.toUpperCase() })} sx={{ width: 160 }} />
        <Button variant="contained" disabled={!!bad} onClick={() => { setApplied(f); setPage(0); }}>Apply</Button>
      </Stack>
      <Loading loading={list.loading} error={list.error} />
      {d && <DataTable rows={d.items} empty="No audit entries." columns={[
        { header: 'When', render: (x) => when(x.createdAt) }, { header: 'RMA', render: (x) => x.returnId ? <Button size="small" onClick={() => onOpen(x.returnId)}>{x.rmaNumber ?? `#${x.returnId}`}</Button> : '—' },
        { header: 'Record', render: (x) => `${x.entityType.toLowerCase()} ${x.entityId}` }, { header: 'Action', render: (x) => x.action.replace(/_/g, ' ').toLowerCase() },
        { header: 'By', render: (x) => x.actorEmail ?? (x.actorId ? `user ${x.actorId}` : 'system') }, { header: 'Detail', render: (x) => x.detail ?? '' },
      ]} />}
      {d && d.total > 50 && <Stack direction="row" spacing={1}><Button disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button><Button disabled={(page + 1) * 50 >= d.total} onClick={() => setPage(page + 1)}>Next</Button></Stack>}
    </Stack>
  );
}
