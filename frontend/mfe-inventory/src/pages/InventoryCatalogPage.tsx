import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  InputAdornment,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import {
  AttachMoneyOutlined,
  Inventory2Outlined,
  SearchOutlined,
  StorefrontRounded,
  TuneOutlined,
  UploadFileOutlined,
  DownloadOutlined,
  RefreshOutlined,
  AddOutlined,
  ArchiveOutlined,
  RestoreOutlined,
  EditOutlined,
} from '@mui/icons-material';
import type { InventoryItem } from '../types/inventory';
import { inventoryApi } from '../api/inventoryApi';
import { CsvImportModal } from '../components/CsvImportModal';

const MetricCard = ({ title, value, caption, icon, tint }: {
  title: string; value: string; caption: string; icon: React.ReactNode; tint: string;
}) => (
  <Card sx={{ height: '100%', borderRadius: 3, border: '1px solid #E8EDF4', boxShadow: '0 8px 24px rgba(15, 23, 42, .035)' }}>
    <CardContent sx={{ p: 2.25, '&:last-child': { pb: 2.25 } }}>
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
        <Box>
          <Typography variant="body2" sx={{ color: '#64748B', fontWeight: 600 }}>{title}</Typography>
          <Typography variant="h5" sx={{ mt: 1, color: '#10223C', fontWeight: 800, letterSpacing: '-.03em' }}>{value}</Typography>
        </Box>
        <Box sx={{ width: 40, height: 40, display: 'grid', placeItems: 'center', borderRadius: 2, color: tint, bgcolor: `${tint}14` }}>{icon}</Box>
      </Stack>
      <Typography variant="caption" sx={{ display: 'block', mt: 1.25, color: '#8A97A9' }}>{caption}</Typography>
    </CardContent>
  </Card>
);

