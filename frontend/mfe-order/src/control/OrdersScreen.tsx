import { useEffect, useMemo, useState } from 'react';
import {
  Alert, Box, Button, Checkbox, Chip, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, IconButton, Menu, MenuItem, Paper, Select, Stack, Table,
  TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, TableSortLabel, TextField, Tooltip, Typography,
} from '@mui/material';
import { ViewColumnRounded, DownloadRounded, SaveRounded } from '@mui/icons-material';
import { Loading, StatusChip, money, useLoad, useNotice, when } from './common';
import { orderApi, errorMessage } from './controlApi';
import type { Row } from './controlApi';
import {
  ALL_COLUMNS, activeFilterCount, bulkActionsFor, bulkParamProblems, buildQuery, emptyFilter, filterProblems, nextSort, previewSummary, sanitizeColumns, slaTone, togglePage,
  toggleSelection,
} from './controlModel';
import type { OrderFilter, SortSpec } from './controlModel';

const COLUMN_LABELS: Record<string, string> = { orderNumber: 'Order', createdAt: 'Date', customer: 'Customer', store: 'Store', vendor: 'Vendor', type: 'Type', items: 'Items', amount: 'Amount',
  payment: 'Payment', fulfillment: 'Fulfillment', delivery: 'Delivery', status: 'Status', partner: 'Delivery partner', sla: 'SLA' };
const SORT_FIELD: Record<string, string> = { orderNumber: 'orderNumber', createdAt: 'createdAt', customer: 'customerName', store: 'storeId', type: 'orderType', amount: 'totalAmount',
  payment: 'paymentStatus', fulfillment: 'fulfillmentStatus', delivery: 'deliveryStatus', status: 'status' };
const COLUMN_KEY = 'ucp.orders.columns';

function loadColumns(): string[] {
  try { return sanitizeColumns(JSON.parse(localStorage.getItem(COLUMN_KEY) ?? 'null')); } catch { return sanitizeColumns(null); }
}

interface Props { view?: string; title: string; can: (p: string) => boolean; role?: string; permissions?: string[]; bulkMode?: boolean; onOpen: (id: number) => void }

