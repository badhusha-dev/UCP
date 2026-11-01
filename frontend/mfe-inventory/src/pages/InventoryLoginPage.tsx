import React, { useState } from 'react';
import { Alert, Box, Button, Card, CardContent, CircularProgress, Stack, TextField, Typography } from '@mui/material';
import { signInToApplication, type ApplicationSession } from '@ecommerce/shared-library/src/auth/applicationSession';

interface InventoryLoginPageProps {
  onAuthenticated: (session: ApplicationSession) => void;
}

export const InventoryLoginPage: React.FC<InventoryLoginPageProps> = ({ onAuthenticated }) => {
  const [tenantSlug, setTenantSlug] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const signIn = async (event: React.FormEvent) => {
    event.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      onAuthenticated(await signInToApplication({ applicationCode: 'INVENTORY', tenantSlug, email, password }));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Sign in failed. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', px: 2, py: 4, bgcolor: '#F4F7FB' }}>
      <Card sx={{ width: 'min(100%, 440px)', borderRadius: 4, border: '1px solid #E3EAF3', boxShadow: '0 24px 70px rgba(20, 35, 60, .10)' }}>
        <CardContent sx={{ p: { xs: 3, sm: 4 } }}>
          <Stack spacing={2.5} component="form" onSubmit={signIn}>
            <Box>
              <Typography variant="overline" sx={{ color: '#4F46E5', fontWeight: 800, letterSpacing: '.12em' }}>Universal Commerce</Typography>
              <Typography variant="h4" sx={{ mt: .5, color: '#14233B', fontWeight: 800, letterSpacing: '-.04em' }}>Inventory sign in</Typography>
              <Typography color="text.secondary" sx={{ mt: 1 }}>Use your client workspace and account. Access is checked for this application.</Typography>
            </Box>
            {error && <Alert severity="error">{error}</Alert>}
            <TextField label="Client workspace" value={tenantSlug} onChange={(event) => setTenantSlug(event.target.value)} placeholder="client-a" autoComplete="organization" required fullWidth />
            <TextField label="Email" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="username" type="email" required fullWidth />
            <TextField label="Password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" type="password" required fullWidth />
            <Button type="submit" size="large" variant="contained" disabled={submitting} sx={{ minHeight: 48, borderRadius: 2, fontWeight: 800, textTransform: 'none' }}>
              {submitting ? <CircularProgress size={22} color="inherit" /> : 'Continue to Inventory'}
            </Button>
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
};
