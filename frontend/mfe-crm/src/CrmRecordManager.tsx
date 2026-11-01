import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle,
  MenuItem, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead,
  TableRow, Tabs, Tab, TextField, Typography,
} from '@mui/material';
import { AddRounded, ArchiveRounded, EditRounded, TaskAltRounded } from '@mui/icons-material';
import axios from 'axios';

type RecordRow = Record<string, any>;
type Field = { key: string; label: string; type?: string; required?: boolean; options?: string[] };
type Resource = { key: string; label: string; endpoint: string; fields: Field[]; title: (row: RecordRow) => string };

const resources: Resource[] = [
  { key: 'contacts', label: 'Contacts', endpoint: '/api/crm/contacts', title: r => r.displayName,
    fields: [
      { key: 'displayName', label: 'Display name', required: true }, { key: 'email', label: 'Email', type: 'email' },
      { key: 'phone', label: 'Phone' }, { key: 'lifecycleStage', label: 'Lifecycle', type: 'select', options: ['LEAD', 'PROSPECT', 'CUSTOMER', 'CHURNED'] },
      { key: 'owner', label: 'Owner' }, { key: 'companyId', label: 'Company ID', type: 'number' },
      { key: 'tags', label: 'Tags (comma separated)' }, { key: 'source', label: 'Source' }, { key: 'notes', label: 'Notes', type: 'multiline' },
    ] },
  { key: 'companies', label: 'Companies', endpoint: '/api/crm/companies', title: r => r.name,
    fields: [
      { key: 'name', label: 'Company name', required: true }, { key: 'domain', label: 'Domain' }, { key: 'industry', label: 'Industry' },
      { key: 'companySize', label: 'Company size' }, { key: 'owner', label: 'Owner' }, { key: 'notes', label: 'Notes', type: 'multiline' },
    ] },
  { key: 'opportunities', label: 'Opportunities', endpoint: '/api/crm/opportunities', title: r => r.name,
    fields: [
      { key: 'name', label: 'Opportunity', required: true }, { key: 'contactId', label: 'Contact ID', type: 'number' },
      { key: 'companyId', label: 'Company ID', type: 'number' }, { key: 'stage', label: 'Stage', type: 'select', options: ['Qualification', 'Discovery', 'Proposal', 'Negotiation', 'Won', 'Lost'] },
      { key: 'amount', label: 'Amount', type: 'number' }, { key: 'currency', label: 'Currency' }, { key: 'probability', label: 'Probability (%)', type: 'number' },
      { key: 'expectedCloseDate', label: 'Expected close date', type: 'date' }, { key: 'owner', label: 'Owner' }, { key: 'notes', label: 'Notes', type: 'multiline' },
    ] },
  { key: 'tasks', label: 'Tasks', endpoint: '/api/crm/tasks', title: r => r.title,
    fields: [
      { key: 'title', label: 'Task title', required: true }, { key: 'description', label: 'Description', type: 'multiline' },
      { key: 'contactId', label: 'Contact ID', type: 'number' }, { key: 'opportunityId', label: 'Opportunity ID', type: 'number' },
      { key: 'owner', label: 'Owner' }, { key: 'dueAt', label: 'Due date and time', type: 'datetime-local' },
      { key: 'priority', label: 'Priority', type: 'select', options: ['LOW', 'NORMAL', 'HIGH'] },
    ] },
  { key: 'segments', label: 'Segments', endpoint: '/api/crm/segments', title: r => r.name,
    fields: [
      { key: 'name', label: 'Segment name', required: true }, { key: 'description', label: 'Description' },
      { key: 'lifecycleStage', label: 'Lifecycle condition', type: 'select', options: ['LEAD', 'PROSPECT', 'CUSTOMER', 'CHURNED'] },
    ] },
];

