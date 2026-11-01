import { useState } from 'react';
import {
  Alert, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Grid, MenuItem, Paper, Select, Stack, Tab, Tabs, TextField, Typography,
} from '@mui/material';
import { DataTable, Loading, StatusChip, money, slaChip, useLoad, useNotice, when } from './common';
import { orderApi } from './controlApi';
import type { Row } from './controlApi';
import { restaurantActions, transitionDialog, transitionsForMenu } from './controlModel';
import type { Transition } from './controlModel';

interface Props { orderId: number; can: (p: string) => boolean; onBack: () => void }

export default function OrderDetails({ orderId, can, onBack }: Props) {
  const details = useLoad(() => orderApi.details(orderId), [orderId]);
  const [tab, setTab] = useState('overview');
  const { run, view } = useNotice();
  const [dialog, setDialog] = useState<{ title: string; needsReason: boolean; prep?: boolean; destructive?: boolean; submit: (reason: string, prep: number) => Promise<unknown> } | null>(null);
  const [reason, setReason] = useState('');
  const [prep, setPrep] = useState('20');
  const [busy, setBusy] = useState(false);
  const [dialogError, setDialogError] = useState('');
  const d = details.data;

  const open = (cfg: NonNullable<typeof dialog>) => { setReason(''); setDialogError(''); setDialog(cfg); };
  const confirm = async () => {
    if (!dialog) return;
    if (dialog.needsReason && reason.trim() === '') { setDialogError('A reason is required.'); return; }
    if (dialog.prep && !(Number(prep) >= 1 && Number(prep) <= 240)) { setDialogError('Preparation time must be 1-240 minutes.'); return; }
    setBusy(true); setDialogError('');
    try { await dialog.submit(reason.trim(), Number(prep)); setDialog(null); await details.reload(); } catch (e) { setDialogError((await import('./controlApi')).errorMessage(e)); } finally { setBusy(false); }
  };

  if (details.loading || details.error || !d) return <Stack spacing={1}><Button onClick={onBack} sx={{ alignSelf: 'flex-start' }}>← Back to orders</Button><Loading loading={details.loading} error={details.error} /></Stack>;
  const o = d.order as Row;
  const transitions: Transition[] = d.allowedTransitions ?? [];
  const { allowed, blocked } = transitionsForMenu(transitions);
  const isRestaurant = o.type === 'RESTAURANT';
  const version = o.version as number;

  const startTransition = (t: Transition) => {
    const cfg = transitionDialog(t);
    open({ title: cfg.title, needsReason: cfg.needsReason, destructive: cfg.destructive, submit: (r) => orderApi.transition(orderId, t.to, r, version) });
  };

  return (
    <Stack spacing={2}>
      {view}
      <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap">
        <Button onClick={onBack}>← Back to orders</Button>
        <Typography variant="h5">{o.orderNumber}</Typography>
        <StatusChip status={o.status} />
        <Chip size="small" variant="outlined" label={isRestaurant ? 'Restaurant' : 'Retail'} />
        {slaChip(d.sla)}
        <Typography variant="body2" color="text.secondary">{o.storeId}{o.vendorId ? ` · ${o.vendorId}` : ''} · placed {when(o.createdAt)}</Typography>
      </Stack>

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Typography variant="subtitle2" gutterBottom>Actions</Typography>
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          {isRestaurant && restaurantActions(o.status).map((a) => (
            <Button key={a.action} variant="contained" size="small" disabled={!can('order:confirm') && !can('order:process')} onClick={() => open({ title: a.label, needsReason: !!a.needsReason, prep: a.needsPrep,
              submit: (r, p) => orderApi.restaurant(orderId, a.action, { reason: r || undefined, prepMinutes: a.needsPrep ? p : undefined }) })}>{a.label}</Button>
          ))}
          {!isRestaurant && allowed.filter((t) => !['ON_HOLD', 'CANCELLED', 'CANCELLATION_REQUESTED'].includes(t.to)).map((t) => (
            <Button key={t.to} variant="contained" size="small" onClick={() => startTransition(t)}>{t.to.replace(/_/g, ' ').toLowerCase()}</Button>
          ))}
          {allowed.some((t) => t.to === 'ON_HOLD') && <Button size="small" variant="outlined" onClick={() => open({ title: 'Put order on hold', needsReason: true, submit: (r) => orderApi.hold(orderId, r) })}>Hold</Button>}
          {(o.status === 'ON_HOLD' || o.status === 'CANCELLATION_REQUESTED') && can(o.status === 'ON_HOLD' ? 'order:release' : 'order:cancel') &&
            <Button size="small" variant="outlined" onClick={() => open({ title: o.status === 'ON_HOLD' ? 'Release from hold' : 'Withdraw cancellation request', needsReason: false, submit: (r) => orderApi.release(orderId, r) })}>{o.status === 'ON_HOLD' ? 'Release' : 'Withdraw request'}</Button>}
          {allowed.some((t) => t.to === 'CANCELLATION_REQUESTED') && <Button size="small" color="warning" variant="outlined" onClick={() => open({ title: 'Request cancellation', needsReason: true, submit: (r) => orderApi.requestCancellation(orderId, r) })}>Request cancellation</Button>}
          {allowed.some((t) => t.to === 'CANCELLED') && <Button size="small" color="error" variant="outlined" onClick={() => open({ title: 'Cancel this order', needsReason: true, destructive: true, submit: (r) => orderApi.cancel(orderId, r) })}>Cancel order</Button>}
          {can('order:process') && <Button size="small" onClick={() => { const v = window.prompt('Assign to operator (user id, blank to clear)', String(o.assignedOperatorId ?? '')); if (v === null) return; if (v !== '' && !/^[1-9]\d*$/.test(v)) { void run(() => Promise.reject(new Error('Operator must be a numeric user id.')), ''); return; } void run(() => orderApi.assign(orderId, v === '' ? null : Number(v)), 'Operator updated.', () => { void details.reload(); }); }}>Assign operator</Button>}
        </Stack>
        {blocked.length > 0 && (
          <Typography variant="caption" color="text.secondary" component="div" sx={{ mt: 1 }}>
            Not available now: {blocked.map((t) => `${t.to.replace(/_/g, ' ').toLowerCase()} (${t.blockedReason})`).join(' · ')}
          </Typography>
        )}
        {o.holdReason && <Alert severity="warning" sx={{ mt: 1 }}>On hold: {o.holdReason}</Alert>}
        {o.rejectionReason && <Alert severity="error" sx={{ mt: 1 }}>Rejected: {o.rejectionReason}</Alert>}
      </Paper>

      <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" scrollButtons="auto">
        {['overview', 'timeline', 'fulfillment', 'payment', 'customer', 'notes', 'audit'].map((t) => <Tab key={t} value={t} label={t === 'notes' ? 'Notes & Communication' : t[0].toUpperCase() + t.slice(1)} />)}
      </Tabs>
      {tab === 'overview' && <Overview d={d} />}
      {tab === 'timeline' && <Timeline orderId={orderId} />}
      {tab === 'fulfillment' && <FulfillmentTab orderId={orderId} can={can} onChanged={() => { void details.reload(); }} />}
      {tab === 'payment' && <PaymentTab d={d} />}
      {tab === 'customer' && <CustomerTab d={d} />}
      {tab === 'notes' && <NotesTab orderId={orderId} initial={d.notes ?? []} can={can} />}
      {tab === 'audit' && <AuditTab orderId={orderId} can={can} />}

      <Dialog open={dialog !== null} onClose={() => setDialog(null)} fullWidth maxWidth="xs">
        <DialogTitle>{dialog?.title}</DialogTitle>
        <DialogContent>
          {dialogError && <Alert severity="error" sx={{ mb: 1 }}>{dialogError}</Alert>}
          {dialog?.prep && <TextField fullWidth size="small" sx={{ mt: 1 }} type="number" label="Estimated preparation time (minutes)" value={prep} onChange={(e) => setPrep(e.target.value)} />}
          {(dialog?.needsReason || dialog?.title.startsWith('Release') || dialog?.title.startsWith('Withdraw')) && <TextField fullWidth multiline minRows={2} sx={{ mt: 1 }} size="small" label={dialog?.needsReason ? 'Reason (required, kept in the audit trail)' : 'Note (optional)'} value={reason} onChange={(e) => setReason(e.target.value)} />}
          {!dialog?.needsReason && !dialog?.prep && !dialog?.title.startsWith('Release') && !dialog?.title.startsWith('Withdraw') && <Typography>Confirm this change? It is recorded in the audit history.</Typography>}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialog(null)}>Cancel</Button>
          <Button variant="contained" color={dialog?.destructive ? 'error' : 'primary'} disabled={busy} onClick={() => { void confirm(); }}>Confirm</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}

