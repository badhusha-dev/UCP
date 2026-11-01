import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Box, Button, Chip, Stack, Typography } from '@mui/material';
import { Loading, useLoad } from '../pricing/common';
import { errorMessage, sf } from './storefrontApi';
import type { Row } from './storefrontApi';
import { statusTone } from './storefrontModel';

export interface ScreenProps { storefront: Row; can: (permission: string) => boolean; onChanged: () => void }

export function StatusChip({ status }: { status: string }) {
  const tone = statusTone(status);
  return <Chip size="small" label={status.replace(/_/g, ' ')} color={tone} variant={tone === 'default' ? 'outlined' : 'filled'} />;
}

export function useNotice() {
  const [notice, setNotice] = useState<{ severity: 'success' | 'error'; text: string } | null>(null);
  const run = async (work: () => Promise<unknown>, success: string, after?: () => void) => {
    try { await work(); setNotice({ severity: 'success', text: success }); after?.(); } catch (e) { setNotice({ severity: 'error', text: errorMessage(e) }); }
  };
  const view = notice ? <Alert severity={notice.severity} onClose={() => setNotice(null)} sx={{ my: 1 }}>{notice.text}</Alert> : null;
  return { run, view, fail: (text: string) => setNotice({ severity: 'error', text }) };
}

/** Finds a storefront document (HOMEPAGE/THEME/NAVIGATION/SETTINGS have slug ''; landing pages carry their slug). */
export function useDocuments(storefrontId: number) {
  return useLoad(() => sf.documents(storefrontId), [storefrontId]);
}

export function findDoc(docs: Row[] | null, type: string, slug = ''): Row | undefined {
  return docs?.find((d) => d.docType === type && d.slug === slug);
}

/**
 * Loads the newest version of a document and lets the user edit it. Saving writes the DRAFT only; the live site changes
 * solely through the publishing workflow. When the newest version is under review or approved it is read-only.
 */
export function DocumentPanel({ doc, can, validate, children, onSaved }: {
  doc: Row; can: (p: string) => boolean; validate?: (content: any) => string[];
  children: (state: { content: any; setContent: (c: any) => void; editable: boolean; version: Row }) => ReactNode; onSaved?: () => void;
}) {
  const latest = useLoad(async () => {
    const versions = await sf.versions(doc.id);
    return sf.version(doc.id, versions[0].id);
  }, [doc.id]);
  const [content, setContent] = useState<any>(null);
  const [dirty, setDirty] = useState(false);
  const { run, view } = useNotice();
  useEffect(() => { if (latest.data) { setContent(latest.data.content); setDirty(false); } }, [latest.data]);
  if (latest.loading || latest.error || !latest.data || content === null) return <Loading loading={latest.loading} error={latest.error} />;
  const version = latest.data;
  const editable = can('storefront:edit') && ['DRAFT', 'PUBLISHED', 'ARCHIVED'].includes(version.status);
  const problems = validate ? validate(content) : [];
  return (
    <Box>
      {view}
      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1, flexWrap: 'wrap' }}>
        <Typography variant="body2" color="text.secondary">Version {version.versionNo}</Typography>
        <StatusChip status={version.status} />
        {doc.publishedVersionNo != null && version.status !== 'PUBLISHED' && <Chip size="small" variant="outlined" label={`Live: v${doc.publishedVersionNo}`} />}
        {dirty && <Chip size="small" color="warning" label="Unsaved changes" />}
        {!editable && <Typography variant="caption" color="text.secondary">This version is {String(version.status).replace('_', ' ').toLowerCase()}; withdraw it in Preview &amp; Publishing to edit.</Typography>}
      </Stack>
      {problems.length > 0 && <Alert severity="warning" sx={{ mb: 1 }}>{problems.slice(0, 6).map((p) => <div key={p}>{p}</div>)}</Alert>}
      {children({ content, setContent: (c: any) => { setContent(c); setDirty(true); }, editable, version })}
      {editable && (
        <Stack direction="row" spacing={1} sx={{ mt: 2 }}>
          <Button variant="contained" disabled={!dirty || problems.length > 0}
            onClick={() => { void run(() => sf.saveDraft(doc.id, content), 'Draft saved. It is not live until it is submitted, approved and published.', () => { void latest.reload(); onSaved?.(); }); }}>Save draft</Button>
          <Button disabled={!dirty} onClick={() => { setContent(version.content); setDirty(false); }}>Discard changes</Button>
        </Stack>
      )}
    </Box>
  );
}
