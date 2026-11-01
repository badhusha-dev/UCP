import { useState } from 'react';
import { Alert, Box, List, ListItemButton, ListItemText, Paper, Typography } from '@mui/material';
import { useSelector } from 'react-redux';
import type { RootState } from '@ecommerce/shared-library';
import { can as hasPermission, visibleMenu } from './controlModel';
import type { ScreenId } from './controlModel';
import OrderDetails from './OrderDetails';
import OrdersScreen from './OrdersScreen';
import { AuditScreen, DashboardScreen, ExceptionsScreen, ReportsScreen, ReturnsScreen, SettingsScreen } from './OtherScreens';

/** "Order Management" area of the E-commerce Admin application. Every number and action comes from the order API. */
export default function OrderControlTower() {
  const user = useSelector((state: RootState) => state.auth.user) as { role?: string; permissions?: string[] } | null;
  const can = (p: string) => hasPermission(user?.role, user?.permissions, p);
  const menu = visibleMenu(user?.role, user?.permissions);
  const [screen, setScreen] = useState<ScreenId | null>(null);
  const [orderId, setOrderId] = useState<number | null>(null);
  const current = screen ?? menu[0]?.id;
  const item = menu.find((m) => m.id === current);

  if (menu.length === 0) return <Alert severity="warning" sx={{ m: 3 }}>You do not have access to Order Management. Ask a tenant administrator for an order role.</Alert>;

  const open = (id: number) => setOrderId(id);
  const body = (() => {
    if (orderId !== null) return <OrderDetails orderId={orderId} can={can} onBack={() => setOrderId(null)} />;
    switch (current) {
      case 'dashboard': return <DashboardScreen />;
      case 'exceptions': return <ExceptionsScreen can={can} onOpen={open} />;
      case 'returns': return <ReturnsScreen onOpen={open} />;
      case 'reports': return <ReportsScreen />;
      case 'settings': return <SettingsScreen can={can} />;
      case 'audit': return <AuditScreen onOpen={open} />;
      case 'bulk': return <OrdersScreen key="bulk" title="Bulk Order Operations" can={can} role={user?.role} permissions={user?.permissions} bulkMode onOpen={open} />;
      default: return item ? <OrdersScreen key={item.id} view={item.view ?? ''} title={item.label} can={can} role={user?.role} permissions={user?.permissions} onOpen={open} /> : null;
    }
  })();

  return (
    <Box sx={{ display: 'flex', gap: 2, p: { xs: 1, md: 3 }, flexDirection: { xs: 'column', md: 'row' } }}>
      <Paper variant="outlined" sx={{ minWidth: 250, alignSelf: 'flex-start' }}>
        <Typography variant="overline" sx={{ px: 2, pt: 1, display: 'block' }}>Order Management</Typography>
        <List dense>{menu.map((m) => (
          <ListItemButton key={m.id} selected={orderId === null && m.id === current} onClick={() => { setScreen(m.id); setOrderId(null); }}><ListItemText primary={m.label} /></ListItemButton>
        ))}</List>
      </Paper>
      <Box sx={{ flex: 1, minWidth: 0 }}>
        {orderId === null && <Typography variant="h5" gutterBottom>{item?.label}</Typography>}
        {body}
      </Box>
    </Box>
  );
}
