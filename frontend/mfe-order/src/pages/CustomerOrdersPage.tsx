import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert, Box, Button, Card, CardContent, Chip, CircularProgress, Dialog, DialogActions, DialogContent,
  DialogTitle, Grid, Paper, Stack, Tab, Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  Tabs, TextField, Typography,
} from '@mui/material';
import { AssignmentReturnRounded, HistoryRounded, RefreshRounded } from '@mui/icons-material';
import { useSelector } from 'react-redux';
import type { RootState } from '@ecommerce/shared-library';
import axios from 'axios';

type Row = Record<string, any>;
const money = (value: unknown) => new Intl.NumberFormat(undefined, { style: 'currency', currency: 'USD' }).format(Number(value || 0));
const date = (value: unknown) => value ? new Date(String(value)).toLocaleDateString() : '—';

export default function CustomerOrdersPage() {
  const user = useSelector((state: RootState) => state.auth?.user);
  const [tab, setTab] = useState('orders');
  const [orders, setOrders] = useState<Row[]>([]);
  const [returns, setReturns] = useState<Row[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [selectedOrder, setSelectedOrder] = useState<Row | null>(null);
  const [reason, setReason] = useState('');
  const [quantities, setQuantities] = useState<Record<number, string>>({});

  const refresh = useCallback(async () => {
    if (!user?.id) return;
    setBusy(true); setError('');
    try {
      const [orderRows, returnRows] = await Promise.all([
        axios.get(`/api/orders/user/${user.id}`), axios.get('/api/orders/returns/me'),
      ]);
      setOrders(orderRows.data || []); setReturns(returnRows.data || []);
    } catch (err: any) {
      setError(err.response?.data?.error || 'Unable to load your order history.');
    } finally { setBusy(false); }
  }, [user?.id]);
  useEffect(() => { void refresh(); }, [refresh]);

  const previouslyRequested = useMemo(() => {
    const values: Record<number, number> = {};
    for (const request of returns) {
      if (request.status === 'REJECTED') continue;
      for (const item of request.items || []) values[item.orderItemId] = (values[item.orderItemId] || 0) + Number(item.quantity || 0);
    }
    return values;
  }, [returns]);

  const openRequest = (order: Row) => {
    setSelectedOrder(order); setReason(''); setQuantities({}); setError('');
  };
  const submitRequest = async () => {
    if (!selectedOrder || !reason.trim()) { setError('Add a reason for the return.'); return; }
    const items = Object.entries(quantities).filter(([, quantity]) => Number(quantity) > 0)
      .map(([orderItemId, quantity]) => ({ orderItemId: Number(orderItemId), quantity: Number(quantity) }));
    if (!items.length) { setError('Select at least one item and quantity.'); return; }
    setBusy(true); setError('');
    try {
      await axios.post(`/api/orders/${selectedOrder.id}/returns`, { reason: reason.trim(), items });
      setSelectedOrder(null); setNotice(`Return request submitted for order #${selectedOrder.id}.`); await refresh();
    } catch (err: any) {
      setError(err.response?.data?.error || 'Unable to submit this return request.');
    } finally { setBusy(false); }
  };

  const statusColor = (status: string) => status === 'REFUNDED' || status === 'DELIVERED' ? 'success' : status === 'REJECTED' ? 'error' : status === 'REQUESTED' ? 'warning' : 'primary';

  return <Box sx={{ bgcolor: '#F8FAFC', minHeight: '100vh', py: { xs: 2, md: 5 } }}><Box sx={{ maxWidth: 1240, mx: 'auto', px: 2 }}>
    <Stack direction={{ xs: 'column', sm: 'row' }} alignItems={{ xs: 'flex-start', sm: 'center' }} spacing={1.5} sx={{ mb: 3 }}>
      <Box sx={{ p: 1.2, borderRadius: 2.5, bgcolor: '#EEF2FF', color: '#4F46E5', display: 'flex' }}><HistoryRounded /></Box>
      <Box><Typography variant="h4" fontWeight={850} color="#0F172A">My orders & returns</Typography><Typography color="text.secondary">Track deliveries and request returns for eligible items.</Typography></Box>
      <Box sx={{ flex: 1 }} /><Button startIcon={<RefreshRounded />} onClick={() => void refresh()} disabled={busy}>Refresh</Button>
    </Stack>
    {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError('')}>{error}</Alert>}
    {notice && <Alert severity="success" sx={{ mb: 2 }} onClose={() => setNotice('')}>{notice}</Alert>}
    <Paper variant="outlined" sx={{ mb: 2.5 }}><Tabs value={tab} onChange={(_, value) => setTab(value)}><Tab value="orders" icon={<HistoryRounded />} iconPosition="start" label="Orders"/><Tab value="returns" icon={<AssignmentReturnRounded />} iconPosition="start" label={`Returns (${returns.length})`}/></Tabs></Paper>
    {busy && <Box sx={{ display: 'flex', justifyContent: 'center', py: 5 }}><CircularProgress /></Box>}
    {!busy && tab === 'orders' && <Stack spacing={1.5}>{orders.map(order => <Card key={order.id} variant="outlined"><CardContent>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} alignItems={{ xs: 'flex-start', sm: 'center' }}><Box sx={{ flex: 1 }}><Typography variant="h6" fontWeight={800}>Order #{order.id}</Typography><Typography variant="body2" color="text.secondary">Placed {date(order.createdAt)} · {money(order.totalAmount)}</Typography></Box><Chip size="small" color={statusColor(order.status)} label={order.status}/>{order.status === 'DELIVERED' && <Button variant="outlined" startIcon={<AssignmentReturnRounded />} onClick={() => openRequest(order)}>Request return</Button>}</Stack>
      <Grid container spacing={1} sx={{ mt: 1 }}>{(order.items || []).map((item: Row) => <Grid item xs={12} sm={6} md={4} key={item.id}><Paper variant="outlined" sx={{ p: 1.3 }}><Typography fontWeight={700}>{item.productName || `Product #${item.productId}`}</Typography><Typography variant="body2" color="text.secondary">{item.quantity} × {money(item.price)} · #{item.id}</Typography></Paper></Grid>)}</Grid>
    </CardContent></Card>)}{orders.length === 0 && <Paper variant="outlined" sx={{ p: 5, textAlign: 'center' }}><Typography color="text.secondary">No orders to show yet.</Typography></Paper>}</Stack>}
    {!busy && tab === 'returns' && <TableContainer component={Paper} variant="outlined"><Table><TableHead><TableRow><TableCell>RETURN</TableCell><TableCell>ORDER</TableCell><TableCell>ITEMS</TableCell><TableCell>REASON</TableCell><TableCell>REFUND</TableCell><TableCell>STATUS</TableCell><TableCell>REQUESTED</TableCell></TableRow></TableHead><TableBody>
      {returns.map(row => <TableRow key={row.id}><TableCell>RMA-{row.id}</TableCell><TableCell>#{row.orderId}</TableCell><TableCell>{(row.items || []).map((item: Row) => <Typography key={item.id} variant="body2">{item.productName} × {item.quantity}</Typography>)}</TableCell><TableCell sx={{ maxWidth: 250 }}>{row.reason}</TableCell><TableCell>{money(row.refundAmount)}</TableCell><TableCell><Chip size="small" color={statusColor(row.status)} label={row.status}/></TableCell><TableCell>{date(row.createdAt)}</TableCell></TableRow>)}
      {returns.length === 0 && <TableRow><TableCell colSpan={7} align="center" sx={{ py: 5, color: 'text.secondary' }}>You have no return requests yet.</TableCell></TableRow>}
    </TableBody></Table></TableContainer>}
  </Box>

  <Dialog open={Boolean(selectedOrder)} onClose={() => setSelectedOrder(null)} fullWidth maxWidth="sm"><DialogTitle>Request a return · Order #{selectedOrder?.id}</DialogTitle><DialogContent><Stack spacing={1.5} sx={{ pt: 1 }}>
    <Alert severity="info">Choose item quantities. Refund eligibility is calculated from the order price and discount; staff confirm it after inspection.</Alert>
    {(selectedOrder?.items || []).map((item: Row) => {
      const remaining = Math.max(0, Number(item.quantity) - (previouslyRequested[item.id] || 0));
      return <Stack key={item.id} direction="row" alignItems="center" spacing={1}><Box sx={{ flex: 1 }}><Typography fontWeight={700}>{item.productName || `Product #${item.productId}`}</Typography><Typography variant="caption" color="text.secondary">{money(item.price)} each · {remaining} eligible</Typography></Box><TextField size="small" type="number" label="Qty" value={quantities[item.id] || ''} inputProps={{ min: 0, max: remaining }} disabled={!remaining} onChange={event => setQuantities({ ...quantities, [item.id]: event.target.value })} sx={{ width: 110 }}/></Stack>;
    })}
    <TextField label="Return reason" value={reason} onChange={event => setReason(event.target.value)} multiline minRows={3} required placeholder="Tell us what happened"/>
    {error && <Alert severity="error">{error}</Alert>}
  </Stack></DialogContent><DialogActions><Button onClick={() => setSelectedOrder(null)}>Cancel</Button><Button variant="contained" onClick={() => void submitRequest()} disabled={busy || !reason.trim()}>Submit return</Button></DialogActions></Dialog>
  </Box>;
}
