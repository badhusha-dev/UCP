import React, { useState, useEffect } from 'react';
import {
  Box,
  Container,
  Grid,
  Paper,
  Typography,
  Button,
  IconButton,
  Divider,
  Stack,
  Chip,
  TextField,
  Alert,
  CircularProgress,
  Tooltip,
} from '@mui/material';
import {
  ShoppingCartOutlined,
  DeleteOutlineRounded,
  AddRounded,
  RemoveRounded,
  CheckCircleRounded,
  ArrowForwardRounded,
  DiscountRounded,
  LockRounded,
  StorefrontRounded,
  RefreshRounded,
  WarningAmberRounded,
} from '@mui/icons-material';
import { useSelector, useDispatch } from 'react-redux';
import { useNavigate } from 'react-router-dom';
import type { RootState, AppDispatch } from '@ecommerce/shared-library';
import { addToCart, removeFromCart, clearCart, updateQuantity } from '@ecommerce/shared-library';
import axios from 'axios';

interface BackendCartItem {
  id: number;
  productId: number;
  sku: string;
  productName: string;
  unitPrice: number;
  quantity: number;
  subtotal: number;
  imageUrl?: string;
}

interface ValidationItem {
  productId: number;
  sku: string;
  productName: string;
  requestedQuantity: number;
  availableStock: number;
  unitPrice: number;
  inStock: boolean;
  statusMessage: string;
}

const DEFAULT_IMAGE = 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=400&q=80';

