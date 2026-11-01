import { useRef, useState } from 'react';
import {
  Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Divider, FormControlLabel, MenuItem, Paper, Select, Stack, Step, StepLabel, Stepper, Tab, Tabs, TextField, Typography, Checkbox,
} from '@mui/material';
import { DataTable, Loading, StatusChip, useLoad, useNotice, when } from '../control/common';
import { returnsApi } from './returnsApi';
import type { Row } from './returnsApi';
import {
  CONDITIONS, OUTCOMES, PACKAGING, TERMINAL, approvalProblems, availableActions, flagLabel, inspectionProblems, isPartial, lifecycleIndex, lifecycleSteps, money, pickupProblems, reasonLabel, refundRows,
  suggestOutcome,
} from './returnsModel';
import type { ActionDef, InspectionRow } from './returnsModel';
import { Fact, Problems, ReasonDialog } from './returnWidgets';

type Dialog_ = null | { kind: 'approve' | 'pickup' | 'inspect' | 'refund' | 'exchange' } | { kind: 'reason'; action: ActionDef };

export default function ReturnDetails({ id, can, onBack }: { id: number; can: (p: string) => boolean; onBack: () => void }) {
  const r = useLoad(() => returnsApi.details(id), [id]);
  const note = useNotice();
  const [tab, setTab] = useState('overview');
  const [dialog, setDialog] = useState<Dialog_>(null);
  const d = r.data;
  const reload = () => { setDialog(null); void r.reload(); };

  if (!d) return <><Button onClick={onBack}>← Back to returns</Button><Loading loading={r.loading} error={r.error} /></>;

  const hasEvidence = (d.attachments ?? []).some((a: Row) => a.kind === 'CUSTOMER_EVIDENCE');
  const actions = availableActions(d as never, can, hasEvidence);
  const steps = lifecycleSteps(d.returnKind, d.resolution);
  const idx = lifecycleIndex(steps, d.status);
  const claim = d.returnKind === 'RESTAURANT_CLAIM';

  const choose = (a: ActionDef) => {
    if (a.id === 'approve' || a.id === 'pickup' || a.id === 'inspect' || a.id === 'refund' || a.id === 'exchange') setDialog({ kind: a.id });
    else setDialog({ kind: 'reason', action: a });
  };

  const runReason = async (a: ActionDef, reason: string) => {
    const run = async () => {
      if (a.id === 'reject') return returnsApi.reject(id, reason);
      if (a.id === 'request-info') return returnsApi.requestInfo(id, reason);
      if (a.id === 'pickup-cancel') return returnsApi.cancelPickup(id, reason);
      if (a.id.startsWith('to:')) return returnsApi.transition(id, a.id.slice(3), reason);
      throw new Error('Unknown action');
    };
    await note.run(run, `${a.label}: done.`, reload);
  };

  return (
    <Stack spacing={2}>
      <Box><Button onClick={onBack}>← Back to returns</Button></Box>
      <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
        <Typography variant="h5">{d.rmaNumber}</Typography>
        <StatusChip status={d.status} />
        <Chip size="small" variant="outlined" label={claim ? 'Restaurant claim' : 'Retail return'} />
        <Chip size="small" variant="outlined" label={`Resolution: ${d.resolution.replace('_', ' ').toLowerCase()}`} />
        {d.autoApproved && <Chip size="small" color="info" variant="outlined" label="Auto-approved by policy" />}
        {(d.riskFlags ?? []).map((f: string) => <Chip key={f} size="small" color="warning" variant="outlined" label={`Review: ${flagLabel(f)}`} />)}
      </Stack>
      {(d.riskFlags ?? []).length > 0 && <Alert severity="info">These flags are signals for a reviewer. They do not block or reject the request.</Alert>}
      {idx >= 0 ? (
        <Stepper activeStep={idx} alternativeLabel sx={{ overflowX: 'auto', pb: 1 }}>{steps.map((s) => <Step key={s}><StepLabel>{s.replace(/_/g, ' ').toLowerCase()}</StepLabel></Step>)}</Stepper>
      ) : <Alert severity={TERMINAL.has(d.status) ? 'info' : 'warning'}>Status: {d.status.replace(/_/g, ' ')}. {d.status === 'REFUND_PROCESSING' ? 'Waiting for the Payment service to confirm.' : ''}</Alert>}
      {d.status === 'REFUND_PROCESSING' && <Alert severity="info">The refund has been sent to Payment. It is shown as refunded only after Payment confirms it.</Alert>}
      {d.infoRequestedNote && <Alert severity="warning">Information requested from the customer: {d.infoRequestedNote}</Alert>}
      {note.view}
      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
        {actions.map((a) => <Button key={a.id} variant={a.tone === 'primary' ? 'contained' : 'outlined'} color={a.tone === 'error' ? 'error' : a.tone === 'primary' ? 'primary' : 'inherit'} onClick={() => choose(a)}>{a.label}</Button>)}
        {actions.length === 0 && <Typography color="text.secondary">No actions are available for this status with your permissions.</Typography>}
      </Stack>

      <Paper variant="outlined">
        <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" scrollButtons="auto">
          {['overview', 'items', 'evidence', ...(claim ? [] : ['pickup', 'inspection']), 'refund', 'exchange', 'notes', 'history', 'exceptions'].map((t) => <Tab key={t} value={t} label={t[0].toUpperCase() + t.slice(1)} />)}
        </Tabs>
        <Box sx={{ p: 2 }}>
          {tab === 'overview' && <Overview d={d} />}
          {tab === 'items' && <Items d={d} />}
          {tab === 'evidence' && <Evidence d={d} can={can} note={note} onChange={() => void r.reload()} />}
          {tab === 'pickup' && <Pickups d={d} />}
          {tab === 'inspection' && <Inspections d={d} />}
          {tab === 'refund' && <Refunds d={d} can={can} note={note} onChange={() => void r.reload()} />}
          {tab === 'exchange' && <Exchanges d={d} can={can} note={note} onChange={() => void r.reload()} />}
          {tab === 'notes' && <Notes d={d} can={can} note={note} onChange={() => void r.reload()} />}
          {tab === 'history' && <History d={d} />}
          {tab === 'exceptions' && <ReturnExceptions d={d} can={can} note={note} onChange={() => void r.reload()} />}
        </Box>
      </Paper>

      {dialog?.kind === 'reason' && <ReasonDialog title={dialog.action.label} required={dialog.action.reasonRequired} confirmLabel={dialog.action.label} tone={dialog.action.tone === 'error' ? 'error' : 'primary'}
        onConfirm={(reason) => runReason((dialog as { action: ActionDef }).action, reason)} onClose={() => setDialog(null)}>
        {dialog.action.id === 'reject' && <Alert severity="warning">The customer is told this request cannot be approved. A reason is required.</Alert>}
        {dialog.action.id === 'request-info' && <Alert severity="info">Say what the customer needs to send, for example a photo of the damage.</Alert>}
      </ReasonDialog>}
      {dialog?.kind === 'approve' && <ApproveDialog d={d} onClose={() => setDialog(null)} onDone={reload} note={note} />}
      {dialog?.kind === 'pickup' && <PickupDialog d={d} onClose={() => setDialog(null)} onDone={reload} note={note} />}
      {dialog?.kind === 'inspect' && <InspectDialog d={d} onClose={() => setDialog(null)} onDone={reload} note={note} />}
      {dialog?.kind === 'refund' && <RefundDialog d={d} onClose={() => setDialog(null)} onDone={reload} note={note} />}
      {dialog?.kind === 'exchange' && <ExchangeDialog d={d} onClose={() => setDialog(null)} onDone={reload} note={note} />}
    </Stack>
  );
}

