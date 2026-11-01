import React, { useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { useNavigate } from 'react-router-dom';
import {
  Typography,
  Box,
  TextField,
  Button,
  Paper,
  CircularProgress,
  Chip,
  InputAdornment,
  IconButton,
  Alert,
  Divider,
  FormControlLabel,
  Checkbox,
  Stack,
  Card,
  CardContent,
  Avatar,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
} from '@mui/material';
import {
  Visibility,
  VisibilityOff,
  EmailOutlined,
  LockOutlined,
  CheckCircleRounded,
  SpeedRounded,
  SecurityRounded,
  StorefrontRounded,
  ShieldRounded,
  KeyRounded,
} from '@mui/icons-material';
import { AuthService, loginSuccess, logout } from '@ecommerce/shared-library';
import type { RootState } from '@ecommerce/shared-library';

interface DemoUser {
  id: string;
  name: string;
  email: string;
  role: string;
  description: string;
  avatarColor: string;
  icon: React.ReactNode;
}

const DEMO_USERS: DemoUser[] = [];

export default function LoginPage() {
  const { isAuthenticated, user } = useSelector((state: RootState) => state.auth);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [isRegistering, setIsRegistering] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [rememberMe, setRememberMe] = useState(true);
  const [forgotPasswordOpen, setForgotPasswordOpen] = useState(false);

  const dispatch = useDispatch();
  const navigate = useNavigate();

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      const token = isRegistering
        ? await AuthService.register(email, name, password)
        : await AuthService.login(email, password);
      dispatch(loginSuccess(token));
      navigate('/');
    } catch (err: any) {
      console.error(err);
      setError(err.message || 'Invalid email or password. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleSelectDemoUser = (user: DemoUser) => {
    setEmail(user.email);
    setPassword('');
    setError('');
  };

  const getRoleBadgeStyle = (role: string) => {
    switch (role) {
      case 'ADMIN':
        return { bg: '#EDE9FE', color: '#6D28D9', border: '#C4B5FD' };
      case 'MANAGER':
        return { bg: '#FEF3C7', color: '#B45309', border: '#FCD34D' };
      default:
        return { bg: '#ECFDF5', color: '#047857', border: '#A7F3D0' };
    }
  };

  return (
    <Box
      sx={{
        minHeight: '85vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        py: { xs: 3, md: 5 },
      }}
    >
      <Paper
        elevation={0}
        sx={{
          width: '100%',
          maxWidth: 1100,
          borderRadius: 4,
          overflow: 'hidden',
          display: 'flex',
          flexDirection: { xs: 'column', md: 'row' },
          border: '1px solid #E2E8F0',
          boxShadow: '0 20px 40px -15px rgba(15, 23, 42, 0.1)',
        }}
      >
        {/* Left Column: Bold Universal Commerce Brand Showcase */}
        <Box
          sx={{
            flex: '1 1 42%',
            background: 'linear-gradient(145deg, #0B0F19 0%, #1E1B4B 50%, #312E81 100%)',
            color: '#FFFFFF',
            p: { xs: 4, md: 5.5 },
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'space-between',
            position: 'relative',
          }}
        >
          {/* Top Brand Badges */}
          <Box>
            <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 3 }}>
              <Chip
                icon={<StorefrontRounded sx={{ color: '#F59E0B !important' }} />}
                label="UNIVERSAL COMMERCE 2.0"
                size="small"
                sx={{
                  bgcolor: 'rgba(245, 158, 11, 0.2)',
                  color: '#FBBF24',
                  fontWeight: 800,
                  border: '1px solid rgba(245, 158, 11, 0.4)',
                }}
              />
              <Chip
                label="● LIVE MFE CLUSTER"
                size="small"
                sx={{
                  bgcolor: 'rgba(16, 185, 129, 0.15)',
                  color: '#34D399',
                  fontWeight: 700,
                  fontSize: '0.7rem',
                }}
              />
            </Stack>

            <Typography
              variant="h3"
              sx={{
                fontWeight: 900,
                lineHeight: 1.15,
                mb: 2,
                letterSpacing: '-0.025em',
                background: 'linear-gradient(to right, #FFFFFF, #E0E7FF)',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent',
              }}
            >
              The Universal Storefront Experience.
            </Typography>

            <Typography variant="body1" sx={{ color: '#CBD5E1', fontSize: '1rem', mb: 4, lineHeight: 1.5 }}>
              The high-performance composable storefront powered by Spring Boot 3 microservices, Istio mTLS service mesh, and sub-second React microfrontends.
            </Typography>

            {/* Architecture Highlights */}
            <Stack spacing={2.5}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                <Box
                  sx={{
                    width: 42,
                    height: 42,
                    borderRadius: 2.5,
                    bgcolor: 'rgba(99, 102, 241, 0.2)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    border: '1px solid rgba(99, 102, 241, 0.4)',
                  }}
                >
                  <SpeedRounded sx={{ color: '#818CF8' }} />
                </Box>
                <Box>
                  <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                    Instant Microfrontend Routing
                  </Typography>
                  <Typography variant="caption" sx={{ color: '#94A3B8' }}>
                    Seamless module federation across Product, Order & User apps
                  </Typography>
                </Box>
              </Box>

              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                <Box
                  sx={{
                    width: 42,
                    height: 42,
                    borderRadius: 2.5,
                    bgcolor: 'rgba(16, 185, 129, 0.2)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    border: '1px solid rgba(16, 185, 129, 0.4)',
                  }}
                >
                  <SecurityRounded sx={{ color: '#34D399' }} />
                </Box>
                <Box>
                  <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                    Vault &amp; Istio mTLS Hardened
                  </Typography>
                  <Typography variant="caption" sx={{ color: '#94A3B8' }}>
                    Strict NetworkPolicies, SPIFFE identities & dynamic secrets
                  </Typography>
                </Box>
              </Box>

              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                <Box
                  sx={{
                    width: 42,
                    height: 42,
                    borderRadius: 2.5,
                    bgcolor: 'rgba(245, 158, 11, 0.2)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    border: '1px solid rgba(245, 158, 11, 0.4)',
                  }}
                >
                  <CheckCircleRounded sx={{ color: '#FBBF24' }} />
                </Box>
                <Box>
                  <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                    Resilience4j Self-Healing
                  </Typography>
                  <Typography variant="caption" sx={{ color: '#94A3B8' }}>
                    Non-blocking fallbacks, circuit breakers & Kafka DLQ
                  </Typography>
                </Box>
              </Box>
            </Stack>
          </Box>

          {/* Bottom Security Note */}
          <Box sx={{ mt: 4, pt: 3, borderTop: '1px solid rgba(255, 255, 255, 0.1)' }}>
            <Typography variant="caption" sx={{ color: '#94A3B8', display: 'flex', alignItems: 'center', gap: 1 }}>
              <ShieldRounded sx={{ fontSize: 16, color: '#10B981' }} />
              256-Bit SSL &bull; Zero-Trust SPIFFE Identity &bull; SOC-2 Compliant
            </Typography>
          </Box>
        </Box>

        {/* Right Column: Interactive Login Form & Demo Users */}
        <Box sx={{ flex: '1 1 58%', p: { xs: 3.5, md: 5.5 }, bgcolor: '#FFFFFF' }}>
          <Box sx={{ mb: 3 }}>
            <Typography variant="h4" sx={{ fontWeight: 900, color: '#0F172A', letterSpacing: '-0.025em', mb: 0.5 }}>
              Universal Sign In
            </Typography>
            <Typography variant="body2" sx={{ color: '#64748B' }}>
              Select a demo persona for instant access, or sign in with your registered account.
            </Typography>
          </Box>

          {isAuthenticated && (
            <Alert
              severity="info"
              sx={{ mb: 3, borderRadius: 2 }}
              action={
                <Stack direction="row" spacing={1}>
                  <Button
                    color="inherit"
                    size="small"
                    variant="outlined"
                    onClick={() => dispatch(logout())}
                    sx={{ textTransform: 'none', fontWeight: 700 }}
                  >
                    Sign Out
                  </Button>
                  <Button
                    color="primary"
                    size="small"
                    variant="contained"
                    onClick={() => navigate('/')}
                    sx={{ textTransform: 'none', fontWeight: 700 }}
                  >
                    Go to Store
                  </Button>
                </Stack>
              }
            >
              You are currently signed in as <strong>{user?.name || user?.email}</strong> ({user?.role}). Sign out before signing in to another account.
            </Alert>
          )}

          {DEMO_USERS.length > 0 && <>
          {/* One-Click Demo User Persona Cards */}
          <Box sx={{ mb: 3 }}>
            <Typography variant="caption" sx={{ fontWeight: 800, color: '#475569', display: 'flex', alignItems: 'center', gap: 0.5, mb: 1.5, letterSpacing: '0.04em' }}>
              <KeyRounded sx={{ fontSize: 15, color: '#4F46E5' }} />
              SELECT A VERIFIED DEMO USER:
            </Typography>

            <Stack spacing={1.2}>
              {DEMO_USERS.map((u) => {
                const isSelected = email === u.email;
                const badgeStyle = getRoleBadgeStyle(u.role);
                return (
                  <Card
                    key={u.id}
                    onClick={() => handleSelectDemoUser(u)}
                    sx={{
                      cursor: 'pointer',
                      borderRadius: 2.5,
                      border: isSelected ? '2px solid #4F46E5' : '1px solid #E2E8F0',
                      bgcolor: isSelected ? '#F5F3FF' : '#FFFFFF',
                      transition: 'all 0.2s ease',
                      boxShadow: isSelected ? '0 4px 12px rgba(79, 70, 229, 0.15)' : 'none',
                      '&:hover': {
                        borderColor: '#818CF8',
                        bgcolor: isSelected ? '#F5F3FF' : '#F8FAFC',
                      },
                    }}
                  >
                    <CardContent sx={{ p: '12px 16px !important' }}>
                      <Stack direction="row" alignItems="center" justifyContent="space-between">
                        <Stack direction="row" spacing={1.5} alignItems="center">
                          <Avatar
                            sx={{
                              width: 38,
                              height: 38,
                              bgcolor: u.avatarColor,
                              boxShadow: '0 2px 6px rgba(0,0,0,0.15)',
                            }}
                          >
                            {u.icon}
                          </Avatar>
                          <Box>
                            <Stack direction="row" spacing={1} alignItems="center">
                              <Typography variant="subtitle2" sx={{ fontWeight: 800, color: '#0F172A', lineHeight: 1.1 }}>
                                {u.name}
                              </Typography>
                              <Chip
                                label={u.role}
                                size="small"
                                sx={{
                                  height: 18,
                                  fontSize: '0.65rem',
                                  fontWeight: 800,
                                  bgcolor: badgeStyle.bg,
                                  color: badgeStyle.color,
                                  border: `1px solid ${badgeStyle.border}`,
                                }}
                              />
                            </Stack>
                            <Typography variant="caption" sx={{ color: '#64748B', display: 'block', mt: 0.2 }}>
                              {u.email} &bull; <span style={{ color: '#4F46E5', fontWeight: 600 }}>password123</span>
                            </Typography>
                          </Box>
                        </Stack>

                        <Button
                          size="small"
                          variant={isSelected ? 'contained' : 'outlined'}
                          sx={{
                            borderRadius: 2,
                            fontSize: '0.75rem',
                            fontWeight: 700,
                            px: 1.5,
                            bgcolor: isSelected ? '#4F46E5' : 'transparent',
                            color: isSelected ? '#FFFFFF' : '#4F46E5',
                            borderColor: '#C7D2FE',
                          }}
                        >
                          {isSelected ? 'Selected' : 'Use User'}
                        </Button>
                      </Stack>
                    </CardContent>
                  </Card>
                );
              })}
            </Stack>
          </Box>
          </>}

          {error && (
            <Alert severity="error" sx={{ mb: 2.5, borderRadius: 2 }}>
              {error}
            </Alert>
          )}

          {/* Login Form */}
          <form onSubmit={handleLogin}>
            {isRegistering && <TextField
              fullWidth
              label="Full name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              sx={{ mb: 2 }}
            />}
            <TextField
              fullWidth
              label="Email Address"
              type="email"
              variant="outlined"
              size="small"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="e.g. admin@ecommerce.com"
              required
              sx={{ mb: 2 }}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <EmailOutlined sx={{ color: '#94A3B8', fontSize: 20 }} />
                  </InputAdornment>
                ),
                sx: { borderRadius: 2.5 },
              }}
            />

            <TextField
              fullWidth
              label={isRegistering ? 'Password (at least 12 characters)' : 'Password'}
              variant="outlined"
              size="small"
              type={showPassword ? 'text' : 'password'}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Enter your password"
              required
              sx={{ mb: 1.5 }}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <LockOutlined sx={{ color: '#94A3B8', fontSize: 20 }} />
                  </InputAdornment>
                ),
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton
                      onClick={() => setShowPassword(!showPassword)}
                      edge="end"
                      size="small"
                    >
                      {showPassword ? <VisibilityOff fontSize="small" /> : <Visibility fontSize="small" />}
                    </IconButton>
                  </InputAdornment>
                ),
                sx: { borderRadius: 2.5 },
              }}
            />

            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2.5 }}>
              <FormControlLabel
                control={
                  <Checkbox
                    checked={rememberMe}
                    onChange={(e) => setRememberMe(e.target.checked)}
                    color="primary"
                    size="small"
                  />
                }
                label={<Typography variant="body2" sx={{ color: '#475569', fontSize: '0.85rem' }}>Remember credentials</Typography>}
              />
              <Typography
                variant="body2"
                onClick={() => setForgotPasswordOpen(true)}
                sx={{
                  color: '#4F46E5',
                  fontWeight: 600,
                  fontSize: '0.85rem',
                  cursor: 'pointer',
                  '&:hover': { textDecoration: 'underline' },
                }}
              >
                Need Help?
              </Typography>
            </Box>

            <Button
              type="submit"
              variant="contained"
              fullWidth
              size="large"
              disabled={loading}
              sx={{
                py: 1.4,
                fontSize: '1rem',
                fontWeight: 800,
                borderRadius: 2.5,
                background: 'linear-gradient(90deg, #4F46E5 0%, #6366F1 100%)',
                boxShadow: '0 4px 14px rgba(79, 70, 229, 0.35)',
                '&:hover': {
                  background: 'linear-gradient(90deg, #4338CA 0%, #4F46E5 100%)',
                  boxShadow: '0 6px 20px rgba(79, 70, 229, 0.45)',
                },
              }}
            >
              {loading ? <CircularProgress size={24} color="inherit" /> : isRegistering ? 'Create Account' : 'Sign In'}
            </Button>
          </form>

          <Button fullWidth sx={{ mt: 1.5, textTransform: 'none' }} onClick={() => { setIsRegistering(!isRegistering); setError(''); }}>
            {isRegistering ? 'Already registered? Sign in' : 'New here? Create an account'}
          </Button>

          <Divider sx={{ my: 3 }}>
            <Typography variant="caption" sx={{ color: '#94A3B8', fontWeight: 700, letterSpacing: '0.05em' }}>
              SECURED BY SPRING SECURITY &amp; JWT
            </Typography>
          </Divider>

          <Typography variant="body2" align="center" sx={{ color: '#64748B', fontSize: '0.85rem' }}>
            Sign in with your account credentials. New accounts can be registered through the account service.
          </Typography>
        </Box>
      </Paper>

      {/* Forgot Password / Credentials Helper Dialog */}
      <Dialog
        open={forgotPasswordOpen}
        onClose={() => setForgotPasswordOpen(false)}
        maxWidth="xs"
        fullWidth
        PaperProps={{ sx: { borderRadius: 3, p: 1 } }}
      >
        <DialogTitle sx={{ fontWeight: 800, color: '#0F172A' }}>
            Account Help
        </DialogTitle>
        <DialogContent>
          <Typography variant="body2" sx={{ color: '#64748B', mb: 2 }}>
            Password reset is not configured in this local deployment. Contact your administrator to reset an account.
          </Typography>
          <Paper sx={{ p: 2, bgcolor: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 2 }}>
            <Typography variant="subtitle2" sx={{ fontWeight: 800, color: '#0F172A' }}>
              No shared or demo password is configured.
            </Typography>
            <Divider sx={{ my: 1 }} />
            <Typography variant="caption" sx={{ color: '#64748B', display: 'block' }}>
              Register a personal account to access the storefront.
            </Typography>
            <Typography variant="caption" sx={{ color: '#64748B', display: 'block' }}>
            </Typography>
            <Typography variant="caption" sx={{ color: '#64748B', display: 'block' }}>
            </Typography>
          </Paper>
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <Button onClick={() => setForgotPasswordOpen(false)} variant="contained" color="primary">
            Got it
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
