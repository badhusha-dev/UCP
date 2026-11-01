import React, { useCallback, useEffect, useMemo, useState } from 'react';
import Grid from '@mui/material/Grid2';
import {
  Alert, Box, Button, Chip, CircularProgress, List, ListItemButton, ListItemText, ListSubheader, MenuItem, Paper, Stack, Table, TableBody,
  TableCell, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import {
  SCREENS, type ScreenKey, visibleScreens, dashboardCards, transferActions, poActions, poTotals, validatePoForm, validateReceipt, newIdempotencyKey,
  validateAdjustment, validateWarehouse, adjustmentNeedsApproval, stockStatus, type ReceiptLineInput,
} from './model';
import { opsApi, procApi, currentToken, tokenPermissions, errorMessage } from './api';

type Row = Record<string, any>;

function useLoad<T = any>(fn: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [tick, setTick] = useState(0);
  useEffect(() => {
    let live = true;
    setLoading(true);
    fn().then(d => { if (live) { setData(d); setError(''); } }).catch(e => { if (live) setError(errorMessage(e)); }).finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);
  return { data, error, loading, reload: () => setTick(t => t + 1) };
}

function Data({ rows, columns, empty = 'No records.' }: { rows: Row[]; columns: { key: string; label: string; render?: (r: Row) => React.ReactNode }[]; empty?: string }) {
  if (!rows.length) return <Typography color="text.secondary" sx={{ py: 2 }}>{empty}</Typography>;
  return (
    <Box sx={{ overflowX: 'auto' }}>
      <Table size="small">
        <TableHead><TableRow>{columns.map(c => <TableCell key={c.key} sx={{ fontWeight: 700 }}>{c.label}</TableCell>)}</TableRow></TableHead>
        <TableBody>{rows.map((r, i) => <TableRow key={r.id ?? r.productId ?? i} hover>{columns.map(c => <TableCell key={c.key}>{c.render ? c.render(r) : String(r[c.key] ?? '')}</TableCell>)}</TableRow>)}</TableBody>
      </Table>
    </Box>
  );
}

function Status({ value }: { value: string }) {
  const bad = ['OUT_OF_STOCK', 'REJECTED', 'CANCELLED', 'LOST', 'DAMAGED', 'SUSPENDED'];
  const warn = ['LOW_STOCK', 'PENDING', 'PENDING_APPROVAL', 'DRAFT', 'REQUESTED', 'PARTIALLY_RECEIVED'];
  return <Chip size="small" label={value} color={bad.includes(value) ? 'error' : warn.includes(value) ? 'warning' : 'success'} variant="outlined" />;
}

function Panel({ title, children, actions }: { title: string; children: React.ReactNode; actions?: React.ReactNode }) {
  return (
    <Paper variant="outlined" sx={{ p: 2, borderRadius: 2, mb: 2 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}><Typography variant="h6" sx={{ fontWeight: 700 }}>{title}</Typography>{actions}</Stack>
      {children}
    </Paper>
  );
}

function Loading({ state }: { state: { loading: boolean; error: string } }) {
  if (state.loading) return <CircularProgress size={22} />;
  if (state.error) return <Alert severity="error">{state.error}</Alert>;
  return null;
}

function useAction() {
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const run = async (fn: () => Promise<unknown>, ok: string, after?: () => void) => {
    try { await fn(); setMessage({ type: 'success', text: ok }); after?.(); } catch (e) { setMessage({ type: 'error', text: errorMessage(e) }); }
  };
  return { message, run, banner: message ? <Alert severity={message.type} sx={{ mb: 2 }} onClose={() => setMessage(null)}>{message.text}</Alert> : null };
}

// ---------------- screens ----------------
function Dashboard() {
  const wh = useLoad(() => opsApi.warehouses(), []);
  const [warehouseId, setWarehouseId] = useState<number | undefined>();
  const d = useLoad(() => opsApi.dashboard(warehouseId), [warehouseId]);
  return (
    <>
      <Panel title="Filters">
        <Stack direction="row" gap={2} flexWrap="wrap">
          <TextField select size="small" label="Warehouse" value={warehouseId ?? ''} onChange={e => setWarehouseId(e.target.value ? Number(e.target.value) : undefined)} sx={{ minWidth: 220 }}>
            <MenuItem value="">All warehouses</MenuItem>
            {(wh.data ?? []).map((w: Row) => <MenuItem key={w.id} value={w.id}>{w.code} · {w.name}</MenuItem>)}
          </TextField>
          <Typography variant="caption" color="text.secondary" sx={{ alignSelf: 'center' }}>Tenant is taken from your sign-in. Category, vendor, status and date filters are on the Inventory, Purchase Orders and Movement screens.</Typography>
        </Stack>
      </Panel>
      <Loading state={d} />
      {d.data && (
        <Grid container spacing={2}>
          {dashboardCards(d.data).map(c => (
            <Grid key={c.label} size={{ xs: 6, md: 3 }}>
              <Paper variant="outlined" sx={{ p: 2, borderRadius: 2, borderColor: c.tone === 'bad' ? 'error.main' : c.tone === 'warn' ? 'warning.main' : 'divider' }}>
                <Typography variant="caption" color="text.secondary">{c.label}</Typography>
                <Typography variant="h5" sx={{ fontWeight: 800 }}>{c.value}</Typography>
              </Paper>
            </Grid>
          ))}
          {warehouseId && <Grid size={12}><Alert severity="info">Selected warehouse: on hand {d.data.warehouseOnHand ?? 0}, reserved {d.data.warehouseReserved ?? 0}.</Alert></Grid>}
        </Grid>
      )}
    </>
  );
}

function Items({ title, fixedStatus, byProduct }: { title: string; fixedStatus?: string; byProduct?: boolean }) {
  const [q, setQ] = useState('');
  const [status, setStatus] = useState(fixedStatus ?? '');
  const [sort, setSort] = useState('productId');
  const [page, setPage] = useState(0);
  const size = 15;
  const d = useLoad(() => opsApi.items({ q, status, sort, page, size }), [q, status, sort, page]);
  const act = useAction();
  const exportCsv = () => act.run(async () => {
    const csv = await opsApi.exportCsv();
    const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' })); a.download = 'inventory.csv'; a.click();
  }, 'Export downloaded.');
  const total = d.data?.total ?? 0;
  return (
    <Panel title={title} actions={!byProduct && <Button size="small" onClick={exportCsv}>Export CSV</Button>}>
      {act.banner}
      <Stack direction="row" gap={2} sx={{ mb: 2 }} flexWrap="wrap">
        <TextField size="small" label={byProduct ? 'Product ID or SKU' : 'Search SKU / product'} value={q} onChange={e => { setQ(e.target.value); setPage(0); }} />
        {!fixedStatus && <TextField select size="small" label="Status" value={status} onChange={e => { setStatus(e.target.value); setPage(0); }} sx={{ minWidth: 160 }}>
          <MenuItem value="">All</MenuItem><MenuItem value="IN_STOCK">In stock</MenuItem><MenuItem value="LOW_STOCK">Low stock</MenuItem><MenuItem value="OUT_OF_STOCK">Out of stock</MenuItem>
        </TextField>}
        <TextField select size="small" label="Sort" value={sort} onChange={e => setSort(e.target.value)} sx={{ minWidth: 140 }}>
          <MenuItem value="productId">Product</MenuItem><MenuItem value="sku">SKU</MenuItem><MenuItem value="available">Available</MenuItem><MenuItem value="onHand">On hand</MenuItem>
        </TextField>
      </Stack>
      <Loading state={d} />
      {d.data && <Data rows={d.data.content} columns={[
        { key: 'productId', label: 'Product' }, { key: 'sku', label: 'SKU' }, { key: 'onHand', label: 'On hand' }, { key: 'reserved', label: 'Reserved' }, { key: 'unavailable', label: 'Unavailable' },
        { key: 'incoming', label: 'Incoming*' }, { key: 'available', label: 'Available' }, { key: 'reorderPoint', label: 'Reorder pt' },
        { key: 'status', label: 'Status', render: r => <Status value={r.status ?? stockStatus(r.available, r.reorderPoint)} /> },
      ]} />}
      <Stack direction="row" gap={1} alignItems="center" sx={{ mt: 1 }}>
        <Button size="small" disabled={page === 0} onClick={() => setPage(p => p - 1)}>Previous</Button>
        <Typography variant="caption">Page {page + 1} of {Math.max(1, Math.ceil(total / size))} · {total} items</Typography>
        <Button size="small" disabled={(page + 1) * size >= total} onClick={() => setPage(p => p + 1)}>Next</Button>
      </Stack>
      <Typography variant="caption" color="text.secondary">* Incoming stock is never available until it is received and accepted. Quantities are read-only here: change stock via adjustments, transfers or goods receiving.</Typography>
    </Panel>
  );
}

function Warehouses() {
  const d = useLoad(() => opsApi.warehouses(), []);
  const act = useAction();
  const [f, setF] = useState({ code: '', name: '', capacityUnits: '', managerEmail: '', storeId: '' });
  const [loc, setLoc] = useState({ warehouseId: '', code: '', capacityUnits: '' });
  const errors = validateWarehouse({ code: f.code, name: f.name, capacityUnits: f.capacityUnits === '' ? undefined : Number(f.capacityUnits) });
  return (
    <>
      <Panel title="Warehouses">
        {act.banner}<Loading state={d} />
        {d.data && <Data rows={d.data} columns={[{ key: 'code', label: 'Code' }, { key: 'name', label: 'Name' }, { key: 'storeId', label: 'Store' }, { key: 'capacityUnits', label: 'Capacity' }, { key: 'managerEmail', label: 'Manager' }]} />}
      </Panel>
      <Panel title="Create warehouse">
        <Stack direction="row" gap={2} flexWrap="wrap">
          {(['code', 'name', 'storeId', 'managerEmail', 'capacityUnits'] as const).map(k => <TextField key={k} size="small" label={k} value={f[k]} onChange={e => setF({ ...f, [k]: e.target.value })} />)}
          <Button variant="contained" disabled={errors.length > 0} onClick={() => act.run(() => opsApi.createWarehouse({ ...f, capacityUnits: f.capacityUnits ? Number(f.capacityUnits) : 0 }), 'Warehouse created.', () => { setF({ code: '', name: '', capacityUnits: '', managerEmail: '', storeId: '' }); d.reload(); })}>Create</Button>
        </Stack>
        {f.code && errors.map(e => <Typography key={e} variant="caption" color="error" display="block">{e}</Typography>)}
      </Panel>
      <Panel title="Add bin / location">
        <Stack direction="row" gap={2} flexWrap="wrap">
          <TextField select size="small" label="Warehouse" value={loc.warehouseId} onChange={e => setLoc({ ...loc, warehouseId: e.target.value })} sx={{ minWidth: 200 }}>
            {(d.data ?? []).map((w: Row) => <MenuItem key={w.id} value={w.id}>{w.code}</MenuItem>)}
          </TextField>
          <TextField size="small" label="Location code" value={loc.code} onChange={e => setLoc({ ...loc, code: e.target.value })} />
          <TextField size="small" label="Capacity" value={loc.capacityUnits} onChange={e => setLoc({ ...loc, capacityUnits: e.target.value })} />
          <Button variant="outlined" disabled={!loc.warehouseId || !loc.code} onClick={() => act.run(() => opsApi.addLocation(Number(loc.warehouseId), { code: loc.code, capacityUnits: Number(loc.capacityUnits || 0) }), 'Location added.')}>Add</Button>
        </Stack>
      </Panel>
    </>
  );
}

function StoreInventory() {
  const wh = useLoad(() => opsApi.warehouses(), []);
  const [warehouseId, setWarehouseId] = useState<number | undefined>();
  const d = useLoad(() => opsApi.warehouseStock(warehouseId), [warehouseId]);
  return (
    <Panel title="Store / warehouse inventory">
      <TextField select size="small" label="Warehouse" value={warehouseId ?? ''} onChange={e => setWarehouseId(e.target.value ? Number(e.target.value) : undefined)} sx={{ minWidth: 220, mb: 2 }}>
        <MenuItem value="">All</MenuItem>{(wh.data ?? []).map((w: Row) => <MenuItem key={w.id} value={w.id}>{w.code} · {w.name}</MenuItem>)}
      </TextField>
      <Loading state={d} />
      {d.data && <Data rows={d.data} columns={[{ key: 'warehouseId', label: 'Warehouse' }, { key: 'productId', label: 'Product' }, { key: 'onHand', label: 'On hand' }, { key: 'reserved', label: 'Reserved' }, { key: 'unavailable', label: 'Unavailable' }, { key: 'inTransitOut', label: 'In transit out' }]} />}
    </Panel>
  );
}

function Transfers({ granted }: { granted: string[] }) {
  const d = useLoad(() => opsApi.transfers(), []);
  const wh = useLoad(() => opsApi.warehouses(), []);
  const act = useAction();
  const [f, setF] = useState({ source: '', destination: '', productId: '', quantity: '' });
  const submit = () => act.run(() => opsApi.createTransfer({ sourceWarehouseId: Number(f.source), destinationWarehouseId: Number(f.destination), lines: [{ productId: Number(f.productId), quantity: Number(f.quantity) }] }), 'Transfer drafted.', () => d.reload());
  return (
    <>
      <Panel title="Stock transfers">
        {act.banner}<Loading state={d} />
        {d.data && <Data rows={d.data} columns={[
          { key: 'transferNo', label: 'Transfer' }, { key: 'status', label: 'Status', render: r => <Status value={r.status} /> },
          { key: 'sourceWarehouseId', label: 'From' }, { key: 'destinationWarehouseId', label: 'To' },
          { key: 'lines', label: 'Lines', render: r => r.lines.map((l: Row) => `#${l.productId}: ${l.quantity} (rcvd ${l.receivedQuantity}, dmg ${l.damagedQuantity})`).join('; ') },
          { key: 'requestedBy', label: 'Requested by' },
          { key: 'actions', label: 'Actions', render: r => <Stack direction="row" gap={0.5} flexWrap="wrap">{transferActions(r.status, granted).map(a =>
            <Button key={a.action} size="small" variant="outlined" onClick={() => act.run(() => opsApi.transferAction(r.id, a.action), `${a.label} done.`, () => d.reload())}>{a.label}</Button>)}</Stack> },
        ]} />}
      </Panel>
      <Panel title="New transfer (source stock is reserved on approval; destination changes only when received)">
        <Stack direction="row" gap={2} flexWrap="wrap">
          {(['source', 'destination'] as const).map(k => <TextField key={k} select size="small" label={k === 'source' ? 'From warehouse' : 'To warehouse'} value={f[k]} onChange={e => setF({ ...f, [k]: e.target.value })} sx={{ minWidth: 180 }}>
            {(wh.data ?? []).map((w: Row) => <MenuItem key={w.id} value={w.id}>{w.code}</MenuItem>)}</TextField>)}
          <TextField size="small" label="Product ID" value={f.productId} onChange={e => setF({ ...f, productId: e.target.value })} />
          <TextField size="small" label="Quantity" value={f.quantity} onChange={e => setF({ ...f, quantity: e.target.value })} />
          <Button variant="contained" disabled={!f.source || !f.destination || f.source === f.destination || !f.productId || !(Number(f.quantity) > 0)} onClick={submit}>Create draft</Button>
        </Stack>
      </Panel>
    </>
  );
}

function Adjustments({ granted, title = 'Stock adjustments', countMode = false }: { granted: string[]; title?: string; countMode?: boolean }) {
  const d = useLoad(() => opsApi.adjustments(), []);
  const wh = useLoad(() => opsApi.warehouses(), []);
  const cfg = useLoad(() => opsApi.config(), []);
  const act = useAction();
  const [f, setF] = useState({ warehouseId: '', productId: '', newQuantity: '', reason: countMode ? 'Cycle count' : '', evidence: '' });
  const errors = validateAdjustment({ warehouseId: f.warehouseId ? Number(f.warehouseId) : undefined, productId: f.productId ? Number(f.productId) : undefined, newQuantity: f.newQuantity === '' ? undefined : Number(f.newQuantity), reason: f.reason });
  const canApprove = granted.includes('*') || granted.includes('inventory:adjust-approve');
  return (
    <>
      <Panel title={title}>
        {act.banner}<Loading state={d} />
        {d.data && <Data rows={d.data} columns={[
          { key: 'id', label: '#' }, { key: 'productId', label: 'Product' }, { key: 'warehouseId', label: 'Warehouse' }, { key: 'beforeQuantity', label: 'Before' }, { key: 'afterQuantity', label: 'After' },
          { key: 'delta', label: 'Change' }, { key: 'reason', label: 'Reason' }, { key: 'status', label: 'Status', render: r => <Status value={r.status} /> }, { key: 'requestedBy', label: 'By' },
          { key: 'actions', label: '', render: r => r.status === 'PENDING' && canApprove ? <Stack direction="row" gap={0.5}>
            <Button size="small" onClick={() => act.run(() => opsApi.decideAdjustment(r.id, true), 'Adjustment approved.', d.reload)}>Approve</Button>
            <Button size="small" color="error" onClick={() => act.run(() => opsApi.decideAdjustment(r.id, false), 'Adjustment rejected.', d.reload)}>Reject</Button></Stack> : null },
        ]} />}
      </Panel>
      <Panel title={countMode ? 'Record a count (variance becomes an audited adjustment)' : 'Request adjustment (before and after are always recorded)'}>
        <Stack direction="row" gap={2} flexWrap="wrap">
          <TextField select size="small" label="Warehouse" value={f.warehouseId} onChange={e => setF({ ...f, warehouseId: e.target.value })} sx={{ minWidth: 180 }}>{(wh.data ?? []).map((w: Row) => <MenuItem key={w.id} value={w.id}>{w.code}</MenuItem>)}</TextField>
          <TextField size="small" label="Product ID" value={f.productId} onChange={e => setF({ ...f, productId: e.target.value })} />
          <TextField size="small" label={countMode ? 'Counted quantity' : 'New on-hand quantity'} value={f.newQuantity} onChange={e => setF({ ...f, newQuantity: e.target.value })} />
          <TextField size="small" label="Reason" value={f.reason} onChange={e => setF({ ...f, reason: e.target.value })} />
          <TextField size="small" label="Evidence (reference)" value={f.evidence} onChange={e => setF({ ...f, evidence: e.target.value })} />
          <Button variant="contained" disabled={errors.length > 0} onClick={() => act.run(() => opsApi.requestAdjustment({ warehouseId: Number(f.warehouseId), productId: Number(f.productId), newQuantity: Number(f.newQuantity), reason: f.reason, evidence: f.evidence, idempotencyKey: `ui-${f.warehouseId}-${f.productId}-${f.newQuantity}-${f.reason}` }), 'Submitted.', () => d.reload())}>Submit</Button>
        </Stack>
        {cfg.data && <Typography variant="caption" color="text.secondary">Changes larger than {cfg.data.adjustmentApprovalThreshold} units wait for a different approver{f.newQuantity !== '' ? ` (${adjustmentNeedsApproval(1, cfg.data.adjustmentApprovalThreshold) ? '' : 'threshold applies to the computed change'})` : ''}.</Typography>}
      </Panel>
    </>
  );
}

function Reservations() {
  const d = useLoad(() => opsApi.reservations(), []);
  const r = useLoad(() => opsApi.reconciliation(), []);
  return (
    <>
      <Panel title="Active reservations"><Loading state={d} />
        {d.data && <Data rows={d.data} columns={[{ key: 'reservationId', label: 'Reservation' }, { key: 'productId', label: 'Product' }, { key: 'quantity', label: 'Qty' }, { key: 'backorderQuantity', label: 'Backorder' }, { key: 'orderId', label: 'Order' }, { key: 'status', label: 'Status', render: x => <Status value={x.status} /> }, { key: 'expiresAt', label: 'Expires' }]} />}
      </Panel>
      <Panel title="Reconciliation (reserved quantity vs active holds and transfer reservations)"><Loading state={r} />
        {r.data && (r.data.length === 0 ? <Alert severity="success">No drift detected.</Alert> : <Data rows={r.data} columns={[{ key: 'productId', label: 'Product' }, { key: 'itemReserved', label: 'Item reserved' }, { key: 'activeHolds', label: 'Active holds' }, { key: 'transferReserved', label: 'Transfer reserved' }, { key: 'drift', label: 'Drift' }]} />)}
      </Panel>
    </>
  );
}

function Movements() {
  const d = useLoad(() => opsApi.movements(), []);
  const [type, setType] = useState('');
  const rows = (d.data ?? []).filter((m: Row) => !type || m.movementType === type);
  const types = Array.from(new Set((d.data ?? []).map((m: Row) => m.movementType)));
  return (
    <Panel title="Stock movement history (latest 50)">
      <TextField select size="small" label="Type" value={type} onChange={e => setType(e.target.value)} sx={{ minWidth: 200, mb: 2 }}><MenuItem value="">All</MenuItem>{types.map(t => <MenuItem key={String(t)} value={String(t)}>{String(t)}</MenuItem>)}</TextField>
      <Loading state={d} /><Data rows={rows} columns={[{ key: 'createdAt', label: 'When' }, { key: 'productId', label: 'Product' }, { key: 'delta', label: 'Change' }, { key: 'movementType', label: 'Type' }, { key: 'reason', label: 'Reason' }, { key: 'performedBy', label: 'By' }]} />
    </Panel>
  );
}

function Forecasting() {
  const d = useLoad(() => opsApi.forecast(), []);
  return (
    <Panel title="Inventory forecasting (rule-based, from real movement history only)"><Loading state={d} />
      {d.data && <Data rows={d.data} columns={[{ key: 'productId', label: 'Product' }, { key: 'sku', label: 'SKU' }, { key: 'available', label: 'Available' }, { key: 'avgDailyDemand', label: 'Avg daily demand' }, { key: 'coverageDays', label: 'Coverage days' },
        { key: 'reorderPoint', label: 'Reorder point' }, { key: 'classification', label: 'Class' }, { key: 'stockoutRisk', label: 'Stockout risk', render: r => r.stockoutRisk ? <Chip size="small" color="error" label="AT RISK" /> : 'No' }, { key: 'suggestedOrderQuantity', label: 'Suggested qty' }]} />}
      <Typography variant="caption" color="text.secondary">Products with no sales in the window are classified DEAD_STOCK; no demand is invented. Convert suggestions to draft purchase orders in Purchase Orders.</Typography>
    </Panel>
  );
}

function Valuation() {
  const d = useLoad(() => opsApi.valuation(), []);
  return (
    <Panel title="Inventory valuation (FIFO layers vs weighted average)"><Loading state={d} />
      {d.data && <>
        <Stack direction="row" gap={3} sx={{ mb: 1 }}><Typography>FIFO total: <b>{d.data.fifoTotal}</b></Typography><Typography>Weighted average total: <b>{d.data.weightedAverageTotal}</b></Typography></Stack>
        <Data rows={d.data.rows} columns={[{ key: 'productId', label: 'Product' }, { key: 'onHand', label: 'On hand' }, { key: 'costedUnits', label: 'Costed units' }, { key: 'uncostedUnits', label: 'Uncosted units' }, { key: 'fifoValue', label: 'FIFO value' }, { key: 'weightedAverageValue', label: 'Weighted avg value' }]} />
        <Typography variant="caption" color="text.secondary">{d.data.note}</Typography></>}
    </Panel>
  );
}

function Audits() {
  const d = useLoad(() => opsApi.audit(), []);
  return <Panel title="Stock audit trail"><Loading state={d} />{d.data && <Data rows={d.data} columns={[{ key: 'createdAt', label: 'When' }, { key: 'entity', label: 'Entity' }, { key: 'entityId', label: 'Id' }, { key: 'action', label: 'Action' }, { key: 'actor', label: 'Actor' }, { key: 'details', label: 'Details' }]} />}</Panel>;
}

function Configuration({ granted }: { granted: string[] }) {
  const d = useLoad(() => opsApi.config(), []);
  const act = useAction();
  const [v, setV] = useState<Row>({});
  useEffect(() => { if (d.data) setV(d.data); }, [d.data]);
  const can = granted.includes('*') || granted.includes('inventory:config');
  const keys = ['adjustmentApprovalThreshold', 'reservationHoldMinutes', 'defaultLeadTimeDays', 'defaultSafetyStock', 'overReceiptTolerancePercent'];
  return (
    <Panel title="Inventory configuration">{act.banner}<Loading state={d} />
      <Stack direction="row" gap={2} flexWrap="wrap">
        {keys.map(k => <TextField key={k} size="small" label={k} value={v[k] ?? ''} disabled={!can} onChange={e => setV({ ...v, [k]: e.target.value })} />)}
        <TextField select size="small" label="valuationMethod" value={v.valuationMethod ?? 'FIFO'} disabled={!can} onChange={e => setV({ ...v, valuationMethod: e.target.value })} sx={{ minWidth: 200 }}><MenuItem value="FIFO">FIFO</MenuItem><MenuItem value="WEIGHTED_AVERAGE">Weighted average</MenuItem></TextField>
        <Button variant="contained" disabled={!can} onClick={() => act.run(() => opsApi.saveConfig(Object.fromEntries(keys.map(k => [k, Number(v[k])]).concat([['valuationMethod', v.valuationMethod]]))), 'Configuration saved.', d.reload)}>Save</Button>
      </Stack>
      {!can && <Typography variant="caption" color="text.secondary">You need inventory:config to change these values.</Typography>}
    </Panel>
  );
}

function Suppliers({ granted }: { granted: string[] }) {
  const d = useLoad(() => procApi.suppliers(), []);
  const act = useAction();
  const [f, setF] = useState({ code: '', name: '', email: '', paymentTermsDays: '30' });
  const [item, setItem] = useState({ supplierId: '', productId: '', unitCost: '', leadTimeDays: '7' });
  const [perf, setPerf] = useState<Row | null>(null);
  const manage = granted.includes('*') || granted.includes('supplier:manage');
  return (
    <>
      <Panel title="Suppliers">{act.banner}<Loading state={d} />
        {d.data && <Data rows={d.data} columns={[{ key: 'code', label: 'Code' }, { key: 'name', label: 'Name' }, { key: 'email', label: 'Email' }, { key: 'payment_terms_days', label: 'Terms (days)' }, { key: 'status', label: 'Status', render: r => <Status value={r.status} /> },
          { key: 'actions', label: '', render: r => <Stack direction="row" gap={0.5}>
            {manage && r.status === 'PENDING' && <Button size="small" onClick={() => act.run(() => procApi.supplierAction(r.id, 'approve'), 'Supplier approved.', d.reload)}>Approve</Button>}
            {manage && r.status === 'APPROVED' && <Button size="small" color="warning" onClick={() => act.run(() => procApi.supplierAction(r.id, 'suspend'), 'Supplier suspended.', d.reload)}>Suspend</Button>}
            <Button size="small" onClick={() => procApi.performance(r.id).then(setPerf).catch(e => act.run(() => Promise.reject(e), ''))}>Performance</Button></Stack> }]} />}
        {perf && <Alert severity="info" sx={{ mt: 1 }} onClose={() => setPerf(null)}>POs {perf.purchaseOrders} · completed {perf.completed} · on-time {perf.onTimeRate ?? 'n/a'}% · quality {perf.qualityRate ?? 'n/a'}%</Alert>}
      </Panel>
      {manage && <Panel title="Onboard supplier (starts PENDING until approved)">
        <Stack direction="row" gap={2} flexWrap="wrap">
          {(['code', 'name', 'email', 'paymentTermsDays'] as const).map(k => <TextField key={k} size="small" label={k} value={f[k]} onChange={e => setF({ ...f, [k]: e.target.value })} />)}
          <Button variant="contained" disabled={!f.code || !f.name} onClick={() => act.run(() => procApi.createSupplier({ ...f, paymentTermsDays: Number(f.paymentTermsDays) }), 'Supplier created.', () => { setF({ code: '', name: '', email: '', paymentTermsDays: '30' }); d.reload(); })}>Create</Button>
        </Stack>
      </Panel>}
      {manage && <Panel title="Supplier catalog price">
        <Stack direction="row" gap={2} flexWrap="wrap">
          <TextField select size="small" label="Supplier" value={item.supplierId} onChange={e => setItem({ ...item, supplierId: e.target.value })} sx={{ minWidth: 200 }}>{(d.data ?? []).map((s: Row) => <MenuItem key={s.id} value={s.id}>{s.code}</MenuItem>)}</TextField>
          {(['productId', 'unitCost', 'leadTimeDays'] as const).map(k => <TextField key={k} size="small" label={k} value={item[k]} onChange={e => setItem({ ...item, [k]: e.target.value })} />)}
          <Button variant="outlined" disabled={!item.supplierId || !item.productId || item.unitCost === ''} onClick={() => act.run(() => procApi.upsertItem(Number(item.supplierId), { productId: Number(item.productId), unitCost: Number(item.unitCost), leadTimeDays: Number(item.leadTimeDays) }), 'Catalog price saved.')}>Save price</Button>
        </Stack>
      </Panel>}
    </>
  );
}

function PurchaseOrders({ granted }: { granted: string[] }) {
  const [status, setStatus] = useState('');
  const d = useLoad(() => procApi.pos({ status }), [status]);
  const sup = useLoad(() => procApi.suppliers({ status: 'APPROVED' }), []);
  const wh = useLoad(() => opsApi.warehouses(), []);
  const rep = useLoad(() => procApi.replenishment(), []);
  const act = useAction();
  const [f, setF] = useState({ supplierId: '', warehouseId: '', discount: '0', taxRatePercent: '0', shipping: '0', expectedDate: '', productId: '', quantity: '', unitCost: '' });
  const line = { productId: f.productId ? Number(f.productId) : undefined, quantity: f.quantity ? Number(f.quantity) : undefined, unitCost: f.unitCost === '' ? undefined : Number(f.unitCost) };
  const errors = validatePoForm({ supplierId: f.supplierId ? Number(f.supplierId) : undefined, warehouseId: f.warehouseId ? Number(f.warehouseId) : undefined, lines: [line] });
  let preview = '';
  try { if (line.quantity && line.unitCost !== undefined) { const t = poTotals([{ quantity: line.quantity, unitCost: line.unitCost }], Number(f.discount), Number(f.taxRatePercent), Number(f.shipping)); preview = `Subtotal ${t.subtotal} · tax ${t.tax} · total ${t.total}`; } } catch (e) { preview = (e as Error).message; }
  const download = async (id: number) => { const html = await procApi.fetchDocument(id); const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([html], { type: 'text/html' })); a.download = `purchase-order-${id}.html`; a.click(); };
  return (
    <>
      <Panel title="Purchase orders" actions={<TextField select size="small" label="Status" value={status} onChange={e => setStatus(e.target.value)} sx={{ minWidth: 180 }}>
        {['', 'DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'ISSUED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED', 'OVERDUE'].map(s => <MenuItem key={s} value={s}>{s || 'All'}</MenuItem>)}</TextField>}>
        {act.banner}<Loading state={d} />
        {d.data && <Data rows={d.data} columns={[{ key: 'po_number', label: 'PO' }, { key: 'status', label: 'Status', render: r => <Stack direction="row" gap={0.5}><Status value={r.status} />{r.overdue && <Chip size="small" color="error" label="OVERDUE" />}</Stack> },
          { key: 'supplier_id', label: 'Supplier' }, { key: 'total', label: 'Total' }, { key: 'expected_date', label: 'Expected' }, { key: 'created_by', label: 'Created by' },
          { key: 'actions', label: 'Actions', render: r => <Stack direction="row" gap={0.5} flexWrap="wrap">{poActions(r.status, granted).map(a => <Button key={a.action} size="small" variant="outlined"
            onClick={() => act.run(() => procApi.poAction(r.id, a.action, a.action === 'reject' ? { reason: window.prompt('Rejection reason') ?? '' } : undefined), `${a.label} done.`, d.reload)}>{a.label}</Button>)}
            <Button size="small" onClick={() => download(r.id)}>PO document</Button></Stack> }]} />}
      </Panel>
      <Panel title="Create purchase order (always starts as a draft)">
        <Stack direction="row" gap={2} flexWrap="wrap">
          <TextField select size="small" label="Approved supplier" value={f.supplierId} onChange={e => setF({ ...f, supplierId: e.target.value })} sx={{ minWidth: 200 }}>{(sup.data ?? []).map((s: Row) => <MenuItem key={s.id} value={s.id}>{s.code} · {s.name}</MenuItem>)}</TextField>
          <TextField select size="small" label="Receiving warehouse" value={f.warehouseId} onChange={e => setF({ ...f, warehouseId: e.target.value })} sx={{ minWidth: 200 }}>{(wh.data ?? []).map((w: Row) => <MenuItem key={w.id} value={w.id}>{w.code}</MenuItem>)}</TextField>
          {(['productId', 'quantity', 'unitCost', 'discount', 'taxRatePercent', 'shipping', 'expectedDate'] as const).map(k => <TextField key={k} size="small" label={k === 'unitCost' ? 'unitCost (blank = catalog)' : k} value={f[k]} onChange={e => setF({ ...f, [k]: e.target.value })} />)}
          <Button variant="contained" disabled={errors.length > 0} onClick={() => act.run(() => procApi.createPo({ supplierId: Number(f.supplierId), warehouseId: Number(f.warehouseId), discount: Number(f.discount), taxRatePercent: Number(f.taxRatePercent), shipping: Number(f.shipping), expectedDate: f.expectedDate || undefined, lines: [{ productId: Number(f.productId), quantity: Number(f.quantity), unitCost: f.unitCost === '' ? undefined : Number(f.unitCost) }] }), 'Draft purchase order created.', d.reload)}>Create draft</Button>
        </Stack>
        {preview && <Typography variant="body2" sx={{ mt: 1 }}>{preview}</Typography>}
        {errors.length > 0 && f.supplierId && errors.map(e => <Typography key={e} variant="caption" color="error" display="block">{e}</Typography>)}
      </Panel>
      <Panel title="Replenishment suggestions (never auto-submitted)"><Loading state={rep} />
        {rep.data && <Data empty="Nothing needs reordering." rows={rep.data} columns={[{ key: 'productId', label: 'Product' }, { key: 'available', label: 'Available' }, { key: 'reorderPoint', label: 'Reorder point' }, { key: 'suggestedOrderQuantity', label: 'Suggested qty' },
          { key: 'sup', label: 'Cheapest approved supplier', render: r => r.suggestedSupplier ? `#${r.suggestedSupplier.supplier_id} @ ${r.suggestedSupplier.unit_cost}` : 'none in catalog' },
          { key: 'a', label: '', render: r => r.suggestedSupplier && wh.data?.[0] ? <Button size="small" onClick={() => act.run(() => procApi.draftPo({ supplierId: r.suggestedSupplier.supplier_id, warehouseId: wh.data[0].id, lines: [{ productId: r.productId, quantity: r.suggestedOrderQuantity }] }), 'Draft created from suggestion.', d.reload)}>Create draft PO</Button> : null }]} />}
      </Panel>
    </>
  );
}

function Receiving() {
  const pos = useLoad(() => procApi.pos(), []);
  const receipts = useLoad(() => procApi.receipts(), []);
  const [poId, setPoId] = useState('');
  const po = useLoad(() => poId ? procApi.po(Number(poId)) : Promise.resolve(null), [poId]);
  const [qty, setQty] = useState<Record<number, { received: string; rejected: string; damaged: string; batchNo: string; expiryDate: string }>>({});
  const [seed] = useState(() => Math.random().toString(36).slice(2, 8));
  const [attempt, setAttempt] = useState(0);
  const act = useAction();
  const open = (pos.data ?? []).filter((p: Row) => ['ISSUED', 'PARTIALLY_RECEIVED'].includes(p.status));
  const inputs: ReceiptLineInput[] = (po.data?.lines ?? []).map((l: Row) => ({ poLineId: l.id, received: Number(qty[l.id]?.received || 0), rejected: Number(qty[l.id]?.rejected || 0), damaged: Number(qty[l.id]?.damaged || 0) }));
  const errors = validateReceipt(inputs);
  const set = (id: number, k: string, v: string) => setQty({ ...qty, [id]: Object.assign({ received: '', rejected: '', damaged: '', batchNo: '', expiryDate: '' }, qty[id], { [k]: v }) });
  return (
    <>
      <Panel title="Receive goods (only accepted quantity increases stock)">
        {act.banner}
        <TextField select size="small" label="Open purchase order" value={poId} onChange={e => { setPoId(e.target.value); setQty({}); }} sx={{ minWidth: 260, mb: 2 }}>{open.map((p: Row) => <MenuItem key={p.id} value={p.id}>{p.po_number} · {p.status}</MenuItem>)}</TextField>
        {po.data && <>
          <Table size="small"><TableHead><TableRow>{['Line', 'Product', 'Ordered', 'Accepted so far', 'Received', 'Rejected', 'Damaged', 'Batch/lot', 'Expiry'].map(h => <TableCell key={h} sx={{ fontWeight: 700 }}>{h}</TableCell>)}</TableRow></TableHead>
            <TableBody>{po.data.lines.map((l: Row) => <TableRow key={l.id}><TableCell>{l.id}</TableCell><TableCell>{l.product_id}</TableCell><TableCell>{l.quantity}</TableCell><TableCell>{l.accepted_quantity}</TableCell>
              {(['received', 'rejected', 'damaged', 'batchNo', 'expiryDate'] as const).map(k => <TableCell key={k}><TextField size="small" sx={{ width: k === 'batchNo' || k === 'expiryDate' ? 130 : 80 }} placeholder={k === 'expiryDate' ? 'YYYY-MM-DD' : ''} value={qty[l.id]?.[k] ?? ''} onChange={e => set(l.id, k, e.target.value)} /></TableCell>)}</TableRow>)}</TableBody></Table>
          {inputs.some(i => i.received > 0) && errors.map(e => <Typography key={e} variant="caption" color="error" display="block">{e}</Typography>)}
          <Button sx={{ mt: 1 }} variant="contained" disabled={errors.length > 0} onClick={() => act.run(() => procApi.receive(Number(poId), { lines: po.data.lines.filter((l: Row) => Number(qty[l.id]?.received || 0) > 0).map((l: Row) => ({ poLineId: l.id, received: Number(qty[l.id].received), rejected: Number(qty[l.id].rejected || 0), damaged: Number(qty[l.id].damaged || 0), batchNo: qty[l.id].batchNo || undefined, expiryDate: qty[l.id].expiryDate || undefined })) }, newIdempotencyKey(Number(poId), `${seed}-${attempt}`)), 'Goods receipt recorded.', () => { setAttempt(a => a + 1); setQty({}); po.reload(); pos.reload(); receipts.reload(); })}>Record receipt</Button>
        </>}
      </Panel>
      <Panel title="Receipts"><Loading state={receipts} />{receipts.data && <Data rows={receipts.data} columns={[{ key: 'receipt_no', label: 'Receipt' }, { key: 'po_id', label: 'PO' }, { key: 'received_by', label: 'By' }, { key: 'created_at', label: 'When' }]} />}</Panel>
    </>
  );
}

function SupplierReturns() {
  const d = useLoad(() => procApi.returns(), []);
  const sup = useLoad(() => procApi.suppliers({ status: 'APPROVED' }), []);
  const wh = useLoad(() => opsApi.warehouses(), []);
  const act = useAction();
  const [f, setF] = useState({ supplierId: '', warehouseId: '', productId: '', quantity: '', reason: '' });
  return (
    <>
      <Panel title="Supplier returns (stock leaves at FIFO cost; credit note is posted through the Accounting API)">{act.banner}<Loading state={d} />
        {d.data && <Data rows={d.data} columns={[{ key: 'return_no', label: 'Return' }, { key: 'status', label: 'Status', render: r => <Status value={r.status} /> }, { key: 'reason', label: 'Reason' }, { key: 'total_credit', label: 'Credit value' }, { key: 'accounting_entry', label: 'Accounting entry' },
          { key: 'a', label: '', render: r => <Stack direction="row" gap={0.5}>{r.status === 'DRAFT' && <Button size="small" onClick={() => act.run(() => procApi.shipReturn(r.id), 'Return shipped; stock removed.', d.reload)}>Ship</Button>}{r.status === 'SHIPPED' && <Button size="small" onClick={() => act.run(() => procApi.creditReturn(r.id), 'Credit note posted.', d.reload)}>Post credit</Button>}</Stack> }]} />}
      </Panel>
      <Panel title="New supplier return">
        <Stack direction="row" gap={2} flexWrap="wrap">
          <TextField select size="small" label="Supplier" value={f.supplierId} onChange={e => setF({ ...f, supplierId: e.target.value })} sx={{ minWidth: 200 }}>{(sup.data ?? []).map((s: Row) => <MenuItem key={s.id} value={s.id}>{s.code}</MenuItem>)}</TextField>
          <TextField select size="small" label="From warehouse" value={f.warehouseId} onChange={e => setF({ ...f, warehouseId: e.target.value })} sx={{ minWidth: 200 }}>{(wh.data ?? []).map((w: Row) => <MenuItem key={w.id} value={w.id}>{w.code}</MenuItem>)}</TextField>
          {(['productId', 'quantity', 'reason'] as const).map(k => <TextField key={k} size="small" label={k} value={f[k]} onChange={e => setF({ ...f, [k]: e.target.value })} />)}
          <Button variant="contained" disabled={!f.supplierId || !f.warehouseId || !f.productId || !(Number(f.quantity) > 0) || !f.reason} onClick={() => act.run(() => procApi.createReturn({ supplierId: Number(f.supplierId), warehouseId: Number(f.warehouseId), reason: f.reason, lines: [{ productId: Number(f.productId), quantity: Number(f.quantity) }] }), 'Return drafted.', d.reload)}>Create draft</Button>
        </Stack>
      </Panel>
    </>
  );
}

function ProcurementAnalytics() {
  const d = useLoad(() => procApi.analytics(), []);
  return (
    <>
      <Loading state={d} />
      {d.data && <>
        <Panel title="Summary"><Typography>Overdue purchase orders: <b>{d.data.overduePurchaseOrders}</b> · Open PO value: <b>{String(d.data.openPurchaseOrderValue)}</b></Typography></Panel>
        <Panel title="Purchase orders by status"><Data rows={d.data.poByStatus} columns={[{ key: 'status', label: 'Status' }, { key: 'count', label: 'Count' }, { key: 'value', label: 'Value' }]} /></Panel>
        <Panel title="Spend by supplier (issued and received POs)"><Data rows={d.data.spendBySupplier} columns={[{ key: 'supplier', label: 'Supplier' }, { key: 'value', label: 'Value' }]} /></Panel></>}
    </>
  );
}

// ---------------- shell ----------------
export default function OpsWorkspace() {
  const granted = useMemo(() => tokenPermissions(currentToken()), []);
  const screens = useMemo(() => visibleScreens(granted), [granted]);
  const [active, setActive] = useState<ScreenKey>(() => (screens[0]?.key ?? 'dashboard'));
  const render = useCallback(() => {
    switch (active) {
      case 'dashboard': return <Dashboard />;
      case 'inventory': return <Items title="All inventory" />;
      case 'product-stock': return <Items title="Product stock lookup" byProduct />;
      case 'warehouses': return <Warehouses />;
      case 'store-inventory': return <StoreInventory />;
      case 'transfers': return <Transfers granted={granted} />;
      case 'adjustments': return <Adjustments granted={granted} />;
      case 'reservations': return <Reservations />;
      case 'movements': return <Movements />;
      case 'low-stock': return <Items title="Low stock alerts" fixedStatus="LOW_STOCK" />;
      case 'out-of-stock': return <Items title="Out of stock products" fixedStatus="OUT_OF_STOCK" />;
      case 'forecasting': return <Forecasting />;
      case 'suppliers': return <Suppliers granted={granted} />;
      case 'purchase-orders': return <PurchaseOrders granted={granted} />;
      case 'receiving': return <Receiving />;
      case 'supplier-returns': return <SupplierReturns />;
      case 'audits': return <Audits />;
      case 'cycle-counting': return <Adjustments granted={granted} title="Cycle counts (recorded as adjustments)" countMode />;
      case 'valuation': return <Valuation />;
      case 'procurement-analytics': return <ProcurementAnalytics />;
      case 'configuration': return <Configuration granted={granted} />;
    }
  }, [active, granted]);
  if (screens.length === 0) return <Alert severity="warning">Your role has no Inventory &amp; Procurement permissions. Ask an administrator to assign one.</Alert>;
  const groups = ['Overview', 'Inventory', 'Procurement', 'Control'] as const;
  return (
    <Grid container spacing={2}>
      <Grid size={{ xs: 12, md: 3 }}>
        <Paper variant="outlined" sx={{ borderRadius: 2 }} component="nav" aria-label="Inventory and Procurement">
          <List dense subheader={<ListSubheader sx={{ fontWeight: 800 }}>Inventory &amp; Procurement</ListSubheader>}>
            {groups.map(g => {
              const items = screens.filter(s => s.group === g);
              return items.length ? <React.Fragment key={g}><ListSubheader sx={{ lineHeight: '28px', fontSize: 12 }}>{g}</ListSubheader>
                {items.map(s => <ListItemButton key={s.key} selected={s.key === active} onClick={() => setActive(s.key)}><ListItemText primary={s.label} /></ListItemButton>)}</React.Fragment> : null;
            })}
          </List>
        </Paper>
      </Grid>
      <Grid size={{ xs: 12, md: 9 }}>{render()}</Grid>
    </Grid>
  );
}
export { SCREENS };
