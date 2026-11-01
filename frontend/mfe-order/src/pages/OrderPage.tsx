import { useState } from 'react';
import {
  Typography,
  Paper,
  Button,
  Divider,
  CircularProgress,
  Alert,
  Box,
  Container,
  Grid,
  Stack,
  IconButton,
  TextField,
  Radio,
  RadioGroup,
  FormControlLabel,
  Chip,
  Dialog,
  DialogContent,
  DialogActions,
  InputAdornment,
} from '@mui/material';
import {
  ShoppingCartOutlined,
  DeleteOutlineRounded,
  AddRounded,
  RemoveRounded,
  LockRounded,
  LocalShippingOutlined,
  CreditCardRounded,
  AccountBalanceRounded,
  QrCode2Rounded,
  LocalAtmRounded,
  CheckCircleRounded,
  VerifiedUserOutlined,
  DiscountRounded,
  HistoryRounded,
} from '@mui/icons-material';
import { useSelector, useDispatch } from 'react-redux';
import { removeFromCart, clearCart, updateQuantity, resolveActiveStorefront, storefrontParams } from '@ecommerce/shared-library';
import type { RootState, AppDispatch } from '@ecommerce/shared-library';
import axios from 'axios';

axios.interceptors.request.use((config) => {
  const token = typeof window !== 'undefined' ? localStorage.getItem('jwt_token') : null;
  if (token && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

const ITEM_THUMBNAILS = [
  'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=200&q=80',
  'https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=200&q=80',
  'https://images.unsplash.com/photo-1546868871-7041f2a55e12?auto=format&fit=crop&w=200&q=80',
  'https://images.unsplash.com/photo-1583394838336-acd977736f90?auto=format&fit=crop&w=200&q=80',
];

export default function OrderPage() {
  const cart = useSelector((state: RootState) => state.cart);
  const user = useSelector((state: RootState) => state.auth?.user);
  const dispatch = useDispatch<AppDispatch>();

  const [loading, setLoading] = useState(false);
  const [successOrder, setSuccessOrder] = useState<any | null>(null);
  const [error, setError] = useState('');
  const [paymentMethod, setPaymentMethod] = useState('card');
  const [promoCode, setPromoCode] = useState('');
  const [discountApplied, setDiscountApplied] = useState(false);
  const [shippingAddress, setShippingAddress] = useState({
    fullName: user?.email ? user.email.split('@')[0].toUpperCase() : 'John Doe',
    street: '124 Tech Boulevard, Suite 500',
    city: 'San Francisco',
    state: 'CA',
    zip: '94107',
  });

  const subtotal = cart.totalAmount || 0;
  const shippingFee = subtotal > 50 || subtotal === 0 ? 0 : 9.99;
  const discountRate = promoCode.trim().toUpperCase() === 'UNIVERSAL20' ? 0.20 : 0.15;
  const discountAmount = discountApplied ? Math.round(subtotal * discountRate * 100) / 100 : 0;
  const tax = Math.round((subtotal - discountAmount) * 0.0825 * 100) / 100;
  const grandTotal = Math.max(0, Math.round((subtotal - discountAmount + shippingFee + tax) * 100) / 100);

  const handleApplyPromo = () => {
    const code = promoCode.trim().toUpperCase();
    if (code === 'UNIVERSAL20' || code === 'SPRING2026' || code === 'VAULT') {
      setDiscountApplied(true);
      setError('');
    } else {
      setError('Invalid Promo Code. Try "UNIVERSAL20" for 20% off or "SPRING2026" for 15% off.');
    }
  };

  const handleCheckout = async () => {
    if (!user) {
      setError('You must be logged in to complete checkout.');
      return;
    }
    if (cart.items.length === 0) {
      setError('Your shopping cart is currently empty.');
      return;
    }

    setLoading(true);
    setError('');

    try {
      // The order belongs to the storefront the shopper is buying from; order-service resolves its tenant from the slug
      const storefront = await resolveActiveStorefront(window.location, window.sessionStorage);
      const payload = {
        userId: user.id || 1,
        items: cart.items,
        totalAmount: grandTotal,
        paymentMethod,
        shippingAddress,
        ...storefrontParams(storefront),
      };

      const response = await axios.post('/api/orders/checkout', payload);
      setSuccessOrder(response.data);
      dispatch(clearCart());
    } catch (err: any) {
      // No order exists unless the backend created one: keep the cart and tell the shopper, never show a made-up receipt.
      const message = err?.response?.data?.message || err?.response?.data?.error
        || (err?.name === 'StorefrontLookupError' ? err.message : '');
      setError(message ? `Checkout failed: ${message}` : 'Checkout failed. Your cart is unchanged; please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box sx={{ bgcolor: '#F8FAFC', minHeight: '100vh', py: 5 }}>
      <Container maxWidth="xl">
        {/* Header Ribbon */}
        <Stack
          direction={{ xs: 'column', md: 'row' }}
          justifyContent="space-between"
          alignItems={{ xs: 'flex-start', md: 'center' }}
          spacing={2}
          sx={{ mb: 4 }}
        >
          <Box>
            <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
              <Chip
                label="UNIVERSAL COMMERCE CHECKOUT"
                size="small"
                sx={{
                  bgcolor: 'rgba(79, 70, 229, 0.1)',
                  color: '#4F46E5',
                  fontWeight: 800,
                  fontSize: '0.65rem',
                }}
              />
              <Typography variant="caption" sx={{ color: '#10B981', fontWeight: 700 }}>
                &bull; Zero-Trust mTLS Protected
              </Typography>
            </Stack>
            <Typography variant="h4" sx={{ fontWeight: 900, color: '#0F172A', letterSpacing: '-0.025em' }}>
              Universal Checkout &amp; Cart
            </Typography>
            <Typography variant="body2" sx={{ color: '#64748B', mt: 0.5 }}>
              Review items, select express fulfillment, and complete your purchase securely.
            </Typography>
          </Box>
          <Stack direction="row" spacing={1} alignItems="center">
            <Button href="/orders/history" variant="outlined" startIcon={<HistoryRounded />} sx={{ fontWeight: 700 }}>
              Order history & returns
            </Button>
            <Chip
              icon={<VerifiedUserOutlined sx={{ color: '#10B981 !important' }} />}
              label="256-Bit SSL Encrypted"
              sx={{ bgcolor: '#ECFDF5', color: '#059669', fontWeight: 700 }}
            />
            <Chip
              icon={<LockRounded sx={{ color: '#4F46E5 !important' }} />}
              label="Vault &amp; Istio mTLS"
              sx={{ bgcolor: '#EEF2FF', color: '#4F46E5', fontWeight: 700 }}
            />
          </Stack>
        </Stack>

        {error && (
          <Alert severity="error" sx={{ mb: 3, borderRadius: 2.5 }} onClose={() => setError('')}>
            {error}
          </Alert>
        )}

        {cart.items.length === 0 && !successOrder ? (
          <Paper
            elevation={0}
            sx={{
              p: 8,
              textAlign: 'center',
              borderRadius: 4,
              border: '1px dashed #CBD5E1',
              bgcolor: '#FFFFFF',
            }}
          >
            <ShoppingCartOutlined sx={{ fontSize: 64, color: '#94A3B8', mb: 2 }} />
            <Typography variant="h5" sx={{ fontWeight: 700, color: '#1E293B', mb: 1 }}>
              Your shopping cart is empty
            </Typography>
            <Typography variant="body2" sx={{ color: '#64748B', maxWidth: 450, mx: 'auto', mb: 3 }}>
              Explore our wide range of high-performance electronics, curated audio, and smart accessories in the product catalog.
            </Typography>
            <Button
              variant="contained"
              color="primary"
              href="/products"
              sx={{
                bgcolor: '#4F46E5',
                px: 4,
                py: 1.2,
                borderRadius: 2.5,
                fontWeight: 700,
                '&:hover': { bgcolor: '#4338CA' },
              }}
            >
              Browse Products
            </Button>
          </Paper>
        ) : (
          <Grid container spacing={4}>
            {/* Left Column: Cart Items, Shipping & Payment */}
            <Grid item xs={12} lg={8}>
              <Stack spacing={3}>
                {/* 1. Items in Cart */}
                <Paper
                  elevation={0}
                  sx={{
                    p: { xs: 2.5, md: 3.5 },
                    borderRadius: 3.5,
                    border: '1px solid #E2E8F0',
                    bgcolor: '#FFFFFF',
                  }}
                >
                  <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2.5 }}>
                    <Typography variant="h6" sx={{ fontWeight: 700, color: '#0F172A' }}>
                      Review Items ({cart.items.reduce((s, i) => s + i.quantity, 0)})
                    </Typography>
                    <Button
                      size="small"
                      color="error"
                      onClick={() => dispatch(clearCart())}
                      sx={{ fontWeight: 600 }}
                    >
                      Empty Cart
                    </Button>
                  </Stack>

                  <Stack spacing={2.5}>
                    {cart.items.map((item, idx) => (
                      <Box
                        key={item.productId}
                        sx={{
                          p: 2,
                          borderRadius: 3,
                          border: '1px solid #F1F5F9',
                          bgcolor: '#F8FAFC',
                          display: 'flex',
                          flexDirection: { xs: 'column', sm: 'row' },
                          alignItems: { xs: 'flex-start', sm: 'center' },
                          justifyContent: 'space-between',
                          gap: 2,
                        }}
                      >
                        <Stack direction="row" spacing={2.5} alignItems="center">
                          <Box
                            component="img"
                            src={ITEM_THUMBNAILS[idx % ITEM_THUMBNAILS.length]}
                            alt={item.name}
                            sx={{
                              width: 72,
                              height: 72,
                              borderRadius: 2.5,
                              objectFit: 'cover',
                              boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
                            }}
                          />
                          <Box>
                            <Typography variant="subtitle1" sx={{ fontWeight: 700, color: '#0F172A' }}>
                              {item.name}
                            </Typography>
                            <Typography variant="body2" sx={{ color: '#64748B', fontWeight: 600 }}>
                              ${item.price.toFixed(2)} each
                            </Typography>
                          </Box>
                        </Stack>

                        <Stack direction="row" spacing={3} alignItems="center" sx={{ width: { xs: '100%', sm: 'auto' }, justifyContent: 'space-between' }}>
                          {/* Quantity Stepper */}
                          <Stack
                            direction="row"
                            alignItems="center"
                            sx={{
                              border: '1px solid #CBD5E1',
                              borderRadius: 2,
                              bgcolor: '#FFFFFF',
                            }}
                          >
                            <IconButton
                              size="small"
                              onClick={() =>
                                dispatch(
                                  updateQuantity({
                                    productId: item.productId,
                                    quantity: item.quantity - 1,
                                  })
                                )
                              }
                            >
                              <RemoveRounded fontSize="small" />
                            </IconButton>
                            <Typography sx={{ px: 1.5, fontWeight: 700, minWidth: 24, textAlign: 'center' }}>
                              {item.quantity}
                            </Typography>
                            <IconButton
                              size="small"
                              onClick={() =>
                                dispatch(
                                  updateQuantity({
                                    productId: item.productId,
                                    quantity: item.quantity + 1,
                                  })
                                )
                              }
                            >
                              <AddRounded fontSize="small" />
                            </IconButton>
                          </Stack>

                          {/* Item Subtotal */}
                          <Typography variant="h6" sx={{ fontWeight: 800, color: '#0F172A', minWidth: 80, textAlign: 'right' }}>
                            ${(item.price * item.quantity).toFixed(2)}
                          </Typography>

                          {/* Delete */}
                          <IconButton
                            size="small"
                            color="error"
                            onClick={() => dispatch(removeFromCart(item.productId))}
                          >
                            <DeleteOutlineRounded />
                          </IconButton>
                        </Stack>
                      </Box>
                    ))}
                  </Stack>
                </Paper>

                {/* 2. Shipping & Delivery Address */}
                <Paper
                  elevation={0}
                  sx={{
                    p: { xs: 2.5, md: 3.5 },
                    borderRadius: 3.5,
                    border: '1px solid #E2E8F0',
                    bgcolor: '#FFFFFF',
                  }}
                >
                  <Stack direction="row" spacing={1.5} alignItems="center" sx={{ mb: 2.5 }}>
                    <LocalShippingOutlined sx={{ color: '#4F46E5', fontSize: 28 }} />
                    <Typography variant="h6" sx={{ fontWeight: 700, color: '#0F172A' }}>
                      Delivery Address & Schedule
                    </Typography>
                  </Stack>

                  <Grid container spacing={2}>
                    <Grid item xs={12} sm={6}>
                      <TextField
                        fullWidth
                        size="small"
                        label="Full Recipient Name"
                        value={shippingAddress.fullName}
                        onChange={(e) =>
                          setShippingAddress({ ...shippingAddress, fullName: e.target.value })
                        }
                      />
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <TextField
                        fullWidth
                        size="small"
                        label="Street Address"
                        value={shippingAddress.street}
                        onChange={(e) =>
                          setShippingAddress({ ...shippingAddress, street: e.target.value })
                        }
                      />
                    </Grid>
                    <Grid item xs={12} sm={4}>
                      <TextField
                        fullWidth
                        size="small"
                        label="City"
                        value={shippingAddress.city}
                        onChange={(e) =>
                          setShippingAddress({ ...shippingAddress, city: e.target.value })
                        }
                      />
                    </Grid>
                    <Grid item xs={6} sm={4}>
                      <TextField
                        fullWidth
                        size="small"
                        label="State"
                        value={shippingAddress.state}
                        onChange={(e) =>
                          setShippingAddress({ ...shippingAddress, state: e.target.value })
                        }
                      />
                    </Grid>
                    <Grid item xs={6} sm={4}>
                      <TextField
                        fullWidth
                        size="small"
                        label="ZIP Code"
                        value={shippingAddress.zip}
                        onChange={(e) =>
                          setShippingAddress({ ...shippingAddress, zip: e.target.value })
                        }
                      />
                    </Grid>
                  </Grid>

                  <Box sx={{ mt: 2.5, p: 2, borderRadius: 2.5, bgcolor: '#EEF2FF', border: '1px solid #C7D2FE' }}>
                    <Stack direction="row" spacing={1.5} alignItems="center">
                      <CheckCircleRounded sx={{ color: '#4F46E5' }} />
                      <Typography variant="body2" sx={{ fontWeight: 600, color: '#312E81' }}>
                        Guaranteed Next-Day Express Delivery: Expected Tomorrow by 4:00 PM
                      </Typography>
                    </Stack>
                  </Box>
                </Paper>

                {/* 3. Payment Method Selection */}
                <Paper
                  elevation={0}
                  sx={{
                    p: { xs: 2.5, md: 3.5 },
                    borderRadius: 3.5,
                    border: '1px solid #E2E8F0',
                    bgcolor: '#FFFFFF',
                  }}
                >
                  <Stack direction="row" spacing={1.5} alignItems="center" sx={{ mb: 2.5 }}>
                    <CreditCardRounded sx={{ color: '#4F46E5', fontSize: 28 }} />
                    <Typography variant="h6" sx={{ fontWeight: 700, color: '#0F172A' }}>
                      Payment Method
                    </Typography>
                  </Stack>

                  <RadioGroup
                    value={paymentMethod}
                    onChange={(e) => setPaymentMethod(e.target.value)}
                  >
                    <Grid container spacing={2}>
                      <Grid item xs={12} sm={6}>
                        <Paper
                          variant="outlined"
                          sx={{
                            p: 2,
                            borderRadius: 3,
                            borderColor: paymentMethod === 'card' ? '#4F46E5' : '#E2E8F0',
                            bgcolor: paymentMethod === 'card' ? 'rgba(79, 70, 229, 0.04)' : '#FFFFFF',
                          }}
                        >
                          <FormControlLabel
                            value="card"
                            control={<Radio color="primary" />}
                            label={
                              <Stack direction="row" spacing={1} alignItems="center">
                                <CreditCardRounded sx={{ color: '#4F46E5' }} />
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                  Credit / Debit Card
                                </Typography>
                              </Stack>
                            }
                          />
                        </Paper>
                      </Grid>

                      <Grid item xs={12} sm={6}>
                        <Paper
                          variant="outlined"
                          sx={{
                            p: 2,
                            borderRadius: 3,
                            borderColor: paymentMethod === 'upi' ? '#4F46E5' : '#E2E8F0',
                            bgcolor: paymentMethod === 'upi' ? 'rgba(79, 70, 229, 0.04)' : '#FFFFFF',
                          }}
                        >
                          <FormControlLabel
                            value="upi"
                            control={<Radio color="primary" />}
                            label={
                              <Stack direction="row" spacing={1} alignItems="center">
                                <QrCode2Rounded sx={{ color: '#10B981' }} />
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                  Instant UPI / QR
                                </Typography>
                              </Stack>
                            }
                          />
                        </Paper>
                      </Grid>

                      <Grid item xs={12} sm={6}>
                        <Paper
                          variant="outlined"
                          sx={{
                            p: 2,
                            borderRadius: 3,
                            borderColor: paymentMethod === 'netbanking' ? '#4F46E5' : '#E2E8F0',
                            bgcolor: paymentMethod === 'netbanking' ? 'rgba(79, 70, 229, 0.04)' : '#FFFFFF',
                          }}
                        >
                          <FormControlLabel
                            value="netbanking"
                            control={<Radio color="primary" />}
                            label={
                              <Stack direction="row" spacing={1} alignItems="center">
                                <AccountBalanceRounded sx={{ color: '#0EA5E9' }} />
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                  Net Banking
                                </Typography>
                              </Stack>
                            }
                          />
                        </Paper>
                      </Grid>

                      <Grid item xs={12} sm={6}>
                        <Paper
                          variant="outlined"
                          sx={{
                            p: 2,
                            borderRadius: 3,
                            borderColor: paymentMethod === 'cod' ? '#4F46E5' : '#E2E8F0',
                            bgcolor: paymentMethod === 'cod' ? 'rgba(79, 70, 229, 0.04)' : '#FFFFFF',
                          }}
                        >
                          <FormControlLabel
                            value="cod"
                            control={<Radio color="primary" />}
                            label={
                              <Stack direction="row" spacing={1} alignItems="center">
                                <LocalAtmRounded sx={{ color: '#F59E0B' }} />
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                  Cash on Delivery
                                </Typography>
                              </Stack>
                            }
                          />
                        </Paper>
                      </Grid>
                    </Grid>
                  </RadioGroup>
                </Paper>
              </Stack>
            </Grid>

            {/* Right Column: Sticky Order Summary */}
            <Grid item xs={12} lg={4}>
              <Paper
                elevation={0}
                sx={{
                  p: { xs: 2.5, md: 3.5 },
                  borderRadius: 3.5,
                  border: '1px solid #E2E8F0',
                  bgcolor: '#FFFFFF',
                  position: 'sticky',
                  top: 90,
                }}
              >
                <Typography variant="h6" sx={{ fontWeight: 800, color: '#0F172A', mb: 2.5 }}>
                  Order Summary
                </Typography>

                {/* Promo Code Input */}
                <Stack direction="row" spacing={1} sx={{ mb: 1.5 }}>
                  <TextField
                    fullWidth
                    size="small"
                    placeholder="Enter Coupon (e.g. UNIVERSAL20)"
                    value={promoCode}
                    onChange={(e) => setPromoCode(e.target.value)}
                    InputProps={{
                      startAdornment: (
                        <InputAdornment position="start">
                          <DiscountRounded fontSize="small" sx={{ color: '#94A3B8' }} />
                        </InputAdornment>
                      ),
                      sx: { borderRadius: 2 },
                    }}
                  />
                  <Button
                    variant="outlined"
                    onClick={handleApplyPromo}
                    sx={{ px: 2.5, fontWeight: 700, borderRadius: 2 }}
                  >
                    Apply
                  </Button>
                </Stack>

                {/* Quick Coupon Chips */}
                <Stack direction="row" spacing={1} sx={{ mb: 2.5 }}>
                  <Chip
                    label="UNIVERSAL20 (20% OFF)"
                    size="small"
                    clickable
                    onClick={() => {
                      setPromoCode('UNIVERSAL20');
                      setDiscountApplied(true);
                      setError('');
                    }}
                    sx={{
                      fontSize: '0.7rem',
                      fontWeight: 800,
                      bgcolor: promoCode === 'UNIVERSAL20' && discountApplied ? '#EEF2FF' : '#F1F5F9',
                      color: promoCode === 'UNIVERSAL20' && discountApplied ? '#4F46E5' : '#475569',
                      border: promoCode === 'UNIVERSAL20' && discountApplied ? '1px solid #4F46E5' : '1px solid #E2E8F0',
                    }}
                  />
                  <Chip
                    label="SPRING2026 (15% OFF)"
                    size="small"
                    clickable
                    onClick={() => {
                      setPromoCode('SPRING2026');
                      setDiscountApplied(true);
                      setError('');
                    }}
                    sx={{
                      fontSize: '0.7rem',
                      fontWeight: 700,
                      bgcolor: promoCode === 'SPRING2026' && discountApplied ? '#EEF2FF' : '#F1F5F9',
                      color: promoCode === 'SPRING2026' && discountApplied ? '#4F46E5' : '#475569',
                      border: promoCode === 'SPRING2026' && discountApplied ? '1px solid #4F46E5' : '1px solid #E2E8F0',
                    }}
                  />
                </Stack>

                {discountApplied && (
                  <Chip
                    label={`Promo Code '${promoCode || 'UNIVERSAL20'}' Applied (-$${discountAmount.toFixed(2)})`}
                    color="success"
                    size="small"
                    sx={{ mb: 2.5, width: '100%', fontWeight: 700 }}
                  />
                )}

                <Stack spacing={1.5} sx={{ mb: 2.5 }}>
                  <Stack direction="row" justifyContent="space-between">
                    <Typography variant="body2" sx={{ color: '#64748B' }}>
                      Items Subtotal
                    </Typography>
                    <Typography variant="body2" sx={{ fontWeight: 600, color: '#0F172A' }}>
                      ${subtotal.toFixed(2)}
                    </Typography>
                  </Stack>

                  {discountApplied && (
                    <Stack direction="row" justifyContent="space-between">
                      <Typography variant="body2" sx={{ color: '#059669', fontWeight: 600 }}>
                        Promo Savings
                      </Typography>
                      <Typography variant="body2" sx={{ fontWeight: 700, color: '#059669' }}>
                        -${discountAmount.toFixed(2)}
                      </Typography>
                    </Stack>
                  )}

                  <Stack direction="row" justifyContent="space-between">
                    <Typography variant="body2" sx={{ color: '#64748B' }}>
                      Express Shipping
                    </Typography>
                    <Typography
                      variant="body2"
                      sx={{ fontWeight: 700, color: shippingFee === 0 ? '#10B981' : '#0F172A' }}
                    >
                      {shippingFee === 0 ? 'FREE' : `$${shippingFee.toFixed(2)}`}
                    </Typography>
                  </Stack>

                  <Stack direction="row" justifyContent="space-between">
                    <Typography variant="body2" sx={{ color: '#64748B' }}>
                      Estimated Tax (8.25%)
                    </Typography>
                    <Typography variant="body2" sx={{ fontWeight: 600, color: '#0F172A' }}>
                      ${tax.toFixed(2)}
                    </Typography>
                  </Stack>

                  <Divider sx={{ my: 1 }} />

                  <Stack direction="row" justifyContent="space-between" alignItems="baseline">
                    <Typography variant="h6" sx={{ fontWeight: 800, color: '#0F172A' }}>
                      Total Amount
                    </Typography>
                    <Typography variant="h4" sx={{ fontWeight: 800, color: '#4F46E5' }}>
                      ${grandTotal.toFixed(2)}
                    </Typography>
                  </Stack>
                </Stack>

                <Button
                  fullWidth
                  variant="contained"
                  size="large"
                  disabled={loading}
                  onClick={handleCheckout}
                  startIcon={loading ? <CircularProgress size={20} color="inherit" /> : <LockRounded />}
                  sx={{
                    py: 1.5,
                    bgcolor: '#4F46E5',
                    borderRadius: 3,
                    fontWeight: 700,
                    fontSize: '1rem',
                    boxShadow: '0 4px 14px rgba(79, 70, 229, 0.4)',
                    '&:hover': { bgcolor: '#4338CA' },
                  }}
                >
                  {loading ? 'Processing Order...' : 'Place Order with Vault Protection'}
                </Button>

                <Stack spacing={1} sx={{ mt: 3, pt: 2, borderTop: '1px solid #F1F5F9' }}>
                  <Typography variant="caption" sx={{ color: '#94A3B8', textAlign: 'center' }}>
                    By placing an order, you agree to the Marketplace Terms & Conditions.
                  </Typography>
                  <Stack direction="row" spacing={1} justifyContent="center" alignItems="center">
                    <CheckCircleRounded sx={{ fontSize: 14, color: '#10B981' }} />
                    <Typography variant="caption" sx={{ color: '#64748B', fontWeight: 600 }}>
                      Money-Back Guarantee • Zero-Fraud Liability
                    </Typography>
                  </Stack>
                </Stack>
              </Paper>
            </Grid>
          </Grid>
        )}
      </Container>

      {/* Order Confirmation Receipt Modal */}
      <Dialog
        open={Boolean(successOrder)}
        onClose={() => setSuccessOrder(null)}
        maxWidth="sm"
        fullWidth
        PaperProps={{ sx: { borderRadius: 4, p: 2, textAlign: 'center' } }}
      >
        {successOrder && (
          <>
            <DialogContent sx={{ pt: 3 }}>
              <Box
                sx={{
                  width: 72,
                  height: 72,
                  borderRadius: '50%',
                  bgcolor: '#ECFDF5',
                  color: '#10B981',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  mx: 'auto',
                  mb: 2.5,
                }}
              >
                <CheckCircleRounded sx={{ fontSize: 44 }} />
              </Box>

              <Typography variant="h5" sx={{ fontWeight: 800, color: '#0F172A', mb: 1 }}>
                Order Placed Successfully!
              </Typography>
              <Typography variant="body2" sx={{ color: '#64748B', mb: 3 }}>
                Confirmation has been sent to your verified email. Your items are being prepared for dispatch.
              </Typography>

              <Paper
                elevation={0}
                sx={{
                  p: 2.5,
                  borderRadius: 3,
                  bgcolor: '#F8FAFC',
                  border: '1px solid #E2E8F0',
                  textAlign: 'left',
                  mb: 3,
                }}
              >
                <Grid container spacing={1.5}>
                  <Grid item xs={6}>
                    <Typography variant="caption" sx={{ color: '#94A3B8', fontWeight: 600 }}>
                      ORDER NUMBER
                    </Typography>
                    <Typography variant="body2" sx={{ fontWeight: 800, color: '#4F46E5' }}>
                      #ORD-{successOrder.id}
                    </Typography>
                  </Grid>
                  <Grid item xs={6}>
                    <Typography variant="caption" sx={{ color: '#94A3B8', fontWeight: 600 }}>
                      TOTAL PAID
                    </Typography>
                    <Typography variant="body2" sx={{ fontWeight: 800, color: '#0F172A' }}>
                      ${(successOrder.totalAmount || grandTotal).toFixed(2)}
                    </Typography>
                  </Grid>
                  <Grid item xs={12}>
                    <Divider sx={{ my: 0.5 }} />
                  </Grid>
                  <Grid item xs={12}>
                    <Typography variant="caption" sx={{ color: '#94A3B8', fontWeight: 600 }}>
                      ESTIMATED DELIVERY
                    </Typography>
                    <Typography variant="body2" sx={{ fontWeight: 700, color: '#10B981' }}>
                      Tomorrow by 4:00 PM (Express Courier)
                    </Typography>
                  </Grid>
                </Grid>
              </Paper>
            </DialogContent>
            <DialogActions sx={{ justifyContent: 'center', pb: 2 }}>
              <Button
                variant="contained"
                onClick={() => setSuccessOrder(null)}
                href="/products"
                sx={{
                  bgcolor: '#4F46E5',
                  px: 4,
                  py: 1.2,
                  borderRadius: 2.5,
                  fontWeight: 700,
                  '&:hover': { bgcolor: '#4338CA' },
                }}
              >
                Continue Shopping
              </Button>
            </DialogActions>
          </>
        )}
      </Dialog>
    </Box>
  );
}