function Overview({ d }: { d: Row }) {
  const o = d.order as Row, a = d.amounts as Row, dl = d.delivery as Row;
  return (
    <Grid container spacing={2}>
      <Grid item xs={12} md={5}>
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Typography variant="subtitle2" gutterBottom>Order</Typography>
          {([['Number', o.orderNumber], ['Type', o.type], ['Status', o.status], ['Store', o.storeId], ['Vendor', o.vendorId ?? '—'], ['Coupon', o.couponCode ?? '—'], ['Operator', o.assignedOperatorId ?? 'Unassigned'],
            ['Prep time', o.prepMinutes ? `${o.prepMinutes} min` : '—'], ['Instructions', o.specialInstructions ?? '—'], ['Delivery', `${dl.status ?? '—'}${dl.partner ? ` via ${dl.partner}` : ''}`], ['Carrier / tracking', `${dl.carrier ?? '—'} ${dl.trackingNumber ?? ''}`]] as [string, unknown][]).map(([k, v]) => (
            <Stack key={k} direction="row" sx={{ py: 0.3 }}><Typography variant="body2" color="text.secondary" sx={{ width: 140 }}>{k}</Typography><Typography variant="body2">{String(v)}</Typography></Stack>
          ))}
        </Paper>
      </Grid>
      <Grid item xs={12} md={7}>
        <DataTable rows={d.items ?? []} empty="No items." columns={[
          { header: 'Item', render: (r) => r.name }, { header: 'Qty', align: 'right', render: (r) => r.quantity }, { header: 'Unit', align: 'right', render: (r) => money(r.unitPrice) },
          { header: 'Line total', align: 'right', render: (r) => money(r.lineTotal) }, { header: 'Allocated / fulfilled', render: (r) => `${r.allocatedQuantity} / ${r.fulfilledQuantity}` }, { header: 'Vendor', render: (r) => r.vendorId ?? '—' },
        ]} />
        <Paper variant="outlined" sx={{ p: 2, mt: 2 }}>
          {([['Subtotal', a.subtotal], ['Discount', `− ${money(a.discount)}`], ['Tax (as recorded)', a.tax], ['Delivery charge (as recorded)', a.deliveryCharge]] as [string, unknown][]).map(([k, v]) => (
            <Stack key={k} direction="row" justifyContent="space-between"><Typography variant="body2">{k}</Typography><Typography variant="body2">{typeof v === 'string' ? v : money(v)}</Typography></Stack>
          ))}
          <Stack direction="row" justifyContent="space-between" sx={{ mt: 1, pt: 1, borderTop: '1px solid', borderColor: 'divider' }}><Typography fontWeight={700}>Final payable amount</Typography><Typography fontWeight={700}>{money(a.payable)}</Typography></Stack>
          <Typography variant="caption" color="text.secondary">Computed by the backend at checkout; orders store no currency code.</Typography>
        </Paper>
      </Grid>
    </Grid>
  );
}

