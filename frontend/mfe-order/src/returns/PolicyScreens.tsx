import { useState } from 'react';
import { Alert, Box, Button, Checkbox, Chip, FormControlLabel, MenuItem, Paper, Select, Stack, TextField, Typography } from '@mui/material';
import { DataTable, Loading, StatusChip, useLoad, useNotice, when } from '../control/common';
import { errorMessage, returnsApi } from './returnsApi';
import type { Row } from './returnsApi';
import { policyProblems, settingsProblems } from './returnsModel';
import { Problems, ReasonDialog } from './returnWidgets';

// ---- return policies -----------------------------------------------------------------------------------------------------------

export function PoliciesScreen({ can }: { can: (p: string) => boolean }) {
  const list = useLoad(() => returnsApi.policies(), []);
  const [selected, setSelected] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);
  if (creating) return <NewPolicy onDone={(id) => { setCreating(false); setSelected(id); void list.reload(); }} onCancel={() => setCreating(false)} />;
  if (selected !== null) return <PolicyEditor id={selected} can={can} onBack={() => { setSelected(null); void list.reload(); }} />;
  return (
    <Stack spacing={2}>
      <Alert severity="info">A policy is data, not code: windows, reasons, evidence rules, thresholds and fees. A change goes draft → review by a second person → publish. Returns already decided keep the policy version they were decided under.</Alert>
      {can('return:policy-manage') && <Box><Button variant="contained" onClick={() => setCreating(true)}>New policy</Button></Box>}
      <Loading loading={list.loading} error={list.error} />
      {list.data && <DataTable rows={list.data} empty="No custom policies yet — the platform defaults apply to every store." columns={[
        { header: 'Name', render: (p) => <Button size="small" onClick={() => setSelected(p.id)}>{p.name}</Button> }, { header: 'Applies to', render: (p) => (p.storeId === '*' ? 'All stores' : `Store ${p.storeId}`) },
        { header: 'Type', render: (p) => (p.kind === 'RESTAURANT_CLAIM' ? 'Restaurant claim' : 'Retail return') }, { header: 'Live version', render: (p) => (p.publishedVersionNo ? `v${p.publishedVersionNo}` : 'none (defaults apply)') },
        { header: 'Latest', render: (p) => <>v{p.latestVersionNo} <StatusChip status={p.latestStatus} /></> },
      ]} />}
    </Stack>
  );
}

function NewPolicy({ onDone, onCancel }: { onDone: (id: number) => void; onCancel: () => void }) {
  const [f, setF] = useState({ name: '', storeId: '*', kind: 'RETAIL_RETURN' });
  const note = useNotice();
  const create = async () => {
    try {
      const defaults = await returnsApi.policyDefaults(f.kind);
      const p = await returnsApi.createPolicy({ name: f.name.trim(), storeId: f.storeId.trim() || '*', kind: f.kind, config: defaults.config });
      onDone(p.id);
    } catch (e) { note.fail(errorMessage(e)); }
  };
  return (
    <Stack spacing={2} sx={{ maxWidth: 480 }}>
      <Typography variant="h6">New return policy</Typography>
      {note.view}
      <TextField label="Name" value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} slotProps={{ htmlInput: { maxLength: 120 } }} />
      <TextField label="Store ID (* for every store)" value={f.storeId} onChange={(e) => setF({ ...f, storeId: e.target.value })} />
      <Select size="small" value={f.kind} onChange={(e) => setF({ ...f, kind: e.target.value })}><MenuItem value="RETAIL_RETURN">Retail returns</MenuItem><MenuItem value="RESTAURANT_CLAIM">Restaurant claims</MenuItem></Select>
      <Typography variant="caption" color="text.secondary">The draft starts from the platform defaults; you edit it next.</Typography>
      <Stack direction="row" spacing={1}><Button onClick={onCancel}>Cancel</Button><Button variant="contained" disabled={!f.name.trim()} onClick={() => void create()}>Create draft</Button></Stack>
    </Stack>
  );
}

interface Reason { code: string; label: string; evidenceRequired: boolean; sellerFault: boolean }

