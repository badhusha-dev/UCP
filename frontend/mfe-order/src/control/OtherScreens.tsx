import { useState } from 'react';
import { Alert, Box, Button, Checkbox, FormControlLabel, Grid, MenuItem, Paper, Select, Stack, TextField, Typography } from '@mui/material';
import { DataTable, Loading, StatusChip, money, useLoad, useNotice, when } from './common';
import { orderApi } from './controlApi';
import type { Row } from './controlApi';
import { exceptionTone, formatMinutes } from './controlModel';

// ---- dashboard ----------------------------------------------------------------------------------------------------------------

export function DashboardScreen() {
  const [f, setF] = useState({ from: '', to: '', storeId: '', vendorId: '', orderType: '', paymentStatus: '', fulfillmentStatus: '' });
  const [applied, setApplied] = useState(f);
  const query = new URLSearchParams(Object.entries(applied).filter(([, v]) => v !== '') as [string, string][]).toString();
  const m = useLoad(() => orderApi.dashboard(query), [query]);
  const bad = f.from && f.to && f.to < f.from ? 'The end date is before the start date.' : '';
  const d = m.data;
  const tiles: [string, string, (v: any) => string][] = d ? [
    ['Orders today', 'ordersToday', String], ['Orders this month', 'ordersThisMonth', String], ['New orders awaiting processing', 'newOrders', String], ['Awaiting confirmation', 'awaitingConfirmation', String],
    ['Being processed', 'processing', String], ['Awaiting fulfillment', 'awaitingFulfillment', String], ['On hold', 'onHold', String], ['Delayed beyond SLA', 'delayedBeyondSla', String],
    ['Delivered (range)', 'delivered', String], ['Cancelled (range)', 'cancelled', String], ['Failed (range)', 'failed', String], ['Orders in range', 'ordersInRange', String],
    ['Avg processing time', 'averageProcessingMinutes', formatMinutes], ['Avg fulfillment time', 'averageFulfillmentMinutes', formatMinutes], ['Avg delivery time', 'averageDeliveryMinutes', formatMinutes],
    ['Gross order value', 'grossOrderValue', money], ['Pending payment value', 'pendingPaymentValue', money],
  ] : [];
  return (
    <Stack spacing={2}>
      <Paper variant="outlined" sx={{ p: 2 }}>
        {bad && <Alert severity="warning" sx={{ mb: 1 }}>{bad}</Alert>}
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} flexWrap="wrap" useFlexGap>
          <TextField size="small" type="date" label="From" InputLabelProps={{ shrink: true }} value={f.from} onChange={(e) => setF({ ...f, from: e.target.value })} />
          <TextField size="small" type="date" label="To" InputLabelProps={{ shrink: true }} value={f.to} onChange={(e) => setF({ ...f, to: e.target.value })} />
          <TextField size="small" label="Store" value={f.storeId} onChange={(e) => setF({ ...f, storeId: e.target.value })} />
          <TextField size="small" label="Vendor" value={f.vendorId} onChange={(e) => setF({ ...f, vendorId: e.target.value })} />
          <Select size="small" displayEmpty value={f.orderType} onChange={(e) => setF({ ...f, orderType: e.target.value })}><MenuItem value="">Any type</MenuItem><MenuItem value="RETAIL">Retail</MenuItem><MenuItem value="RESTAURANT">Restaurant</MenuItem></Select>
          <Select size="small" displayEmpty value={f.paymentStatus} onChange={(e) => setF({ ...f, paymentStatus: e.target.value })}><MenuItem value="">Any payment</MenuItem>{['UNKNOWN', 'PENDING', 'PAID', 'FAILED', 'REFUNDED'].map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}</Select>
          <Select size="small" displayEmpty value={f.fulfillmentStatus} onChange={(e) => setF({ ...f, fulfillmentStatus: e.target.value })}><MenuItem value="">Any fulfillment</MenuItem>{['UNFULFILLED', 'ALLOCATED', 'PARTIAL', 'FULFILLED'].map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}</Select>
          <Button variant="contained" disabled={!!bad} onClick={() => setApplied(f)}>Apply</Button>
        </Stack>
      </Paper>
      <Loading loading={m.loading} error={m.error} />
      {d && <>
        <Typography variant="caption" color="text.secondary">Date range {d.range.from} to {d.range.to}. Queue sizes are current; delivered, cancelled and failed count orders created in the range.{d.truncated ? ' Averages use the first 20,000 orders.' : ''}</Typography>
        <Grid container spacing={2}>{tiles.map(([label, key, fmt]) => (
          <Grid item xs={6} md={3} key={key}><Paper variant="outlined" sx={{ p: 2 }}><Typography variant="caption" color="text.secondary">{label}</Typography><Typography variant="h5">{d[key] === null || d[key] === undefined ? '—' : fmt(d[key])}</Typography></Paper></Grid>
        ))}</Grid>
        <Typography variant="caption" color="text.secondary">Order amounts are shown without a currency symbol: orders do not store a currency code.</Typography>
      </>}
    </Stack>
  );
}