type Notice = ReturnType<typeof useNotice>;

// ---- tabs ---------------------------------------------------------------------------------------------------------------------

function Overview({ d }: { d: Row }) {
  const o = d.order ?? {}, c = o.customer ?? {};
  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={3} flexWrap="wrap" useFlexGap>
        <Fact label="Order">{o.orderNumber} · {o.status}</Fact>
        <Fact label="Store / vendor">{o.storeId}{o.vendorId ? ` / ${o.vendorId}` : ''}</Fact>
        <Fact label="Order total">{money(o.total)} (discount {money(o.discount)}, tax {money(o.tax)}, delivery {money(o.deliveryCharge)})</Fact>
        <Fact label="Payment">{o.paymentMethod ?? '—'} · {o.paymentStatus} · refund {o.refundStatus}</Fact>
        <Fact label="Placed / delivered">{when(o.placedAt)} / {when(o.deliveredAt)}</Fact>
      </Stack>
      <Divider />
      <Stack direction="row" spacing={3} flexWrap="wrap" useFlexGap>
        <Fact label="Customer">{c.name ?? '—'}</Fact>
        <Fact label="Email">{c.email ?? '—'}</Fact>
        {c.phone !== undefined && <Fact label="Phone">{c.phone}</Fact>}
        {c.address !== undefined && <Fact label="Address">{c.address}</Fact>}
      </Stack>
      {d.customerDetailsMasked && <Alert severity="info">Customer contact details are masked. You need the “view customer details” permission to see them.</Alert>}
      <Divider />
      <Stack direction="row" spacing={3} flexWrap="wrap" useFlexGap>
        <Fact label="Reason">{reasonLabel(d.reasonCode)}</Fact>
        <Fact label="Customer's words">{d.reason}</Fact>
        <Fact label="Evidence">{d.evidenceRequired ? 'Required by policy' : 'Not required'}</Fact>
        <Fact label="Requested">{when(d.createdAt)} via {String(d.source).toLowerCase()}</Fact>
        <Fact label="Estimated / current refund amount">{money(d.refundAmount)}</Fact>
      </Stack>
      <Fact label="Policy decision at request time">{d.policyDecision ?? '—'}{d.policyVersionId ? ` (policy version #${d.policyVersionId})` : ' (platform defaults)'}</Fact>
    </Stack>
  );
}

