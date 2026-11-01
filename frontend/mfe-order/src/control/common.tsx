import { useCallback, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Box, Chip, CircularProgress, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography } from '@mui/material';
import { errorMessage } from './controlApi';
import { slaTone, statusTone } from './controlModel';
import type { Tone } from './controlModel';

/** Loads real data from the order API; failures are shown, never replaced with placeholder numbers. */
export function useLoad<T>(loader: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const reload = useCallback(async () => {
    setLoading(true); setError('');
    try { setData(await loader()); } catch (e) { setError(errorMessage(e)); } finally { setLoading(false); }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  useEffect(() => { void reload(); }, [reload]);
  return { data, loading, error, reload };
}

export function Loading({ loading, error }: { loading: boolean; error: string }) {
  if (loading) return <Box sx={{ py: 3, textAlign: 'center' }}><CircularProgress size={24} /></Box>;
  if (error) return <Alert severity="error" sx={{ my: 1 }}>{error}</Alert>;
  return null;
}

export function StatusChip({ status, tone }: { status: string; tone?: Tone }) {
  const t = tone ?? statusTone(status);
  return <Chip size="small" label={status.replace(/_/g, ' ')} color={t} variant={t === 'default' ? 'outlined' : 'filled'} />;
}

export interface Column<R> { header: string; render: (row: R) => ReactNode; align?: 'right' }

export function DataTable<R extends Record<string, any>>({ rows, columns, empty }: { rows: R[]; columns: Column<R>[]; empty: string }) {
  if (rows.length === 0) return <Typography color="text.secondary" sx={{ py: 2 }}>{empty}</Typography>;
  return (
    <TableContainer component={Paper} variant="outlined">
      <Table size="small">
        <TableHead><TableRow>{columns.map((c) => <TableCell key={c.header} align={c.align}>{c.header}</TableCell>)}</TableRow></TableHead>
        <TableBody>{rows.map((r, i) => <TableRow key={r.id ?? i} hover>{columns.map((c) => <TableCell key={c.header} align={c.align}>{c.render(r)}</TableCell>)}</TableRow>)}</TableBody>
      </Table>
    </TableContainer>
  );
}

export function useNotice() {
  const [notice, setNotice] = useState<{ severity: 'success' | 'error'; text: string } | null>(null);
  const run = async (work: () => Promise<unknown>, success: string, after?: () => void) => {
    try { await work(); setNotice({ severity: 'success', text: success }); after?.(); } catch (e) { setNotice({ severity: 'error', text: errorMessage(e) }); }
  };
  const view = notice ? <Alert severity={notice.severity} onClose={() => setNotice(null)} sx={{ my: 1 }}>{notice.text}</Alert> : null;
  return { run, view, fail: (text: string) => setNotice({ severity: 'error', text }) };
}

export const when = (v?: string | null) => (v ? new Date(v).toLocaleString() : '—');
export const money = (v: unknown) => (v === null || v === undefined || v === '' ? '—' : Number(v).toFixed(2));

export function slaChip(sla?: { indicator: string; phase: string; elapsedMinutes: number; thresholdMinutes: number } | null) {
  if (!sla || sla.indicator === 'NONE') return null;
  return <Chip size="small" color={slaTone(sla.indicator)} variant={sla.indicator === 'ON_TRACK' ? 'outlined' : 'filled'}
    label={`SLA ${sla.indicator.replace('_', ' ').toLowerCase()} · ${sla.phase.toLowerCase()} ${sla.elapsedMinutes}/${sla.thresholdMinutes} min`} />;
}
