import { useState } from 'react';
import { Alert, Box, List, ListItemButton, ListItemText, Paper, Typography } from '@mui/material';
import { useSelector } from 'react-redux';
import type { RootState } from '@ecommerce/shared-library';
import { can as hasPermission, visibleMenu } from './returnsModel';
import type { ScreenId } from './returnsModel';
import ReturnDetails from './ReturnDetails';
import { ConfigScreen, PoliciesScreen } from './PolicyScreens';
import { ExceptionsScreen, ExchangesScreen, ReturnsAnalytics, ReturnsAudit, ReturnsDashboard, ReturnsQueue } from './ReturnsScreens';

/** "Returns & Refunds" area of the E-commerce Admin application. Every number and action comes from the order service's RMA API. */
export default function ReturnsConsole() {
  const user = useSelector((state: RootState) => state.auth.user) as { role?: string; permissions?: string[] } | null;
  const can = (p: string) => hasPermission(user?.role, user?.permissions, p);
  const menu = visibleMenu(user?.role, user?.permissions);
  const [screen, setScreen] = useState<ScreenId | null>(null);
  const [returnId, setReturnId] = useState<number | null>(null);
  const current = screen ?? menu[0]?.id;
  const item = menu.find((m) => m.id === current);

  if (menu.length === 0) return <Alert severity="warning" sx={{ m: 3 }}>You do not have access to Returns & Refunds. Ask a tenant administrator for a returns role.</Alert>;

  const open = (id: number) => setReturnId(id);
  const body = (() => {
    if (returnId !== null) return <ReturnDetails key={returnId} id={returnId} can={can} onBack={() => setReturnId(null)} />;
    switch (current) {
      case 'dashboard': return <ReturnsDashboard />;
      case 'all': return <ReturnsQueue key="all" can={can} onOpen={open} />;
      case 'exchanges': return <ExchangesScreen onOpen={open} />;
      case 'exceptions': return <ExceptionsScreen can={can} onOpen={open} />;
      case 'policies': return <PoliciesScreen can={can} />;
      case 'config': return <ConfigScreen can={can} />;
      case 'analytics': return <ReturnsAnalytics />;
      case 'audit': return <ReturnsAudit onOpen={open} />;
      default: return item ? <ReturnsQueue key={item.id} view={item.view} can={can} onOpen={open} /> : null;
    }
  })();

  return (
    <Box sx={{ display: 'flex', gap: 2, p: { xs: 1, md: 3 }, flexDirection: { xs: 'column', md: 'row' } }}>
      <Paper variant="outlined" sx={{ minWidth: 250, alignSelf: 'flex-start' }}>
        <Typography variant="overline" sx={{ px: 2, pt: 1, display: 'block' }}>Returns &amp; Refunds</Typography>
        <List dense>{menu.map((m) => (
          <ListItemButton key={m.id} selected={returnId === null && m.id === current} onClick={() => { setScreen(m.id); setReturnId(null); }}><ListItemText primary={m.label} /></ListItemButton>
        ))}</List>
      </Paper>
      <Box sx={{ flex: 1, minWidth: 0 }}>
        {returnId === null && <Typography variant="h5" gutterBottom>{item?.label}</Typography>}
        {body}
      </Box>
    </Box>
  );
}