function PolicyEditor({ id, can, onBack }: { id: number; can: (p: string) => boolean; onBack: () => void }) {
  const p = useLoad(() => returnsApi.policy(id), [id]);
  const note = useNotice();
  const [cfg, setCfg] = useState<Row | null>(null);
  const [dialog, setDialog] = useState<null | { kind: 'send-back' | 'rollback'; versionId: number }>(null);
  const versions: Row[] = p.data?.versions ?? [];
  const latest = versions[0];
  const editable = latest && latest.status === 'DRAFT';
  const working: Row | null = cfg ?? (latest ? latest.config : null);
  const reload = () => { setCfg(null); setDialog(null); void p.reload(); };
  const reasons: Reason[] = working?.reasons ?? [];
  const set = (patch: Row) => setCfg({ ...(working as Row), ...patch });
  const problems = working ? policyProblems({ returnWindowDays: working.returnWindowDays, maxQuantityPercent: working.maxQuantityPercent, restockingFeePercent: Number(working.restockingFeePercent), claimWindowHours: working.claimWindowHours, reasons }) : [];
  const num = (k: string, v: string) => set({ [k]: v === '' ? NaN : Number(v) });
  const flag = (k: string, label: string) => <FormControlLabel control={<Checkbox checked={!!working?.[k]} disabled={!editable || !can('return:policy-manage')} onChange={(e) => set({ [k]: e.target.checked })} />} label={label} />;
  const dis = !editable || !can('return:policy-manage');

  return (
    <Stack spacing={2}>
      <Box><Button onClick={onBack}>← Back to policies</Button></Box>
      <Typography variant="h6">{p.data?.name} <Typography component="span" color="text.secondary">({p.data?.storeId === '*' ? 'all stores' : `store ${p.data?.storeId}`} · {p.data?.kind === 'RESTAURANT_CLAIM' ? 'restaurant claims' : 'retail returns'})</Typography></Typography>
      <Loading loading={p.loading} error={p.error} />
      {note.view}
      {working && <Paper variant="outlined" sx={{ p: 2 }}>
        <Typography variant="subtitle2" gutterBottom>{editable ? 'Editing the draft' : `Version ${latest.versionNo} is ${latest.status.replace('_', ' ').toLowerCase()} and cannot be edited${latest.status === 'PUBLISHED' || latest.status === 'ARCHIVED' ? '; saving creates a new draft' : ''}`}</Typography>
        <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap sx={{ mb: 1 }}>
          <TextField size="small" type="number" label="Return window (days)" value={Number.isNaN(working.returnWindowDays) ? '' : working.returnWindowDays} disabled={dis && latest.status === 'DRAFT'} onChange={(e) => num('returnWindowDays', e.target.value)} />
          <TextField size="small" type="number" label="Max quantity (%)" value={Number.isNaN(working.maxQuantityPercent) ? '' : working.maxQuantityPercent} onChange={(e) => num('maxQuantityPercent', e.target.value)} />
          <TextField size="small" type="number" label="Restocking fee (%)" value={Number.isNaN(Number(working.restockingFeePercent)) ? '' : working.restockingFeePercent} onChange={(e) => num('restockingFeePercent', e.target.value)} />
          <TextField size="small" type="number" label="Claim window (hours)" value={Number.isNaN(working.claimWindowHours) ? '' : working.claimWindowHours} onChange={(e) => num('claimWindowHours', e.target.value)} />
          <TextField size="small" type="number" label="Auto-approve up to (amount, blank = never)" value={working.autoApproveMaxAmount ?? ''} onChange={(e) => set({ autoApproveMaxAmount: e.target.value === '' ? null : Number(e.target.value) })} sx={{ minWidth: 260 }} />
          <TextField size="small" type="number" label="Manual review from (amount)" value={working.manualApprovalMinAmount ?? ''} onChange={(e) => set({ manualApprovalMinAmount: e.target.value === '' ? null : Number(e.target.value) })} sx={{ minWidth: 220 }} />
          <Select size="small" value={working.deliveryFeeTreatment} onChange={(e) => set({ deliveryFeeTreatment: e.target.value })}>
            <MenuItem value="NEVER">Delivery fee never refunded</MenuItem><MenuItem value="FULL_RETURN_ONLY">Refund delivery fee on full returns</MenuItem><MenuItem value="ALWAYS">Always refund delivery fee</MenuItem></Select>
        </Stack>
        <Stack direction="row" flexWrap="wrap" useFlexGap>
          {flag('requireDelivered', 'Order must be delivered')}{flag('allowPartial', 'Allow partial returns')}{flag('usedReturnable', 'Used items returnable')}{flag('damagedReturnable', 'Damaged items returnable')}
          {flag('inspectionRequired', 'Inspect before refund')}{flag('storeCreditAllowed', 'Allow store credit')}{flag('exchangeAllowed', 'Allow exchanges')}{flag('exchangePriceDifferenceAllowed', 'Allow exchange price differences')}
        </Stack>
        <Typography variant="subtitle2" sx={{ mt: 2 }}>Return reasons</Typography>
        {reasons.map((r, i) => (
          <Stack key={i} direction="row" spacing={1} alignItems="center" sx={{ my: 0.5 }} flexWrap="wrap" useFlexGap>
            <TextField size="small" label="Code" value={r.code} onChange={(e) => set({ reasons: reasons.map((x, k) => (k === i ? { ...x, code: e.target.value.toUpperCase() } : x)) })} sx={{ width: 200 }} />
            <TextField size="small" label="Label" value={r.label} onChange={(e) => set({ reasons: reasons.map((x, k) => (k === i ? { ...x, label: e.target.value } : x)) })} />
            <FormControlLabel control={<Checkbox checked={r.evidenceRequired} onChange={(e) => set({ reasons: reasons.map((x, k) => (k === i ? { ...x, evidenceRequired: e.target.checked } : x)) })} />} label="Evidence required" />
            <FormControlLabel control={<Checkbox checked={r.sellerFault} onChange={(e) => set({ reasons: reasons.map((x, k) => (k === i ? { ...x, sellerFault: e.target.checked } : x)) })} />} label="Seller fault" />
            <Button size="small" color="error" onClick={() => set({ reasons: reasons.filter((_, k) => k !== i) })}>Remove</Button>
          </Stack>
        ))}
        <Button size="small" onClick={() => set({ reasons: [...reasons, { code: '', label: '', evidenceRequired: false, sellerFault: false }] })}>Add reason</Button>
        <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 1 }}>Product and category lists (non-returnable, final sale, per-category windows) are carried over unchanged from the version you started from.</Typography>
        <Problems items={problems} />
        {can('return:policy-manage') && <Button sx={{ mt: 1 }} variant="contained" disabled={problems.length > 0 || cfg === null} onClick={() => void note.run(() => returnsApi.saveDraft(id, cfg as Row, 'Edited in the policy editor'), 'Draft saved.', reload)}>Save draft</Button>}
      </Paper>}
      <Typography variant="subtitle1">Versions</Typography>
      <DataTable rows={versions} empty="No versions." columns={[
        { header: 'Version', render: (v) => `v${v.versionNo}` }, { header: 'Status', render: (v) => <StatusChip status={v.status} /> }, { header: 'Note', render: (v) => v.changeNote ?? '' },
        { header: 'Published', render: (v) => when(v.publishedAt) }, { header: 'Updated', render: (v) => when(v.updatedAt) },
        { header: '', render: (v) => <Stack direction="row" spacing={0.5}>
          {v.status === 'DRAFT' && can('return:policy-manage') && <Button size="small" onClick={() => void note.run(() => returnsApi.submitPolicy(id, v.id), 'Submitted for review.', reload)}>Submit for review</Button>}
          {v.status === 'IN_REVIEW' && can('return:policy-approve') && <><Button size="small" onClick={() => void note.run(() => returnsApi.decidePolicy(id, v.id, true, ''), 'Approved.', reload)}>Approve</Button><Button size="small" color="error" onClick={() => setDialog({ kind: 'send-back', versionId: v.id })}>Send back</Button></>}
          {['IN_REVIEW', 'APPROVED'].includes(v.status) && can('return:policy-manage') && <Button size="small" color="inherit" onClick={() => void note.run(() => returnsApi.withdrawPolicy(id, v.id), 'Withdrawn to draft.', reload)}>Withdraw</Button>}
          {v.status === 'APPROVED' && can('return:policy-approve') && <Button size="small" variant="contained" onClick={() => void note.run(() => returnsApi.publishPolicy(id, v.id), 'Published. New requests use this version.', reload)}>Publish</Button>}
          {v.status === 'ARCHIVED' && can('return:policy-approve') && <Button size="small" onClick={() => setDialog({ kind: 'rollback', versionId: v.id })}>Roll back to this</Button>}
        </Stack> },
      ]} />
      <Typography variant="caption" color="text.secondary">You cannot review a version you submitted. Publishing or rolling back never changes returns that were already decided.</Typography>
      {dialog?.kind === 'send-back' && <ReasonDialog title="Send back to draft" required confirmLabel="Send back" tone="error" onClose={() => setDialog(null)} onConfirm={async (r) => { await note.run(() => returnsApi.decidePolicy(id, dialog.versionId, false, r), 'Sent back.', reload); }} />}
      {dialog?.kind === 'rollback' && <ReasonDialog title="Roll back to this version" required={false} confirmLabel="Roll back" onClose={() => setDialog(null)} onConfirm={async (r) => { await note.run(() => returnsApi.rollbackPolicy(id, dialog.versionId, r), 'Rolled back as a new published version.', reload); }}>
        <Alert severity="warning">This publishes a copy of the old version as a new version. Cases already decided keep the version they were decided under.</Alert></ReasonDialog>}
      <Chip size="small" variant="outlined" label="Audit entries for every step are in Return Audit History" sx={{ alignSelf: 'flex-start' }} />
    </Stack>
  );
}

