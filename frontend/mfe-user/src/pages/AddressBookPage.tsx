import { useCallback, useEffect, useState } from 'react';
import axios from 'axios';
import { useSelector } from 'react-redux';
import type { RootState } from '@ecommerce/shared-library';
import {
  Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent,
  DialogTitle, FormControlLabel, MenuItem, Stack, Switch, TextField, Typography,
} from '@mui/material';
import { AddRounded, DeleteOutlineRounded, EditRounded, HomeRounded } from '@mui/icons-material';

type Address = { id: number; addressType: string; street: string; city: string; state?: string; postalCode: string; country: string; isDefault: boolean };
type Form = Omit<Address, 'id'>;
const blank: Form = { addressType: 'SHIPPING', street: '', city: '', state: '', postalCode: '', country: 'US', isDefault: false };
const api = axios.create({ baseURL: '/api' });
api.interceptors.request.use(config => {
  const token = localStorage.getItem('jwt_token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
const message = (error: any) => String(error?.response?.data?.message || error?.message || 'Unable to complete the request.');

export default function AddressBookPage() {
  const user = useSelector((state: RootState) => state.auth.user);
  const [rows, setRows] = useState<Address[]>([]);
  const [busy, setBusy] = useState(true);
  const [failure, setFailure] = useState('');
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<Address | null>(null);
  const [form, setForm] = useState<Form>(blank);

  const load = useCallback(async () => {
    if (!user?.id) { setRows([]); setBusy(false); return; }
    setBusy(true); setFailure('');
    try { setRows((await api.get<Address[]>(`/users/${user.id}/addresses`)).data); }
    catch (error) { setFailure(message(error)); }
    finally { setBusy(false); }
  }, [user?.id]);
  useEffect(() => { void load(); }, [load]);

  const add = () => { setEditing(null); setForm({ ...blank }); setFailure(''); setOpen(true); };
  const edit = (row: Address) => { setEditing(row); setForm({ addressType: row.addressType, street: row.street, city: row.city, state: row.state || '', postalCode: row.postalCode, country: row.country, isDefault: row.isDefault }); setFailure(''); setOpen(true); };
  const save = async () => {
    if (!user?.id) return;
    setFailure('');
    try {
      if (editing) await api.put(`/users/${user.id}/addresses/${editing.id}`, form);
      else await api.post(`/users/${user.id}/addresses`, form);
      setOpen(false); await load();
    } catch (error) { setFailure(message(error)); }
  };
  const remove = async (row: Address) => {
    if (!user?.id || !window.confirm(`Remove this ${row.addressType.toLowerCase()} address?`)) return;
    try { await api.delete(`/users/${user.id}/addresses/${row.id}`); await load(); }
    catch (error) { setFailure(message(error)); }
  };

  return <Box sx={{ maxWidth: 1040, mx: 'auto', p: { xs: 1.5, md: 3 } }}>
    <Stack direction={{ xs: 'column', sm: 'row' }} alignItems={{ sm: 'center' }} justifyContent="space-between" spacing={2} sx={{ mb: 3 }}>
      <Box><Typography variant="h4" fontWeight={850}>Address book</Typography><Typography color="text.secondary">Maintain saved shipping and billing addresses for checkout.</Typography></Box>
      <Button variant="contained" startIcon={<AddRounded />} onClick={add}>Add address</Button>
    </Stack>
    {failure && !open && <Alert severity="error" sx={{ mb: 2 }}>{failure}</Alert>}
    {busy ? <Typography color="text.secondary">Loading saved addresses…</Typography> : <Stack spacing={1.5}>
      {rows.map(row => <Card key={row.id} variant="outlined"><CardContent><Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'center' }}>
        <Box sx={{ p: 1.2, borderRadius: 2, bgcolor: 'action.hover', display: 'flex' }}><HomeRounded color="primary" /></Box>
        <Box sx={{ flex: 1 }}><Stack direction="row" spacing={1} alignItems="center"><Typography fontWeight={800}>{row.addressType}</Typography>{row.isDefault && <Chip size="small" color="primary" label="Default" />}</Stack><Typography>{row.street}</Typography><Typography color="text.secondary">{[row.city, row.state, row.postalCode, row.country].filter(Boolean).join(', ')}</Typography></Box>
        <Stack direction="row"><Button startIcon={<EditRounded />} onClick={() => edit(row)}>Edit</Button><Button color="error" startIcon={<DeleteOutlineRounded />} onClick={() => void remove(row)}>Delete</Button></Stack>
      </Stack></CardContent></Card>)}
      {!rows.length && <Card variant="outlined"><CardContent><Typography color="text.secondary">No saved addresses yet. Add one to speed up checkout.</Typography></CardContent></Card>}
    </Stack>}
    <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="sm"><DialogTitle>{editing ? 'Edit address' : 'Add address'}</DialogTitle><DialogContent><Stack spacing={2} sx={{ pt: 1 }}>
      <TextField select label="Address type" value={form.addressType} onChange={event => setForm({ ...form, addressType: event.target.value })}><MenuItem value="SHIPPING">Shipping</MenuItem><MenuItem value="BILLING">Billing</MenuItem></TextField>
      <TextField required label="Street address" value={form.street} onChange={event => setForm({ ...form, street: event.target.value })} />
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}><TextField required fullWidth label="City" value={form.city} onChange={event => setForm({ ...form, city: event.target.value })} /><TextField fullWidth label="State / province" value={form.state} onChange={event => setForm({ ...form, state: event.target.value })} /></Stack>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}><TextField required fullWidth label="Postal code" value={form.postalCode} onChange={event => setForm({ ...form, postalCode: event.target.value })} /><TextField required fullWidth label="Country code" value={form.country} onChange={event => setForm({ ...form, country: event.target.value.toUpperCase() })} /></Stack>
      <FormControlLabel control={<Switch checked={form.isDefault} onChange={event => setForm({ ...form, isDefault: event.target.checked })} />} label="Make this my default address" />
      {open && failure && <Alert severity="error">{failure}</Alert>}
    </Stack></DialogContent><DialogActions><Button onClick={() => setOpen(false)}>Cancel</Button><Button variant="contained" disabled={!form.street.trim() || !form.city.trim() || !form.postalCode.trim() || !form.country.trim()} onClick={() => void save()}>{editing ? 'Save changes' : 'Save address'}</Button></DialogActions></Dialog>
  </Box>;
}