function Timeline({ orderId }: { orderId: number }) {
  const t = useLoad(() => orderApi.timeline(orderId), [orderId]);
  return (
    <>
      <Loading loading={t.loading} error={t.error} />
      {t.data && <DataTable rows={t.data.events ?? []} empty="No events." columns={[
        { header: 'When', render: (r) => when(r.at) }, { header: 'Event', render: (r) => r.label },
        { header: 'Change', render: (r) => (r.from || r.to ? `${r.from ?? '—'} → ${r.to ?? '—'}` : '') }, { header: 'By', render: (r) => r.actor ?? r.source ?? 'system' },
        { header: 'Reason', render: (r) => r.reason ?? '' }, { header: 'Correlation', render: (r) => <code>{String(r.correlationId ?? '').slice(0, 8)}</code> },
      ]} />}
    </>
  );
}

function FulfillmentTab({ orderId, can, onChanged }: { orderId: number; can: (p: string) => boolean; onChanged: () => void }) {
  const f = useLoad(() => orderApi.fulfillment(orderId), [orderId]);
  const { run, view } = useNotice();
  const [alloc, setAlloc] = useState<Record<number, string>>({});
  const [location, setLocation] = useState('');
  const [method, setMethod] = useState('SHIP');
  if (f.loading || f.error || !f.data) return <Loading loading={f.loading} error={f.error} />;
  const data = f.data;
  if (!data.applicable) return <Alert severity="info">Restaurant orders follow the kitchen workflow (accept, prepare, ready for pickup). Warehouse fulfillment does not apply.</Alert>;
  const lines = Object.entries(alloc).filter(([, v]) => Number(v) > 0).map(([id, v]) => ({ orderItemId: Number(id), quantity: Number(v) }));
  const canCreate = can('order:process') && ['READY_FOR_FULFILLMENT', 'PARTIALLY_FULFILLED'].includes(data.status);
  const reload = () => { void f.reload(); onChanged(); };
  return (
    <Stack spacing={2}>
      {view}
      <Typography variant="body2">Fulfillment status: <StatusChip status={data.fulfillmentStatus} /> · Delivery: {data.delivery.status} {data.delivery.partner !== 'null' ? `· partner ${data.delivery.partner}` : ''}</Typography>
      <DataTable rows={data.items} empty="No items." columns={[
        { header: 'Item', render: (r) => r.name }, { header: 'Ordered', align: 'right', render: (r) => r.ordered }, { header: 'Allocated', align: 'right', render: (r) => r.allocated },
        { header: 'Fulfilled', align: 'right', render: (r) => r.fulfilled }, { header: 'Unallocated', align: 'right', render: (r) => r.unallocated },
        { header: 'Allocate now', render: (r) => canCreate && r.unallocated > 0 ? <TextField size="small" type="number" sx={{ width: 90 }} inputProps={{ min: 0, max: r.unallocated }} value={alloc[r.orderItemId] ?? ''} onChange={(e) => setAlloc({ ...alloc, [r.orderItemId]: e.target.value })} /> : '' },
      ]} />
      {canCreate && (
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={1}>
            <TextField size="small" label="Location code (warehouse / store)" value={location} onChange={(e) => setLocation(e.target.value)} helperText="Letters, digits, _ or -" />
            <Select size="small" value={method} onChange={(e) => setMethod(e.target.value)}><MenuItem value="SHIP">Ship</MenuItem><MenuItem value="DELIVERY">Local delivery</MenuItem><MenuItem value="PICKUP">Customer pickup</MenuItem></Select>
            <Button variant="contained" disabled={lines.length === 0 || !/^[A-Za-z0-9_-]{1,64}$/.test(location)} onClick={() => run(() => orderApi.createFulfillment(orderId, { locationCode: location, method, lines }), 'Fulfillment created.', () => { setAlloc({}); reload(); })}>Create fulfillment (split shipment)</Button>
          </Stack>
        </Paper>
      )}
      <DataTable rows={data.fulfillments} empty="No fulfillments yet." columns={[
        { header: '#', render: (r) => r.id }, { header: 'Location', render: (r) => `${r.locationName ?? r.locationCode}` }, { header: 'Method', render: (r) => r.method },
        { header: 'Items', render: (r) => (r.items ?? []).map((i: Row) => `#${i.orderItemId}×${i.quantity}`).join(', ') }, { header: 'Packing', render: (r) => r.packingStatus },
        { header: 'Status', render: (r) => <StatusChip status={r.status} /> }, { header: 'Carrier / tracking', render: (r) => `${r.carrier ?? '—'} ${r.trackingReference ?? ''}` },
        { header: '', render: (r) => can('order:process') && ['ALLOCATED', 'PACKED'].includes(r.status) && (
          <Stack direction="row" spacing={1}>
            <Button size="small" onClick={() => run(() => orderApi.packing(r.id, 'PACKED'), 'Marked packed.', reload)}>Packed</Button>
            <Button size="small" variant="contained" onClick={() => { const carrier = r.method === 'PICKUP' ? 'In-store pickup' : window.prompt('Carrier'); if (!carrier) return; const tracking = r.method === 'PICKUP' ? '' : window.prompt('Tracking reference (optional)') ?? ''; void run(() => orderApi.ship(r.id, carrier, tracking), 'Shipped.', reload); }}>Ship</Button>
            <Button size="small" color="error" onClick={() => { if (window.confirm('Cancel this fulfillment and release its allocation?')) void run(() => orderApi.cancelFulfillment(r.id), 'Fulfillment cancelled.', reload); }}>Cancel</Button>
          </Stack>) },
      ]} />
    </Stack>
  );
}