function Items({ d }: { d: Row }) {
  return <DataTable rows={d.items ?? []} empty="No items." columns={[
    { header: 'Product', render: (i) => i.productName }, { header: 'Unit price', align: 'right', render: (i) => money(i.unitPrice) },
    { header: 'Requested', align: 'right', render: (i) => i.quantity }, { header: 'Approved', align: 'right', render: (i) => i.approvedQuantity ?? '—' },
    { header: 'Received', align: 'right', render: (i) => i.receivedQuantity ?? '—' }, { header: 'Accepted', align: 'right', render: (i) => i.acceptedQuantity ?? '—' },
    { header: 'Refunded', align: 'right', render: (i) => i.refundedQuantity }, { header: 'Disposition', render: (i) => i.disposition },
  ]} />;
}

function Evidence({ d, can, note, onChange }: { d: Row; can: (p: string) => boolean; note: Notice; onChange: () => void }) {
  const input = useRef<HTMLInputElement>(null);
  const [text, setText] = useState('');
  const open = async (a: Row) => {
    try {
      const blob = await returnsApi.downloadAttachment(d.id, a.id);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a'); link.href = url; link.download = a.fileName; link.click();
      setTimeout(() => URL.revokeObjectURL(url), 10_000);
    } catch { note.fail('The file could not be downloaded.'); }
  };
  const upload = async (file: File | undefined) => {
    if (!file) return;
    if (file.size > 5 * 1024 * 1024) { note.fail('Files may be at most 5 MB.'); return; }
    await note.run(() => returnsApi.upload(d.id, file, 'CUSTOMER_EVIDENCE', text.trim()), 'Evidence uploaded.', () => { setText(''); onChange(); });
    if (input.current) input.current.value = '';
  };
  return (
    <Stack spacing={2}>
      <Typography variant="body2" color="text.secondary">Photos (PNG, JPEG, GIF) or PDFs, up to 5 MB each. Files are private: they are only served to authorised staff of this business.</Typography>
      <DataTable rows={d.attachments ?? []} empty="No files attached." columns={[
        { header: 'File', render: (a) => <Button size="small" onClick={() => void open(a)}>{a.fileName}</Button> }, { header: 'Kind', render: (a) => a.kind.replace('_', ' ').toLowerCase() },
        { header: 'Size', align: 'right', render: (a) => `${Math.ceil(a.sizeBytes / 1024)} KB` }, { header: 'Note', render: (a) => a.note ?? '' }, { header: 'Added', render: (a) => when(a.createdAt) },
      ]} />
      {can('return:review') && <Stack direction="row" spacing={1} alignItems="center">
        <TextField size="small" label="Note (optional)" value={text} onChange={(e) => setText(e.target.value)} slotProps={{ htmlInput: { maxLength: 255 } }} />
        <Button variant="outlined" component="label">Add evidence<input ref={input} hidden type="file" accept="image/png,image/jpeg,image/gif,application/pdf" onChange={(e) => void upload(e.target.files?.[0])} /></Button>
      </Stack>}
    </Stack>
  );
}