// ---- refund configuration --------------------------------------------------------------------------------------------------------

const FIELDS: [string, string, string][] = [
  ['highValueRefundThreshold', 'High-value refund threshold', 'Refunds at or above this are flagged for review.'],
  ['refundApprovalThreshold', 'Second-approver threshold', 'Refunds above this need a second person with refund-approval permission.'],
  ['repeatReturnCount', 'Repeat returns (count)', 'Flag a customer after this many requests…'],
  ['repeatReturnDays', 'Repeat returns (days)', '…within this many days.'],
  ['pickupFailureLimit', 'Pickup failures before review', 'Failed pickup attempts that raise a review item.'],
  ['lostShipmentDays', 'Days in transit before “lost”', 'In-transit returns older than this are flagged.'],
  ['reviewSlaHours', 'Review SLA (hours)', 'Target time to decide a new request.'],
  ['refundSlaHours', 'Refund SLA (hours)', 'Target time from calculation to a completed refund.'],
];

export function ConfigScreen({ can }: { can: (p: string) => boolean }) {
  const s = useLoad(() => returnsApi.settings(), []);
  const note = useNotice();
  const [draft, setDraft] = useState<Record<string, string> | null>(null);
  const cur = s.data;
  const values = draft ?? (cur ? Object.fromEntries(FIELDS.map(([k]) => [k, String(cur[k])])) : {});
  const parsed = Object.fromEntries(FIELDS.map(([k]) => [k, values[k] === '' || values[k] === undefined ? NaN : Number(values[k])])) as never;
  const problems = cur ? settingsProblems(parsed) : [];
  const editable = can('return:config');
  return (
    <Stack spacing={2} sx={{ maxWidth: 560 }}>
      <Alert severity="info">Thresholds that drive refund approvals and the review queue. Refund amounts themselves are always calculated from policy and what was accepted — they are never typed in.</Alert>
      <Loading loading={s.loading} error={s.error} />
      {note.view}
      {cur && FIELDS.map(([k, label, help]) => (
        <TextField key={k} size="small" type="number" label={label} helperText={help} value={values[k] ?? ''} disabled={!editable} onChange={(e) => setDraft({ ...values, [k]: e.target.value })} />
      ))}
      <Problems items={problems} />
      {editable ? <Box><Button variant="contained" disabled={draft === null || problems.length > 0} onClick={() => void note.run(() => returnsApi.saveSettings(Object.fromEntries(FIELDS.map(([k]) => [k, Number(values[k])]))), 'Saved.', () => { setDraft(null); void s.reload(); })}>Save</Button></Box>
        : <Typography color="text.secondary" variant="body2">You can view these settings. Changing them needs the refund-configuration permission.</Typography>}
    </Stack>
  );
}