export default function CartPage() {
  const dispatch = useDispatch<AppDispatch>();
  const navigate = useNavigate();
  const reduxCart = useSelector((state: RootState) => state.cart);
  const user = useSelector((state: RootState) => state.auth?.user);

  const [backendItems, setBackendItems] = useState<BackendCartItem[]>([]);
  const [validationItems, setValidationItems] = useState<ValidationItem[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [promoCode, setPromoCode] = useState<string>('');
  const [discountPercent, setDiscountPercent] = useState<number>(0);
  const [promoMessage, setPromoMessage] = useState<string>('');
  const [errorMsg, setErrorMsg] = useState<string>('');

  const getSessionToken = () => {
    let token = localStorage.getItem('cart_session_token');
    if (!token) {
      token = 'sess_' + Math.random().toString(36).substring(2, 12);
      localStorage.setItem('cart_session_token', token);
    }
    return token;
  };

  const fetchCartAndValidate = async () => {
    setLoading(true);
    setErrorMsg('');
    try {
      const sessionToken = getSessionToken();
      const jwtToken = localStorage.getItem('jwt_token');
      const headers: Record<string, string> = {
        'X-Cart-Session': sessionToken,
      };
      if (jwtToken) {
        headers['Authorization'] = `Bearer ${jwtToken}`;
      }

      // If user has local items in Redux, sync them to backend cart
      if (reduxCart?.items && reduxCart.items.length > 0) {
        const syncPayload = {
          sessionToken,
          items: reduxCart.items.map((i: any) => ({
            productId: i.productId,
            sku: `SKU-${i.productId}`,
            productName: i.name || i.productName || 'Catalog Product',
            unitPrice: i.price || i.unitPrice || 0,
            quantity: i.quantity || 1,
            imageUrl: i.imageUrl || DEFAULT_IMAGE,
          })),
        };
        await axios.post('/api/cart/sync', syncPayload, { headers });
      }

      // Fetch authoritative backend cart
      const cartRes = await axios.get('/api/cart', { headers });
      if (cartRes.data && cartRes.data.items) {
        setBackendItems(cartRes.data.items);
      }

      // Fetch live stock validation against inventory-service
      const valRes = await axios.get('/api/cart/validate', { headers });
      if (valRes.data && valRes.data.items) {
        setValidationItems(valRes.data.items);
      }
    } catch (err: any) {
      console.warn('Backend cart service query:', err.message);
      // Fallback to redux items if backend unavailable
      if (reduxCart?.items) {
        setBackendItems(
          reduxCart.items.map((item: any, idx: number) => ({
            id: idx + 1,
            productId: item.productId,
            sku: `SKU-${item.productId}`,
            productName: item.name || 'Product',
            unitPrice: item.price || 0,
            quantity: item.quantity,
            subtotal: (item.price || 0) * item.quantity,
            imageUrl: DEFAULT_IMAGE,
          }))
        );
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCartAndValidate();
  }, []);

  const handleQuantityChange = async (productId: number, newQty: number, itemId?: number) => {
    dispatch(updateQuantity({ productId, quantity: newQty }));
    try {
      const sessionToken = getSessionToken();
      const jwtToken = localStorage.getItem('jwt_token');
      const headers: Record<string, string> = { 'X-Cart-Session': sessionToken };
      if (jwtToken) headers['Authorization'] = `Bearer ${jwtToken}`;

      if (itemId) {
        await axios.put(`/api/cart/items/${itemId}`, { quantity: newQty }, { headers });
      }
      fetchCartAndValidate();
    } catch (err) {
      console.warn('Failed to update quantity on backend', err);
    }
  };

  const handleRemoveItem = async (productId: number, itemId?: number) => {
    dispatch(removeFromCart(productId));
    try {
      const sessionToken = getSessionToken();
      const jwtToken = localStorage.getItem('jwt_token');
      const headers: Record<string, string> = { 'X-Cart-Session': sessionToken };
      if (jwtToken) headers['Authorization'] = `Bearer ${jwtToken}`;

      if (itemId) {
        await axios.delete(`/api/cart/items/${itemId}`, { headers });
      }
      fetchCartAndValidate();
    } catch (err) {
      console.warn('Failed to remove item on backend', err);
    }
  };

  const handleClearCart = async () => {
    dispatch(clearCart());
    try {
      const sessionToken = getSessionToken();
      const jwtToken = localStorage.getItem('jwt_token');
      const headers: Record<string, string> = { 'X-Cart-Session': sessionToken };
      if (jwtToken) headers['Authorization'] = `Bearer ${jwtToken}`;
      await axios.delete('/api/cart', { headers });
      setBackendItems([]);
      setValidationItems([]);
    } catch (err) {
      console.warn('Failed to clear cart on backend', err);
    }
  };

  const handleApplyPromo = () => {
    const code = promoCode.trim().toUpperCase();
    if (code === 'UNIVERSAL20') {
      setDiscountPercent(20);
      setPromoMessage('20% Universal Commerce discount applied!');
      setErrorMsg('');
    } else if (code === 'COMEBACK15' || code === 'SAVE15') {
      setDiscountPercent(15);
      setPromoMessage('15% Customer Recovery discount applied!');
      setErrorMsg('');
    } else {
      setDiscountPercent(0);
      setErrorMsg('Invalid discount code. Try "UNIVERSAL20" or "COMEBACK15".');
      setPromoMessage('');
    }
  };

  const subtotal = backendItems.reduce((sum, item) => sum + (item.unitPrice * item.quantity), 0);
  const discountAmount = Math.round(subtotal * (discountPercent / 100) * 100) / 100;
  const shippingFee = subtotal > 100 || subtotal === 0 ? 0 : 9.99;
  const tax = Math.round((subtotal - discountAmount) * 0.0825 * 100) / 100;
  const grandTotal = Math.max(0, Math.round((subtotal - discountAmount + shippingFee + tax) * 100) / 100);

  const totalItemCount = backendItems.reduce((sum, item) => sum + item.quantity, 0);

  return (
    <Box sx={{ minHeight: '80vh', py: 4 }}>
      {/* Header Banner */}
      <Box sx={{ mb: 4, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 2 }}>
        <Box>
          <Stack direction="row" spacing={1.5} alignItems="center">
            <Box
              sx={{
                width: 44,
                height: 44,
                borderRadius: 2.5,
                background: 'linear-gradient(135deg, #4F46E5 0%, #06B6D4 100%)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                boxShadow: '0 4px 14px rgba(79, 70, 229, 0.3)',
              }}
            >
              <ShoppingCartOutlined sx={{ color: '#FFFFFF', fontSize: 24 }} />
            </Box>
            <Box>
              <Typography variant="h4" sx={{ fontWeight: 900, color: '#F8FAFC', letterSpacing: '-0.02em' }}>
                Shopping Basket
              </Typography>
              <Typography variant="body2" sx={{ color: '#94A3B8' }}>
                {totalItemCount} {totalItemCount === 1 ? 'item' : 'items'} in your persistent customer cart
              </Typography>
            </Box>
          </Stack>
        </Box>

        <Stack direction="row" spacing={1.5}>
          <Button
            variant="outlined"
            size="small"
            startIcon={<RefreshRounded />}
            onClick={fetchCartAndValidate}
            sx={{
              borderColor: '#334155',
              color: '#94A3B8',
              '&:hover': { borderColor: '#4F46E5', color: '#F8FAFC' },
            }}
          >
            Refresh Stock
          </Button>
          {backendItems.length > 0 && (
            <Button
              variant="outlined"
              color="error"
              size="small"
              startIcon={<DeleteOutlineRounded />}
              onClick={handleClearCart}
              sx={{ textTransform: 'none', fontWeight: 600 }}
            >
              Clear Basket
            </Button>
          )}
        </Stack>
      </Box>

      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 12 }}>
          <CircularProgress color="primary" />
        </Box>
      ) : backendItems.length === 0 ? (
        <Paper
          sx={{
            p: 8,
            textAlign: 'center',
            bgcolor: '#111827',
            borderRadius: 4,
            border: '1px dashed #334155',
          }}
        >
          <Box
            sx={{
              width: 80,
              height: 80,
              borderRadius: '50%',
              bgcolor: 'rgba(79, 70, 229, 0.1)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 24px',
            }}
          >
            <ShoppingCartOutlined sx={{ fontSize: 40, color: '#6366F1' }} />
          </Box>
          <Typography variant="h5" sx={{ fontWeight: 800, color: '#F8FAFC', mb: 1 }}>
            Your Cart is Empty
          </Typography>
          <Typography variant="body1" sx={{ color: '#94A3B8', mb: 4, maxWidth: 460, mx: 'auto' }}>
            Explore our curated catalog of smart electronics, audio gear, and lifestyle products to start shopping.
          </Typography>
          <Button
            variant="contained"
            size="large"
            onClick={() => navigate('/products')}
            startIcon={<StorefrontRounded />}
            sx={{
              bgcolor: '#4F46E5',
              fontWeight: 700,
              borderRadius: 3,
              px: 4,
              py: 1.5,
              '&:hover': { bgcolor: '#4338CA' },
            }}
          >
            Explore Catalog
          </Button>
        </Paper>
      ) : (
        <Grid container spacing={4}>
          {/* Cart Line Items Column */}
          <Grid item xs={12} lg={8}>
            <Stack spacing={2.5}>
              {backendItems.map((item) => {
                const validation = validationItems.find((v) => v.productId === item.productId);
                const isAvailable = validation ? validation.inStock : true;
                const stockQty = validation ? validation.availableStock : 50;

                return (
                  <Paper
                    key={item.id || item.productId}
                    sx={{
                      p: 2.5,
                      bgcolor: '#111827',
                      borderRadius: 3,
                      border: '1px solid #1E293B',
                      transition: 'border-color 0.2s ease',
                      '&:hover': { borderColor: '#334155' },
                    }}
                  >
                    <Grid container spacing={2.5} alignItems="center">
                      {/* Product Thumbnail */}
                      <Grid item xs={3} sm={2.5}>
                        <Box
                          component="img"
                          src={item.imageUrl || DEFAULT_IMAGE}
                          alt={item.productName}
                          sx={{
                            width: '100%',
                            height: 96,
                            objectFit: 'cover',
                            borderRadius: 2.5,
                            border: '1px solid #1E293B',
                          }}
                        />
                      </Grid>

                      {/* Product Details */}
                      <Grid item xs={9} sm={5}>
                        <Typography variant="h6" sx={{ fontWeight: 800, color: '#F8FAFC', fontSize: '1rem', mb: 0.5 }}>
                          {item.productName}
                        </Typography>
                        <Typography variant="caption" sx={{ color: '#64748B', display: 'block', mb: 1 }}>
                          SKU: {item.sku || `SKU-${item.productId}`}
                        </Typography>

                        {/* Real-time Inventory Status Badge */}
                        <Box sx={{ mt: 0.5 }}>
                          {isAvailable ? (
                            <Chip
                              icon={<CheckCircleRounded sx={{ fontSize: '14px !important', color: '#10B981 !important' }} />}
                              label={`In Stock (${stockQty} units ready)`}
                              size="small"
                              sx={{
                                bgcolor: 'rgba(16, 185, 129, 0.1)',
                                color: '#34D399',
                                border: '1px solid rgba(16, 185, 129, 0.2)',
                                fontWeight: 700,
                                fontSize: '0.72rem',
                              }}
                            />
                          ) : (
                            <Chip
                              icon={<WarningAmberRounded sx={{ fontSize: '14px !important', color: '#EF4444 !important' }} />}
                              label={`Low Stock (Only ${stockQty} available)`}
                              size="small"
                              sx={{
                                bgcolor: 'rgba(239, 68, 68, 0.1)',
                                color: '#F87171',
                                border: '1px solid rgba(239, 68, 68, 0.2)',
                                fontWeight: 700,
                                fontSize: '0.72rem',
                              }}
                            />
                          )}
                        </Box>
                      </Grid>

                      {/* Quantity Controls */}
                      <Grid item xs={6} sm={2.5}>
                        <Stack direction="row" alignItems="center" spacing={1}>
                          <IconButton
                            size="small"
                            onClick={() => handleQuantityChange(item.productId, item.quantity - 1, item.id)}
                            sx={{
                              border: '1px solid #334155',
                              color: '#94A3B8',
                              '&:hover': { bgcolor: '#1E293B', color: '#FFFFFF' },
                            }}
                          >
                            <RemoveRounded sx={{ fontSize: 16 }} />
                          </IconButton>
                          <Typography sx={{ fontWeight: 800, color: '#F8FAFC', minWidth: 28, textAlign: 'center' }}>
                            {item.quantity}
                          </Typography>
                          <IconButton
                            size="small"
                            onClick={() => handleQuantityChange(item.productId, item.quantity + 1, item.id)}
                            sx={{
                              border: '1px solid #334155',
                              color: '#94A3B8',
                              '&:hover': { bgcolor: '#1E293B', color: '#FFFFFF' },
                            }}
                          >
                            <AddRounded sx={{ fontSize: 16 }} />
                          </IconButton>
                        </Stack>
                      </Grid>

                      {/* Line Item Total & Delete */}
                      <Grid item xs={6} sm={2} sx={{ textAlign: 'right' }}>
                        <Typography variant="h6" sx={{ fontWeight: 800, color: '#F8FAFC', fontSize: '1.05rem' }}>
                          ${(item.unitPrice * item.quantity).toFixed(2)}
                        </Typography>
                        <Typography variant="caption" sx={{ color: '#64748B', display: 'block' }}>
                          ${item.unitPrice.toFixed(2)} each
                        </Typography>

                        <Tooltip title="Remove item">
                          <IconButton
                            size="small"
                            onClick={() => handleRemoveItem(item.productId, item.id)}
                            sx={{ color: '#64748B', mt: 0.5, '&:hover': { color: '#EF4444' } }}
                          >
                            <DeleteOutlineRounded sx={{ fontSize: 18 }} />
                          </IconButton>
                        </Tooltip>
                      </Grid>
                    </Grid>
                  </Paper>
                );
              })}
            </Stack>
          </Grid>

          {/* Cart Summary & Checkout Column */}
          <Grid item xs={12} lg={4}>
            <Paper
              sx={{
                p: 3.5,
                bgcolor: '#111827',
                borderRadius: 4,
                border: '1px solid #1E293B',
                boxShadow: '0 8px 32px rgba(0, 0, 0, 0.4)',
              }}
            >
              <Typography variant="h6" sx={{ fontWeight: 800, color: '#F8FAFC', mb: 2.5 }}>
                Order Summary
              </Typography>

              {/* Promo Code Input */}
              <Box sx={{ mb: 3 }}>
                <Typography variant="caption" sx={{ color: '#94A3B8', fontWeight: 700, mb: 1, display: 'block' }}>
                  HAVE A PROMO CODE?
                </Typography>
                <Stack direction="row" spacing={1}>
                  <TextField
                    size="small"
                    fullWidth
                    placeholder="e.g. UNIVERSAL20"
                    value={promoCode}
                    onChange={(e) => setPromoCode(e.target.value)}
                    sx={{
                      '& .MuiOutlinedInput-root': {
                        bgcolor: '#0B0F19',
                        borderRadius: 2,
                        color: '#F8FAFC',
                        '& fieldset': { borderColor: '#334155' },
                        '&:hover fieldset': { borderColor: '#4F46E5' },
                      },
                    }}
                  />
                  <Button
                    variant="contained"
                    onClick={handleApplyPromo}
                    sx={{
                      bgcolor: '#312E81',
                      color: '#E0E7FF',
                      fontWeight: 700,
                      borderRadius: 2,
                      px: 2.5,
                      '&:hover': { bgcolor: '#4338CA' },
                    }}
                  >
                    Apply
                  </Button>
                </Stack>
                {promoMessage && (
                  <Alert severity="success" sx={{ mt: 1.5, py: 0.25, fontSize: '0.8rem', borderRadius: 2 }}>
                    {promoMessage}
                  </Alert>
                )}
                {errorMsg && (
                  <Alert severity="error" sx={{ mt: 1.5, py: 0.25, fontSize: '0.8rem', borderRadius: 2 }}>
                    {errorMsg}
                  </Alert>
                )}
              </Box>

              <Divider sx={{ borderColor: '#1E293B', mb: 2.5 }} />

              {/* Breakdown */}
              <Stack spacing={1.5} sx={{ mb: 3 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body2" sx={{ color: '#94A3B8' }}>Subtotal</Typography>
                  <Typography variant="body2" sx={{ fontWeight: 700, color: '#F8FAFC' }}>
                    ${subtotal.toFixed(2)}
                  </Typography>
                </Box>

                {discountAmount > 0 && (
                  <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                    <Typography variant="body2" sx={{ color: '#34D399', fontWeight: 600 }}>
                      Discount ({discountPercent}%)
                    </Typography>
                    <Typography variant="body2" sx={{ fontWeight: 700, color: '#34D399' }}>
                      -${discountAmount.toFixed(2)}
                    </Typography>
                  </Box>
                )}

                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body2" sx={{ color: '#94A3B8' }}>Estimated Shipping</Typography>
                  <Typography variant="body2" sx={{ fontWeight: 700, color: shippingFee === 0 ? '#34D399' : '#F8FAFC' }}>
                    {shippingFee === 0 ? 'FREE' : `$${shippingFee.toFixed(2)}`}
                  </Typography>
                </Box>

                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body2" sx={{ color: '#94A3B8' }}>Estimated Tax (8.25%)</Typography>
                  <Typography variant="body2" sx={{ fontWeight: 700, color: '#F8FAFC' }}>
                    ${tax.toFixed(2)}
                  </Typography>
                </Box>
              </Stack>

              <Divider sx={{ borderColor: '#1E293B', mb: 3 }} />

              {/* Grand Total */}
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
                <Typography variant="h6" sx={{ fontWeight: 800, color: '#F8FAFC' }}>
                  Total
                </Typography>
                <Typography variant="h5" sx={{ fontWeight: 900, color: '#06B6D4' }}>
                  ${grandTotal.toFixed(2)}
                </Typography>
              </Box>

              {/* Checkout CTA */}
              <Button
                variant="contained"
                fullWidth
                size="large"
                endIcon={<ArrowForwardRounded />}
                onClick={() => navigate('/orders')}
                sx={{
                  py: 1.8,
                  borderRadius: 3,
                  fontWeight: 800,
                  fontSize: '1rem',
                  background: 'linear-gradient(135deg, #4F46E5 0%, #06B6D4 100%)',
                  boxShadow: '0 4px 18px rgba(79, 70, 229, 0.4)',
                  '&:hover': {
                    background: 'linear-gradient(135deg, #4338CA 0%, #0891B2 100%)',
                  },
                }}
              >
                Proceed to Checkout
              </Button>

              <Stack direction="row" spacing={1} justifyContent="center" alignItems="center" sx={{ mt: 2.5 }}>
                <LockRounded sx={{ fontSize: 16, color: '#64748B' }} />
                <Typography variant="caption" sx={{ color: '#64748B', fontWeight: 600 }}>
                  256-Bit SSL Encrypted &bull; Zero-Trust Saga Protected
                </Typography>
              </Stack>
            </Paper>
          </Grid>
        </Grid>
      )}
    </Box>
  );
}
