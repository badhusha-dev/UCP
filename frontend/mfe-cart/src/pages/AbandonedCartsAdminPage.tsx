import React, { useState, useEffect } from 'react';
import {
  Box,
  Container,
  Grid,
  Paper,
  Typography,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  IconButton,
  Tooltip,
  CircularProgress,
  Alert,
  Stack,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Avatar,
} from '@mui/material';
import {
  ProductionQuantityLimitsRounded,
  MarkEmailReadRounded,
  SendRounded,
  TrendingDownRounded,
  AttachMoneyRounded,
  ShoppingCartCheckoutRounded,
  RefreshRounded,
  AccessTimeRounded,
  LocalOfferRounded,
} from '@mui/icons-material';
import axios from 'axios';

interface AbandonedCartItem {
  id: number;
  productId: number;
  sku: string;
  productName: string;
  unitPrice: number;
  quantity: number;
  subtotal: number;
  imageUrl?: string;
}

interface AbandonedCart {
  cartId: number;
  userId?: number;
  customerEmail: string;
  sessionToken: string;
  itemCount: number;
  totalAmount: number;
  lastUpdated: string;
  inactiveHours: number;
  remindersSent: number;
  status: string;
  items: AbandonedCartItem[];
}

interface FunnelStats {
  totalCartsCreated: number;
  activeCarts: number;
  abandonedCarts: number;
  convertedCarts: number;
  abandonedCartValue: number;
  activeCartValue: number;
  abandonedRatePercent: number;
  conversionRatePercent: number;
  remindersSent: number;
}