const host = typeof window !== 'undefined' ? window.location.hostname : 'localhost';
const api = axios.create({ baseURL: `http://${host}:8080` });
api.interceptors.request.use(config => {
  const token = typeof window !== 'undefined' ? localStorage.getItem('jwt_token') : null;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
const requestError = (error: any) => String(error?.response?.data?.message || error?.response?.data || error?.message || 'Unable to complete the request.');

export default function CrmRecordManager() {
  const [resourceKey, setResourceKey] = useState('contacts');
  const resource = resources.find(item => item.key === resourceKey)!;
  const [rows, setRows] = useState<RecordRow[]>([]);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [selected, setSelected] = useState<RecordRow | null>(null);
  const [form, setForm] = useState<Record<string, string>>({});

  const load = useCallback(async () => {
    setBusy(true); setFailure('');
    try {
      const params = resource.key === 'opportunities' ? { page: 0, size: 100 } : resource.key === 'tasks' ? { completed: false, page: 0, size: 100 } : { page: 0, size: 100 };
      const response = await api.get(resource.endpoint, { params });
      setRows(Array.isArray(response.data) ? response.data : response.data.items || []);
    } catch (error) { setFailure(requestError(error)); }
    finally { setBusy(false); }
  }, [resource]);

  useEffect(() => { void load(); }, [load]);
  const startCreate = () => { setSelected(null); setForm(defaultForm(resource)); setFailure(''); setDialogOpen(true); };
  const startEdit = (row: RecordRow) => { setSelected(row); setForm(formFromRow(resource, row)); setFailure(''); setDialogOpen(true); };
  const body = useMemo(() => formToBody(resource, form), [resource, form]);

  const save = async () => {
    setFailure('');
    try {
      if (selected) {
        if (resource.key === 'contacts') await api.patch(`${resource.endpoint}/${selected.id}`, body);
        else await api.put(`${resource.endpoint}/${selected.id}`, body);
        if (resource.key === 'opportunities' && body.stage !== selected.stage) {
          await api.patch(`${resource.endpoint}/${selected.id}/stage`, { stage: body.stage });
        }
      } else await api.post(resource.endpoint, body);
      setDialogOpen(false); await load();
    } catch (error) { setFailure(requestError(error)); }
  };

  const archive = async (row: RecordRow) => {
    if (!window.confirm(`Archive “${resource.title(row)}”? Its activity and audit history will remain.`)) return;
    setFailure('');
    try { await api.delete(`${resource.endpoint}/${row.id}`); await load(); }
    catch (error) { setFailure(requestError(error)); }
  };
  const completeTask = async (row: RecordRow) => {
    try { await api.patch(`/api/crm/tasks/${row.id}/complete`); await load(); }
    catch (error) { setFailure(requestError(error)); }
  };

  return <Box>
    <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ sm: 'center' }} spacing={1.5} sx={{ mb: 2 }}>
      <Box><Typography variant="h5" fontWeight={800}>CRM record management</Typography><Typography variant="body2" color="text.secondary">Edit active records or archive them while keeping their audit history.</Typography></Box>
      <Button variant="contained" startIcon={<AddRounded />} onClick={startCreate}>Add {singular(resource.key)}</Button>
    </Stack>
    <Paper variant="outlined" sx={{ mb: 2, bgcolor: 'background.paper' }}><Tabs value={resourceKey} onChange={(_, value) => setResourceKey(value)} variant="scrollable" scrollButtons="auto">{resources.map(item => <Tab key={item.key} value={item.key} label={item.label} />)}</Tabs></Paper>
    {failure && !dialogOpen && <Alert severity="error" sx={{ mb: 2 }}>{failure}</Alert>}
    {busy ? <Typography color="text.secondary" sx={{ py: 3 }}>Loading records…</Typography> : <TableContainer component={Paper} variant="outlined"><Table size="small"><TableHead><TableRow>
      <TableCell>Record</TableCell><TableCell>Details</TableCell><TableCell>State</TableCell><TableCell align="right">Actions</TableCell>
    </TableRow></TableHead><TableBody>
      {rows.map(row => <TableRow key={row.id} hover>
        <TableCell><Typography fontWeight={750}>{resource.title(row) || 'Untitled'}</Typography><Typography variant="caption" color="text.secondary">#{row.id}</Typography></TableCell>
        <TableCell>{renderDetails(resource.key, row)}</TableCell>
        <TableCell>{renderState(resource.key, row)}</TableCell>
        <TableCell align="right"><Stack direction="row" justifyContent="flex-end" spacing={0.5}>
          {resource.key === 'tasks' && !row.completed && <Button size="small" startIcon={<TaskAltRounded />} onClick={() => void completeTask(row)}>Complete</Button>}
          {!(resource.key === 'tasks' && row.completed) && <Button size="small" startIcon={<EditRounded />} onClick={() => startEdit(row)}>Edit</Button>}
          <Button size="small" color="warning" startIcon={<ArchiveRounded />} onClick={() => void archive(row)}>Archive</Button>
        </Stack></TableCell>
      </TableRow>)}
      {!rows.length && <TableRow><TableCell colSpan={4} align="center" sx={{ py: 5, color: 'text.secondary' }}>No active {resource.label.toLowerCase()}.</TableCell></TableRow>}
    </TableBody></Table></TableContainer>}

    <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} fullWidth maxWidth="sm">
      <DialogTitle>{selected ? `Edit ${singular(resource.key)}` : `Add ${singular(resource.key)}`}</DialogTitle>
      <DialogContent><Stack spacing={2} sx={{ pt: 1 }}>{resource.fields.map(field => field.type === 'select' ?
        <TextField key={field.key} select label={field.label} required={field.required} value={form[field.key] || ''} onChange={event => setForm({ ...form, [field.key]: event.target.value })}>
          {field.options?.map(option => <MenuItem key={option} value={option}>{option}</MenuItem>)}
        </TextField> : <TextField key={field.key} label={field.label} required={field.required} type={field.type === 'multiline' ? 'text' : field.type || 'text'} multiline={field.type === 'multiline'} minRows={field.type === 'multiline' ? 3 : undefined} value={form[field.key] || ''} onChange={event => setForm({ ...form, [field.key]: event.target.value })} />)}
        {resource.key === 'opportunities' && <Alert severity="info">Stage changes are written to the opportunity timeline.</Alert>}
        {resource.key === 'tasks' && <Alert severity="info">Completed tasks are retained as history and cannot be edited.</Alert>}
        {failure && <Alert severity="error">{failure}</Alert>}
      </Stack></DialogContent>
      <DialogActions><Button onClick={() => setDialogOpen(false)}>Cancel</Button><Button variant="contained" onClick={() => void save()} disabled={resource.fields.some(field => field.required && !String(form[field.key] || '').trim())}>{selected ? 'Save changes' : 'Create record'}</Button></DialogActions>
    </Dialog>
  </Box>;
}