// ---- exceptions -----------------------------------------------------------------------------------------------------------------

export function ExceptionsScreen({ can, onOpen }: { can: (p: string) => boolean; onOpen: (id: number) => void }) {
  const [filters, setFilters] = useState({ status: 'ACTIVE', category: '', priority: '' });
  const [page, setPage] = useState(0);
  const query = new URLSearchParams({ ...Object.fromEntries(Object.entries(filters).filter(([, v]) => v)), page: String(page), size: '25' }).toString();
  const list = useLoad(() => orderApi.exceptions(query), [query]);
  const [open, setOpen] = useState<Row | null>(null);
  const [note, setNote] = useState('');
  const [assignee, setAssignee] = useState('');
  const { run, view } = useNotice();
  const manage = can('order:exception:manage');
  const act = (action: string, body: Row) => open && run(async () => setOpen(await orderApi.exceptionAction(open.id, action, body)), 'Updated.', () => { setNote(''); void list.reload(); });
  const show = async (id: number) => { setOpen(await orderApi.exception(id)); setNote(''); };
  const s = list.data?.summary as Row | undefined;
  return (
    <Stack spacing={2}>
      {view}
      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap alignItems="center">
        {s && Object.entries(s).map(([k, v]) => <StatusChip key={k} status={`${k}: ${v}`} tone={k === 'RESOLVED' ? 'success' : k === 'ESCALATED' ? 'error' : 'warning'} />)}
        <Box sx={{ flex: 1 }} />
        <Select size="small" value={filters.status} onChange={(e) => { setFilters({ ...filters, status: e.target.value }); setPage(0); }}>{['ACTIVE', 'OPEN', 'ASSIGNED', 'ESCALATED', 'RESOLVED', 'DISMISSED'].map((x) => <MenuItem key={x} value={x}>{x}</MenuItem>)}</Select>
        <Select size="small" displayEmpty value={filters.priority} onChange={(e) => { setFilters({ ...filters, priority: e.target.value }); setPage(0); }}><MenuItem value="">Any priority</MenuItem>{['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((x) => <MenuItem key={x} value={x}>{x}</MenuItem>)}</Select>
        {manage && <Button variant="outlined" onClick={() => run(async () => { const r = await orderApi.scanExceptions(); void list.reload(); return r; }, 'Scan complete.')}>Scan now</Button>}
      </Stack>
      <Loading loading={list.loading} error={list.error} />
      {list.data && <DataTable rows={list.data.content} empty="No exceptions in this view." columns={[
        { header: 'Priority', render: (r) => <StatusChip status={r.priority} tone={exceptionTone(r.priority)} /> }, { header: 'Category', render: (r) => String(r.category).replace(/_/g, ' ') },
        { header: 'Order', render: (r) => <Button size="small" onClick={() => onOpen(r.orderId)}>{r.orderNumber}</Button> }, { header: 'Summary', render: (r) => r.summary },
        { header: 'Status', render: (r) => <StatusChip status={r.status} /> }, { header: 'Assignee', render: (r) => r.assigneeId ?? 'Unassigned' }, { header: 'Opened', render: (r) => when(r.createdAt) },
        { header: '', render: (r) => <Button size="small" onClick={() => { void show(r.id); }}>Work</Button> },
      ]} />}
      {list.data && list.data.totalElements > 25 && <Stack direction="row" spacing={1}><Button disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button><Button disabled={(page + 1) * 25 >= list.data.totalElements} onClick={() => setPage(page + 1)}>Next</Button></Stack>}
      {open && (
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Typography variant="subtitle1">{open.orderNumber} — {String(open.category).replace(/_/g, ' ')} <StatusChip status={open.status} /></Typography>
          <Typography variant="body2" sx={{ mb: 1 }}>{open.summary}</Typography>
          {manage && ['OPEN', 'ASSIGNED', 'ESCALATED'].includes(open.status) && (
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} sx={{ mb: 1 }}>
              <TextField size="small" label="Assign to user ID" sx={{ width: 160 }} value={assignee} onChange={(e) => setAssignee(e.target.value)} />
              <Button disabled={!/^[1-9]\d*$/.test(assignee)} onClick={() => { void act('assign', { assigneeId: Number(assignee), note }); }}>Assign</Button>
              <TextField size="small" fullWidth label="Note (required to escalate or resolve)" value={note} onChange={(e) => setNote(e.target.value)} />
              <Button disabled={note.trim() === ''} onClick={() => { void act('notes', { note }); }}>Add note</Button>
              <Button color="warning" disabled={note.trim() === ''} onClick={() => { void act('escalate', { note }); }}>Escalate</Button>
              <Button color="success" variant="contained" disabled={note.trim() === ''} onClick={() => { void act('resolve', { note }); }}>Resolve</Button>
              <Button disabled={note.trim() === ''} onClick={() => { void act('dismiss', { note }); }}>Dismiss</Button>
            </Stack>
          )}
          <Typography variant="subtitle2">Resolution history</Typography>
          <DataTable rows={open.events ?? []} empty="No history." columns={[{ header: 'When', render: (r) => when(r.createdAt) }, { header: 'Action', render: (r) => r.action }, { header: 'By', render: (r) => r.actorId ?? 'system' }, { header: 'Note', render: (r) => r.note ?? '' }]} />
        </Paper>
      )}
    </Stack>
  );
}

// ---- returns, reports, settings, audit ----------------------------------------------------------------------------------------

export function ReturnsScreen({ onOpen }: { onOpen: (id: number) => void }) {
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const r = useLoad(() => orderApi.returns(status, page), [status, page]);
  return (
    <Stack spacing={2}>
      <Alert severity="info">Return approval and receiving happen in <a href="/orders/returns">Returns & Refund Management</a>. A refund is requested there and shows as <b>REFUND PENDING</b> until the Payment/Accounting system confirms it; nothing here marks money as refunded.</Alert>
      <Select size="small" displayEmpty value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }} sx={{ maxWidth: 240 }}>
        <MenuItem value="">All requests</MenuItem>{['REQUESTED', 'APPROVED', 'RECEIVED', 'REFUND_PENDING', 'REFUND_FAILED', 'REFUNDED', 'REJECTED'].map((s) => <MenuItem key={s} value={s}>{s.replace('_', ' ')}</MenuItem>)}
      </Select>
      <Loading loading={r.loading} error={r.error} />
      {r.data && <DataTable rows={r.data.content} empty="No return requests." columns={[
        { header: 'Return', render: (x) => `#${x.id}` }, { header: 'Order', render: (x) => <Button size="small" onClick={() => onOpen(x.orderId)}>{x.orderNumber}</Button> }, { header: 'Reason', render: (x) => x.reason },
        { header: 'Items', render: (x) => (x.items ?? []).map((i: Row) => `#${i.orderItemId}×${i.quantity}`).join(', ') }, { header: 'Refund amount', align: 'right', render: (x) => money(x.refundAmount) },
        { header: 'Status', render: (x) => <StatusChip status={x.status} /> }, { header: 'Refund confirmed', render: (x) => (x.refundConfirmed ? 'Yes' : 'No') }, { header: 'Requested', render: (x) => when(x.createdAt) },
      ]} />}
      {r.data && r.data.totalElements > 25 && <Stack direction="row" spacing={1}><Button disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button><Button disabled={(page + 1) * 25 >= r.data.totalElements} onClick={() => setPage(page + 1)}>Next</Button></Stack>}
    </Stack>
  );
}

