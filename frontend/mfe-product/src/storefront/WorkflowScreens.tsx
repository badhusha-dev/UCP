import { useState } from 'react';
import { Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Paper, Select, Stack, TextField, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material';
import { DataTable, Loading, shortDate, useLoad } from '../pricing/common';
import { StatusChip, useDocuments, useNotice } from './common';
import type { ScreenProps } from './common';
import { sf } from './storefrontApi';
import type { Row } from './storefrontApi';
import { versionActions } from './storefrontModel';

/** A lightweight visual of rendered content (the API already resolved media urls and live catalog data). */
function PreviewPane({ preview }: { preview: Row }) {
  const [device, setDevice] = useState<'desktop' | 'mobile'>('desktop');
  const sections: Row[] = preview.content?.sections ?? [];
  const shown = sections.filter((s) => s.visible && s.layout?.[device]?.visible !== false);
  return (
    <Box>
      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
        <ToggleButtonGroup size="small" exclusive value={device} onChange={(_, v) => v && setDevice(v)}>
          <ToggleButton value="desktop">Desktop</ToggleButton><ToggleButton value="mobile">Mobile</ToggleButton>
        </ToggleButtonGroup>
        <Chip size="small" label={`Previewing v${preview.versionNo} (${String(preview.status).replace('_', ' ').toLowerCase()})`} color="warning" />
      </Stack>
      <Paper variant="outlined" sx={{ p: 2, maxWidth: device === 'mobile' ? 380 : '100%', mx: device === 'mobile' ? 'auto' : 0 }}>
        {preview.docType !== 'HOMEPAGE' && preview.docType !== 'LANDING' && <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontSize: 12 }}>{JSON.stringify(preview.content, null, 2)}</pre>}
        {(preview.docType === 'HOMEPAGE' || preview.docType === 'LANDING') && shown.length === 0 && <Typography color="text.secondary">No visible sections.</Typography>}
        {shown.map((s) => (
          <Box key={s.id} sx={{ mb: 2, p: 1.5, border: '1px dashed', borderColor: 'divider', borderRadius: 1 }}>
            <Typography variant="caption" color="text.secondary">{s.type.replace(/_/g, ' ')} · {s.layout?.[device]?.columns} column(s)</Typography>
            {s.props.heading && <Typography variant="h6">{s.props.heading}</Typography>}
            {s.props.subheading && <Typography variant="body2">{s.props.subheading}</Typography>}
            {(s.props.desktopImage || s.props.mobileImage) && <img alt={(device === 'mobile' && s.props.mobileImage ? s.props.mobileImage : s.props.desktopImage)?.alt ?? ''}
              src={`${(device === 'mobile' && s.props.mobileImage ? s.props.mobileImage : s.props.desktopImage).url}`} style={{ maxWidth: '100%', maxHeight: 160 }} />}
            {s.props.buttonLabel && <Button size="small" variant="contained" sx={{ mt: 1 }}>{s.props.buttonLabel}</Button>}
            {(s.props.products ?? []).length > 0 && (
              <Stack direction="row" spacing={1} sx={{ mt: 1, flexWrap: 'wrap' }}>
                {s.props.products.map((p: Row) => <Chip key={p.id} label={`${p.name} · ${p.salePrice ?? p.price} ${p.currency} · ${String(p.availability).replace('_', ' ').toLowerCase()}`} />)}
              </Stack>
            )}
            {(s.props.categories ?? []).length > 0 && <Stack direction="row" spacing={1} sx={{ mt: 1 }}>{s.props.categories.map((c: Row) => <Chip key={c.id} label={c.name} />)}</Stack>}
            {s.props.endsAt && <Typography variant="body2">Countdown to {shortDate(s.props.endsAt)}</Typography>}
            {(s.props.blocks ?? []).map((b: Row, i: number) => <Typography key={i} variant={b.type === 'HEADING' ? 'subtitle1' : 'body2'}>{b.text ?? b.label}</Typography>)}
          </Box>
        ))}
      </Paper>
    </Box>
  );
}

