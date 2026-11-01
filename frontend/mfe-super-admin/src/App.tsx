import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert, AppBar, Box, Button, Card, CardContent, Chip, CircularProgress, Container, Dialog,
  DialogActions, DialogContent, DialogTitle, Divider, IconButton, Paper, Stack, Switch,
  Table, TableBody, TableCell, TableHead, TableRow, TextField, Toolbar, Typography,
} from '@mui/material';
import { AddRounded, ApartmentRounded, LogoutRounded, RefreshRounded, SecurityRounded } from '@mui/icons-material';
import {
  clearPlatformSession, readPlatformSession, signInToPlatform, storePlatformSession,
} from '@ecommerce/shared-library/src/auth/applicationSession';

type Tenant = { id: string; slug: string; displayName: string; status: string };
type Application = { applicationCode: string; displayName: string; status: string; enabled: boolean };
type Member = { userId: number; email: string; name: string; membershipStatus: string; grantActive: boolean; roleKey?: string };

async function request<T>(path: string, token: string, method = 'GET', body?: unknown): Promise<T> {
  const response = await fetch(path, {
    method,
    headers: { Authorization: `Bearer ${token}`, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const contentType = response.headers.get('content-type') || '';
  const payload = contentType.includes('application/json') ? await response.json() : null;
  if (!response.ok) throw new Error(payload?.message || payload?.error || `Request failed (${response.status})`);
  return payload as T;
}

export default function App() {
  const [token, setToken] = useState(() => readPlatformSession(sessionStorage)?.token || '');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loginError, setLoginError] = useState('');
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [selectedTenantId, setSelectedTenantId] = useState('');
  const [applications, setApplications] = useState<Application[]>([]);
  const [members, setMembers] = useState<Member[]>([]);
  const [selectedApplicationCode, setSelectedApplicationCode] = useState('DELIVERY_OPS');
  const [selectedRoleKey, setSelectedRoleKey] = useState('DELIVERY_PARTNER');
  const [createOpen, setCreateOpen] = useState(false);
  const [tenantName, setTenantName] = useState('');
  const [tenantSlug, setTenantSlug] = useState('');
  const [memberEmail, setMemberEmail] = useState('');

  const selectedTenant = useMemo(() => tenants.find((tenant) => tenant.id === selectedTenantId), [tenants, selectedTenantId]);

  const loadTenants = async (accessToken = token) => {
    const values = await request<Tenant[]>('/api/platform/tenants', accessToken);
    setTenants(values);
    if (!selectedTenantId && values.length) setSelectedTenantId(values[0].id);
    if (selectedTenantId && !values.some((tenant) => tenant.id === selectedTenantId)) setSelectedTenantId(values[0]?.id || '');
  };

  const loadTenantDetails = async (tenantId: string, accessToken = token) => {
    if (!tenantId) { setApplications([]); setMembers([]); return; }
    const [apps, users] = await Promise.all([
      request<Application[]>(`/api/platform/tenants/${tenantId}/applications`, accessToken),
      request<Member[]>(`/api/platform/tenants/${tenantId}/memberships?applicationCode=${selectedApplicationCode}`, accessToken),
    ]);
    setApplications(apps);
    setMembers(users);
  };

  useEffect(() => {
    if (!token) return;
    setBusy(true);
    loadTenants().catch((cause) => setError(cause instanceof Error ? cause.message : 'Could not load clients.'))
      .finally(() => setBusy(false));
  }, [token]);

  useEffect(() => {
    if (!token || !selectedTenantId) return;
    loadTenantDetails(selectedTenantId).catch((cause) => setError(cause instanceof Error ? cause.message : 'Could not load client access.'));
  }, [token, selectedTenantId, selectedApplicationCode]);

  const signIn = async (event: React.FormEvent) => {
    event.preventDefault(); setBusy(true); setLoginError('');
    try {
      const session = await signInToPlatform({ email, password }, 'SUPER_ADMIN');
      storePlatformSession(session, sessionStorage);
      setToken(session.token);
      setPassword('');
    } catch (cause) { setLoginError(cause instanceof Error ? cause.message : 'Sign in failed.'); }
    finally { setBusy(false); }
  };

  const signOut = () => { clearPlatformSession(sessionStorage); setToken(''); setTenants([]); setApplications([]); setMembers([]); };

  const withFeedback = async (action: () => Promise<void>, successMessage: string) => {
    setError(''); setNotice(''); setBusy(true);
    try { await action(); setNotice(successMessage); }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'The operation failed.'); }
    finally { setBusy(false); }
  };

  const createTenant = async () => withFeedback(async () => {
    const created = await request<Tenant>('/api/platform/tenants', token, 'POST', { displayName: tenantName, slug: tenantSlug });
    setTenantName(''); setTenantSlug(''); setCreateOpen(false);
    const values = await request<Tenant[]>('/api/platform/tenants', token);
    setTenants(values); setSelectedTenantId(created.id);
  }, 'Client created. Enable an application and add its members below.');

  const toggleApplication = async (enabled: boolean) => {
    if (!selectedTenant) return;
    await withFeedback(async () => {
      await request<void>(`/api/platform/tenants/${selectedTenant.id}/applications/${selectedApplicationCode}`, token, 'PUT', { enabled });
      await loadTenantDetails(selectedTenant.id);
    }, `${selectedApplicationCode} ${enabled ? 'enabled' : 'disabled'} for ${selectedTenant.displayName}.`);
  };

  const addMember = async () => {
    if (!selectedTenant) return;
    await withFeedback(async () => {
      await request<void>(`/api/platform/tenants/${selectedTenant.id}/memberships`, token, 'POST', { email: memberEmail });
      setMemberEmail(''); await loadTenantDetails(selectedTenant.id);
    }, 'Client membership added.');
  };

  const changeGrant = async (member: Member, grant: boolean) => {
    if (!selectedTenant) return;
    await withFeedback(async () => {
      const path = `/api/platform/tenants/${selectedTenant.id}/memberships/${encodeURIComponent(member.email)}/applications/${selectedApplicationCode}`;
      await request<void>(path, token, grant ? 'PUT' : 'DELETE', grant ? { roleKey: selectedRoleKey } : undefined);
      await loadTenantDetails(selectedTenant.id);
    }, `${grant ? `${selectedApplicationCode} access granted to` : `${selectedApplicationCode} access revoked for`} ${member.email}.`);
  };

  if (!token) return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 2 }}>
      <Card sx={{ width: 'min(100%, 430px)', border: '1px solid #E4EAF2', boxShadow: '0 24px 70px rgba(16,24,40,.10)' }}>
        <CardContent sx={{ p: 4 }}>
          <Stack component="form" spacing={2.5} onSubmit={signIn}>
            <Box><Typography variant="overline" color="primary" fontWeight={800}>Universal Commerce Platform</Typography><Typography variant="h4" fontWeight={800}>Super Admin</Typography><Typography color="text.secondary" sx={{ mt: 1 }}>Sign in to configure client access and applications.</Typography></Box>
            {loginError && <Alert severity="error">{loginError}</Alert>}
            <TextField label="Email" type="email" autoComplete="username" required value={email} onChange={(event) => setEmail(event.target.value)} />
            <TextField label="Password" type="password" autoComplete="current-password" required value={password} onChange={(event) => setPassword(event.target.value)} />
            <Button type="submit" variant="contained" size="large" disabled={busy} sx={{ minHeight: 48, textTransform: 'none', fontWeight: 800 }}>{busy ? <CircularProgress size={22} color="inherit" /> : 'Sign in securely'}</Button>
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );

  const selectedApplication = applications.find((application) => application.applicationCode === selectedApplicationCode);
  const roles = selectedApplicationCode === 'DELIVERY_OPS'
    ? ['DELIVERY_ADMIN', 'DISPATCHER', 'DELIVERY_PARTNER', 'DELIVERY_ANALYST']
    : ['WAREHOUSE_OPERATOR'];

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: '#F4F7FB' }}>
      <AppBar position="sticky" color="inherit" elevation={0} sx={{ borderBottom: '1px solid #E4EAF2' }}>
        <Toolbar><ApartmentRounded color="primary" sx={{ mr: 1.5 }} /><Box sx={{ flexGrow: 1 }}><Typography fontWeight={800}>Platform control plane</Typography><Typography variant="caption" color="text.secondary">Tenant and application access</Typography></Box><IconButton aria-label="Refresh clients" onClick={() => void loadTenants()}><RefreshRounded /></IconButton><IconButton aria-label="Sign out" onClick={signOut}><LogoutRounded /></IconButton></Toolbar>
      </AppBar>
      <Container maxWidth="lg" sx={{ py: { xs: 3, md: 5 } }}>
        <Stack spacing={3}>
          <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ sm: 'center' }} gap={2}>
            <Box><Typography variant="overline" color="primary" fontWeight={800}>Super Admin</Typography><Typography variant="h3" fontWeight={800} sx={{ letterSpacing: '-.04em', color: '#15243B' }}>Client access</Typography><Typography color="text.secondary" sx={{ mt: .75 }}>Create client workspaces, control app entitlements, and grant access per employee.</Typography></Box>
            <Button startIcon={<AddRounded />} variant="contained" onClick={() => setCreateOpen(true)} sx={{ textTransform: 'none', fontWeight: 800 }}>Create client</Button>
          </Stack>
          {notice && <Alert severity="success" onClose={() => setNotice('')}>{notice}</Alert>}
          {error && <Alert severity="error" onClose={() => setError('')}>{error}</Alert>}
          <Card sx={{ border: '1px solid #E4EAF2' }}><CardContent>
              <Stack direction={{ xs: 'column', md: 'row' }} spacing={3} alignItems={{ md: 'center' }}>
              <TextField select SelectProps={{ native: true }} label="Client workspace" value={selectedTenantId} onChange={(event) => setSelectedTenantId(event.target.value)} sx={{ minWidth: { md: 300 } }}>
                {tenants.map((tenant) => <option key={tenant.id} value={tenant.id}>{tenant.displayName} · {tenant.slug}</option>)}
              </TextField>
              {selectedTenant && <Stack direction="row" spacing={1} alignItems="center"><Chip size="small" label={selectedTenant.status} color={selectedTenant.status === 'ACTIVE' ? 'success' : 'default'} /><Typography variant="body2" color="text.secondary">{selectedTenant.id}</Typography></Stack>}
              <TextField select SelectProps={{ native: true }} label="Application" value={selectedApplicationCode} onChange={(event) => { setSelectedApplicationCode(event.target.value); setSelectedRoleKey(event.target.value === 'DELIVERY_OPS' ? 'DELIVERY_PARTNER' : 'WAREHOUSE_OPERATOR'); }} sx={{ minWidth: { md: 240 } }}>
                {applications.filter((application) => ['INVENTORY', 'DELIVERY_OPS'].includes(application.applicationCode)).map((application) => <option key={application.applicationCode} value={application.applicationCode}>{application.displayName || application.applicationCode}</option>)}
                {!applications.some((application) => ['INVENTORY', 'DELIVERY_OPS'].includes(application.applicationCode)) && <option value={selectedApplicationCode}>{selectedApplicationCode}</option>}
              </TextField>
            </Stack>
          </CardContent></Card>
          <Card sx={{ border: '1px solid #E4EAF2' }}><CardContent>
            <Stack direction={{ xs: 'column', sm: 'row' }} alignItems={{ sm: 'center' }} justifyContent="space-between" gap={2}>
              <Stack direction="row" spacing={1.5} alignItems="center"><SecurityRounded color="primary" /><Box><Typography fontWeight={800}>{selectedApplication?.displayName || selectedApplicationCode} entitlement</Typography><Typography variant="body2" color="text.secondary">Enable the application for this client before granting individual roles.</Typography></Box></Stack>
              {selectedApplication && <Stack direction="row" spacing={1} alignItems="center"><Chip label={selectedApplication.status} size="small" /><Typography variant="body2">{selectedApplication.enabled ? 'Enabled' : 'Disabled'}</Typography><Switch checked={selectedApplication.enabled} disabled={busy || selectedApplication.status !== 'ACTIVE' || !selectedTenant} onChange={(event) => void toggleApplication(event.target.checked)} inputProps={{ 'aria-label': `Enable ${selectedApplicationCode} for selected client` }} /></Stack>}
            </Stack>
          </CardContent></Card>
          <Card sx={{ border: '1px solid #E4EAF2' }}><CardContent>
            <Stack spacing={2}>
              <Box><Typography variant="h6" fontWeight={800}>Client members</Typography><Typography variant="body2" color="text.secondary">Add an existing identity, then grant or revoke {selectedApplicationCode} access separately.</Typography></Box>
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}><TextField label="Employee email" type="email" value={memberEmail} onChange={(event) => setMemberEmail(event.target.value)} disabled={!selectedTenant || busy} fullWidth /><Button onClick={() => void addMember()} disabled={!selectedTenant || !memberEmail || busy} variant="outlined" sx={{ minWidth: 150, textTransform: 'none' }}>Add member</Button></Stack>
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} alignItems={{ sm: 'center' }}><Typography variant="body2" color="text.secondary">Role for next grant</Typography><TextField select SelectProps={{ native: true }} size="small" value={selectedRoleKey} onChange={(event) => setSelectedRoleKey(event.target.value)} sx={{ minWidth: 230 }}>{roles.map((role) => <option key={role} value={role}>{role.replaceAll('_', ' ')}</option>)}</TextField></Stack>
              <Divider />
              <Table size="small"><TableHead><TableRow><TableCell>Employee</TableCell><TableCell>Membership</TableCell><TableCell>{selectedApplicationCode} role</TableCell><TableCell align="right">Access</TableCell></TableRow></TableHead><TableBody>
                {members.map((member) => <TableRow key={member.userId}><TableCell><Typography fontWeight={700}>{member.name || member.email}</Typography><Typography variant="caption" color="text.secondary">{member.email}</Typography></TableCell><TableCell>{member.membershipStatus}</TableCell><TableCell>{member.grantActive ? member.roleKey : '—'}</TableCell><TableCell align="right"><Button size="small" onClick={() => void changeGrant(member, !member.grantActive)} disabled={busy || !selectedApplication?.enabled || member.membershipStatus !== 'ACTIVE'} color={member.grantActive ? 'error' : 'primary'} sx={{ textTransform: 'none' }}>{member.grantActive ? 'Revoke' : `Grant ${selectedApplicationCode}`}</Button></TableCell></TableRow>)}
                {!members.length && <TableRow><TableCell colSpan={4}><Typography color="text.secondary" align="center" sx={{ py: 3 }}>No client members yet.</Typography></TableCell></TableRow>}
              </TableBody></Table>
            </Stack>
          </CardContent></Card>
          <Paper variant="outlined" sx={{ p: 2, bgcolor: 'transparent' }}><Typography variant="body2" color="text.secondary">Employees need an account before they can be added here. App access is tenant-scoped; enabling Inventory for a client does not grant it to every member.</Typography></Paper>
        </Stack>
      </Container>
      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Create client workspace</DialogTitle>
        <DialogContent><Stack spacing={2} sx={{ pt: 1 }}><TextField autoFocus label="Client name" value={tenantName} onChange={(event) => setTenantName(event.target.value)} required /><TextField label="Workspace slug" value={tenantSlug} onChange={(event) => setTenantSlug(event.target.value.toLowerCase().replace(/[^a-z0-9-]/g, '-'))} helperText="Lowercase letters, numbers and hyphens. Used at application sign-in." required /></Stack></DialogContent>
        <DialogActions sx={{ p: 2.5 }}><Button onClick={() => setCreateOpen(false)} sx={{ textTransform: 'none' }}>Cancel</Button><Button variant="contained" disabled={!tenantName.trim() || !tenantSlug.trim() || busy} onClick={() => void createTenant()} sx={{ textTransform: 'none' }}>Create client</Button></DialogActions>
      </Dialog>
    </Box>
  );
}
