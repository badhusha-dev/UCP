import React from 'react';
import { Route, Routes, useNavigate } from 'react-router-dom';
import { Box, Button, Container, Paper, Tab, Tabs, Typography } from '@mui/material';
import LoyaltyDashboardPage from './pages/LoyaltyDashboardPage';
import LoyaltyAdminPage from './pages/LoyaltyAdminPage';

export default function App() {
  const navigate = useNavigate();
  const admin = window.location.pathname.includes('/admin');
  return <Container maxWidth="lg" sx={{ py: 3 }}>
    <Paper sx={{ p: 2, mb: 3, borderRadius: 3 }}>
      <Box display="flex" alignItems="center" justifyContent="space-between" flexWrap="wrap" gap={2}>
        <Box><Typography variant="h4" fontWeight={800}>Rewards & Loyalty</Typography><Typography color="text.secondary">Track points, membership tier, and rewards.</Typography></Box>
        <Button variant="outlined" onClick={() => navigate(admin ? '/loyalty' : '/loyalty/admin')}>{admin ? 'My rewards' : 'Loyalty administration'}</Button>
      </Box>
      <Tabs value={admin ? 1 : 0} onChange={(_, value) => navigate(value ? '/loyalty/admin' : '/loyalty')} sx={{ mt: 2 }}>
        <Tab label="My rewards" /><Tab label="Administration" />
      </Tabs>
    </Paper>
    <Routes>
      <Route path="/admin" element={<LoyaltyAdminPage />} />
      <Route path="*" element={<LoyaltyDashboardPage />} />
    </Routes>
  </Container>;
}
