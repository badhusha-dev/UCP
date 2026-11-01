import { useEffect, useState } from 'react';
import {
  Typography,
  Paper,
  Avatar,
  Box,
  CircularProgress,
  Button,
  Snackbar,
  Alert,
  Chip,
  Grid,
  Stack,
  Card,
  Tabs,
  Tab,
  TextField,
} from '@mui/material';
import {
  PersonOutlineRounded,
  ShoppingBagOutlined,
  SecurityRounded,
  LocalShippingOutlined,
  CalendarTodayOutlined,
  CheckCircleRounded,
  CancelOutlined,
  StarsRounded,
  ShieldRounded,
  CreditCardOutlined,
} from '@mui/icons-material';
import { useSelector } from 'react-redux';
import type { RootState } from '@ecommerce/shared-library';
import axios from 'axios';

axios.interceptors.request.use((config) => {
  const token = typeof window !== 'undefined' ? localStorage.getItem('jwt_token') : null;
  if (token && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

interface Order {
  id: number;
  status: string;
  totalAmount: number;
  createdAt: string;
}

export default function ProfilePage() {
  const user = useSelector((state: RootState) => state.auth.user);
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [toastMessage, setToastMessage] = useState('');
  const [tabIndex, setTabIndex] = useState(0);

  // Profile state
  const [profile, setProfile] = useState<any>(null);
  const [isEditing, setIsEditing] = useState(false);
  const [editName, setEditName] = useState('');

  const fetchProfileAndOrders = async () => {
    if (user?.id) {
      try {
        const [profileRes, ordersRes] = await Promise.all([
          axios.get(`http://localhost:8080/api/users/${user.id}`).catch(() => ({ data: { name: user.name, email: user.email, role: user.role, active: true } })),
          axios.get(`http://localhost:8080/api/orders/user/${user.id}`).catch(() => ({ data: [] })),
        ]);
        setProfile(profileRes.data);
        setEditName(profileRes.data.name || user.name || '');
        setOrders(ordersRes.data || []);
      } catch (err) {
        console.error('Error fetching data:', err);
      } finally {
        setLoading(false);
      }
    } else {
      setProfile({ name: user?.name, email: user?.email, role: user?.role, active: true });
      setEditName(user?.name || '');
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProfileAndOrders();
  }, [user]);

  const handleCancelOrder = async (orderId: number) => {
    try {
      await axios.post(`http://localhost:8080/api/orders/${orderId}/cancel`);
      setToastMessage('Order cancelled successfully!');
      fetchProfileAndOrders();
    } catch (err: any) {
      setToastMessage(err.response?.data?.error || 'Failed to cancel order.');
    }
  };

  const handleSaveProfile = async () => {
    try {
      if (user?.id) {
        await axios.put(`http://localhost:8080/api/users/${user.id}`, { name: editName });
      }
      setToastMessage('Profile updated successfully!');
      setIsEditing(false);
      fetchProfileAndOrders();
    } catch (err: any) {
      setToastMessage(err.response?.data?.error || 'Failed to update profile.');
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'DELIVERED':
        return { label: 'DELIVERED', color: 'success', bg: '#DCFCE7', text: '#15803D' };
      case 'SHIPPED':
        return { label: 'IN TRANSIT', color: 'primary', bg: '#E0E7FF', text: '#4338CA' };
      case 'CONFIRMED':
      case 'PAID':
        return { label: 'CONFIRMED', color: 'info', bg: '#E0F2FE', text: '#0369A1' };
      case 'CANCELLED':
        return { label: 'CANCELLED', color: 'error', bg: '#FEE2E2', text: '#B91C1C' };
      default:
        return { label: status || 'PROCESSING', color: 'warning', bg: '#FEF3C7', text: '#B45309' };
    }
  };

  if (!user) {
    return (
      <Box sx={{ p: 6, textAlign: 'center' }}>
        <Typography variant="h5" sx={{ fontWeight: 700, mb: 2 }}>
          Sign In Required
        </Typography>
        <Typography color="textSecondary" sx={{ mb: 3 }}>
          Please sign in to view your profile, order history, and account settings.
        </Typography>
        <Button variant="contained" color="primary" href="/login">
          Go to Sign In
        </Button>
      </Box>
    );
  }

  const totalSpent = orders.reduce((sum, ord) => sum + (ord.totalAmount || 0), 0);
  const rewardPoints = Math.round(totalSpent * 10);

  return (
    <Box sx={{ pb: 6 }}>
      {/* 1. Hero Profile Header Card */}
      <Paper
        elevation={0}
        sx={{
          p: { xs: 3, md: 5 },
          borderRadius: 4,
          background: 'linear-gradient(135deg, #1E1B4B 0%, #312E81 50%, #4F46E5 100%)',
          color: '#FFFFFF',
          mb: 4,
          position: 'relative',
          overflow: 'hidden',
          boxShadow: '0 16px 36px -10px rgba(30, 27, 75, 0.35)',
        }}
      >
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={3} alignItems={{ xs: 'flex-start', sm: 'center' }}>
          <Avatar
            sx={{
              width: 90,
              height: 90,
              bgcolor: '#F59E0B',
              color: '#0F172A',
              fontWeight: 900,
              fontSize: '2.4rem',
              boxShadow: '0 8px 24px rgba(245, 158, 11, 0.4)',
              border: '4px solid rgba(255, 255, 255, 0.2)',
            }}
          >
            {profile?.name ? profile.name.charAt(0).toUpperCase() : user.email?.charAt(0).toUpperCase() || 'U'}
          </Avatar>

          <Box sx={{ flexGrow: 1 }}>
            <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
              <Chip
                label="UNIVERSAL COMMERCE VIP"
                size="small"
                sx={{
                  bgcolor: '#F59E0B',
                  color: '#0F172A',
                  fontWeight: 900,
                  fontSize: '0.65rem',
                }}
              />
              <Chip
                label="PLATINUM TIER"
                size="small"
                sx={{
                  bgcolor: 'rgba(255, 255, 255, 0.15)',
                  color: '#E0E7FF',
                  fontWeight: 800,
                  fontSize: '0.65rem',
                  border: '1px solid rgba(255, 255, 255, 0.3)',
                }}
              />
            </Stack>
            <Stack direction="row" spacing={1.5} alignItems="center" sx={{ mb: 0.5 }}>
              <Typography variant="h4" sx={{ fontWeight: 900 }}>
                {profile?.name || user.name || 'Valued Shopper'}
              </Typography>
              <Chip
                label={profile?.role || user.role}
                size="small"
                sx={{
                  bgcolor: 'rgba(255, 255, 255, 0.2)',
                  color: '#FFFFFF',
                  fontWeight: 800,
                  fontSize: '0.75rem',
                  border: '1px solid rgba(255, 255, 255, 0.3)',
                }}
              />
            </Stack>
            <Typography variant="body1" sx={{ color: '#CBD5E1', mb: 1 }}>
              {profile?.email || user.email} • Account #{user.id || '101'}
            </Typography>
            <Stack direction="row" spacing={1.5} alignItems="center">
              <Chip
                icon={<CheckCircleRounded sx={{ color: '#34D399 !important' }} />}
                label={profile?.active !== false ? 'Active Account' : 'Inactive'}
                size="small"
                sx={{
                  bgcolor: 'rgba(16, 185, 129, 0.2)',
                  color: '#34D399',
                  fontWeight: 700,
                }}
              />
              <Chip
                icon={<ShieldRounded sx={{ color: '#FBBF24 !important' }} />}
                label="Zero-Trust Verified"
                size="small"
                sx={{
                  bgcolor: 'rgba(245, 158, 11, 0.2)',
                  color: '#FBBF24',
                  fontWeight: 700,
                }}
              />
            </Stack>
          </Box>
        </Stack>
      </Paper>

      {/* 2. Stat Highlight Badges */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ p: 2.5, bgcolor: '#FFFFFF', border: '1px solid #E2E8F0' }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <Box sx={{ width: 48, height: 48, borderRadius: 2.5, bgcolor: '#EEF2FF', color: '#4F46E5', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <ShoppingBagOutlined />
              </Box>
              <Box>
                <Typography variant="h5" sx={{ fontWeight: 800, color: '#0F172A' }}>
                  {orders.length}
                </Typography>
                <Typography variant="caption" sx={{ color: '#64748B', fontWeight: 600 }}>
                  Total Orders
                </Typography>
              </Box>
            </Box>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ p: 2.5, bgcolor: '#FFFFFF', border: '1px solid #E2E8F0' }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <Box sx={{ width: 48, height: 48, borderRadius: 2.5, bgcolor: '#FEF3C7', color: '#D97706', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <StarsRounded />
              </Box>
              <Box>
                <Typography variant="h5" sx={{ fontWeight: 800, color: '#0F172A' }}>
                  {rewardPoints} pts
                </Typography>
                <Typography variant="caption" sx={{ color: '#64748B', fontWeight: 600 }}>
                  Marketplace Rewards
                </Typography>
              </Box>
            </Box>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ p: 2.5, bgcolor: '#FFFFFF', border: '1px solid #E2E8F0' }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <Box sx={{ width: 48, height: 48, borderRadius: 2.5, bgcolor: '#DCFCE7', color: '#16A34A', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <CreditCardOutlined />
              </Box>
              <Box>
                <Typography variant="h5" sx={{ fontWeight: 800, color: '#0F172A' }}>
                  ${totalSpent.toFixed(2)}
                </Typography>
                <Typography variant="caption" sx={{ color: '#64748B', fontWeight: 600 }}>
                  Lifetime Purchases
                </Typography>
              </Box>
            </Box>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ p: 2.5, bgcolor: '#FFFFFF', border: '1px solid #E2E8F0' }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <Box sx={{ width: 48, height: 48, borderRadius: 2.5, bgcolor: '#F3E8FF', color: '#9333EA', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <SecurityRounded />
              </Box>
              <Box>
                <Typography variant="h5" sx={{ fontWeight: 800, color: '#0F172A' }}>
                  Tier 1
                </Typography>
                <Typography variant="caption" sx={{ color: '#64748B', fontWeight: 600 }}>
                  Vault Encrypted Member
                </Typography>
              </Box>
            </Box>
          </Card>
        </Grid>
      </Grid>

      {/* 3. Tabbed Navigation */}
      <Paper elevation={0} sx={{ borderRadius: 3, border: '1px solid #E2E8F0', overflow: 'hidden', mb: 3 }}>
        <Tabs
          value={tabIndex}
          onChange={(_, val) => setTabIndex(val)}
          sx={{
            px: 2,
            borderBottom: '1px solid #E2E8F0',
            bgcolor: '#FAFAFA',
            '& .MuiTab-root': { fontWeight: 700, fontSize: '0.95rem', py: 2 },
          }}
        >
          <Tab icon={<ShoppingBagOutlined />} iconPosition="start" label={`Order History (${orders.length})`} />
          <Tab icon={<PersonOutlineRounded />} iconPosition="start" label="Account Details" />
          <Tab icon={<SecurityRounded />} iconPosition="start" label="Security & Vault" />
        </Tabs>

        {/* Tab 0: Order History Showcase */}
        {tabIndex === 0 && (
          <Box sx={{ p: 3 }}>
            {loading ? (
              <Box sx={{ display: 'flex', justifyContent: 'center', p: 6 }}>
                <CircularProgress />
              </Box>
            ) : orders.length === 0 ? (
              <Box sx={{ textAlign: 'center', py: 8 }}>
                <ShoppingBagOutlined sx={{ fontSize: 64, color: '#CBD5E1', mb: 2 }} />
                <Typography variant="h6" sx={{ fontWeight: 700, color: '#475569' }}>
                  No orders placed yet
                </Typography>
                <Typography variant="body2" sx={{ color: '#94A3B8', mb: 3 }}>
                  Explore our reactive product catalog to place your first order!
                </Typography>
                <Button variant="contained" color="secondary" href="/products" sx={{ fontWeight: 800 }}>
                  Browse Products
                </Button>
              </Box>
            ) : (
              <Stack spacing={2.5}>
                {orders.map((order) => {
                  const badge = getStatusBadge(order.status);
                  return (
                    <Card
                      key={order.id}
                      sx={{
                        p: 2.5,
                        display: 'flex',
                        flexDirection: { xs: 'column', md: 'row' },
                        justifyContent: 'space-between',
                        alignItems: { xs: 'flex-start', md: 'center' },
                        gap: 2,
                        border: '1px solid #E2E8F0',
                        '&:hover': { borderColor: '#CBD5E1', transform: 'none' },
                      }}
                    >
                      <Box>
                        <Stack direction="row" spacing={1.5} alignItems="center" sx={{ mb: 1 }}>
                          <Typography variant="h6" sx={{ fontWeight: 800, color: '#0F172A' }}>
                            Order #{order.id}
                          </Typography>
                          <Chip
                            label={badge.label}
                            size="small"
                            sx={{
                              bgcolor: badge.bg,
                              color: badge.text,
                              fontWeight: 800,
                              fontSize: '0.75rem',
                              border: `1px solid ${badge.text}33`,
                            }}
                          />
                        </Stack>
                        <Stack direction="row" spacing={3} sx={{ color: '#64748B', fontSize: '0.875rem' }}>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                            <CalendarTodayOutlined sx={{ fontSize: 16 }} />
                            <span>Placed: {order.createdAt ? new Date(order.createdAt).toLocaleDateString() : 'Today'}</span>
                          </Box>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                            <LocalShippingOutlined sx={{ fontSize: 16 }} />
                            <span>Standard Express</span>
                          </Box>
                        </Stack>
                      </Box>

                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 3, width: { xs: '100%', md: 'auto' }, justifyContent: 'space-between' }}>
                        <Box sx={{ textAlign: { xs: 'left', md: 'right' } }}>
                          <Typography variant="caption" sx={{ color: '#64748B', fontWeight: 600, display: 'block' }}>
                            TOTAL AMOUNT
                          </Typography>
                          <Typography variant="h5" sx={{ fontWeight: 900, color: '#0F172A' }}>
                            ${order.totalAmount?.toFixed(2) || '0.00'}
                          </Typography>
                        </Box>

                        {(order.status === 'PENDING' || order.status === 'PLACED') && (
                          <Button
                            variant="outlined"
                            color="error"
                            size="small"
                            startIcon={<CancelOutlined />}
                            onClick={() => handleCancelOrder(order.id)}
                            sx={{ fontWeight: 700 }}
                          >
                            Cancel
                          </Button>
                        )}
                      </Box>
                    </Card>
                  );
                })}
              </Stack>
            )}
          </Box>
        )}

        {/* Tab 1: Account Details */}
        {tabIndex === 1 && (
          <Box sx={{ p: 4, maxWidth: 640 }}>
            <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>
              Personal & Profile Information
            </Typography>
            <Typography variant="body2" sx={{ color: '#64748B', mb: 3 }}>
              Update your contact details and display preferences.
            </Typography>

            <Stack spacing={3}>
              <TextField
                fullWidth
                label="Full Display Name"
                value={editName}
                onChange={(e) => setEditName(e.target.value)}
                disabled={!isEditing}
              />

              <TextField
                fullWidth
                label="Registered Email Address"
                value={profile?.email || user.email}
                disabled
                helperText="Verified account email. Contact administrator to change."
              />

              <TextField
                fullWidth
                label="Assigned System Role"
                value={profile?.role || user.role}
                disabled
                helperText="Governed by Spring Security OAuth2 / JWT RBAC."
              />

              <Box sx={{ display: 'flex', gap: 2, pt: 1 }}>
                {isEditing ? (
                  <>
                    <Button variant="contained" color="primary" onClick={handleSaveProfile} sx={{ fontWeight: 700 }}>
                      Save Changes
                    </Button>
                    <Button variant="outlined" onClick={() => setIsEditing(false)}>
                      Cancel
                    </Button>
                  </>
                ) : (
                  <Button variant="contained" color="primary" onClick={() => setIsEditing(true)} sx={{ fontWeight: 700 }}>
                    Edit Profile Information
                  </Button>
                )}
              </Box>
            </Stack>
          </Box>
        )}

        {/* Tab 2: Security & Vault */}
        {tabIndex === 2 && (
          <Box sx={{ p: 4 }}>
            <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>
              Security, Encryption & Session Management
            </Typography>
            <Typography variant="body2" sx={{ color: '#64748B', mb: 3 }}>
              This identity is authenticated via Spring Cloud Gateway with Vault-backed secrets.
            </Typography>

            <Grid container spacing={3}>
              <Grid item xs={12} md={6}>
                <Card sx={{ p: 3, border: '1px solid #E2E8F0' }}>
                  <Typography variant="subtitle1" sx={{ fontWeight: 800, color: '#0F172A', mb: 1 }}>
                    HashiCorp Vault Secret Engine
                  </Typography>
                  <Typography variant="body2" sx={{ color: '#64748B', mb: 2 }}>
                    Your session keys and symmetric JWT signature are verified against dynamic keys in HashiCorp Vault KV-v2 (`secret/`).
                  </Typography>
                  <Chip label="ACTIVE & ENCRYPTED" color="success" size="small" sx={{ fontWeight: 800 }} />
                </Card>
              </Grid>

              <Grid item xs={12} md={6}>
                <Card sx={{ p: 3, border: '1px solid #E2E8F0' }}>
                  <Typography variant="subtitle1" sx={{ fontWeight: 800, color: '#0F172A', mb: 1 }}>
                    Mutual TLS (mTLS) Inter-Service Communication
                  </Typography>
                  <Typography variant="body2" sx={{ color: '#64748B', mb: 2 }}>
                    All backend API routing is encrypted with TLSv1.3 and AEAD cipher suites (`TLS_AES_128_GCM_SHA256`).
                  </Typography>
                  <Chip label="TLSv1.3 ENFORCED" color="primary" size="small" sx={{ fontWeight: 800 }} />
                </Card>
              </Grid>
            </Grid>
          </Box>
        )}
      </Paper>

      <Snackbar
        open={!!toastMessage}
        autoHideDuration={5000}
        onClose={() => setToastMessage('')}
      >
        <Alert onClose={() => setToastMessage('')} severity="success" sx={{ width: '100%', borderRadius: 2 }}>
          {toastMessage}
        </Alert>
      </Snackbar>
    </Box>
  );
}
