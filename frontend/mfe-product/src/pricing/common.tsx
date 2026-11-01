import { useCallback, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import {
  Alert, Box, Chip, CircularProgress, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography,
} from '@mui/material';
import { errorMessage } from './pricingApi';
import { statusTone } from './pricingModel';

/** Loads data from the real API; failures are shown, never replaced with placeholder numbers. */
export function useLoad<T>(loader: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const reload = useCallback(async () => {
    setLoading(true);
    setError('');
    try { setData(await loader()); } catch (e) { setError(errorMessage(e)); } finally { setLoading(false); }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  useEffect(() => { void reload(); }, [reload]);
  return { data, loading, error, reload };
}

export function StatusChip({ status }: { status: string }) {
  return <Chip size="small" label={status.replace(/_/g, ' ')} color={statusTone(status)} variant={statusTone(status) === 'default' ? 'outlined' : 'filled'} />;
}

export function Loading({ loading, error }: { loading: boolean; error: string }) {
  if (loading) return <Box sx={{ py: 3, textAlign: 'center' }}><CircularProgress size={24} /></Box>;
  if (error) return <Alert severity="error" sx={{ my: 1 }}>{error}</Alert>;
  return null;
}

export interface Column<R> { header: string; render: (row: R) => ReactNode; align?: 'right' }

export function DataTable<R extends Record<string, any>>({ rows, columns, empty }: { rows: R[]; columns: Column<R>[]; empty: string }) {
  if (rows.length === 0) return <Typography color="text.secondary" sx={{ py: 2 }}>{empty}</Typography>;
  return (
    <TableContainer component={Paper} variant="outlined">
      <Table size="small">
        <TableHead><TableRow>{columns.map((c) => <TableCell key={c.header} align={c.align}>{c.header}</TableCell>)}</TableRow></TableHead>
        <TableBody>
          {rows.map((r, i) => (
            <TableRow key={r.id ?? i} hover>{columns.map((c) => <TableCell key={c.header} align={c.align}>{c.render(r)}</TableCell>)}</TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

export const shortDate = (v?: string) => (v ? new Date(v).toLocaleString() : '—');
