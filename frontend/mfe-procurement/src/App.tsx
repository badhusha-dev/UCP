import React, { useEffect, useMemo, useState } from 'react';
import axios from 'axios';
import {
  Alert, Box, Button, Card, CardContent, Checkbox, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle,
  Divider, FormControl, FormControlLabel, Grid, IconButton, InputLabel, LinearProgress, MenuItem, Paper, Select, Stack, Tab, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, Tabs, TextField, Tooltip, Typography,
} from '@mui/material';
import {
  AddRounded, ArchiveRounded, CheckCircleRounded, EditRounded, Inventory2Rounded, LocalShippingRounded,
  ReceiptLongRounded, RefreshRounded, RestoreRounded, StorefrontRounded, WarningAmberRounded,
} from '@mui/icons-material';

type Row = Record<string, any>;
const api = axios.create({ baseURL: '/api/procurement' }); // Same origin: nginx (containers) and the Vite dev proxy forward /api to the gateway.
api.interceptors.request.use((config) => {
  const token = typeof window !== 'undefined' ? localStorage.getItem('jwt_token') || localStorage.getItem('token') : null;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
const failureText = (error: any) => String(error?.response?.data?.message || error?.response?.data || error?.message || 'Unable to complete the request.');
const money = (value: unknown, currency = 'USD') => new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(Number(value || 0));
const day = (value: unknown) => value ? new Date(String(value)).toLocaleDateString() : '—';
const inputSx = { '& .MuiOutlinedInput-root': { borderRadius: 2 } };

function Metric({ title, value, helper, icon, color = '#60A5FA' }: { title: string; value: React.ReactNode; helper: string; icon: React.ReactNode; color?: string }) {
  return <Card variant="outlined" sx={{ height: '100%', borderColor: '#26334B', bgcolor: '#121B2D' }}><CardContent>
    <Stack direction="row" justifyContent="space-between"><Box><Typography variant="body2" color="text.secondary">{title}</Typography><Typography variant="h4" sx={{ mt: 1, color, fontWeight: 850 }}>{value}</Typography></Box><Box sx={{ p: 1.2, color, bgcolor: `${color}20`, borderRadius: 2, height: 'fit-content' }}>{icon}</Box></Stack>
    <Typography variant="caption" color="text.secondary">{helper}</Typography>
  </CardContent></Card>;
}

function DataTable({ columns, rows, empty }: { columns: { key: string; label: string; render?: (row: Row) => React.ReactNode }[]; rows: Row[]; empty: string }) {
  return <TableContainer component={Paper} variant="outlined" sx={{ borderColor: '#26334B', bgcolor: '#121B2D' }}><Table size="small"><TableHead><TableRow>{columns.map(column => <TableCell key={column.key} sx={{ color: 'text.secondary', fontWeight: 800, whiteSpace: 'nowrap' }}>{column.label}</TableCell>)}</TableRow></TableHead><TableBody>
    {rows.map((row, index) => <TableRow hover key={row.id ?? index}>{columns.map(column => <TableCell key={column.key}>{column.render ? column.render(row) : String(row[column.key] ?? '—')}</TableCell>)}</TableRow>)}
    {!rows.length && <TableRow><TableCell colSpan={columns.length} align="center"><Typography color="text.secondary" sx={{ py: 3 }}>{empty}</Typography></TableCell></TableRow>}
  </TableBody></Table></TableContainer>;
}

export default function App() {
  const [tab, setTab] = useState('overview');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [dashboard, setDashboard] = useState<Row>({});
  const [suppliers, setSuppliers] = useState<Row[]>([]);
  const [orders, setOrders] = useState<Row[]>([]);
  const [invoices, setInvoices] = useState<Row[]>([]);
  const [suggestions, setSuggestions] = useState<Row[]>([]);

  const [supplierDialog, setSupplierDialog] = useState(false);
  const [supplierEdit, setSupplierEdit] = useState<Row | null>(null);
  const [supplierForm, setSupplierForm] = useState<Row>({ supplierCode: '', name: '', contactName: '', email: '', phone: '', paymentTermsDays: '30', currency: 'USD', address: '', notes: '' });
  const [catalogSupplier, setCatalogSupplier] = useState<Row | null>(null);
  const [catalogItems, setCatalogItems] = useState<Row[]>([]);
  const [catalogEdit, setCatalogEdit] = useState<Row | null>(null);
  const [catalogForm, setCatalogForm] = useState<Row>({ productId: '', supplierSku: '', unitCost: '', leadTimeDays: '7', minimumOrderQuantity: '1', preferred: false });

  const [poDialog, setPoDialog] = useState(false);
  const [poEdit, setPoEdit] = useState<Row | null>(null);
  const [poSupplierId, setPoSupplierId] = useState('');
  const [poExpectedDate, setPoExpectedDate] = useState('');
  const [poNote, setPoNote] = useState('');
  const [poCatalog, setPoCatalog] = useState<Row[]>([]);
  const [poLines, setPoLines] = useState<Row[]>([{ supplierProductId: '', quantity: '1' }]);
  const [receiveOrder, setReceiveOrder] = useState<Row | null>(null);
  const [receiptNumber, setReceiptNumber] = useState('');
  const [receiptLines, setReceiptLines] = useState<Row[]>([]);
  const [invoiceOrder, setInvoiceOrder] = useState<Row | null>(null);
  const [invoiceForm, setInvoiceForm] = useState<Row>({ invoiceNumber: '', issueDate: new Date().toISOString().slice(0, 10), dueDate: '', taxAmount: '0' });
  const [invoiceLines, setInvoiceLines] = useState<Row[]>([]);

  const refresh = async () => {
    setLoading(true); setError('');
    try {
      const [dash, vendorRows, poRows, invoiceRows, stockRows] = await Promise.all([
        api.get('/dashboard'), api.get('/suppliers'), api.get('/purchase-orders'), api.get('/supplier-invoices'), api.get('/replenishment-suggestions'),
      ]);
      setDashboard(dash.data); setSuppliers(vendorRows.data); setOrders(poRows.data); setInvoices(invoiceRows.data); setSuggestions(stockRows.data);
    } catch (e) { setError(failureText(e)); }
    finally { setLoading(false); }
  };
  useEffect(() => { void refresh(); }, []);

  const activeSuppliers = useMemo(() => suppliers.filter(row => !row.archived), [suppliers]);
  const run = async (work: () => Promise<unknown>, message: string) => {
    setError(''); setNotice('');
    try { await work(); setNotice(message); await refresh(); }
    catch (e) { setError(failureText(e)); }
  };

  const openSupplier = (row?: Row) => {
    setSupplierEdit(row || null);
    setSupplierForm(row ? { ...row, paymentTermsDays: String(row.paymentTermsDays ?? 30) } : { supplierCode: '', name: '', contactName: '', email: '', phone: '', paymentTermsDays: '30', currency: 'USD', address: '', notes: '' });
    setSupplierDialog(true);
  };
  const saveSupplier = async () => {
    const body = { ...supplierForm, paymentTermsDays: Number(supplierForm.paymentTermsDays) };
    await run(async () => { if (supplierEdit) await api.put(`/suppliers/${supplierEdit.id}`, body); else await api.post('/suppliers', body); setSupplierDialog(false); }, supplierEdit ? 'Supplier details updated.' : 'Supplier added.');
  };

  const openCatalog = async (supplier: Row) => {
    setCatalogSupplier(supplier); setCatalogEdit(null); setCatalogForm({ productId: '', supplierSku: '', unitCost: '', leadTimeDays: '7', minimumOrderQuantity: '1', preferred: false });
    try { setCatalogItems((await api.get(`/suppliers/${supplier.id}/products?includeInactive=true`)).data); }
    catch (e) { setError(failureText(e)); }
  };
  const saveCatalogItem = async () => {
    if (!catalogSupplier) return;
    const body = { ...catalogForm, productId: Number(catalogForm.productId), unitCost: Number(catalogForm.unitCost), leadTimeDays: Number(catalogForm.leadTimeDays), minimumOrderQuantity: Number(catalogForm.minimumOrderQuantity) };
    await run(async () => {
      if (catalogEdit) await api.put(`/suppliers/${catalogSupplier.id}/products/${catalogEdit.productId}`, body);
      else await api.post(`/suppliers/${catalogSupplier.id}/products`, body);
      setCatalogEdit(null); setCatalogForm({ productId: '', supplierSku: '', unitCost: '', leadTimeDays: '7', minimumOrderQuantity: '1', preferred: false });
      setCatalogItems((await api.get(`/suppliers/${catalogSupplier.id}/products?includeInactive=true`)).data);
    }, catalogEdit ? 'Supplier catalog item updated.' : 'Product added to supplier catalog.');
  };

  const loadPoCatalog = async (supplierId: string) => {
    setPoSupplierId(supplierId); setPoCatalog([]);
    if (supplierId) {
      try { setPoCatalog((await api.get(`/suppliers/${supplierId}/products`)).data); }
      catch (e) { setError(failureText(e)); }
    }
  };
  const openPurchaseOrder = async (order?: Row) => {
    setPoEdit(order || null); setPoExpectedDate(''); setPoNote(''); setPoLines([{ supplierProductId: '', quantity: '1' }]);
    if (order) {
      try {
        const detail = (await api.get(`/purchase-orders/${order.id}`)).data;
        setPoSupplierId(String(detail.supplierId)); setPoExpectedDate(detail.expectedDate ? String(detail.expectedDate).slice(0, 10) : ''); setPoNote(detail.note || '');
        setPoLines((detail.lines || []).map((line: Row) => ({ supplierProductId: String(line.supplierProductId), quantity: String(line.quantityOrdered) })));
        setPoCatalog((await api.get(`/suppliers/${detail.supplierId}/products`)).data);
      } catch (e) { setError(failureText(e)); }
    } else if (activeSuppliers.length) await loadPoCatalog(String(activeSuppliers[0].id));
    else setPoSupplierId('');
    setPoDialog(true);
  };
  const savePurchaseOrder = async () => {
    const body = { supplierId: Number(poSupplierId), expectedDate: poExpectedDate || null, note: poNote, lines: poLines.filter(line => line.supplierProductId).map(line => ({ supplierProductId: Number(line.supplierProductId), quantity: Number(line.quantity) })) };
    if (!body.supplierId || !body.lines.length || body.lines.some(line => line.quantity < 1)) { setError('Choose a supplier, add a catalog item, and enter positive quantities.'); return; }
    await run(async () => { if (poEdit) await api.put(`/purchase-orders/${poEdit.id}`, body); else await api.post('/purchase-orders', body); setPoDialog(false); }, poEdit ? 'Draft purchase order saved.' : 'Draft purchase order created.');
  };

  const openReceive = async (order: Row) => {
    try {
      const detail = (await api.get(`/purchase-orders/${order.id}`)).data;
      const remaining = (detail.lines || []).filter((line: Row) => Number(line.quantityOrdered) > Number(line.quantityReceived)).map((line: Row) => ({ productId: line.productId, quantity: String(Number(line.quantityOrdered) - Number(line.quantityReceived)), remaining: Number(line.quantityOrdered) - Number(line.quantityReceived), sku: line.sku }));
      setReceiveOrder(detail); setReceiptNumber(`RCV-${crypto.randomUUID().slice(0, 8).toUpperCase()}`); setReceiptLines(remaining);
    } catch (e) { setError(failureText(e)); }
  };
  const receiveStock = async () => {
    if (!receiveOrder) return;
    const lines = receiptLines.filter(line => Number(line.quantity) > 0).map(line => ({ productId: Number(line.productId), quantity: Number(line.quantity) }));
    await run(async () => { await api.post(`/purchase-orders/${receiveOrder.id}/receive`, { receiptNumber, lines }); setReceiveOrder(null); }, 'Goods receipt recorded and stock updated.');
  };

  const openInvoice = async (order: Row) => {
    try {
      const detail = (await api.get(`/purchase-orders/${order.id}`)).data;
      setInvoiceOrder(detail); setInvoiceForm({ invoiceNumber: '', issueDate: new Date().toISOString().slice(0, 10), dueDate: '', taxAmount: '0' });
      setInvoiceLines((detail.lines || []).filter((line: Row) => Number(line.quantityReceived) > 0).map((line: Row) => ({ productId: line.productId, quantity: String(line.quantityReceived), unitCost: String(line.unitCost), sku: line.sku })));
    } catch (e) { setError(failureText(e)); }
  };
  const submitInvoice = async () => {
    if (!invoiceOrder || !String(invoiceForm.invoiceNumber).trim()) { setError('Enter the supplier invoice number.'); return; }
    await run(async () => {
      await api.post(`/purchase-orders/${invoiceOrder.id}/supplier-invoices`, {
        ...invoiceForm, taxAmount: Number(invoiceForm.taxAmount),
        lines: invoiceLines.filter(line => Number(line.quantity) > 0).map(line => ({ productId: Number(line.productId), quantity: Number(line.quantity), unitCost: Number(line.unitCost) })),
      });
      setInvoiceOrder(null);
    }, 'Invoice matched against received quantities and purchase order cost.');
  };

  const createReplenishmentPO = async (row: Row) => {
    const supplierId = row.preferredSupplier?.supplierId;
    if (!supplierId) { setError(`Add Product #${row.productId} to a supplier catalog to create its purchase order.`); return; }
    await run(() => api.post(`/replenishment-suggestions/${row.productId}/draft-purchase-order`, null, { params: { supplierId } }), `Draft PO created for Product #${row.productId}.`);
    setTab('orders');
  };

  const tabs = [
    { key: 'overview', label: 'Overview', icon: <StorefrontRounded /> },
    { key: 'suppliers', label: 'Suppliers', icon: <StorefrontRounded /> },
    { key: 'orders', label: 'Purchase orders', icon: <LocalShippingRounded /> },
    { key: 'replenishment', label: 'Replenishment', icon: <WarningAmberRounded /> },
    { key: 'invoices', label: 'Supplier invoices', icon: <ReceiptLongRounded /> },
  ];

  return <Box sx={{ minHeight: '100vh', bgcolor: '#0B1220', color: '#E7EDF7', p: { xs: 1.5, md: 3 } }}>
    <Box sx={{ maxWidth: 1480, mx: 'auto' }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} alignItems={{ xs: 'flex-start', sm: 'center' }} spacing={1.5} sx={{ mb: 2.5 }}>
        <Box sx={{ p: 1.3, borderRadius: 3, bgcolor: '#172A43', color: '#70D1C0', display: 'flex' }}><LocalShippingRounded /></Box>
        <Box><Typography variant="h4" sx={{ fontWeight: 850, letterSpacing: '-.04em' }}>Suppliers &amp; Procurement</Typography><Typography color="text.secondary">Replenish stock, receive goods, and match supplier invoices.</Typography></Box>
        <Box sx={{ flex: 1 }} />
        <Button variant="outlined" startIcon={<RefreshRounded />} disabled={loading} onClick={() => void refresh()}>Refresh</Button>
      </Stack>
      {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError('')}>{error}</Alert>}
      {notice && <Alert severity="success" sx={{ mb: 2 }} onClose={() => setNotice('')}>{notice}</Alert>}
      <Paper variant="outlined" sx={{ mb: 2.5, bgcolor: '#121B2D', borderColor: '#26334B' }}><Tabs value={tab} onChange={(_, value) => setTab(value)} variant="scrollable" scrollButtons="auto">{tabs.map(item => <Tab key={item.key} value={item.key} icon={item.icon} iconPosition="start" label={item.label} />)}</Tabs></Paper>
      {loading && <LinearProgress sx={{ mb: 2, borderRadius: 2 }} />}

      {tab === 'overview' && <>
        <Grid container spacing={2} sx={{ mb: 3 }}>
          <Grid item xs={12} sm={6} lg={3}><Metric title="Active suppliers" value={dashboard.activeSuppliers ?? '—'} helper="Approved purchasing partners" icon={<StorefrontRounded />} /></Grid>
          <Grid item xs={12} sm={6} lg={3}><Metric title="Open purchase orders" value={dashboard.openPurchaseOrders ?? '—'} helper="Approved, ordered, or receiving" icon={<LocalShippingRounded />} color="#A78BFA" /></Grid>
          <Grid item xs={12} sm={6} lg={3}><Metric title="Low-stock suggestions" value={suggestions.length} helper="Based on authoritative Stock levels" icon={<WarningAmberRounded />} color="#FBBF24" /></Grid>
          <Grid item xs={12} sm={6} lg={3}><Metric title="Approved payables" value={money(dashboard.approvedPayables)} helper="Supplier invoices posted to Accounting" icon={<ReceiptLongRounded />} color="#4ADE80" /></Grid>
        </Grid>
        <Paper variant="outlined" sx={{ p: 2.5, bgcolor: '#121B2D', borderColor: '#26334B' }}>
          <Typography variant="h6" fontWeight={800}>Procurement workflow</Typography>
          <Typography color="text.secondary" sx={{ mt: 1 }}>Maintain supplier catalogs, create draft purchase orders, approve and issue them, then receive goods into Stock. Match invoices to received quantities before approval posts the payable journal to Accounting.</Typography>
          <Stack direction="row" spacing={1} sx={{ mt: 2 }}><Button variant="contained" startIcon={<AddRounded />} onClick={() => openSupplier()}>Add supplier</Button><Button variant="outlined" onClick={() => void openPurchaseOrder()}>New purchase order</Button><Button variant="outlined" onClick={() => setTab('replenishment')}>Review low stock</Button></Stack>
        </Paper>
      </>}

      {tab === 'suppliers' && <Box>
        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}><Box><Typography variant="h5" fontWeight={800}>Supplier directory</Typography><Typography color="text.secondary">Contacts, payment terms, and vendor-specific product catalogs.</Typography></Box><Button variant="contained" startIcon={<AddRounded />} onClick={() => openSupplier()}>Add supplier</Button></Stack>
        <DataTable rows={suppliers} empty="No suppliers yet. Add a supplier to create your first purchase order." columns={[
          { key: 'supplierCode', label: 'CODE' }, { key: 'name', label: 'SUPPLIER', render: row => <Stack><Typography fontWeight={750}>{row.name}</Typography><Typography variant="caption" color="text.secondary">{row.contactName || row.email || 'No contact set'}</Typography></Stack> },
          { key: 'paymentTermsDays', label: 'TERMS', render: row => `${row.paymentTermsDays} days` }, { key: 'currency', label: 'CURRENCY' }, { key: 'status', label: 'STATUS', render: row => <Chip size="small" label={row.archived ? 'ARCHIVED' : 'ACTIVE'} color={row.archived ? 'default' : 'success'} /> },
          { key: 'actions', label: 'ACTIONS', render: row => <Stack direction="row" spacing={.5}><Button size="small" onClick={() => void openCatalog(row)}>Product catalog</Button>{row.archived ? <Button size="small" startIcon={<RestoreRounded />} onClick={() => void run(() => api.patch(`/suppliers/${row.id}/restore`), 'Supplier restored.')}>Restore</Button> : <><IconButton size="small" aria-label="Edit supplier" onClick={() => openSupplier(row)}><EditRounded fontSize="small" /></IconButton><Tooltip title="Archive supplier"><span><IconButton size="small" color="warning" onClick={() => void run(() => api.delete(`/suppliers/${row.id}`), 'Supplier archived.')}><ArchiveRounded fontSize="small" /></IconButton></span></Tooltip></>}</Stack> },
        ]} />
      </Box>}

      {tab === 'orders' && <Box>
        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}><Box><Typography variant="h5" fontWeight={800}>Purchase orders</Typography><Typography color="text.secondary">Draft, approve, issue, receive, and reconcile each order.</Typography></Box><Button variant="contained" startIcon={<AddRounded />} onClick={() => void openPurchaseOrder()} disabled={!activeSuppliers.length}>New purchase order</Button></Stack>
        <DataTable rows={orders} empty="No purchase orders yet." columns={[
          { key: 'poNumber', label: 'PO' }, { key: 'supplierName', label: 'SUPPLIER' }, { key: 'status', label: 'STATUS', render: row => <Chip size="small" label={String(row.status).replaceAll('_', ' ')} color={row.status === 'RECEIVED' ? 'success' : row.status === 'DRAFT' ? 'default' : 'primary'} /> },
          { key: 'subtotal', label: 'SUBTOTAL', render: row => money(row.subtotal, row.currency) }, { key: 'expectedDate', label: 'EXPECTED' , render: row => day(row.expectedDate) }, { key: 'lineCount', label: 'LINES' },
          { key: 'actions', label: 'ACTIONS', render: row => <Stack direction="row" spacing={.5}>
            {row.status === 'DRAFT' && <><Button size="small" startIcon={<EditRounded />} onClick={() => void openPurchaseOrder(row)}>Edit</Button><Button size="small" onClick={() => void run(() => api.post(`/purchase-orders/${row.id}/approve`), 'Purchase order approved.')}>Approve</Button></>}
            {row.status === 'APPROVED' && <Button size="small" startIcon={<LocalShippingRounded />} onClick={() => void run(() => api.post(`/purchase-orders/${row.id}/issue`), 'Purchase order issued to supplier.')}>Issue</Button>}
            {['ORDERED', 'PARTIALLY_RECEIVED'].includes(row.status) && <Button size="small" onClick={() => void openReceive(row)}>Receive</Button>}
            {['PARTIALLY_RECEIVED', 'RECEIVED'].includes(row.status) && <Button size="small" startIcon={<ReceiptLongRounded />} onClick={() => void openInvoice(row)}>Match invoice</Button>}
            {['DRAFT', 'APPROVED', 'ORDERED'].includes(row.status) && <Button size="small" color="error" onClick={() => void run(() => api.post(`/purchase-orders/${row.id}/cancel`), 'Purchase order cancelled.')}>Cancel</Button>}
          </Stack> },
        ]} />
      </Box>}

      {tab === 'replenishment' && <Box>
        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}><Box><Typography variant="h5" fontWeight={800}>Replenishment suggestions</Typography><Typography color="text.secondary">Live low-stock items joined to active supplier catalogs.</Typography></Box><Chip icon={<Inventory2Rounded />} label={`${suggestions.length} items need review`} color={suggestions.length ? 'warning' : 'success'} variant="outlined" /></Stack>
        <DataTable rows={suggestions} empty="Stock has no active low-stock alerts." columns={[
          { key: 'productId', label: 'PRODUCT' }, { key: 'sku', label: 'SKU' }, { key: 'availableQuantity', label: 'AVAILABLE' }, { key: 'reorderThreshold', label: 'REORDER AT' }, { key: 'suggestedQuantity', label: 'SUGGESTED ORDER' },
          { key: 'preferredSupplier', label: 'SUPPLIER', render: row => row.preferredSupplier ? <Stack><Typography>{row.preferredSupplier.supplierName}</Typography><Typography variant="caption" color="text.secondary">{money(row.preferredSupplier.unitCost)} · {row.preferredSupplier.leadTimeDays} day lead</Typography></Stack> : <Chip size="small" label="CATALOG MISSING" color="warning" /> },
          { key: 'actions', label: 'ACTION', render: row => <Button size="small" variant="contained" disabled={!row.preferredSupplier} onClick={() => void createReplenishmentPO(row)}>Create draft PO</Button> },
        ]} />
      </Box>}

      {tab === 'invoices' && <Box>
        <Stack sx={{ mb: 2 }}><Typography variant="h5" fontWeight={800}>Supplier invoices</Typography><Typography color="text.secondary">Invoices are matched against received PO quantities and agreed unit costs.</Typography></Stack>
        <DataTable rows={invoices} empty="No supplier invoices have been entered." columns={[
          { key: 'invoiceNumber', label: 'INVOICE' }, { key: 'supplierName', label: 'SUPPLIER' }, { key: 'poNumber', label: 'PURCHASE ORDER' }, { key: 'status', label: 'STATUS', render: row => <Chip size="small" label={row.status} color={row.status === 'APPROVED' || row.status === 'PAID' ? 'success' : 'warning'} /> },
          { key: 'totalAmount', label: 'TOTAL', render: row => money(row.totalAmount, row.currency) }, { key: 'dueDate', label: 'DUE DATE', render: row => day(row.dueDate) }, { key: 'accountingEntryId', label: 'ACCOUNTING ENTRY', render: row => row.accountingEntryId ? `#${row.accountingEntryId}` : 'Not posted' },
          { key: 'action', label: 'ACTION', render: row => row.status === 'MATCHED' ? <Button size="small" startIcon={<CheckCircleRounded />} onClick={() => void run(() => api.post(`/supplier-invoices/${row.id}/approve`), 'Payable posted to the Accounting journal.')}>Approve &amp; post</Button> : null },
        ]} />
      </Box>}
    </Box>

    <Dialog open={supplierDialog} onClose={() => setSupplierDialog(false)} fullWidth maxWidth="sm"><DialogTitle>{supplierEdit ? 'Edit supplier' : 'Add supplier'}</DialogTitle><DialogContent><Grid container spacing={1.5} sx={{ pt: 1 }}>
      {[['supplierCode', 'Supplier code'], ['name', 'Supplier name'], ['contactName', 'Contact name'], ['email', 'Email'], ['phone', 'Phone'], ['paymentTermsDays', 'Payment terms (days)'], ['currency', 'Currency'], ['address', 'Address'], ['notes', 'Notes']].map(([key, label]) => <Grid item xs={12} sm={key === 'address' || key === 'notes' ? 12 : 6} key={key}><TextField fullWidth size="small" label={label} value={supplierForm[key]} onChange={event => setSupplierForm({ ...supplierForm, [key]: event.target.value })} disabled={Boolean(supplierEdit && key === 'supplierCode')} sx={inputSx} /></Grid>)}
    </Grid></DialogContent><DialogActions><Button onClick={() => setSupplierDialog(false)}>Cancel</Button><Button variant="contained" onClick={() => void saveSupplier()} disabled={!supplierForm.name || (!supplierEdit && !supplierForm.supplierCode)}>Save supplier</Button></DialogActions></Dialog>

    <Dialog open={Boolean(catalogSupplier)} onClose={() => setCatalogSupplier(null)} fullWidth maxWidth="md"><DialogTitle>Product catalog · {catalogSupplier?.name}</DialogTitle><DialogContent>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>Map product IDs to vendor SKUs, costs, minimum order quantities, and lead times.</Typography>
      <Grid container spacing={1} sx={{ mb: 2 }}>{[['productId', 'Product ID'], ['supplierSku', 'Supplier SKU'], ['unitCost', 'Unit cost'], ['leadTimeDays', 'Lead days'], ['minimumOrderQuantity', 'Minimum qty']].map(([key, label]) => <Grid item xs={6} sm={key === 'supplierSku' ? 4 : 2} key={key}><TextField fullWidth size="small" label={label} type={key === 'productId' || key === 'leadTimeDays' || key === 'minimumOrderQuantity' || key === 'unitCost' ? 'number' : 'text'} value={catalogForm[key]} disabled={Boolean(catalogEdit && key === 'productId')} onChange={event => setCatalogForm({ ...catalogForm, [key]: event.target.value })} sx={inputSx} /></Grid>)}<Grid item xs={6} sm={2}><FormControlLabel control={<Checkbox checked={Boolean(catalogForm.preferred)} onChange={event => setCatalogForm({ ...catalogForm, preferred: event.target.checked })} />} label="Preferred" /></Grid><Grid item xs={12}><Button variant="contained" onClick={() => void saveCatalogItem()} disabled={!catalogForm.productId || !catalogForm.unitCost}>{catalogEdit ? 'Save catalog item' : 'Add product'}</Button>{catalogEdit && <Button sx={{ ml: 1 }} onClick={() => { setCatalogEdit(null); setCatalogForm({ productId: '', supplierSku: '', unitCost: '', leadTimeDays: '7', minimumOrderQuantity: '1', preferred: false }); }}>Cancel edit</Button>}</Grid></Grid>
      <Divider sx={{ my: 2 }} /><DataTable rows={catalogItems} empty="No products in this supplier catalog." columns={[{ key: 'productId', label: 'PRODUCT' }, { key: 'supplierSku', label: 'SUPPLIER SKU' }, { key: 'unitCost', label: 'UNIT COST', render: row => money(row.unitCost, catalogSupplier?.currency || 'USD') }, { key: 'leadTimeDays', label: 'LEAD' }, { key: 'minimumOrderQuantity', label: 'MIN QTY' }, { key: 'preferred', label: 'PREFERRED', render: row => row.preferred ? 'Yes' : '—' }, { key: 'active', label: 'STATUS', render: row => row.active ? 'Active' : 'Archived' }, { key: 'actions', label: 'ACTIONS', render: row => row.active ? <Stack direction="row"><IconButton size="small" onClick={() => { setCatalogEdit(row); setCatalogForm({ ...row, productId: String(row.productId), unitCost: String(row.unitCost), leadTimeDays: String(row.leadTimeDays), minimumOrderQuantity: String(row.minimumOrderQuantity) }); }}><EditRounded fontSize="small" /></IconButton><IconButton size="small" color="warning" onClick={() => void run(async () => { await api.delete(`/suppliers/${catalogSupplier?.id}/products/${row.productId}`); setCatalogItems((await api.get(`/suppliers/${catalogSupplier?.id}/products?includeInactive=true`)).data); }, 'Supplier catalog item archived.')}><ArchiveRounded fontSize="small" /></IconButton></Stack> : '—' }]} />
    </DialogContent><DialogActions><Button onClick={() => setCatalogSupplier(null)}>Close</Button></DialogActions></Dialog>

    <Dialog open={poDialog} onClose={() => setPoDialog(false)} fullWidth maxWidth="md"><DialogTitle>{poEdit ? `Edit draft · ${poEdit.poNumber}` : 'Create draft purchase order'}</DialogTitle><DialogContent>
      <Grid container spacing={1.5} sx={{ pt: 1, mb: 2 }}><Grid item xs={12} sm={5}><FormControl fullWidth size="small"><InputLabel>Supplier</InputLabel><Select label="Supplier" value={poSupplierId} disabled={Boolean(poEdit)} onChange={event => void loadPoCatalog(String(event.target.value))}>{activeSuppliers.map(row => <MenuItem key={row.id} value={String(row.id)}>{row.name}</MenuItem>)}</Select></FormControl></Grid><Grid item xs={12} sm={3}><TextField fullWidth size="small" type="date" label="Expected date" InputLabelProps={{ shrink: true }} value={poExpectedDate} onChange={event => setPoExpectedDate(event.target.value)} /></Grid><Grid item xs={12} sm={4}><TextField fullWidth size="small" label="Note" value={poNote} onChange={event => setPoNote(event.target.value)} /></Grid></Grid>
      <Typography fontWeight={750} sx={{ mb: 1 }}>Order lines</Typography><Stack spacing={1}>{poLines.map((line, index) => <Stack key={index} direction={{ xs: 'column', sm: 'row' }} spacing={1}><FormControl fullWidth size="small"><InputLabel>Supplier product</InputLabel><Select label="Supplier product" value={String(line.supplierProductId)} onChange={event => setPoLines(poLines.map((entry, i) => i === index ? { ...entry, supplierProductId: event.target.value } : entry))}>{poCatalog.map(item => <MenuItem key={item.id} value={String(item.id)}>Product #{item.productId} · {item.supplierSku || 'No vendor SKU'} · {money(item.unitCost)}</MenuItem>)}</Select></FormControl><TextField size="small" type="number" label="Quantity" value={line.quantity} inputProps={{ min: 1 }} onChange={event => setPoLines(poLines.map((entry, i) => i === index ? { ...entry, quantity: event.target.value } : entry))} sx={{ minWidth: 150 }} /><IconButton disabled={poLines.length === 1} onClick={() => setPoLines(poLines.filter((_, i) => i !== index))}><ArchiveRounded /></IconButton></Stack>)}</Stack><Button sx={{ mt: 1 }} onClick={() => setPoLines([...poLines, { supplierProductId: '', quantity: '1' }])} disabled={!poCatalog.length}>Add line</Button>
    </DialogContent><DialogActions><Button onClick={() => setPoDialog(false)}>Cancel</Button><Button variant="contained" onClick={() => void savePurchaseOrder()} disabled={!poSupplierId}>Save draft</Button></DialogActions></Dialog>

    <Dialog open={Boolean(receiveOrder)} onClose={() => setReceiveOrder(null)} fullWidth maxWidth="sm"><DialogTitle>Receive goods · {receiveOrder?.poNumber}</DialogTitle><DialogContent><Stack spacing={1.5} sx={{ pt: 1 }}><TextField label="Receipt reference" value={receiptNumber} onChange={event => setReceiptNumber(event.target.value)} required />{receiptLines.map((line, index) => <Stack key={line.productId} direction="row" alignItems="center" spacing={1}><Typography sx={{ flex: 1 }}>Product #{line.productId} · {line.sku || 'SKU'} <Typography component="span" variant="caption" color="text.secondary">({line.remaining} remaining)</Typography></Typography><TextField size="small" type="number" label="Receive" value={line.quantity} inputProps={{ min: 0, max: line.remaining }} onChange={event => setReceiptLines(receiptLines.map((entry, i) => i === index ? { ...entry, quantity: event.target.value } : entry))} sx={{ width: 130 }} /></Stack>)}<Alert severity="info">Receiving creates an idempotent stock movement. Reuse the same receipt reference if you retry after a service interruption.</Alert></Stack></DialogContent><DialogActions><Button onClick={() => setReceiveOrder(null)}>Cancel</Button><Button variant="contained" onClick={() => void receiveStock()} disabled={!receiptNumber || !receiptLines.some(line => Number(line.quantity) > 0)}>Confirm receipt</Button></DialogActions></Dialog>

    <Dialog open={Boolean(invoiceOrder)} onClose={() => setInvoiceOrder(null)} fullWidth maxWidth="sm"><DialogTitle>Match supplier invoice · {invoiceOrder?.poNumber}</DialogTitle><DialogContent><Stack spacing={1.5} sx={{ pt: 1 }}><TextField label="Supplier invoice number" value={invoiceForm.invoiceNumber} onChange={event => setInvoiceForm({ ...invoiceForm, invoiceNumber: event.target.value })} required /><Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}><TextField fullWidth type="date" label="Issue date" InputLabelProps={{ shrink: true }} value={invoiceForm.issueDate} onChange={event => setInvoiceForm({ ...invoiceForm, issueDate: event.target.value })} /><TextField fullWidth type="date" label="Due date" InputLabelProps={{ shrink: true }} value={invoiceForm.dueDate} onChange={event => setInvoiceForm({ ...invoiceForm, dueDate: event.target.value })} /></Stack><TextField type="number" label="Tax amount" value={invoiceForm.taxAmount} onChange={event => setInvoiceForm({ ...invoiceForm, taxAmount: event.target.value })} inputProps={{ min: 0, step: '0.0001' }} />{invoiceLines.map((line, index) => <Stack key={line.productId} direction="row" alignItems="center" spacing={1}><Typography sx={{ flex: 1 }}>Product #{line.productId}</Typography><TextField size="small" type="number" label="Qty" value={line.quantity} onChange={event => setInvoiceLines(invoiceLines.map((entry, i) => i === index ? { ...entry, quantity: event.target.value } : entry))} sx={{ width: 105 }} /><Typography sx={{ width: 110, textAlign: 'right' }}>{money(line.unitCost, invoiceOrder?.currency)}</Typography></Stack>)}<Alert severity="info">Invoice quantities must be within received quantities, and unit costs must equal the purchase order.</Alert></Stack></DialogContent><DialogActions><Button onClick={() => setInvoiceOrder(null)}>Cancel</Button><Button variant="contained" onClick={() => void submitInvoice()} disabled={!invoiceForm.invoiceNumber}>Match invoice</Button></DialogActions></Dialog>
  </Box>;
}