export default function AbandonedCartsAdminPage() {
  const [carts, setCarts] = useState<AbandonedCart[]>([]);
  const [funnel, setFunnel] = useState<FunnelStats | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [selectedCart, setSelectedCart] = useState<AbandonedCart | null>(null);
  const [discountCode, setDiscountCode] = useState<string>('COMEBACK15');
  const [discountPercent, setDiscountPercent] = useState<number>(15);
  const [sending, setSending] = useState<boolean>(false);
  const [toastMsg, setToastMsg] = useState<string>('');

  const fetchData = async () => {
    setLoading(true);
    try {
      const token = localStorage.getItem('jwt_token');
      const headers = token ? { Authorization: `Bearer ${token}` } : {};

      const [funnelRes, cartsRes] = await Promise.all([
        axios.get('/api/cart/admin/funnel', { headers }),
        axios.get('/api/cart/admin/abandoned?inactiveHours=1', { headers }),
      ]);

      setFunnel(funnelRes.data);
      setCarts(cartsRes.data);
    } catch (err: any) {
      console.warn('Failed to fetch abandoned cart analytics', err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const handleSendReminder = async () => {
    if (!selectedCart) return;
    setSending(true);
    try {
      const token = localStorage.getItem('jwt_token');
      const headers = token ? { Authorization: `Bearer ${token}` } : {};

      await axios.post(
        `/api/cart/admin/abandoned/${selectedCart.cartId}/remind`,
        { discountCode, discountPercent },
        { headers }
      );

      setToastMsg(`Successfully sent ${discountPercent}% recovery reminder to ${selectedCart.customerEmail}`);
      setSelectedCart(null);
      fetchData();
    } catch (err: any) {
      alert(`Error sending reminder: ${err.message}`);
    } finally {
      setSending(false);
    }
  };

  return (
    <Box sx={{ py: 3 }}>
      {/* Page Title & Refresh */}
      <Box sx={{ mb: 4, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900, color: '#F8FAFC', letterSpacing: '-0.02em' }}>
            Abandoned Cart Recovery & Funnel
          </Typography>
          <Typography variant="body2" sx={{ color: '#94A3B8' }}>
            Track abandoned customer sessions, potential lost revenue, and dispatch personalized win-back promos
          </Typography>
        </Box>
        <Button
          variant="outlined"
          startIcon={<RefreshRounded />}
          onClick={fetchData}
          sx={{ borderColor: '#334155', color: '#94A3B8', '&:hover': { borderColor: '#4F46E5', color: '#F8FAFC' } }}
        >
          Refresh
        </Button>
      </Box>

      {toastMsg && (
        <Alert severity="success" onClose={() => setToastMsg('')} sx={{ mb: 3, borderRadius: 2 }}>
          {toastMsg}
        </Alert>
      )}

      {/* Funnel Metric Stat Cards */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        <Grid item xs={12} sm={6} md={3}>
          <Paper sx={{ p: 3, bgcolor: '#111827', borderRadius: 3, border: '1px solid #1E293B' }}>
            <Stack direction="row" justifyContent="space-between" alignItems="center">
              <Box>
                <Typography variant="caption" sx={{ color: '#94A3B8', fontWeight: 700 }}>
                  ABANDONED CARTS
                </Typography>
                <Typography variant="h4" sx={{ fontWeight: 900, color: '#F87171', mt: 0.5 }}>
                  {funnel?.abandonedCarts ?? 0}
                </Typography>
              </Box>
              <Avatar sx={{ bgcolor: 'rgba(239, 68, 68, 0.1)', color: '#F87171', width: 48, height: 48 }}>
                <TrendingDownRounded />
              </Avatar>
            </Stack>
            <Typography variant="caption" sx={{ color: '#64748B', mt: 1, display: 'block' }}>
              Abandonment rate: {funnel?.abandonedRatePercent ?? 0}%
            </Typography>
          </Paper>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Paper sx={{ p: 3, bgcolor: '#111827', borderRadius: 3, border: '1px solid #1E293B' }}>
            <Stack direction="row" justifyContent="space-between" alignItems="center">
              <Box>
                <Typography variant="caption" sx={{ color: '#94A3B8', fontWeight: 700 }}>
                  REVENUE AT RISK
                </Typography>
                <Typography variant="h4" sx={{ fontWeight: 900, color: '#FBBF24', mt: 0.5 }}>
                  ${(funnel?.abandonedCartValue ?? 0).toFixed(2)}
                </Typography>
              </Box>
              <Avatar sx={{ bgcolor: 'rgba(245, 158, 11, 0.1)', color: '#FBBF24', width: 48, height: 48 }}>
                <AttachMoneyRounded />
              </Avatar>
            </Stack>
            <Typography variant="caption" sx={{ color: '#64748B', mt: 1, display: 'block' }}>
              Active in-flight carts: ${(funnel?.activeCartValue ?? 0).toFixed(2)}
            </Typography>
          </Paper>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Paper sx={{ p: 3, bgcolor: '#111827', borderRadius: 3, border: '1px solid #1E293B' }}>
            <Stack direction="row" justifyContent="space-between" alignItems="center">
              <Box>
                <Typography variant="caption" sx={{ color: '#94A3B8', fontWeight: 700 }}>
                  CHECKOUT CONVERSIONS
                </Typography>
                <Typography variant="h4" sx={{ fontWeight: 900, color: '#34D399', mt: 0.5 }}>
                  {funnel?.convertedCarts ?? 0}
                </Typography>
              </Box>
              <Avatar sx={{ bgcolor: 'rgba(16, 185, 129, 0.1)', color: '#34D399', width: 48, height: 48 }}>
                <ShoppingCartCheckoutRounded />
              </Avatar>
            </Stack>
            <Typography variant="caption" sx={{ color: '#64748B', mt: 1, display: 'block' }}>
              Conversion rate: {funnel?.conversionRatePercent ?? 0}%
            </Typography>
          </Paper>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Paper sx={{ p: 3, bgcolor: '#111827', borderRadius: 3, border: '1px solid #1E293B' }}>
            <Stack direction="row" justifyContent="space-between" alignItems="center">
              <Box>
                <Typography variant="caption" sx={{ color: '#94A3B8', fontWeight: 700 }}>
                  RECOVERY EMAILS SENT
                </Typography>
                <Typography variant="h4" sx={{ fontWeight: 900, color: '#A78BFA', mt: 0.5 }}>
                  {funnel?.remindersSent ?? 0}
                </Typography>
              </Box>
              <Avatar sx={{ bgcolor: 'rgba(124, 58, 237, 0.1)', color: '#A78BFA', width: 48, height: 48 }}>
                <MarkEmailReadRounded />
              </Avatar>
            </Stack>
            <Typography variant="caption" sx={{ color: '#64748B', mt: 1, display: 'block' }}>
              Automated Kafka recovery triggers
            </Typography>
          </Paper>
        </Grid>
      </Grid>

      {/* Abandoned Sessions Table */}
      <Paper sx={{ bgcolor: '#111827', borderRadius: 3, border: '1px solid #1E293B', overflow: 'hidden' }}>
        <Box sx={{ p: 2.5, borderBottom: '1px solid #1E293B' }}>
          <Typography variant="h6" sx={{ fontWeight: 800, color: '#F8FAFC' }}>
            Untouched Inactive Carts
          </Typography>
        </Box>

        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
            <CircularProgress color="primary" />
          </Box>
        ) : carts.length === 0 ? (
          <Box sx={{ p: 6, textAlign: 'center' }}>
            <Typography variant="body1" sx={{ color: '#94A3B8' }}>
              No abandoned carts found! All shoppers are actively converting.
            </Typography>
          </Box>
        ) : (
          <TableContainer>
            <Table>
              <TableHead sx={{ bgcolor: '#0B0F19' }}>
                <TableRow>
                  <TableCell sx={{ color: '#94A3B8', fontWeight: 700 }}>CART ID</TableCell>
                  <TableCell sx={{ color: '#94A3B8', fontWeight: 700 }}>CUSTOMER / SESSION</TableCell>
                  <TableCell sx={{ color: '#94A3B8', fontWeight: 700 }}>ABANDONED ITEMS</TableCell>
                  <TableCell sx={{ color: '#94A3B8', fontWeight: 700 }}>TOTAL VALUE</TableCell>
                  <TableCell sx={{ color: '#94A3B8', fontWeight: 700 }}>INACTIVE TIME</TableCell>
                  <TableCell sx={{ color: '#94A3B8', fontWeight: 700 }}>RECOVERY ACTION</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {carts.map((cart) => (
                  <TableRow key={cart.cartId} hover sx={{ '&:hover': { bgcolor: '#1E293B' } }}>
                    <TableCell sx={{ color: '#F8FAFC', fontWeight: 700 }}>#{cart.cartId}</TableCell>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontWeight: 700, color: '#F8FAFC' }}>
                        {cart.customerEmail}
                      </Typography>
                      <Typography variant="caption" sx={{ color: '#64748B' }}>
                        Token: {cart.sessionToken?.substring(0, 16)}...
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Stack direction="row" spacing={1} alignItems="center">
                        {cart.items?.slice(0, 3).map((item, idx) => (
                          <Tooltip key={idx} title={`${item.productName} (${item.quantity}x)`}>
                            <Avatar
                              src={item.imageUrl}
                              variant="rounded"
                              sx={{ width: 36, height: 36, bgcolor: '#1E293B' }}
                            >
                              {item.productName[0]}
                            </Avatar>
                          </Tooltip>
                        ))}
                        {cart.items && cart.items.length > 3 && (
                          <Chip
                            label={`+${cart.items.length - 3}`}
                            size="small"
                            sx={{ bgcolor: '#1E293B', color: '#94A3B8', fontSize: '0.7rem' }}
                          />
                        )}
                      </Stack>
                    </TableCell>
                    <TableCell sx={{ color: '#06B6D4', fontWeight: 800 }}>
                      ${cart.totalAmount?.toFixed(2)}
                    </TableCell>
                    <TableCell>
                      <Chip
                        icon={<AccessTimeRounded sx={{ fontSize: '14px !important' }} />}
                        label={`${cart.inactiveHours} hrs ago`}
                        size="small"
                        sx={{ bgcolor: 'rgba(239, 68, 68, 0.1)', color: '#F87171', fontWeight: 700 }}
                      />
                    </TableCell>
                    <TableCell>
                      <Button
                        variant="contained"
                        size="small"
                        startIcon={<SendRounded />}
                        onClick={() => setSelectedCart(cart)}
                        sx={{
                          bgcolor: '#4F46E5',
                          fontWeight: 700,
                          borderRadius: 2,
                          textTransform: 'none',
                          fontSize: '0.8rem',
                          '&:hover': { bgcolor: '#4338CA' },
                        }}
                      >
                        Send Promo
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Paper>

      {/* Send Recovery Promo Dialog */}
      <Dialog
        open={Boolean(selectedCart)}
        onClose={() => setSelectedCart(null)}
        PaperProps={{
          sx: { bgcolor: '#111827', color: '#F8FAFC', borderRadius: 3, border: '1px solid #1E293B', minWidth: 420 },
        }}
      >
        <DialogTitle sx={{ fontWeight: 800 }}>Dispatch Win-Back Recovery Campaign</DialogTitle>
        <DialogContent>
          <Typography variant="body2" sx={{ color: '#94A3B8', mb: 3 }}>
            Send an automated notification with an exclusive discount code to entice{' '}
            <strong>{selectedCart?.customerEmail}</strong> to finalize checkout on Cart #{selectedCart?.cartId}.
          </Typography>

          <Stack spacing={2.5}>
            <TextField
              label="Promo Code"
              fullWidth
              value={discountCode}
              onChange={(e) => setDiscountCode(e.target.value.toUpperCase())}
              sx={{
                '& .MuiInputLabel-root': { color: '#94A3B8' },
                '& .MuiOutlinedInput-root': {
                  bgcolor: '#0B0F19',
                  color: '#F8FAFC',
                  '& fieldset': { borderColor: '#334155' },
                },
              }}
            />
            <TextField
              label="Discount Percentage (%)"
              type="number"
              fullWidth
              value={discountPercent}
              onChange={(e) => setDiscountPercent(Number(e.target.value))}
              sx={{
                '& .MuiInputLabel-root': { color: '#94A3B8' },
                '& .MuiOutlinedInput-root': {
                  bgcolor: '#0B0F19',
                  color: '#F8FAFC',
                  '& fieldset': { borderColor: '#334155' },
                },
              }}
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ p: 2.5 }}>
          <Button onClick={() => setSelectedCart(null)} sx={{ color: '#94A3B8' }}>
            Cancel
          </Button>
          <Button
            variant="contained"
            disabled={sending}
            onClick={handleSendReminder}
            startIcon={sending ? <CircularProgress size={16} color="inherit" /> : <SendRounded />}
            sx={{
              bgcolor: '#4F46E5',
              fontWeight: 700,
              '&:hover': { bgcolor: '#4338CA' },
            }}
          >
            {sending ? 'Sending...' : 'Dispatch Reminder'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