function defaultForm(resource: Resource) {
  return Object.fromEntries(resource.fields.map(field => [field.key, field.key === 'currency' ? 'USD' : field.key === 'lifecycleStage' ? 'LEAD' : field.key === 'stage' ? 'Qualification' : field.key === 'priority' ? 'NORMAL' : field.key === 'probability' ? '10' : '']));
}
function singular(key: string) {
  return ({ contacts: 'contact', companies: 'company', opportunities: 'opportunity', tasks: 'task', segments: 'segment' } as Record<string, string>)[key] || 'record';
}
function formFromRow(resource: Resource, row: RecordRow) {
  const form: Record<string, string> = {};
  for (const field of resource.fields) {
    let value = row[field.key];
    if (resource.key === 'segments' && field.key === 'lifecycleStage') {
      try { value = JSON.parse(row.criteria || '{}').lifecycleStage || 'CUSTOMER'; } catch { value = 'CUSTOMER'; }
    }
    if (field.key === 'tags') {
      try { value = Array.isArray(row.tags) ? row.tags : JSON.parse(row.tags || '[]'); } catch { value = String(row.tags || '').split(','); }
      if (Array.isArray(value)) value = value.join(', ');
    }
    if (field.key === 'dueAt' && value) value = String(value).slice(0, 16);
    form[field.key] = value == null ? '' : String(value);
  }
  return form;
}
function formToBody(resource: Resource, form: Record<string, string>) {
  const body: Record<string, any> = {};
  for (const field of resource.fields) {
    const raw = form[field.key]?.trim() || '';
    if (['amount', 'probability', 'contactId', 'companyId', 'opportunityId'].includes(field.key)) body[field.key] = raw ? Number(raw) : null;
    else if (field.key === 'tags') body.tags = raw.split(',').map(tag => tag.trim()).filter(Boolean);
    else if (field.type === 'datetime-local') body[field.key] = raw || null;
    else body[field.key] = raw || null;
  }
  if (resource.key === 'segments') body.criteria = { lifecycleStage: body.lifecycleStage || 'CUSTOMER' };
  delete body.lifecycleStage;
  return body;
}
function renderDetails(key: string, row: RecordRow) {
  if (key === 'contacts') return `${row.email || 'No email'} · ${row.phone || 'No phone'} · ${row.owner || 'Unassigned'}`;
  if (key === 'companies') return `${row.domain || 'No domain'} · ${row.industry || 'No industry'} · ${row.contactCount || 0} contacts`;
  if (key === 'opportunities') return `${row.stage} · ${Number(row.probability || 0)}% · ${new Intl.NumberFormat(undefined, { style: 'currency', currency: row.currency || 'USD' }).format(Number(row.amount || 0))}`;
  if (key === 'tasks') return `${row.priority} · ${row.owner || 'Unassigned'} · ${row.dueAt ? new Date(row.dueAt).toLocaleString() : 'No due date'}`;
  return `${JSON.stringify(row.criteria || {})} · ${row.memberCount || 0} members`;
}
function renderState(key: string, row: RecordRow) {
  if (key === 'contacts') return <Chip size="small" label={row.lifecycleStage || 'LEAD'} />;
  if (key === 'opportunities') return <Chip size="small" color={row.status === 'WON' ? 'success' : row.status === 'LOST' ? 'error' : 'primary'} label={row.status || 'OPEN'} />;
  if (key === 'tasks') return <Chip size="small" color={row.completed ? 'success' : 'warning'} label={row.completed ? 'Completed' : 'Open'} />;
  if (key === 'segments') return <Chip size="small" color="success" label="Active" />;
  return <Chip size="small" color="success" label="Active" />;
}