function PaymentTab({ d }: { d: Row }) {
  const p = d.payment as Row;
  return (
    <Stack spacing={2}>
      <Alert severity="info">Payment and refund state is reported by the Payment/Accounting domain. Administrators cannot mark an order paid or refunded here.</Alert>
      <Paper variant="outlined" sx={{ p: 2 }}>
        {([['Payment status', p.status], ['Method', p.method ?? '—'], ['Transaction reference', p.reference ?? '—'], ['Payment events', p.attempts], ['Refund status', p.refundStatus], ['Refund reference', p.refundReference ?? '—']] as [string, unknown][]).map(([k, v]) => (
          <Stack key={k} direction="row" sx={{ py: 0.3 }}><Typography variant="body2" color="text.secondary" sx={{ width: 180 }}>{k}</Typography><Typography variant="body2">{String(v)}</Typography></Stack>
        ))}
      </Paper>
      <Typography variant="subtitle2">Payment history</Typography>
      <DataTable rows={p.history ?? []} empty="No payment events recorded." columns={[{ header: 'When', render: (r) => when(r.createdAt) }, { header: 'Event', render: (r) => r.action }, { header: 'Source', render: (r) => r.source }, { header: 'Detail', render: (r) => r.reason ?? '' }]} />
      <Typography variant="subtitle2">Returns & refunds</Typography>
      <DataTable rows={d.returns ?? []} empty="No returns for this order." columns={[
        { header: 'Return', render: (r) => `#${r.id}` }, { header: 'Status', render: (r) => <StatusChip status={r.status} /> }, { header: 'Reason', render: (r) => r.reason },
        { header: 'Items', render: (r) => (r.items ?? []).map((i: Row) => `#${i.orderItemId}×${i.quantity}`).join(', ') }, { header: 'Refund', align: 'right', render: (r) => money(r.refundAmount) },
        { header: 'Refund confirmed', render: (r) => (r.refundConfirmed ? 'Yes' : 'No') },
      ]} />
      <Button href="/orders/returns" sx={{ alignSelf: 'flex-start' }}>Open Returns & Refund Management</Button>
    </Stack>
  );
}