export const InventoryCatalogPage: React.FC = () => {
  const [items, setItems] = useState<InventoryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [importOpen, setImportOpen] = useState(false);
  const [formOpen, setFormOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<InventoryItem | null>(null);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState({ productId: '', sku: '', reorderThreshold: '10', costPrice: '0', warehouseLocation: '' });

  const loadItems = async () => {
    setLoading(true);
    setError(null);
    try {
      setItems(await inventoryApi.getAllItems());
    } catch (err: any) {
      setError(err?.response?.data?.message || err?.message || 'Could not load the inventory catalog.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void loadItems(); }, []);

  const warehouses = useMemo(() => new Set(items.map((item) => item.warehouseLocation?.trim()).filter(Boolean)).size, [items]);
  const activeItems = useMemo(() => items.filter((item) => item.active !== false), [items]);
  const filteredItems = items.filter((item) =>
    !query || `${item.productId} ${item.sku ?? ''} ${item.warehouseLocation ?? ''}`.toLowerCase().includes(query.toLowerCase()),
  );

  const exportCsv = async () => {
    try {
      const csv = await inventoryApi.exportCsv();
      const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8;' }));
      const link = document.createElement('a');
      link.href = url;
      link.download = `inventory-catalog-${new Date().toISOString().slice(0, 10)}.csv`;
      link.click();
      URL.revokeObjectURL(url);
    } catch (err: any) {
      setError(err?.response?.data?.message || err?.message || 'Could not export the inventory catalog.');
    }
  };

  const openCreate = () => {
    setEditingItem(null);
    setForm({ productId: '', sku: '', reorderThreshold: '10', costPrice: '0', warehouseLocation: '' });
    setFormOpen(true);
  };

  const openEdit = (item: InventoryItem) => {
    setEditingItem(item);
    setForm({
      productId: String(item.productId),
      sku: item.sku || '',
      reorderThreshold: String(item.reorderThreshold ?? 0),
      costPrice: String(item.costPrice ?? 0),
      warehouseLocation: item.warehouseLocation || '',
    });
    setFormOpen(true);
  };

  const saveMetadata = async () => {
    const productId = Number(form.productId);
    const reorderThreshold = Number(form.reorderThreshold);
    const costPrice = Number(form.costPrice);
    if (!Number.isInteger(productId) || productId <= 0 || !Number.isInteger(reorderThreshold) || reorderThreshold < 0 || !Number.isFinite(costPrice) || costPrice < 0) {
      setError('Enter a valid product ID, non-negative reorder threshold, and non-negative unit cost.');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const payload = { productId, sku: form.sku.trim(), reorderThreshold, costPrice, warehouseLocation: form.warehouseLocation.trim() };
      if (editingItem) await inventoryApi.updateMetadata(editingItem.productId, payload);
      else await inventoryApi.createMetadata(payload);
      setFormOpen(false);
      await loadItems();
    } catch (err: any) {
      setError(err?.response?.data?.message || err?.message || 'Could not save inventory metadata.');
    } finally {
      setSaving(false);
    }
  };

  const archiveItem = async (item: InventoryItem) => {
    if (!window.confirm(`Archive SKU metadata for Product #${item.productId}? This is available only when physical and reserved stock are zero.`)) return;
    try {
      await inventoryApi.archiveMetadata(item.productId);
      await loadItems();
    } catch (err: any) {
      setError(err?.response?.data?.message || err?.message || 'Could not archive inventory metadata.');
    }
  };

  const restoreItem = async (item: InventoryItem) => {
    try {
      await inventoryApi.restoreMetadata(item.productId);
      await loadItems();
    } catch (err: any) {
      setError(err?.response?.data?.message || err?.message || 'Could not restore inventory metadata.');
    }
  };

  return (
    <Box sx={{ pb: 5 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: { xs: 'stretch', md: 'center' }, flexDirection: { xs: 'column', md: 'row' }, gap: 2, mb: 3 }}>
        <Box>
          <Typography variant="overline" sx={{ color: '#4F46E5', fontWeight: 800, letterSpacing: '.12em' }}>CATALOG CONFIGURATION</Typography>
          <Typography variant="h4" sx={{ color: '#10223C', fontWeight: 800, letterSpacing: '-.035em' }}>Inventory catalog</Typography>
          <Typography variant="body1" sx={{ mt: .75, color: '#66758A' }}>Manage SKU identity, warehouse placement, unit cost, and replenishment rules.</Typography>
        </Box>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.25}>
          <Button variant="outlined" startIcon={<RefreshOutlined />} onClick={() => void loadItems()} disabled={loading} sx={{ borderRadius: 2.5, px: 2, textTransform: 'none', fontWeight: 700 }}>Refresh</Button>
          <Button variant="outlined" startIcon={<DownloadOutlined />} onClick={() => void exportCsv()} sx={{ borderRadius: 2.5, px: 2, textTransform: 'none', fontWeight: 700 }}>Export CSV</Button>
          <Button variant="contained" startIcon={<UploadFileOutlined />} onClick={() => setImportOpen(true)} sx={{ borderRadius: 2.5, px: 2, textTransform: 'none', fontWeight: 700, boxShadow: 'none' }}>Import metadata</Button>
          <Button variant="contained" color="secondary" startIcon={<AddOutlined />} onClick={openCreate} sx={{ borderRadius: 2.5, px: 2, textTransform: 'none', fontWeight: 700, boxShadow: 'none' }}>Add SKU</Button>
        </Stack>
      </Box>

      {error && <Alert severity="error" sx={{ mb: 2.5, borderRadius: 2 }}>{error}</Alert>}

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} lg={3}><MetricCard title="Active catalog SKUs" value={loading ? '—' : activeItems.length.toLocaleString()} caption="Products with active inventory configuration" tint="#4F46E5" icon={<Inventory2Outlined />} /></Grid>
        <Grid item xs={12} sm={6} lg={3}><MetricCard title="Warehouse locations" value={loading ? '—' : warehouses.toLocaleString()} caption="Distinct bins and warehouse sites" tint="#0F9F8F" icon={<StorefrontRounded />} /></Grid>
        <Grid item xs={12} sm={6} lg={3}><MetricCard title="Reorder rules" value={loading ? '—' : activeItems.filter((item) => item.reorderThreshold > 0).length.toLocaleString()} caption="Active SKUs with a positive threshold" tint="#D18B1F" icon={<TuneOutlined />} /></Grid>
        <Grid item xs={12} sm={6} lg={3}><MetricCard title="Average unit cost" value={loading || activeItems.length === 0 ? '—' : `$${(activeItems.reduce((sum, item) => sum + Number(item.costPrice || 0), 0) / activeItems.length).toFixed(2)}`} caption="Configured cost across active SKUs" tint="#138A50" icon={<AttachMoneyOutlined />} /></Grid>
      </Grid>

      <Paper sx={{ p: 2, mb: 2, border: '1px solid #E8EDF4', borderRadius: 3, boxShadow: '0 4px 16px rgba(15, 23, 42, .025)' }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ xs: 'stretch', sm: 'center' }} spacing={1.5}>
          <Box>
            <Typography variant="h6" sx={{ color: '#10223C', fontWeight: 800 }}>SKU and warehouse records</Typography>
            <Typography variant="body2" sx={{ color: '#77869A', mt: .25 }}>Quantity, reservations, and stock movements are managed in Stock.</Typography>
          </Box>
          <TextField
            size="small"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search SKU, product, or location"
            sx={{ width: { xs: '100%', sm: 320 }, '& .MuiOutlinedInput-root': { borderRadius: 2.5 } }}
            InputProps={{ startAdornment: <InputAdornment position="start"><SearchOutlined sx={{ color: '#94A3B8' }} /></InputAdornment> }}
          />
        </Stack>
      </Paper>

      <TableContainer component={Paper} sx={{ border: '1px solid #E8EDF4', borderRadius: 3, boxShadow: '0 8px 24px rgba(15, 23, 42, .035)' }}>
        <Table>
          <TableHead sx={{ bgcolor: '#F8FAFC' }}>
            <TableRow>
              <TableCell sx={{ fontWeight: 800, color: '#66758A' }}>PRODUCT</TableCell>
              <TableCell sx={{ fontWeight: 800, color: '#66758A' }}>SKU</TableCell>
              <TableCell sx={{ fontWeight: 800, color: '#66758A' }}>WAREHOUSE / BIN</TableCell>
              <TableCell align="right" sx={{ fontWeight: 800, color: '#66758A' }}>REORDER AT</TableCell>
              <TableCell align="right" sx={{ fontWeight: 800, color: '#66758A' }}>UNIT COST</TableCell>
              <TableCell sx={{ fontWeight: 800, color: '#66758A' }}>STATUS</TableCell>
              <TableCell align="right" sx={{ fontWeight: 800, color: '#66758A' }}>UPDATED</TableCell>
              <TableCell align="right" sx={{ fontWeight: 800, color: '#66758A' }}>ACTIONS</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {loading ? (
              <TableRow><TableCell colSpan={8} align="center" sx={{ py: 7 }}><CircularProgress size={30} /><Typography variant="body2" sx={{ mt: 1, color: '#77869A' }}>Loading inventory metadata…</Typography></TableCell></TableRow>
            ) : filteredItems.length === 0 ? (
              <TableRow><TableCell colSpan={8} align="center" sx={{ py: 7 }}><Typography sx={{ color: '#66758A', fontWeight: 700 }}>{query ? 'No catalog records match this search.' : 'No inventory metadata yet.'}</Typography><Typography variant="body2" sx={{ mt: .5, color: '#94A3B8' }}>Add SKU metadata or import a catalog CSV.</Typography></TableCell></TableRow>
            ) : filteredItems.map((item) => (
              <TableRow key={item.productId} hover sx={{ opacity: item.active === false ? 0.65 : 1 }}>
                <TableCell><Chip size="small" label={`Product #${item.productId}`} sx={{ bgcolor: '#F1F5F9', color: '#475569', fontWeight: 700 }} /></TableCell>
                <TableCell sx={{ color: '#10223C', fontWeight: 800 }}>{item.sku || '—'}</TableCell>
                <TableCell sx={{ color: '#475569' }}>{item.warehouseLocation || 'Unassigned'}</TableCell>
                <TableCell align="right" sx={{ color: '#475569', fontWeight: 700 }}>{item.reorderThreshold ?? 0} units</TableCell>
                <TableCell align="right" sx={{ color: '#10223C', fontWeight: 800 }}>${Number(item.costPrice || 0).toFixed(2)}</TableCell>
                <TableCell><Chip size="small" label={item.active === false ? 'ARCHIVED' : 'ACTIVE'} color={item.active === false ? 'default' : 'success'} /></TableCell>
                <TableCell align="right" sx={{ color: '#77869A' }}>{item.updatedAt ? new Date(item.updatedAt).toLocaleDateString() : '—'}</TableCell>
                <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                  {item.active === false ? (
                    <Button size="small" startIcon={<RestoreOutlined />} onClick={() => void restoreItem(item)} sx={{ textTransform: 'none' }}>Restore</Button>
                  ) : (
                    <>
                      <Button size="small" startIcon={<EditOutlined />} onClick={() => openEdit(item)} sx={{ textTransform: 'none' }}>Edit</Button>
                      <Button size="small" color="error" startIcon={<ArchiveOutlined />} disabled={Number(item.stockQuantity || 0) > 0 || Number(item.reservedQuantity || 0) > 0} onClick={() => void archiveItem(item)} sx={{ textTransform: 'none' }}>Archive</Button>
                    </>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      <CsvImportModal open={importOpen} onClose={() => setImportOpen(false)} onSuccess={() => void loadItems()} />

      <Dialog open={formOpen} onClose={() => !saving && setFormOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>{editingItem ? `Edit metadata · Product #${editingItem.productId}` : 'Add inventory metadata'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField label="Product ID" type="number" value={form.productId} disabled={Boolean(editingItem)} onChange={(event) => setForm({ ...form, productId: event.target.value })} inputProps={{ min: 1 }} required />
            <TextField label="SKU" value={form.sku} onChange={(event) => setForm({ ...form, sku: event.target.value })} inputProps={{ maxLength: 64 }} />
            <TextField label="Warehouse / bin" value={form.warehouseLocation} onChange={(event) => setForm({ ...form, warehouseLocation: event.target.value })} inputProps={{ maxLength: 128 }} />
            <TextField label="Reorder threshold" type="number" value={form.reorderThreshold} onChange={(event) => setForm({ ...form, reorderThreshold: event.target.value })} inputProps={{ min: 0, step: 1 }} required />
            <TextField label="Unit cost" type="number" value={form.costPrice} onChange={(event) => setForm({ ...form, costPrice: event.target.value })} inputProps={{ min: 0, step: 0.0001 }} required />
            <Typography variant="caption" color="text.secondary">Stock quantity and reservations are managed from the Stock module.</Typography>
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setFormOpen(false)} disabled={saving}>Cancel</Button>
          <Button onClick={() => void saveMetadata()} variant="contained" disabled={saving}>{saving ? 'Saving…' : editingItem ? 'Save changes' : 'Create SKU'}</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};