export function ReportsScreen() {
  const [groupBy, setGroupBy] = useState('day');
  const [range, setRange] = useState({ from: '', to: '' });
  const [applied, setApplied] = useState({ groupBy: 'day', from: '', to: '' });
  const rep = useLoad(() => orderApi.reports(applied.groupBy, applied.from, applied.to), [applied.groupBy, applied.from, applied.to]);
  const max = Math.max(1, ...(rep.data?.series ?? []).map((s: Row) => s.orders));
  const bad = range.from && range.to && range.to < range.from;
  return (
    <Stack spacing={2}>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1}>
        <Select size="small" value={groupBy} onChange={(e) => setGroupBy(e.target.value)}>{[['day', 'By day'], ['store', 'By store'], ['status', 'By status'], ['type', 'By order type'], ['payment', 'By payment status']].map(([v, l]) => <MenuItem key={v} value={v}>{l}</MenuItem>)}</Select>
        <TextField size="small" type="date" label="From" InputLabelProps={{ shrink: true }} value={range.from} onChange={(e) => setRange({ ...range, from: e.target.value })} />
        <TextField size="small" type="date" label="To" InputLabelProps={{ shrink: true }} value={range.to} onChange={(e) => setRange({ ...range, to: e.target.value })} />
        <Button variant="contained" disabled={!!bad} onClick={() => setApplied({ groupBy, ...range })}>Run report</Button>
      </Stack>
      <Loading loading={rep.loading} error={rep.error} />
      {rep.data && <>
        <Typography variant="caption" color="text.secondary">{rep.data.from} to {rep.data.to} · {rep.data.totalOrders} orders{rep.data.truncated ? ' (first 20,000 only)' : ''}. Value excludes cancelled, rejected and payment-failed orders.</Typography>
        <DataTable rows={rep.data.series} empty="No orders in this range." columns={[
          { header: applied.groupBy, render: (r) => r.key }, { header: 'Orders', align: 'right', render: (r) => r.orders },
          { header: '', render: (r) => <Box sx={{ height: 10, width: `${Math.max(2, (r.orders / max) * 100)}%`, minWidth: 6, bgcolor: 'primary.main', borderRadius: 1 }} /> }, { header: 'Value', align: 'right', render: (r) => money(r.value) },
        ]} />
      </>}
    </Stack>
  );
}