function Pickups({ d }: { d: Row }) {
  return <DataTable rows={d.pickups ?? []} empty="No reverse pickup has been scheduled." columns={[
    { header: 'Reference', render: (p) => p.deliveryReference }, { header: 'Status', render: (p) => <StatusChip status={p.status} /> }, { header: 'Window', render: (p) => `${when(p.scheduledFrom)} – ${when(p.scheduledTo)}` },
    { header: 'Address', render: (p) => p.pickupAddress }, { header: 'Carrier', render: (p) => p.partnerName ?? '—' }, { header: 'Tracking', render: (p) => p.trackingReference ?? '—' },
    { header: 'Attempts', align: 'right', render: (p) => p.attempts }, { header: 'Note', render: (p) => p.failureReason ?? '' },
  ]} />;
}

function Inspections({ d }: { d: Row }) {
  const list = d.inspections ?? [];
  if (list.length === 0) return <Typography color="text.secondary">No inspection has been recorded.</Typography>;
  return <Stack spacing={1}>{list.map((i: Row) => (
    <Paper key={i.id} variant="outlined" sx={{ p: 1.5 }}>
      <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap alignItems="center">
        <StatusChip status={i.outcome} tone={i.outcome.startsWith('ACCEPTED') ? 'success' : i.outcome === 'REJECTED' ? 'error' : 'warning'} />
        <Fact label="Packaging">{i.packagingCondition.toLowerCase()}</Fact><Fact label="Accessories">{i.accessoriesReceived ? 'Complete' : 'Missing'}</Fact>
        <Fact label="Inspector">{i.inspectorEmail ?? i.inspectorId}</Fact><Fact label="When">{when(i.createdAt)}</Fact>
        {i.missingItems && <Fact label="Missing">{i.missingItems}</Fact>}{i.damageDetails && <Fact label="Damage">{i.damageDetails}</Fact>}{i.notes && <Fact label="Notes">{i.notes}</Fact>}
      </Stack>
    </Paper>
  ))}</Stack>;
}

function Refunds({ d, can, note, onChange }: { d: Row; can: (p: string) => boolean; note: Notice; onChange: () => void }) {
  const list = d.refunds ?? [];
  if (list.length === 0) return <Typography color="text.secondary">No refund has been calculated yet.</Typography>;
  return <Stack spacing={2}>{list.map((f: Row) => {
    const tx = (d.refundTransactions ?? []).filter((x: Row) => x.refundRequestId === f.id);
    return (
      <Paper key={f.id} variant="outlined" sx={{ p: 2 }}>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }} flexWrap="wrap" useFlexGap>
          <Typography variant="subtitle1">Refund #{f.id}</Typography><StatusChip status={f.status} />
          <Chip size="small" variant="outlined" label={f.kind.replace('_', ' ').toLowerCase()} /><Chip size="small" variant="outlined" label={f.method === 'STORE_CREDIT' ? 'Store credit' : 'Original payment method'} />
        </Stack>
        <Stack spacing={0.5} sx={{ maxWidth: 420 }}>{refundRows(f as never).map((x) => (
          <Stack key={x.label} direction="row" justifyContent="space-between"><Typography variant="body2" fontWeight={x.strong ? 700 : 400}>{x.label}</Typography><Typography variant="body2" fontWeight={x.strong ? 700 : 400}>{x.value}</Typography></Stack>
        ))}</Stack>
        {f.calculation && <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 1 }}>{f.calculation}</Typography>}
        {f.failureReason && <Alert severity="error" sx={{ mt: 1 }}>{f.failureReason}</Alert>}
        {tx.length > 0 && <Box sx={{ mt: 1 }}><DataTable rows={tx} empty="" columns={[
          { header: 'Attempt', render: (x) => x.attemptNo }, { header: 'Key', render: (x) => x.idempotencyKey }, { header: 'Status', render: (x) => <StatusChip status={x.status} /> },
          { header: 'Amount', align: 'right', render: (x) => money(x.amount) }, { header: 'Payment reference', render: (x) => x.paymentReference ?? '—' }, { header: 'Requested', render: (x) => when(x.requestedAt) }, { header: 'Confirmed', render: (x) => when(x.confirmedAt) },
        ]} /></Box>}
        <Stack direction="row" spacing={1} sx={{ mt: 1.5 }}>
          {f.status === 'CALCULATED' && can('return:refund-process') && <Button variant="contained" onClick={() => void note.run(() => returnsApi.approveRefund(f.id), 'Refund approved.', onChange)}>Approve refund</Button>}
          {['APPROVED', 'FAILED'].includes(f.status) && can('return:refund-process') && <Button variant="contained" onClick={() => void note.run(() => returnsApi.processRefund(f.id), 'Refund sent to Payment. It stays pending until Payment confirms.', onChange)}>{f.status === 'FAILED' ? 'Retry refund' : 'Send to Payment'}</Button>}
          {f.status === 'PROCESSING' && <Typography color="text.secondary" variant="body2">Waiting for Payment to confirm. This cannot be marked complete by hand.</Typography>}
        </Stack>
      </Paper>
    );
  })}</Stack>;
}