/** One screen, two modes: "publishing" focuses on the latest version's workflow; "history" focuses on comparing and restoring. */
export function PublishingScreen({ storefront, can, mode }: ScreenProps & { mode: 'publishing' | 'history' }) {
  const docs = useDocuments(storefront.id);
  const [docId, setDocId] = useState<number | null>(null);
  const doc = (docs.data ?? []).find((d) => d.id === (docId ?? docs.data?.[0]?.id));
  const versions = useLoad(() => (doc ? sf.versions(doc.id) : Promise.resolve([] as Row[])), [doc?.id]);
  const [preview, setPreview] = useState<Row | null>(null);
  const [diff, setDiff] = useState<Row | null>(null);
  const [pick, setPick] = useState<number[]>([]);
  const [schedule, setSchedule] = useState<{ vid: number; at: string } | null>(null);
  const [note, setNote] = useState('');
  const { run, view } = useNotice();
  const refresh = () => { void versions.reload(); void docs.reload(); };
  const act = (v: Row, action: string) => {
    if (!doc) return;
    const id = doc.id as number, vid = v.id as number;
    switch (action) {
      case 'submit': void run(() => sf.submit(id, vid, note), 'Submitted for review.', refresh); break;
      case 'approve': void run(() => sf.decide(id, vid, true, note), 'Approved.', refresh); break;
      case 'send back': if (note.trim() === '') { void run(() => Promise.reject(new Error('Enter a reason in the note box first.')), ''); } else void run(() => sf.decide(id, vid, false, note), 'Sent back to draft.', refresh); break;
      case 'withdraw': void run(() => sf.withdraw(id, vid), 'Withdrawn to draft.', refresh); break;
      case 'publish now': if (window.confirm(`Publish version ${v.versionNo} of "${doc.title}" to the live storefront now?`)) void run(() => sf.publish(id, vid, undefined, note), 'Published.', refresh); break;
      case 'schedule': setSchedule({ vid, at: '' }); break;
      case 'cancel schedule': void run(() => sf.cancelSchedule(id, vid), 'Schedule cancelled.', refresh); break;
      case 'restore': if (window.confirm(`Restore version ${v.versionNo} as the live version of "${doc.title}"? It goes live immediately; any open draft is kept.`)) void run(() => sf.rollback(id, vid, note), 'Restored and published.', refresh); break;
      default: break;
    }
  };
  return (
    <Stack spacing={2}>
      {view}
      <Loading loading={docs.loading} error={docs.error} />
      {docs.data && <Select size="small" value={doc?.id ?? ''} onChange={(e) => { setDocId(Number(e.target.value)); setPreview(null); setDiff(null); setPick([]); }} sx={{ maxWidth: 360 }}>
        {docs.data.map((d) => <MenuItem key={d.id} value={d.id}>{d.title} ({d.docType.toLowerCase()})</MenuItem>)}
      </Select>}
      <TextField size="small" label="Note (review comment / reason)" value={note} onChange={(e) => setNote(e.target.value)} sx={{ maxWidth: 480 }} />
      {doc?.docType === 'LANDING' && doc.publishedVersionId && can('storefront:publish') && mode === 'publishing' && (
        <Button color="warning" sx={{ alignSelf: 'flex-start' }} onClick={() => { if (window.confirm(`Take "${doc.title}" offline?`)) void run(() => sf.unpublish(doc.id), 'Page unpublished.', refresh); }}>Unpublish page</Button>
      )}
      <Loading loading={versions.loading} error={versions.error} />
      {versions.data && <DataTable rows={versions.data} empty="No versions." columns={[
        { header: 'Version', render: (r) => `v${r.versionNo}${r.id === doc?.publishedVersionId ? ' (live)' : ''}` },
        { header: 'Status', render: (r) => <StatusChip status={r.status} /> },
        { header: 'Note', render: (r) => r.changeNote ?? '' },
        { header: 'Updated', render: (r) => shortDate(r.updatedAt) },
        { header: 'Scheduled', render: (r) => (r.scheduledPublishAt ? shortDate(r.scheduledPublishAt) : '—') },
        { header: 'Compare', render: (r) => <input type="checkbox" aria-label={`compare v${r.versionNo}`} checked={pick.includes(r.id)} onChange={(e) => setPick(e.target.checked ? [...pick, r.id].slice(-2) : pick.filter((x) => x !== r.id))} /> },
        { header: '', render: (r) => (
          <Stack direction="row" spacing={1} flexWrap="wrap">
            <Button size="small" onClick={() => { void run(async () => setPreview(await sf.preview(doc!.id, r.id)), 'Preview loaded.'); }}>Preview</Button>
            {mode === 'publishing' && versionActions(r.status, !!r.scheduledPublishAt, can).filter((a) => a !== 'restore').map((a) => <Button key={a} size="small" variant={a === 'publish now' ? 'contained' : 'text'} onClick={() => act(r, a)}>{a}</Button>)}
            {mode === 'history' && versionActions(r.status, false, can).filter((a) => a === 'restore').map((a) => <Button key={a} size="small" onClick={() => act(r, a)}>{a}</Button>)}
          </Stack>) },
      ]} />}
      {pick.length === 2 && <Button sx={{ alignSelf: 'flex-start' }} onClick={() => { const [a, b] = [...pick].sort((x, y) => x - y); void run(async () => setDiff(await sf.compare(doc!.id, a, b)), 'Comparison ready.'); }}>Compare selected versions</Button>}
      {diff && (
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Typography variant="subtitle2">Changes from v{diff.from} to v{diff.to}</Typography>
          {diff.changes.length === 0 ? <Typography color="text.secondary">No differences.</Typography> : <DataTable rows={diff.changes} empty="" columns={[
            { header: 'Field', render: (r) => <code>{r.path}</code> }, { header: 'Before', render: (r) => r.before ?? '—' }, { header: 'After', render: (r) => r.after ?? '—' }, { header: '', render: (r) => r.change },
          ]} />}
        </Paper>
      )}
      {preview && <PreviewPane preview={preview} />}
      <Dialog open={schedule !== null} onClose={() => setSchedule(null)}>
        <DialogTitle>Schedule publication</DialogTitle>
        <DialogContent><TextField type="datetime-local" fullWidth sx={{ mt: 1 }} InputLabelProps={{ shrink: true }} label="Go live at" value={schedule?.at ?? ''} onChange={(e) => setSchedule(schedule && { ...schedule, at: e.target.value })} />
          {schedule?.at && new Date(schedule.at) <= new Date() && <Alert severity="warning" sx={{ mt: 1 }}>Pick a time in the future (a past time would publish immediately).</Alert>}</DialogContent>
        <DialogActions><Button onClick={() => setSchedule(null)}>Cancel</Button>
          <Button variant="contained" disabled={!schedule?.at || new Date(schedule.at) <= new Date()} onClick={() => { const s = schedule!; setSchedule(null); void run(() => sf.publish(doc!.id, s.vid, new Date(s.at).toISOString(), note), 'Scheduled.', refresh); }}>Schedule</Button></DialogActions>
      </Dialog>
    </Stack>
  );
}