export function SettingsScreen({ can }: { can: (p: string) => boolean }) {
  const s = useLoad(() => orderApi.settings(), []);
  const [draft, setDraft] = useState<Row | null>(null);
  const { run, view } = useNotice();
  const v = draft ?? s.data;
  const editable = can('order:settings');
  if (s.loading || s.error || !v) return <Loading loading={s.loading} error={s.error} />;
  const fields: [string, string][] = [['confirmSlaMinutes', 'Confirmation SLA (minutes)'], ['processingSlaMinutes', 'Processing SLA'], ['fulfillmentSlaMinutes', 'Fulfillment SLA'], ['deliverySlaMinutes', 'Delivery SLA'],
    ['refundSlaMinutes', 'Refund SLA'], ['restaurantAcceptSlaMinutes', 'Restaurant acceptance SLA'], ['maxBulkSize', 'Maximum orders per bulk operation']];
  const problems = fields.filter(([k]) => !(Number.isInteger(Number(v[k])) && Number(v[k]) >= 1 && Number(v[k]) <= (k === 'maxBulkSize' ? 1000 : 100000))).map(([, l]) => `${l} must be a whole number in range.`);
  return (
    <Stack spacing={2} sx={{ maxWidth: 520 }}>
      {view}
      {!editable && <Alert severity="info">Read only. Changing settings needs the order:settings permission.</Alert>}
      {problems.length > 0 && <Alert severity="warning">{problems.map((p) => <div key={p}>{p}</div>)}</Alert>}
      {fields.map(([k, label]) => <TextField key={k} size="small" label={label} disabled={!editable} value={v[k]} onChange={(e) => setDraft({ ...v, [k]: e.target.value })} />)}
      <FormControlLabel control={<Checkbox disabled={!editable} checked={!!v.requirePaymentBeforeProcessing} onChange={(e) => setDraft({ ...v, requirePaymentBeforeProcessing: e.target.checked })} />} label="Require confirmed payment before processing or shipping" />
      <Typography variant="caption" color="text.secondary">SLA clocks run from the last status change and pause while an order is on hold. They drive the SLA indicators, the exception centre and the dashboard.</Typography>
      {editable && <Button variant="contained" disabled={problems.length > 0 || !draft} onClick={() => run(async () => { const body: Row = {}; fields.forEach(([k]) => { body[k] = Number(v[k]); }); body.requirePaymentBeforeProcessing = !!v.requirePaymentBeforeProcessing; await orderApi.saveSettings(body); }, 'Settings saved.', () => { setDraft(null); void s.reload(); })}>Save settings</Button>}
    </Stack>
  );
}

export function AuditScreen({ onOpen }: { onOpen: (id: number) => void }) {
  const [orderId, setOrderId] = useState('');
  const [action, setAction] = useState('');
  const [applied, setApplied] = useState({ orderId: '', action: '' });
  const a = useLoad(() => orderApi.audit(applied.orderId, applied.action), [applied.orderId, applied.action]);
  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={1}>
        <TextField size="small" label="Order ID" value={orderId} onChange={(e) => setOrderId(e.target.value.replace(/\D/g, ''))} />
        <TextField size="small" label="Action (e.g. TRANSITION, HOLD)" value={action} onChange={(e) => setAction(e.target.value.toUpperCase().replace(/[^A-Z_]/g, ''))} />
        <Button variant="contained" onClick={() => setApplied({ orderId, action })}>Filter</Button>
      </Stack>
      <Loading loading={a.loading} error={a.error} />
      {a.data && <DataTable rows={a.data} empty="No audit entries." columns={[
        { header: 'When', render: (r) => when(r.createdAt) }, { header: 'Order', render: (r) => <Button size="small" onClick={() => onOpen(r.orderId)}>{r.orderNumber}</Button> }, { header: 'Action', render: (r) => r.action },
        { header: 'Previous', render: (r) => r.fromStatus ?? '—' }, { header: 'New', render: (r) => r.toStatus }, { header: 'Actor', render: (r) => r.actorEmail ?? r.source },
        { header: 'Reason', render: (r) => r.reason ?? '' }, { header: 'Correlation ID', render: (r) => <code>{String(r.correlationId).slice(0, 13)}</code> },
      ]} />}
    </Stack>
  );
}