function Exchanges({ d, can, note, onChange }: { d: Row; can: (p: string) => boolean; note: Notice; onChange: () => void }) {
  const [reject, setReject] = useState<Row | null>(null);
  const list = d.exchanges ?? [];
  if (list.length === 0) return <Typography color="text.secondary">No exchange has been proposed.</Typography>;
  return <>
    <DataTable rows={list} empty="" columns={[
      { header: 'Replacement', render: (x) => `${x.newProductName ?? '#' + x.newProductId}${x.newVariant ? ` (${x.newVariant})` : ''} × ${x.quantity}` }, { header: 'Old price', align: 'right', render: (x) => money(x.oldUnitPrice) },
      { header: 'New price', align: 'right', render: (x) => money(x.newUnitPrice) }, { header: 'Customer pays / (refund)', align: 'right', render: (x) => money(x.priceDifference) },
      { header: 'Stock at check', align: 'right', render: (x) => x.stockAvailable ?? '—' }, { header: 'Status', render: (x) => <StatusChip status={x.status} /> }, { header: 'Replacement order', render: (x) => x.replacementOrderId ?? '—' },
      { header: '', render: (x) => can('return:exchange') ? <Stack direction="row" spacing={0.5}>
        {x.status === 'PENDING_APPROVAL' && <><Button size="small" onClick={() => void note.run(() => returnsApi.decideExchange(x.id, true, ''), 'Exchange approved.', onChange)}>Approve</Button><Button size="small" color="error" onClick={() => setReject(x)}>Reject</Button></>}
        {x.status === 'APPROVED' && <Button size="small" variant="contained" onClick={() => void note.run(() => returnsApi.fulfillExchange(x.id), 'Replacement order created.', onChange)}>Create replacement</Button>}
      </Stack> : null },
    ]} />
    {reject && <ReasonDialog title="Reject exchange" required confirmLabel="Reject" tone="error" onClose={() => setReject(null)} onConfirm={async (reason) => { await note.run(() => returnsApi.decideExchange(reject.id, false, reason), 'Exchange rejected.', () => { setReject(null); onChange(); }); }} />}
  </>;
}

function Notes({ d, can, note, onChange }: { d: Row; can: (p: string) => boolean; note: Notice; onChange: () => void }) {
  const [text, setText] = useState('');
  return (
    <Stack spacing={2}>
      {can('return:review') && <Stack direction="row" spacing={1}><TextField size="small" fullWidth multiline label="Internal note (never shown to the customer)" value={text} onChange={(e) => setText(e.target.value)} slotProps={{ htmlInput: { maxLength: 2000 } }} />
        <Button variant="contained" disabled={!text.trim()} onClick={() => void note.run(() => returnsApi.addNote(d.id, text), 'Note added.', () => { setText(''); onChange(); })}>Add</Button></Stack>}
      <DataTable rows={d.notes ?? []} empty="No internal notes." columns={[{ header: 'When', render: (n) => when(n.createdAt) }, { header: 'Author', render: (n) => n.authorEmail ?? n.authorId }, { header: 'Note', render: (n) => n.body }]} />
    </Stack>
  );
}

function History({ d }: { d: Row }) {
  return <DataTable rows={d.history ?? []} empty="No history." columns={[
    { header: 'When', render: (h) => when(h.createdAt) }, { header: 'From', render: (h) => h.fromStatus ?? '—' }, { header: 'To', render: (h) => <StatusChip status={h.toStatus} /> }, { header: 'Action', render: (h) => h.action.replace(/_/g, ' ').toLowerCase() },
    { header: 'By', render: (h) => h.actorEmail ?? (h.actorId ? `user ${h.actorId}` : h.source.toLowerCase()) }, { header: 'Source', render: (h) => h.source.toLowerCase() }, { header: 'Reason', render: (h) => h.reason ?? '' },
  ]} />;
}

