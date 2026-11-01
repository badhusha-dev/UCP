import { useEffect, useMemo, useState } from 'react';
import axios from 'axios';
import {
  Alert, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent,
  DialogTitle, FormControl, InputLabel, MenuItem, Paper, Select, Stack, Table,
  TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { AddRounded, ArchiveRounded, EditRounded, RestoreRounded } from '@mui/icons-material';
import { useSelector } from 'react-redux';
import type { RootState } from '@ecommerce/shared-library';

axios.interceptors.request.use((config) => {
  const token = typeof window !== 'undefined' ? localStorage.getItem('jwt_token') : null;
  if (token && !config.headers.Authorization) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

type Category = { id: number; name: string };
type Product = {
  id: number; name: string; description?: string; price: number; costPrice?: number | null;
  reorderThreshold?: number; imageUrl?: string; active: boolean; category?: Category | null;
};
type Form = {
  name: string; description: string; price: string; costPrice: string;
  reorderThreshold: string; categoryId: string; imageUrl: string;
};
const emptyForm: Form = { name: '', description: '', price: '', costPrice: '', reorderThreshold: '10', categoryId: '', imageUrl: '' };
const api = 'http://localhost:8080/api/products/admin';

export default function ProductAdminPage() {
  const role = useSelector((state: RootState) => state.auth.user?.role);
  const [rows, setRows] = useState<Product[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [failure, setFailure] = useState('');
  const [showArchived, setShowArchived] = useState(false);
  const [editing, setEditing] = useState<Product | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState<Form>(emptyForm);

  const load = async () => {
    setLoading(true);
    setFailure('');
    try {
      const [products, categoryList] = await Promise.all([
        axios.get<Product[]>(api), axios.get<Category[]>('http://localhost:8080/api/products/categories'),
      ]);
      setRows(products.data);
      setCategories(categoryList.data);
    } catch (error: any) {
      setFailure(error?.response?.data?.message || error?.message || 'Unable to load catalog records.');
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => { if (role === 'ADMIN') void load(); else setLoading(false); }, [role]);

  const visibleRows = useMemo(() => rows.filter((row) => showArchived || row.active), [rows, showArchived]);
  const startCreate = () => { setEditing(null); setForm(emptyForm); setFailure(''); setOpen(true); };
  const startEdit = (row: Product) => {
    setEditing(row);
    setForm({ name: row.name, description: row.description || '', price: String(row.price),
      costPrice: row.costPrice == null ? '' : String(row.costPrice),
      reorderThreshold: String(row.reorderThreshold ?? 10), categoryId: row.category?.id ? String(row.category.id) : '', imageUrl: row.imageUrl || '' });
    setFailure(''); setOpen(true);
  };
  const save = async () => {
    setFailure(''); setSaving(true);
    const body = { name: form.name.trim(), description: form.description.trim() || null,
      price: Number(form.price), costPrice: form.costPrice === '' ? null : Number(form.costPrice),
      reorderThreshold: Number(form.reorderThreshold), categoryId: form.categoryId ? Number(form.categoryId) : null,
      imageUrl: form.imageUrl.trim() || null };
    try {
      if (editing) await axios.put(`${api}/${editing.id}`, body);
      else await axios.post(api, body);
      setOpen(false); await load();
    } catch (error: any) {
      setFailure(error?.response?.data?.message || error?.message || 'Unable to save product.');
    } finally { setSaving(false); }
  };
  const toggleArchived = async (row: Product) => {
    setFailure('');
    try {
      if (row.active) await axios.delete(`${api}/${row.id}`);
      else await axios.patch(`${api}/${row.id}/restore`);
      await load();
    } catch (error: any) { setFailure(error?.response?.data?.message || error?.message || 'Unable to update product status.'); }
  };

  if (role !== 'ADMIN') return <Box sx={{ maxWidth: 760, mx: 'auto', p: { xs: 2, md: 5 } }}><Alert severity="warning">Catalog management is available to administrators.</Alert></Box>;

  return <Box sx={{ maxWidth: 1440, mx: 'auto', p: { xs: 1.5, md: 3 } }}>
    <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ sm: 'center' }} spacing={2} sx={{ mb: 2.5 }}>
      <Box><Typography variant="h4" fontWeight={850}>Catalog management</Typography><Typography color="text.secondary">Create and maintain product details. Stock levels remain managed by the Stock service.</Typography></Box>
      <Stack direction="row" spacing={1}><Button variant={showArchived ? 'contained' : 'outlined'} onClick={() => setShowArchived((value) => !value)}>{showArchived ? 'Showing archived' : 'Show archived'}</Button><Button startIcon={<AddRounded />} variant="contained" onClick={startCreate}>Add product</Button></Stack>
    </Stack>
    {failure && !open && <Alert severity="error" sx={{ mb: 2 }}>{failure}</Alert>}
    {loading ? <Box sx={{ display: 'grid', placeItems: 'center', minHeight: 240 }}><CircularProgress /></Box> :
      <TableContainer component={Paper} variant="outlined"><Table><TableHead><TableRow>
        <TableCell>Product</TableCell><TableCell>Category</TableCell><TableCell align="right">Price</TableCell><TableCell align="right">Cost</TableCell><TableCell align="right">Reorder at</TableCell><TableCell>Status</TableCell><TableCell align="right">Actions</TableCell>
      </TableRow></TableHead><TableBody>
        {visibleRows.map((row) => <TableRow key={row.id} hover>
          <TableCell><Typography fontWeight={750}>{row.name}</Typography><Typography variant="caption" color="text.secondary">#{row.id} · {row.description || 'No description'}</Typography></TableCell>
          <TableCell>{row.category?.name || 'Uncategorized'}</TableCell><TableCell align="right">${Number(row.price).toFixed(2)}</TableCell><TableCell align="right">{row.costPrice == null ? '—' : `$${Number(row.costPrice).toFixed(2)}`}</TableCell><TableCell align="right">{row.reorderThreshold ?? 10}</TableCell>
          <TableCell><Chip size="small" color={row.active ? 'success' : 'default'} label={row.active ? 'Active' : 'Archived'} /></TableCell>
          <TableCell align="right"><Stack direction="row" justifyContent="flex-end"><Button size="small" startIcon={<EditRounded />} onClick={() => startEdit(row)}>Edit</Button><Button size="small" color={row.active ? 'warning' : 'success'} startIcon={row.active ? <ArchiveRounded /> : <RestoreRounded />} onClick={() => void toggleArchived(row)}>{row.active ? 'Archive' : 'Restore'}</Button></Stack></TableCell>
        </TableRow>)}
        {!visibleRows.length && <TableRow><TableCell colSpan={7} align="center" sx={{ py: 5, color: 'text.secondary' }}>No products match this view.</TableCell></TableRow>}
      </TableBody></Table></TableContainer>}
    <Dialog open={open} onClose={() => !saving && setOpen(false)} fullWidth maxWidth="sm">
      <DialogTitle>{editing ? `Edit ${editing.name}` : 'Add product'}</DialogTitle>
      <DialogContent><Stack spacing={2} sx={{ pt: 1 }}>
        <TextField required autoFocus label="Product name" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} />
        <TextField multiline minRows={2} label="Description" value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} />
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}><TextField required type="number" inputProps={{ min: 0, step: '0.01' }} label="Price" value={form.price} onChange={(event) => setForm({ ...form, price: event.target.value })} /><TextField type="number" inputProps={{ min: 0, step: '0.01' }} label="Cost price" value={form.costPrice} onChange={(event) => setForm({ ...form, costPrice: event.target.value })} /></Stack>
        <FormControl fullWidth><InputLabel id="product-category-label">Category</InputLabel><Select labelId="product-category-label" label="Category" value={form.categoryId} onChange={(event) => setForm({ ...form, categoryId: event.target.value })}><MenuItem value="">Uncategorized</MenuItem>{categories.map((category) => <MenuItem key={category.id} value={String(category.id)}>{category.name}</MenuItem>)}</Select></FormControl>
        <TextField type="number" inputProps={{ min: 0, step: 1 }} label="Low stock threshold" value={form.reorderThreshold} onChange={(event) => setForm({ ...form, reorderThreshold: event.target.value })} />
        <TextField label="Image URL" value={form.imageUrl} onChange={(event) => setForm({ ...form, imageUrl: event.target.value })} />
        <Alert severity="info">Initial stock is zero. Receive or adjust stock in the Stock module after saving.</Alert>
        {open && failure && <Alert severity="error">{failure}</Alert>}
      </Stack></DialogContent>
      <DialogActions><Button disabled={saving} onClick={() => setOpen(false)}>Cancel</Button><Button disabled={saving || !form.name.trim() || form.price === ''} variant="contained" onClick={() => void save()}>{saving ? 'Saving…' : editing ? 'Save changes' : 'Create product'}</Button></DialogActions>
    </Dialog>
  </Box>;
}