function CustomerTab({ d }: { d: Row }) {
  const c = d.customer as Row;
  if (c.hidden) return <Alert severity="info">You do not have permission to view customer details (order:customer:view).</Alert>;
  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      {([['Name', c.name], ['Email', c.email], ['Phone', c.phone], ['Delivery address', c.deliveryAddress], ['Customer ID', c.userId], ['Other orders by this customer', c.previousOrders]] as [string, unknown][]).map(([k, v]) => (
        <Stack key={k} direction="row" sx={{ py: 0.3 }}><Typography variant="body2" color="text.secondary" sx={{ width: 200 }}>{k}</Typography><Typography variant="body2">{v === null || v === undefined || v === '' ? '—' : String(v)}</Typography></Stack>
      ))}
    </Paper>
  );
}

function NotesTab({ orderId, initial, can }: { orderId: number; initial: Row[]; can: (p: string) => boolean }) {
  const [notes, setNotes] = useState<Row[]>(initial);
  const [kind, setKind] = useState('INTERNAL');
  const [body, setBody] = useState('');
  const { run, view } = useNotice();
  return (
    <Stack spacing={2}>
      {view}
      {can('order:note:create') && (
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1}>
          <Select size="small" value={kind} onChange={(e) => setKind(e.target.value)}><MenuItem value="INTERNAL">Internal note</MenuItem><MenuItem value="COMMUNICATION">Customer communication</MenuItem><MenuItem value="ADMIN_COMMENT">Administrative comment</MenuItem></Select>
          <TextField size="small" fullWidth multiline label="Note (plain text)" value={body} onChange={(e) => setBody(e.target.value)} />
          <Button variant="contained" disabled={body.trim() === '' || /[<>]/.test(body)} onClick={() => run(async () => { const r = await orderApi.addNote(orderId, kind, body); setNotes(r.notes); }, 'Note added.', () => setBody(''))}>Add</Button>
        </Stack>
      )}
      <DataTable rows={notes} empty="No notes yet." columns={[{ header: 'When', render: (r) => when(r.createdAt) }, { header: 'Type', render: (r) => r.kind }, { header: 'By', render: (r) => r.authorEmail ?? r.authorId }, { header: 'Note', render: (r) => r.body }]} />
    </Stack>
  );
}

function AuditTab({ orderId, can }: { orderId: number; can: (p: string) => boolean }) {
  const a = useLoad(() => (can('order:audit:view') ? orderApi.audit(String(orderId)) : Promise.resolve([] as Row[])), [orderId]);
  if (!can('order:audit:view')) return <Alert severity="info">You do not have permission to view audit history (order:audit:view). The Timeline tab shows status changes.</Alert>;
  return (
    <>
      <Loading loading={a.loading} error={a.error} />
      {a.data && <DataTable rows={a.data} empty="No audit entries." columns={[
        { header: 'When', render: (r) => when(r.createdAt) }, { header: 'Action', render: (r) => r.action }, { header: 'Previous', render: (r) => r.fromStatus ?? '—' }, { header: 'New', render: (r) => r.toStatus },
        { header: 'Actor', render: (r) => r.actorEmail ?? r.source }, { header: 'Reason', render: (r) => r.reason ?? '' }, { header: 'Correlation ID', render: (r) => <code>{r.correlationId}</code> },
      ]} />}
    </>
  );
}