function ReturnExceptions({ d, can, note, onChange }: { d: Row; can: (p: string) => boolean; note: Notice; onChange: () => void }) {
  return (
    <Stack spacing={1}>
      <DataTable rows={d.exceptions ?? []} empty="No review items for this return." columns={[
        { header: 'Category', render: (e) => e.category.replace(/_/g, ' ').toLowerCase() }, { header: 'Priority', render: (e) => <StatusChip status={e.priority} /> }, { header: 'Status', render: (e) => <StatusChip status={e.status} /> },
        { header: 'Summary', render: (e) => e.summary }, { header: 'Raised', render: (e) => when(e.createdAt) },
      ]} />
      {can('return:exception-manage') && <Box><Button variant="outlined" onClick={() => void note.run(() => returnsApi.scan(d.id), 'Re-checked against the thresholds.', onChange)}>Re-check this return</Button></Box>}
    </Stack>
  );
}

// ---- dialogs -------------------------------------------------------------------------------------------------------------------

interface DialogProps { d: Row; onClose: () => void; onDone: () => void; note: Notice }

function ApproveDialog({ d, onClose, onDone, note }: DialogProps) {
  const items = (d.items ?? []) as Row[];
  const [q, setQ] = useState<Record<number, number>>({});
  const [text, setText] = useState('');
  const problems = approvalProblems(items as never, q);
  const partial = isPartial(items as never, q);
  const submit = () => note.run(() => returnsApi.approve(d.id, q, text.trim()), partial ? 'Partially approved.' : 'Approved.', onDone);
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{d.evidenceRequired ? 'Approve (evidence on file)' : 'Approve return'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Typography variant="body2" color="text.secondary">Choose how many units of each item to approve. Approving fewer than requested is a partial approval.</Typography>
          {items.map((it) => (
            <Stack key={it.id} direction="row" spacing={2} alignItems="center">
              <Typography sx={{ flex: 1 }}>{it.productName} <Typography component="span" color="text.secondary">(requested {it.quantity})</Typography></Typography>
              <TextField size="small" type="number" sx={{ width: 90 }} value={q[it.orderItemId] ?? it.quantity} onChange={(e) => setQ({ ...q, [it.orderItemId]: Number(e.target.value) })} slotProps={{ htmlInput: { min: 0, max: it.quantity } }} />
            </Stack>
          ))}
          <TextField label="Note (optional)" value={text} onChange={(e) => setText(e.target.value)} multiline minRows={2} slotProps={{ htmlInput: { maxLength: 500 } }} />
          <Problems items={problems} />
        </Stack>
      </DialogContent>
      <DialogActions><Button onClick={onClose}>Cancel</Button><Button variant="contained" disabled={problems.length > 0} onClick={() => void submit()}>{partial ? 'Approve partially' : 'Approve'}</Button></DialogActions>
    </Dialog>
  );
}

function PickupDialog({ d, onClose, onDone, note }: DialogProps) {
  const [f, setF] = useState({ address: '', phone: '', from: '', to: '', destination: '' });
  const problems = f.from || f.to ? pickupProblems(f.from, f.to) : [];
  const ready = f.from && f.to && problems.length === 0;
  const submit = () => note.run(() => returnsApi.schedulePickup(d.id, { address: f.address.trim() || null, phone: f.phone.trim() || null, from: new Date(f.from).toISOString(), to: new Date(f.to).toISOString(), destination: f.destination.trim() || null }),
    'Pickup scheduled. Delivery will report progress.', onDone);
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Schedule reverse pickup</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Alert severity="info">The Delivery service collects the goods and reports progress back. Leave the address empty to use the order's delivery address.</Alert>
          <TextField label="Pickup address (optional)" value={f.address} onChange={(e) => setF({ ...f, address: e.target.value })} slotProps={{ htmlInput: { maxLength: 500 } }} />
          <TextField label="Contact phone (optional)" value={f.phone} onChange={(e) => setF({ ...f, phone: e.target.value })} slotProps={{ htmlInput: { maxLength: 40 } }} />
          <Stack direction="row" spacing={2}>
            <TextField fullWidth type="datetime-local" label="Window starts" value={f.from} onChange={(e) => setF({ ...f, from: e.target.value })} slotProps={{ inputLabel: { shrink: true } }} />
            <TextField fullWidth type="datetime-local" label="Window ends" value={f.to} onChange={(e) => setF({ ...f, to: e.target.value })} slotProps={{ inputLabel: { shrink: true } }} />
          </Stack>
          <TextField label="Destination warehouse (optional)" value={f.destination} onChange={(e) => setF({ ...f, destination: e.target.value })} slotProps={{ htmlInput: { maxLength: 160 } }} />
          <Problems items={problems} />
        </Stack>
      </DialogContent>
      <DialogActions><Button onClick={onClose}>Cancel</Button><Button variant="contained" disabled={!ready} onClick={() => void submit()}>Schedule</Button></DialogActions>
    </Dialog>
  );
}

