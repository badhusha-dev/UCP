import { useState } from 'react';
import { Alert, Box, Button, List, ListItemButton, ListItemText, MenuItem, Paper, Select, Stack, TextField, Typography } from '@mui/material';
import { useSelector } from 'react-redux';
import type { RootState } from '@ecommerce/shared-library';
import { Loading, useLoad } from '../pricing/common';
import { useNotice } from './common';
import { sf } from './storefrontApi';
import type { Row } from './storefrontApi';
import { can as hasPermission, slugify, visibleScreens } from './storefrontModel';
import type { ScreenId } from './storefrontModel';
import { HomepageScreen, LandingScreen } from './BuilderScreens';
import { BannersScreen, DashboardScreen, DomainsScreen, LocalizationScreen, MediaLibrary, NavigationScreen, OverviewScreen, ProductDisplayScreen, RestaurantScreen, SeoScreen, SettingsScreen, ThemeScreen } from './SiteScreens';
import { PublishingScreen } from './WorkflowScreens';

function CreateStorefront({ onCreated }: { onCreated: () => void }) {
  const [form, setForm] = useState({ name: '', slug: '', storeId: '', storefrontType: 'RETAIL' });
  const { run, view } = useNotice();
  const valid = form.name.trim() !== '' && /^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$/.test(form.slug) && form.storeId.trim() !== '';
  return (
    <Paper variant="outlined" sx={{ p: 2, maxWidth: 560 }}>
      {view}
      <Typography variant="subtitle1" gutterBottom>Create a storefront</Typography>
      <Stack spacing={1}>
        <TextField size="small" label="Name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value, slug: form.slug === slugify(form.name) || form.slug === '' ? slugify(e.target.value) : form.slug })} />
        <TextField size="small" label="Address name (subdomain)" helperText="Lowercase letters, digits and hyphens" value={form.slug} onChange={(e) => setForm({ ...form, slug: e.target.value })} />
        <TextField size="small" label="Store ID" helperText="Your own identifier for this store; one storefront per store" value={form.storeId} onChange={(e) => setForm({ ...form, storeId: e.target.value })} />
        <Select size="small" value={form.storefrontType} onChange={(e) => setForm({ ...form, storefrontType: e.target.value })}><MenuItem value="RETAIL">Retail store</MenuItem><MenuItem value="RESTAURANT">Restaurant</MenuItem></Select>
        <Button variant="contained" disabled={!valid} onClick={() => { void run(() => sf.create(form), 'Storefront created.', onCreated); }}>Create storefront</Button>
      </Stack>
    </Paper>
  );
}

/** "Storefront Management" area of the E-commerce Admin application. Everything shown comes from the storefront API. */
export default function StorefrontManagementPage() {
  const user = useSelector((state: RootState) => state.auth.user) as { role?: string; permissions?: string[] } | null;
  const can = (permission: string) => hasPermission(user?.role, user?.permissions, permission);
  const screens = visibleScreens(user?.role, user?.permissions);
  const list = useLoad(() => sf.list(), []);
  const [storefrontId, setStorefrontId] = useState<number | null>(null);
  const [screen, setScreen] = useState<ScreenId | null>(null);

  if (screens.length === 0) return <Alert severity="warning" sx={{ m: 3 }}>You do not have access to Storefront Management. Ask a tenant administrator for a storefront role.</Alert>;
  const storefront: Row | undefined = (list.data ?? []).find((s) => s.id === (storefrontId ?? list.data?.[0]?.id));
  const current = screen ?? screens[0].id;
  const props = storefront ? { storefront, can, onChanged: () => { void list.reload(); } } : null;

  const body = (() => {
    if (!props) return null;
    switch (current) {
      case 'dashboard': return <DashboardScreen {...props} />;
      case 'overview': return <OverviewScreen {...props} />;
      case 'homepage': return <HomepageScreen key={`h${storefront!.id}`} {...props} />;
      case 'collections': return <HomepageScreen key={`c${storefront!.id}`} {...props} collections />;
      case 'landing': return <LandingScreen key={storefront!.id} {...props} />;
      case 'banners': return <BannersScreen key={storefront!.id} {...props} />;
      case 'navigation': return <NavigationScreen key={storefront!.id} {...props} />;
      case 'products': return <ProductDisplayScreen key={storefront!.id} {...props} />;
      case 'restaurant': return <RestaurantScreen key={storefront!.id} {...props} />;
      case 'theme': return <ThemeScreen key={storefront!.id} {...props} />;
      case 'content': return <MediaLibrary key={storefront!.id} {...props} />;
      case 'seo': return <SeoScreen key={storefront!.id} {...props} />;
      case 'domains': return <DomainsScreen key={storefront!.id} {...props} />;
      case 'localization': return <LocalizationScreen {...props} />;
      case 'publishing': return <PublishingScreen key={`p${storefront!.id}`} {...props} mode="publishing" />;
      case 'history': return <PublishingScreen key={`v${storefront!.id}`} {...props} mode="history" />;
      case 'settings': return <SettingsScreen key={storefront!.id} {...props} />;
      default: return null;
    }
  })();

  return (
    <Box sx={{ display: 'flex', gap: 2, p: { xs: 1, md: 3 }, flexDirection: { xs: 'column', md: 'row' } }}>
      <Paper variant="outlined" sx={{ minWidth: 250, alignSelf: 'flex-start' }}>
        <Typography variant="overline" sx={{ px: 2, pt: 1, display: 'block' }}>Storefront Management</Typography>
        {(list.data ?? []).length > 1 && (
          <Box sx={{ px: 2, pb: 1 }}><Select size="small" fullWidth value={storefront?.id ?? ''} onChange={(e) => setStorefrontId(Number(e.target.value))}>
            {(list.data ?? []).map((s) => <MenuItem key={s.id} value={s.id}>{s.name}</MenuItem>)}</Select></Box>
        )}
        <List dense>{screens.map((s) => <ListItemButton key={s.id} selected={s.id === current} onClick={() => setScreen(s.id)}><ListItemText primary={s.label} /></ListItemButton>)}</List>
      </Paper>
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Loading loading={list.loading} error={list.error} />
        {!list.loading && !list.error && !storefront && (can('storefront:manage')
          ? <CreateStorefront onCreated={() => { void list.reload(); }} />
          : <Alert severity="info">No storefront exists for this business yet. Ask a storefront manager to create one.</Alert>)}
        {storefront && <>
          <Stack direction="row" alignItems="center" spacing={2} sx={{ mb: 1 }}>
            <Typography variant="h5">{screens.find((s) => s.id === current)?.label}</Typography>
            <Typography variant="body2" color="text.secondary">{storefront.name} · {storefront.storefrontType === 'RESTAURANT' ? 'Restaurant' : 'Retail'}</Typography>
          </Stack>
          {body}
          {can('storefront:manage') && <Box sx={{ mt: 4 }}><Typography variant="caption" color="text.secondary">Need another store?</Typography><CreateStorefront onCreated={() => { void list.reload(); }} /></Box>}
        </>}
      </Box>
    </Box>
  );
}