export default function OrdersScreen({ view = '', title, can, role, permissions, bulkMode, onOpen }: Props) {
  const [filter, setFilter] = useState<OrderFilter>(emptyFilter(view));
  const [draft, setDraft] = useState<OrderFilter>(emptyFilter(view));
  const [sort, setSort] = useState<SortSpec[]>([]);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [selected, setSelected] = useState<number[]>([]);
  const [columns, setColumns] = useState<string[]>(loadColumns);
  const [colMenu, setColMenu] = useState<HTMLElement | null>(null);
  const [showFilters, setShowFilters] = useState(false);
  const { run, view: notice } = useNotice();
  const problems = filterProblems(draft);

  useEffect(() => { const f = emptyFilter(view); setFilter(f); setDraft(f); setPage(0); setSelected([]); setSort([]); }, [view]);
  useEffect(() => { try { localStorage.setItem(COLUMN_KEY, JSON.stringify(columns)); } catch { /* storage unavailable: the choice just isn't remembered */ } }, [columns]);

  const query = useMemo(() => buildQuery(filter, sort, page, size), [filter, sort, page, size]);
  const list = useLoad(() => orderApi.list(query), [query]);
  const views = useLoad(() => orderApi.savedViews(), []);
  const rows: Row[] = list.data?.content ?? [];
  const bulkActions = bulkActionsFor(role, permissions);

  const apply = () => { if (problems.length === 0) { setFilter({ ...draft, view }); setPage(0); setSelected([]); } };
  const clear = () => { const f = emptyFilter(view); setDraft(f); setFilter(f); setPage(0); };
  const show = (c: string) => columns.includes(c);

  const exportCsv = () => run(async () => {
    const csv = await orderApi.exportCsv(buildQuery(filter, sort, 0, 100));
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
    const a = document.createElement('a'); a.href = url; a.download = 'orders.csv'; a.click(); URL.revokeObjectURL(url);
  }, 'Export downloaded (customer columns appear only if you may see them).');

  const saveView = () => {
    const name = window.prompt('Name this view');
    if (!name) return;
    const { view: _v, ...rest } = filter;
    void run(() => orderApi.saveView(name, { ...rest, view: view || undefined, sort: sort.map((s) => `${s.field}:${s.dir}`), columns }), 'View saved.', () => { void views.reload(); });
  };
  const applyView = (v: Row) => {
    const f = { ...emptyFilter(view), ...(v.filters ?? {}) } as OrderFilter;
    f.view = view; f.status = Array.isArray(f.status) ? f.status : [];
    setDraft(f); setFilter(f); setPage(0);
    if (Array.isArray(v.filters?.sort)) setSort(v.filters.sort.map((s: string) => { const [field, dir] = s.split(':'); return { field, dir: dir === 'desc' ? 'desc' : 'asc' } as SortSpec; }));
    if (Array.isArray(v.filters?.columns)) setColumns(sanitizeColumns(v.filters.columns));
  };

  const cell = (c: string, r: Row) => {
    switch (c) {
      case 'orderNumber': return <Typography component="a" sx={{ cursor: 'pointer', color: 'primary.main', fontWeight: 600 }} onClick={() => onOpen(r.id)}>{r.orderNumber}</Typography>;
      case 'createdAt': return when(r.createdAt);
      case 'customer': return r.customerHidden ? <em>Hidden</em> : <>{r.customer?.name || '—'}<br /><Typography variant="caption" color="text.secondary">{r.customer?.email}</Typography></>;
      case 'store': return r.storeId;
      case 'vendor': return r.vendorId ?? '—';
      case 'type': return r.orderType === 'RESTAURANT' ? 'Restaurant' : 'Retail';
      case 'items': return r.itemCount;
      case 'amount': return money(r.totalAmount);
      case 'payment': return <StatusChip status={r.paymentStatus} />;
      case 'fulfillment': return <StatusChip status={r.fulfillmentStatus} />;
      case 'delivery': return r.deliveryStatus ?? '—';
      case 'status': return <StatusChip status={r.status} />;
      case 'partner': return r.deliveryPartner ?? '—';
      case 'sla': return r.sla?.indicator === 'NONE' ? '—' : <Tooltip title={`${r.sla?.phase}: ${r.sla?.elapsedMinutes} of ${r.sla?.thresholdMinutes} min`}><span><StatusChip status={r.sla?.indicator} tone={slaTone(r.sla?.indicator)} /></span></Tooltip>;
      default: return null;
    }
  };

  return (
    <Stack spacing={2}>
      {notice}
      {bulkMode && <Alert severity="info">Select orders below, choose an action, review the preview, then confirm. Each order is re-checked individually, so a bulk action can never skip the order lifecycle rules.</Alert>}
      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} alignItems={{ md: 'center' }}>
          <TextField size="small" sx={{ minWidth: 260 }} label={can('order:customer:view') ? 'Search order no., customer, email or phone' : 'Search order number'} value={draft.q}
            onChange={(e) => setDraft({ ...draft, q: e.target.value })} onKeyDown={(e) => { if (e.key === 'Enter') apply(); }} />
          <Button variant="contained" onClick={apply} disabled={problems.length > 0}>Search</Button>
          <Button onClick={() => setShowFilters(!showFilters)}>Filters{activeFilterCount(filter, view) ? ` (${activeFilterCount(filter, view)})` : ''}</Button>
          {activeFilterCount(filter, view) > 0 && <Button onClick={clear}>Clear</Button>}
          <Box sx={{ flex: 1 }} />
          <Select size="small" displayEmpty value="" onChange={(e) => { const v = (views.data ?? []).find((x) => x.id === e.target.value); if (v) applyView(v); }} sx={{ minWidth: 160 }}>
            <MenuItem value="">Saved views…</MenuItem>{(views.data ?? []).map((v) => <MenuItem key={v.id} value={v.id}>{v.name}</MenuItem>)}
          </Select>
          <Tooltip title="Save current filters, sort and columns"><IconButton onClick={saveView} aria-label="Save view"><SaveRounded /></IconButton></Tooltip>
          <Tooltip title="Choose columns"><IconButton onClick={(e) => setColMenu(e.currentTarget)} aria-label="Columns"><ViewColumnRounded /></IconButton></Tooltip>
          {can('order:export') && <Tooltip title="Export the current result set to CSV"><IconButton onClick={exportCsv} aria-label="Export CSV"><DownloadRounded /></IconButton></Tooltip>}
        </Stack>
        {showFilters && (
          <Stack spacing={1} sx={{ mt: 2 }}>
            {problems.length > 0 && <Alert severity="warning">{problems.map((p) => <div key={p}>{p}</div>)}</Alert>}
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} flexWrap="wrap">
              <TextField size="small" type="date" label="From" InputLabelProps={{ shrink: true }} value={draft.from} onChange={(e) => setDraft({ ...draft, from: e.target.value })} />
              <TextField size="small" type="date" label="To" InputLabelProps={{ shrink: true }} value={draft.to} onChange={(e) => setDraft({ ...draft, to: e.target.value })} />
              <Select size="small" displayEmpty value={draft.orderType} onChange={(e) => setDraft({ ...draft, orderType: e.target.value })}><MenuItem value="">Any type</MenuItem><MenuItem value="RETAIL">Retail</MenuItem><MenuItem value="RESTAURANT">Restaurant</MenuItem></Select>
              <TextField size="small" label="Store" value={draft.storeId} onChange={(e) => setDraft({ ...draft, storeId: e.target.value })} />
              <TextField size="small" label="Vendor" value={draft.vendorId} onChange={(e) => setDraft({ ...draft, vendorId: e.target.value })} />
              <Select size="small" displayEmpty value={draft.paymentStatus} onChange={(e) => setDraft({ ...draft, paymentStatus: e.target.value })}>
                <MenuItem value="">Any payment</MenuItem>{['UNKNOWN', 'PENDING', 'PAID', 'FAILED', 'REFUNDED', 'PARTIALLY_REFUNDED'].map((s) => <MenuItem key={s} value={s}>{s.replace('_', ' ')}</MenuItem>)}
              </Select>
              <Select size="small" displayEmpty value={draft.fulfillmentStatus} onChange={(e) => setDraft({ ...draft, fulfillmentStatus: e.target.value })}>
                <MenuItem value="">Any fulfillment</MenuItem>{['UNFULFILLED', 'ALLOCATED', 'PARTIAL', 'FULFILLED'].map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}
              </Select>
              <TextField size="small" type="number" label="Min amount" sx={{ width: 120 }} value={draft.minAmount} onChange={(e) => setDraft({ ...draft, minAmount: e.target.value })} />
              <TextField size="small" type="number" label="Max amount" sx={{ width: 120 }} value={draft.maxAmount} onChange={(e) => setDraft({ ...draft, maxAmount: e.target.value })} />
              <TextField size="small" label="Operator ID" sx={{ width: 120 }} value={draft.operatorId} onChange={(e) => setDraft({ ...draft, operatorId: e.target.value })} />
              <Select size="small" displayEmpty value={draft.slaBreached} onChange={(e) => setDraft({ ...draft, slaBreached: e.target.value as OrderFilter['slaBreached'] })}>
                <MenuItem value="">Any SLA</MenuItem><MenuItem value="true">SLA breached</MenuItem><MenuItem value="false">Within SLA</MenuItem>
              </Select>
              <Button variant="outlined" onClick={apply} disabled={problems.length > 0}>Apply filters</Button>
            </Stack>
          </Stack>
        )}
      </Paper>
      <Menu anchorEl={colMenu} open={!!colMenu} onClose={() => setColMenu(null)}>
        {ALL_COLUMNS.map((c) => (
          <MenuItem key={c} dense><FormControlLabel label={COLUMN_LABELS[c]} control={<Checkbox size="small" checked={show(c)} disabled={show(c) && columns.length === 1}
            onChange={(e) => setColumns(sanitizeColumns(e.target.checked ? [...columns, c] : columns.filter((x) => x !== c)))} />} /></MenuItem>
        ))}
      </Menu>

      {selected.length > 0 && <BulkBar selected={selected} actions={bulkActions} onDone={() => { setSelected([]); void list.reload(); }} onClear={() => setSelected([])} />}
      <Loading loading={list.loading} error={list.error} />
      {list.data && (
        <Paper variant="outlined">
          <TableContainer sx={{ overflowX: 'auto' }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell padding="checkbox"><Checkbox size="small" aria-label="Select page" checked={rows.length > 0 && rows.every((r) => selected.includes(r.id))}
                    indeterminate={rows.some((r) => selected.includes(r.id)) && !rows.every((r) => selected.includes(r.id))} onChange={() => setSelected(togglePage(selected, rows.map((r) => r.id)))} /></TableCell>
                  {ALL_COLUMNS.filter(show).map((c) => {
                    const field = SORT_FIELD[c]; const active = sort.find((s) => s.field === field);
                    return (
                      <TableCell key={c} sortDirection={active ? active.dir : false}>
                        {field ? <TableSortLabel active={!!active} direction={active?.dir ?? 'asc'} onClick={(e) => { setSort(nextSort(sort, field, e.shiftKey)); setPage(0); }}>{COLUMN_LABELS[c]}</TableSortLabel> : COLUMN_LABELS[c]}
                      </TableCell>
                    );
                  })}
                  <TableCell>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.length === 0 && <TableRow><TableCell colSpan={columns.length + 2}><Typography color="text.secondary" sx={{ py: 2 }}>No orders match.</Typography></TableCell></TableRow>}
                {rows.map((r) => (
                  <TableRow key={r.id} hover selected={selected.includes(r.id)}>
                    <TableCell padding="checkbox"><Checkbox size="small" aria-label={`Select ${r.orderNumber}`} checked={selected.includes(r.id)} onChange={() => setSelected(toggleSelection(selected, r.id))} /></TableCell>
                    {ALL_COLUMNS.filter(show).map((c) => <TableCell key={c}>{cell(c, r)}</TableCell>)}
                    <TableCell><Button size="small" onClick={() => onOpen(r.id)}>Open</Button></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination component="div" count={list.data.totalElements} page={page} rowsPerPage={size} rowsPerPageOptions={[10, 25, 50, 100]}
            onPageChange={(_, p) => setPage(p)} onRowsPerPageChange={(e) => { setSize(Number(e.target.value)); setPage(0); }} />
        </Paper>
      )}
      <Typography variant="caption" color="text.secondary">{title}: results are paged and sorted by the server. Shift-click a column heading to add a secondary sort.</Typography>
    </Stack>
  );
}

function BulkBar({ selected, actions, onDone, onClear }: { selected: number[]; actions: ReturnType<typeof bulkActionsFor>; onDone: () => void; onClear: () => void }) {
  const [action, setAction] = useState('');
  const [params, setParams] = useState<Record<string, string>>({});
  const [job, setJob] = useState<Row | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState<Row | null>(null);
  const meta = actions.find((a) => a.action === action);
  const problems = meta ? bulkParamProblems(meta, params) : [];

  const preview = async () => {
    if (!meta) return;
    setBusy(true); setError('');
    try {
      const body: Row = { ...params }; if (body.operatorId) body.operatorId = Number(body.operatorId);
      setJob(await orderApi.bulkPreview(action, selected, body));
    } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); }
  };
  const execute = async () => {
    if (!job) return;
    setBusy(true); setError('');
    try {
      const done = await orderApi.bulkExecute(job.id);
      setResult(done);
      if (done.csv) { const url = URL.createObjectURL(new Blob([done.csv], { type: 'text/csv' })); const a = document.createElement('a'); a.href = url; a.download = 'selected-orders.csv'; a.click(); URL.revokeObjectURL(url); }
    } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); }
  };
  const close = () => { const finished = !!result; setJob(null); setResult(null); setError(''); if (finished) onDone(); };

  return (
    <Paper variant="outlined" sx={{ p: 1.5 }}>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} alignItems={{ md: 'center' }}>
        <Chip label={`${selected.length} selected`} onDelete={onClear} />
        {actions.length === 0 ? <Typography variant="body2" color="text.secondary">You do not have permission for bulk actions.</Typography> : <>
          <Select size="small" displayEmpty value={action} onChange={(e) => { setAction(e.target.value); setParams({}); }} sx={{ minWidth: 220 }}>
            <MenuItem value="">Bulk action…</MenuItem>{actions.map((a) => <MenuItem key={a.action} value={a.action}>{a.label}</MenuItem>)}
          </Select>
          {meta?.params.map((p) => <TextField key={p} size="small" label={p === 'operatorId' ? 'Operator user ID' : p} value={params[p] ?? ''} onChange={(e) => setParams({ ...params, [p]: e.target.value })} />)}
          <Button variant="contained" disabled={!meta || problems.length > 0 || busy} title={problems.join(' ')} onClick={() => { void preview(); }}>Preview</Button>
        </>}
      </Stack>
      {error && !job && <Alert severity="error" sx={{ mt: 1 }}>{error}</Alert>}
      <Dialog open={job !== null} onClose={close} maxWidth="sm" fullWidth>
        <DialogTitle>{result ? 'Bulk operation finished' : `Confirm: ${meta?.label ?? action}`}</DialogTitle>
        <DialogContent>
          {job && !result && <Typography sx={{ mb: 1 }}>{previewSummary(job as { totalCount: number; eligibleCount: number }).text}</Typography>}
          {result && <Alert severity={result.failureCount > 0 ? 'warning' : 'success'} sx={{ mb: 1 }}>{result.alreadyExecuted ? 'This job had already run.' : `${result.successCount} succeeded, ${result.failureCount} failed.`}</Alert>}
          {error && <Alert severity="error" sx={{ mb: 1 }}>{error}</Alert>}
          <Box sx={{ maxHeight: 260, overflow: 'auto' }}>
            {((result ?? job)?.items ?? []).map((i: Row) => (
              <Stack key={i.orderId} direction="row" spacing={1} sx={{ py: 0.3 }}>
                <Typography variant="body2" sx={{ width: 90 }}>#{i.orderId}</Typography>
                <Chip size="small" label={result ? i.outcome : i.eligible ? 'will change' : 'skipped'} color={(result ? i.outcome === 'DONE' : i.eligible) ? 'success' : i.outcome === 'FAILED' ? 'error' : 'default'} />
                <Typography variant="caption" color="text.secondary">{i.message ?? ''}</Typography>
              </Stack>
            ))}
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={close}>{result ? 'Close' : 'Cancel'}</Button>
          {!result && <Button variant="contained" color={action === 'REQUEST_CANCELLATION' ? 'error' : 'primary'} disabled={busy || !job || !previewSummary(job as { totalCount: number; eligibleCount: number }).canExecute} onClick={() => { void execute(); }}>Confirm and run</Button>}
        </DialogActions>
      </Dialog>
    </Paper>
  );
}