function InspectDialog({ d, onClose, onDone, note }: DialogProps) {
  const items = (d.items ?? []) as Row[];
  const [rows, setRows] = useState<InspectionRow[]>(items.map((i) => {
    const approved = i.approvedQuantity ?? i.quantity;
    return { returnItemId: i.id, approved, receivedQuantity: i.receivedQuantity ?? approved, acceptedQuantity: i.receivedQuantity ?? approved, condition: 'NEW', restockable: true, note: '' };
  }));
  const [head, setHead] = useState({ packagingCondition: 'INTACT', accessoriesReceived: true, missingItems: '', damageDetails: '', notes: '' });
  const [outcome, setOutcome] = useState(suggestOutcome(rows, d.resolution));
  const problems = inspectionProblems(rows, outcome, d.resolution);
  const set = (i: number, patch: Partial<InspectionRow>) => setRows(rows.map((r, k) => (k === i ? { ...r, ...patch } : r)));
  const submit = () => note.run(() => returnsApi.inspect(d.id, {
    ...head, missingItems: head.missingItems.trim() || null, damageDetails: head.damageDetails.trim() || null, notes: head.notes.trim() || null, outcome,
    items: rows.map((r) => ({ returnItemId: r.returnItemId, receivedQuantity: r.receivedQuantity, acceptedQuantity: r.acceptedQuantity, condition: r.condition, restockable: r.restockable, note: r.note.trim() || null })),
  }), 'Inspection recorded.', onDone);
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>Inspection workspace</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {items.map((it, i) => (
            <Paper key={it.id} variant="outlined" sx={{ p: 1.5 }}>
              <Typography variant="subtitle2" gutterBottom>{it.productName} <Typography component="span" color="text.secondary">(approved {rows[i].approved})</Typography></Typography>
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap alignItems="center">
                <TextField size="small" type="number" label="Received" sx={{ width: 100 }} value={rows[i].receivedQuantity} onChange={(e) => set(i, { receivedQuantity: Number(e.target.value) })} />
                <TextField size="small" type="number" label="Accepted" sx={{ width: 100 }} value={rows[i].acceptedQuantity} onChange={(e) => set(i, { acceptedQuantity: Number(e.target.value) })} />
                <Select size="small" value={rows[i].condition} onChange={(e) => set(i, { condition: e.target.value, restockable: rows[i].restockable && ['NEW', 'LIKE_NEW'].includes(e.target.value) })}>{CONDITIONS.map((c) => <MenuItem key={c} value={c}>{c.replace('_', ' ').toLowerCase()}</MenuItem>)}</Select>
                <FormControlLabel control={<Checkbox checked={rows[i].restockable} onChange={(e) => set(i, { restockable: e.target.checked })} />} label="Return to stock" />
                <TextField size="small" label="Item note" value={rows[i].note} onChange={(e) => set(i, { note: e.target.value })} slotProps={{ htmlInput: { maxLength: 500 } }} />
              </Stack>
            </Paper>
          ))}
          <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap alignItems="center">
            <Select size="small" value={head.packagingCondition} onChange={(e) => setHead({ ...head, packagingCondition: e.target.value })}>{PACKAGING.map((p) => <MenuItem key={p} value={p}>Packaging: {p.toLowerCase()}</MenuItem>)}</Select>
            <FormControlLabel control={<Checkbox checked={head.accessoriesReceived} onChange={(e) => setHead({ ...head, accessoriesReceived: e.target.checked })} />} label="All accessories received" />
          </Stack>
          <TextField label="Missing items" value={head.missingItems} onChange={(e) => setHead({ ...head, missingItems: e.target.value })} slotProps={{ htmlInput: { maxLength: 500 } }} />
          <TextField label="Damage details" value={head.damageDetails} onChange={(e) => setHead({ ...head, damageDetails: e.target.value })} slotProps={{ htmlInput: { maxLength: 1000 } }} />
          <TextField label="Inspection notes" multiline minRows={2} value={head.notes} onChange={(e) => setHead({ ...head, notes: e.target.value })} slotProps={{ htmlInput: { maxLength: 2000 } }} />
          <Select size="small" value={outcome} onChange={(e) => setOutcome(e.target.value)}>{OUTCOMES.map((o) => <MenuItem key={o} value={o}>{o.replace(/_/g, ' ').toLowerCase()}</MenuItem>)}</Select>
          <Typography variant="caption" color="text.secondary">Inspection photos can be added on the Evidence tab after saving. Only accepted units are refunded. Goods marked “return to stock” are restocked.</Typography>
          <Problems items={problems} />
        </Stack>
      </DialogContent>
      <DialogActions><Button onClick={onClose}>Cancel</Button><Button variant="contained" disabled={problems.length > 0} onClick={() => void submit()}>Save inspection</Button></DialogActions>
    </Dialog>
  );
}

