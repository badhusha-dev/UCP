import React, { useState } from 'react';
import { Routes, Route } from 'react-router-dom';
import { InventoryCatalogPage } from './pages/InventoryCatalogPage';
import { InventoryLoginPage } from './pages/InventoryLoginPage';
import OpsWorkspace from './ops/OpsWorkspace';
import { Button, Container, Paper, Stack, Typography } from '@mui/material';
import {
  clearApplicationSession,
  readApplicationSession,
  storeApplicationSession,
  type ApplicationSession,
} from '@ecommerce/shared-library/src/auth/applicationSession';

export default function App() {
  const [session, setSession] = useState<ApplicationSession | null>(
    () => readApplicationSession('INVENTORY', sessionStorage));

  const authenticate = (next: ApplicationSession) => {
    storeApplicationSession(next, sessionStorage);
    setSession(next);
  };

  const signOut = () => {
    clearApplicationSession('INVENTORY', sessionStorage);
    setSession(null);
  };

  if (!session) return <InventoryLoginPage onAuthenticated={authenticate} />;

  return (
    <>
      <Paper square elevation={0} sx={{ borderBottom: '1px solid #E5EAF2', bgcolor: '#fff' }}>
        <Container maxWidth="xl" sx={{ py: 1.5 }}>
          <Stack direction="row" alignItems="center" justifyContent="space-between" gap={2}>
            <div>
              <Typography variant="subtitle1" sx={{ fontWeight: 800, color: '#14233B' }}>Inventory & Procurement</Typography>
              <Typography variant="caption" color="text.secondary">{session.tenantName || session.tenantSlug} · {session.role}</Typography>
            </div>
            <Button onClick={signOut} variant="outlined" size="small" sx={{ textTransform: 'none', borderRadius: 2 }}>Sign out</Button>
          </Stack>
        </Container>
      </Paper>
      <Container maxWidth="xl" sx={{ py: { xs: 2, md: 3 } }}>
        <Routes>
          <Route path="catalog" element={<InventoryCatalogPage />} />
          <Route path="*" element={<OpsWorkspace />} />
        </Routes>
      </Container>
    </>
  );
}