function RefundDialog({ d, onClose, onDone, note }: DialogProps) {
  const [method, setMethod] = useState('ORIGINAL');
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Calculate refund</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Typography variant="body2" color="text.secondary">The amount is calculated from what was accepted, with discounts, tax and delivery fees allocated by policy. It cannot be typed in, and nothing is paid until Payment confirms.</Typography>
          <Select size="small" value={method} onChange={(e) => setMethod(e.target.value)}><MenuItem value="ORIGINAL">Original payment method</MenuItem><MenuItem value="STORE_CREDIT">Store credit (if the policy allows)</MenuItem></Select>
        </Stack>
      </DialogContent>
      <DialogActions><Button onClick={onClose}>Cancel</Button><Button variant="contained" onClick={() => void note.run(() => returnsApi.prepareRefund(d.id, method), 'Refund calculated. Review it on the Refund tab.', onDone)}>Calculate</Button></DialogActions>
    </Dialog>
  );
}

function ExchangeDialog({ d, onClose, onDone, note }: DialogProps) {
  const items = (d.items ?? []) as Row[];
  const [f, setF] = useState({ returnItemId: items[0]?.id ?? 0, newProductId: '', variant: '', quantity: '1', note: '' });
  const ok = f.returnItemId > 0 && /^\d+$/.test(f.newProductId) && /^\d+$/.test(f.quantity) && Number(f.quantity) > 0;
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Propose exchange</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Alert severity="info">The replacement price and stock are checked when you save and again before anything is sent. Nothing is shipped until the exchange is approved and the returned goods are accepted.</Alert>
          <Select size="small" value={f.returnItemId} onChange={(e) => setF({ ...f, returnItemId: Number(e.target.value) })}>{items.map((i) => <MenuItem key={i.id} value={i.id}>{i.productName} (returned {i.acceptedQuantity ?? i.approvedQuantity ?? i.quantity})</MenuItem>)}</Select>
          <TextField label="Replacement product ID" value={f.newProductId} onChange={(e) => setF({ ...f, newProductId: e.target.value })} helperText="Product, variant or size to send instead" />
          <TextField label="Variant / size (optional)" value={f.variant} onChange={(e) => setF({ ...f, variant: e.target.value })} slotProps={{ htmlInput: { maxLength: 80 } }} />
          <TextField label="Quantity" value={f.quantity} onChange={(e) => setF({ ...f, quantity: e.target.value })} />
          <TextField label="Note (optional)" value={f.note} onChange={(e) => setF({ ...f, note: e.target.value })} slotProps={{ htmlInput: { maxLength: 500 } }} />
        </Stack>
      </DialogContent>
      <DialogActions><Button onClick={onClose}>Cancel</Button><Button variant="contained" disabled={!ok} onClick={() => void note.run(() => returnsApi.proposeExchange(d.id, { returnItemId: f.returnItemId, newProductId: Number(f.newProductId), variant: f.variant.trim() || null, quantity: Number(f.quantity), note: f.note.trim() || null }), 'Exchange proposed.', onDone)}>Propose</Button></DialogActions>
    </Dialog>
  );
}
