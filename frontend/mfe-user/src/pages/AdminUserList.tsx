import React, { useEffect, useState } from 'react';
import {
  Typography,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  MenuItem,
  Select,
  CircularProgress,
  Switch,
  Box,
  TextField,
  InputAdornment,
  Chip,
  Stack,
  Grid,
  Card,
  Avatar,
  Snackbar,
  Alert,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  FormGroup,
} from '@mui/material';
import {
  SearchRounded,
  AdminPanelSettingsRounded,
  SupervisorAccountRounded,
  PersonOutlineRounded,
  PeopleAltRounded,
} from '@mui/icons-material';
import axios from 'axios';

axios.interceptors.request.use((config) => {
  const token = typeof window !== 'undefined' ? localStorage.getItem('jwt_token') : null;
  if (token && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

interface User {
  id: number;
  name: string;
  email: string;
  role: string;
  active: boolean;
}

interface AccessRole {
  key: string;
  name: string;
  hierarchyLevel: number;
  systemRole: boolean;
  permissions: string[];
}

interface AccessPermission { key: string; module: string; description: string; }

function currentRole() {
  try {
    const payload = localStorage.getItem('jwt_token')?.split('.')[1];
    if (!payload) return 'USER';
    const claims = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')));
    return String(claims.role || 'USER').replace(/^ROLE_/, '').toUpperCase();
  } catch { return 'USER'; }
}

export default function AdminUserList() {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedRole, setSelectedRole] = useState('ALL');
  const [toastMessage, setToastMessage] = useState('');
  const [toastError, setToastError] = useState(false);
  const [roles, setRoles] = useState<AccessRole[]>([]);
  const [permissions, setPermissions] = useState<AccessPermission[]>([]);
  const [roleDialogOpen, setRoleDialogOpen] = useState(false);
  const [editingRole, setEditingRole] = useState<AccessRole | null>(null);
  const [roleForm, setRoleForm] = useState({ key: '', name: '', hierarchyLevel: 100, permissions: [] as string[] });
  const [roleError, setRoleError] = useState('');
  const actorRole = currentRole();

  const fetchUsers = async () => {
    try {
      const res = await axios.get('http://localhost:8080/api/users');
      setUsers(res.data);
    } catch (err) {
      console.error('Failed to fetch users', err);
    } finally {
      setLoading(false);
    }
  };

  const fetchAccessConfiguration = async () => {
    try {
      const [roleResponse, permissionResponse] = await Promise.all([
        axios.get('http://localhost:8080/api/access/roles'),
        axios.get('http://localhost:8080/api/access/permissions'),
      ]);
      setRoles(roleResponse.data);
      setPermissions(permissionResponse.data);
    } catch (err) {
      console.error('Failed to fetch access configuration', err);
    }
  };

  useEffect(() => {
    fetchUsers();
    fetchAccessConfiguration();
  }, []);

  const handleRoleChange = async (userId: number, newRole: string) => {
    try {
      await axios.put(`http://localhost:8080/api/users/${userId}/role`, { role: newRole });
      setToastMessage(`Updated User #${userId} role to ${newRole}. They must sign in again for the new access token.`);
      setToastError(false);
      fetchUsers();
      fetchAccessConfiguration();
    } catch (err) {
      console.error('Failed to update role', err);
      setToastMessage('Error updating user role.');
      setToastError(true);
    }
  };

  const handleStatusChange = async (userId: number, newActive: boolean) => {
    try {
      await axios.put(`http://localhost:8080/api/users/${userId}/status`, { active: newActive });
      setToastMessage(`User #${userId} status set to ${newActive ? 'Active' : 'Deactivated'}`);
      setToastError(false);
      fetchUsers();
    } catch (err) {
      console.error('Failed to update status', err);
      setToastMessage('Error updating user status.');
      setToastError(true);
    }
  };

  const openRoleEditor = (role?: AccessRole) => {
    setRoleError('');
    setEditingRole(role || null);
    setRoleForm(role
      ? { key: role.key, name: role.name, hierarchyLevel: role.hierarchyLevel, permissions: [...role.permissions] }
      : { key: '', name: '', hierarchyLevel: Math.max(0, (roles.find(r => r.key === actorRole)?.hierarchyLevel || 101) - 1), permissions: [] });
    setRoleDialogOpen(true);
  };

  const saveRole = async () => {
    setRoleError('');
    try {
      if (editingRole) {
        await axios.put(`http://localhost:8080/api/access/roles/${editingRole.key}/permissions`, { permissions: roleForm.permissions });
      } else {
        await axios.post('http://localhost:8080/api/access/roles', roleForm);
      }
      setRoleDialogOpen(false);
      setToastMessage(editingRole ? `Updated ${editingRole.name} access. Assigned users must sign in again.` : `Created role ${roleForm.name}.`);
      setToastError(false);
      fetchAccessConfiguration();
    } catch (err: any) {
      setRoleError(err?.response?.data?.message || 'Unable to save this role. Check your hierarchy and permissions.');
    }
  };

  const deleteRole = async (role: AccessRole) => {
    if (!window.confirm(`Delete ${role.name}? Users assigned to this role must be reassigned first.`)) return;
    try {
      await axios.delete(`http://localhost:8080/api/access/roles/${role.key}`);
      setToastMessage(`Deleted ${role.name}.`); setToastError(false); fetchAccessConfiguration();
    } catch (err: any) {
      setToastMessage(err?.response?.data?.message || 'Unable to delete this role.'); setToastError(true);
    }
  };

  const filteredUsers = users.filter((u) => {
    const matchesSearch =
      (u.name && u.name.toLowerCase().includes(searchTerm.toLowerCase())) ||
      (u.email && u.email.toLowerCase().includes(searchTerm.toLowerCase()));
    const matchesRole = selectedRole === 'ALL' || u.role === selectedRole;
    return matchesSearch && matchesRole;
  });

  const adminCount = users.filter((u) => u.role === 'ADMIN').length;
  const managerCount = users.filter((u) => u.role === 'MANAGER').length;
  const userCount = users.filter((u) => u.role === 'USER').length;

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 8 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box sx={{ pb: 6 }}>
      {/* 1. Header Title */}
      <Box sx={{ mb: 4 }}>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.8 }}>
          <Chip
            label="UNIVERSAL COMMERCE 2.0"
            size="small"
            sx={{
              bgcolor: 'rgba(79, 70, 229, 0.1)',
              color: '#4F46E5',
              fontWeight: 800,
              fontSize: '0.65rem',
            }}
          />
          <Typography variant="caption" sx={{ color: '#10B981', fontWeight: 700 }}>
            &bull; RBAC Governance Console
          </Typography>
        </Stack>
        <Typography variant="h4" sx={{ fontWeight: 900, color: '#0F172A', letterSpacing: '-0.025em', mb: 0.5 }}>
          Universal Identity &amp; Access Governance
        </Typography>
        <Typography variant="body1" sx={{ color: '#64748B' }}>
          Administer platform access, assign RBAC permissions, and manage active user identities across microservices.
        </Typography>
      </Box>

      {/* 2. Metric Highlight Cards */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ p: 2.5, border: '1px solid #E2E8F0' }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <Box sx={{ width: 44, height: 44, borderRadius: 2, bgcolor: '#EEF2FF', color: '#4F46E5', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <PeopleAltRounded />
              </Box>
              <Box>
                <Typography variant="h5" sx={{ fontWeight: 800 }}>{users.length}</Typography>
                <Typography variant="caption" sx={{ color: '#64748B', fontWeight: 600 }}>Total Accounts</Typography>
              </Box>
            </Box>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ p: 2.5, border: '1px solid #E2E8F0' }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <Box sx={{ width: 44, height: 44, borderRadius: 2, bgcolor: '#F3E8FF', color: '#7C3AED', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <AdminPanelSettingsRounded />
              </Box>
              <Box>
                <Typography variant="h5" sx={{ fontWeight: 800 }}>{adminCount}</Typography>
                <Typography variant="caption" sx={{ color: '#64748B', fontWeight: 600 }}>Administrators</Typography>
              </Box>
            </Box>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ p: 2.5, border: '1px solid #E2E8F0' }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <Box sx={{ width: 44, height: 44, borderRadius: 2, bgcolor: '#FEF3C7', color: '#D97706', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <SupervisorAccountRounded />
              </Box>
              <Box>
                <Typography variant="h5" sx={{ fontWeight: 800 }}>{managerCount}</Typography>
                <Typography variant="caption" sx={{ color: '#64748B', fontWeight: 600 }}>Operations Managers</Typography>
              </Box>
            </Box>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ p: 2.5, border: '1px solid #E2E8F0' }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <Box sx={{ width: 44, height: 44, borderRadius: 2, bgcolor: '#DCFCE7', color: '#16A34A', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <PersonOutlineRounded />
              </Box>
              <Box>
                <Typography variant="h5" sx={{ fontWeight: 800 }}>{userCount}</Typography>
                <Typography variant="caption" sx={{ color: '#64748B', fontWeight: 600 }}>Shoppers & Users</Typography>
              </Box>
            </Box>
          </Card>
        </Grid>
      </Grid>

      {/* 3. Search & Filter Bar */}
      <Paper elevation={0} sx={{ p: 2.5, mb: 3, border: '1px solid #E2E8F0', borderRadius: 3, display: 'flex', flexWrap: 'wrap', gap: 2, justifyContent: 'space-between', alignItems: 'center' }}>
        <TextField
          placeholder="Search by name or email..."
          size="small"
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          sx={{ width: { xs: '100%', sm: 340 } }}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchRounded sx={{ color: '#94A3B8' }} />
              </InputAdornment>
            ),
          }}
        />

        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          {['ALL', ...roles.map((role) => role.key)].map((role) => (
            <Chip
              key={role}
              label={role}
              clickable
              onClick={() => setSelectedRole(role)}
              color={selectedRole === role ? 'primary' : 'default'}
              variant={selectedRole === role ? 'filled' : 'outlined'}
              sx={{ fontWeight: 700 }}
            />
          ))}
        </Stack>
      </Paper>

      {/* 4. Modern User Table */}
      <TableContainer component={Paper} elevation={0} sx={{ borderRadius: 3, border: '1px solid #E2E8F0', overflow: 'hidden' }}>
        <Table>
          <TableHead sx={{ bgcolor: '#F8FAFC' }}>
            <TableRow>
              <TableCell sx={{ fontWeight: 800, color: '#475569' }}>User</TableCell>
              <TableCell sx={{ fontWeight: 800, color: '#475569' }}>Account ID</TableCell>
              <TableCell sx={{ fontWeight: 800, color: '#475569' }}>Assigned Role</TableCell>
              <TableCell sx={{ fontWeight: 800, color: '#475569' }}>Status</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {filteredUsers.map((user) => (
              <TableRow key={user.id} hover sx={{ '&:hover': { bgcolor: '#F8FAFC' } }}>
                <TableCell>
                  <Stack direction="row" spacing={2} alignItems="center">
                    <Avatar sx={{ bgcolor: '#4F46E5', width: 38, height: 38, fontWeight: 700 }}>
                      {user.name ? user.name.charAt(0).toUpperCase() : user.email?.charAt(0).toUpperCase() || 'U'}
                    </Avatar>
                    <Box>
                      <Typography variant="subtitle2" sx={{ fontWeight: 800, color: '#0F172A' }}>
                        {user.name || 'Unnamed User'}
                      </Typography>
                      <Typography variant="caption" sx={{ color: '#64748B' }}>
                        {user.email}
                      </Typography>
                    </Box>
                  </Stack>
                </TableCell>

                <TableCell>
                  <Chip
                    label={`#USR-${user.id}`}
                    size="small"
                    sx={{ bgcolor: '#F1F5F9', fontWeight: 700, color: '#475569' }}
                  />
                </TableCell>

                <TableCell>
                  <Select
                    value={user.role}
                    disabled={(roles.find((role) => role.key === user.role)?.hierarchyLevel || 0) >= (roles.find((role) => role.key === actorRole)?.hierarchyLevel || 0) && actorRole !== 'SUPER_ADMIN'}
                    onChange={(e) => handleRoleChange(user.id, e.target.value)}
                    size="small"
                    sx={{
                      fontWeight: 700,
                      borderRadius: 2,
                      fontSize: '0.875rem',
                      '& .MuiSelect-select': { py: 0.8 },
                    }}
                  >
                    {roles.filter((role) => actorRole === 'SUPER_ADMIN' || role.hierarchyLevel < (roles.find((item) => item.key === actorRole)?.hierarchyLevel || 0) || role.key === user.role).map((role) => (
                      <MenuItem key={role.key} value={role.key} sx={{ fontWeight: 700 }}>{role.name} · {role.key}</MenuItem>
                    ))}
                  </Select>
                </TableCell>

                <TableCell>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Switch
                      checked={user.active}
                      disabled={(roles.find((role) => role.key === user.role)?.hierarchyLevel || 0) >= (roles.find((role) => role.key === actorRole)?.hierarchyLevel || 0) && actorRole !== 'SUPER_ADMIN'}
                      onChange={(e) => handleStatusChange(user.id, e.target.checked)}
                      color="success"
                      size="small"
                    />
                    <Chip
                      label={user.active ? 'Active' : 'Deactivated'}
                      size="small"
                      sx={{
                        bgcolor: user.active ? '#DCFCE7' : '#F1F5F9',
                        color: user.active ? '#15803D' : '#64748B',
                        fontWeight: 800,
                        fontSize: '0.75rem',
                      }}
                    />
                  </Stack>
                </TableCell>
              </TableRow>
            ))}

            {filteredUsers.length === 0 && (
              <TableRow>
                <TableCell colSpan={4} align="center" sx={{ py: 6 }}>
                  <Typography variant="body1" sx={{ color: '#64748B', fontWeight: 600 }}>
                    No users matching criteria.
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      <Box sx={{ mt: 4, mb: 2 }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ xs: 'flex-start', sm: 'center' }} spacing={2}>
          <Box><Typography variant="h5" sx={{ fontWeight: 850, color: '#0F172A' }}>Role &amp; permission configuration</Typography>
            <Typography variant="body2" sx={{ color: '#64748B' }}>Delegate access by hierarchy. A role can only grant permissions its administrator already holds.</Typography></Box>
          <Button variant="contained" onClick={() => openRoleEditor()}>Create role</Button>
        </Stack>
      </Box>
      <TableContainer component={Paper} elevation={0} sx={{ borderRadius: 3, border: '1px solid #E2E8F0', overflow: 'hidden' }}>
        <Table size="small"><TableHead sx={{ bgcolor: '#F8FAFC' }}><TableRow>
          <TableCell sx={{ fontWeight: 800 }}>Role</TableCell><TableCell sx={{ fontWeight: 800 }}>Hierarchy</TableCell>
          <TableCell sx={{ fontWeight: 800 }}>Permissions</TableCell><TableCell sx={{ fontWeight: 800 }}>Type</TableCell><TableCell sx={{ fontWeight: 800 }}>Actions</TableCell>
        </TableRow></TableHead><TableBody>{roles.map((role) => (
          <TableRow key={role.key} hover>
            <TableCell><Typography fontWeight={750}>{role.name}</Typography><Typography variant="caption" color="text.secondary">{role.key}</Typography></TableCell>
            <TableCell><Chip size="small" label={`Level ${role.hierarchyLevel}`} /></TableCell>
            <TableCell><Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">{role.permissions.slice(0, 5).map((permission) => <Chip key={permission} size="small" variant="outlined" label={permission} />)}{role.permissions.length > 5 && <Chip size="small" label={`+${role.permissions.length - 5}`} />}</Stack></TableCell>
            <TableCell><Chip size="small" label={role.systemRole ? 'Built-in' : 'Custom'} color={role.systemRole ? 'default' : 'primary'} /></TableCell>
            <TableCell><Stack direction="row" spacing={1}><Button size="small" disabled={role.key === 'SUPER_ADMIN' || (role.hierarchyLevel >= (roles.find((item) => item.key === actorRole)?.hierarchyLevel || 0) && actorRole !== 'SUPER_ADMIN')} onClick={() => openRoleEditor(role)}>Permissions</Button>{!role.systemRole && <Button size="small" color="error" disabled={role.hierarchyLevel >= (roles.find((item) => item.key === actorRole)?.hierarchyLevel || 0) && actorRole !== 'SUPER_ADMIN'} onClick={() => deleteRole(role)}>Delete</Button>}</Stack></TableCell>
          </TableRow>
        ))}{!roles.length && <TableRow><TableCell colSpan={5} align="center" sx={{ py: 4 }}>No access roles are available.</TableCell></TableRow>}</TableBody></Table>
      </TableContainer>

      <Dialog open={roleDialogOpen} onClose={() => setRoleDialogOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>{editingRole ? `Configure ${editingRole.name}` : 'Create delegated role'}</DialogTitle>
        <DialogContent><Stack spacing={2} sx={{ pt: 1 }}>
          {!editingRole && <><TextField label="Role key" value={roleForm.key} onChange={(event) => setRoleForm({ ...roleForm, key: event.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, '_') })} helperText="Use uppercase letters, numbers, and underscores." />
            <TextField label="Display name" value={roleForm.name} onChange={(event) => setRoleForm({ ...roleForm, name: event.target.value })} />
            <TextField type="number" label="Hierarchy level" value={roleForm.hierarchyLevel} inputProps={{ min: 0, max: Math.max(0, (roles.find(r => r.key === actorRole)?.hierarchyLevel || 1) - 1) }} onChange={(event) => setRoleForm({ ...roleForm, hierarchyLevel: Number(event.target.value) })} helperText="Lower levels have less authority. Your role can only create roles beneath its own level." />
            <Divider />
          </>}
          <Typography variant="subtitle2" fontWeight={800}>Grantable permissions</Typography>
          {[...new Set(permissions.map((permission) => permission.module))].map((module) => (
            <Box key={module}><Typography variant="overline" color="text.secondary">{module}</Typography><FormGroup row>
              {permissions.filter((permission) => permission.module === module).map((permission) => {
                const actorPermissions = roles.find((role) => role.key === actorRole)?.permissions || [];
                const canGrant = actorRole === 'SUPER_ADMIN' || actorPermissions.includes('*') || actorPermissions.includes(permission.key);
                return <FormControlLabel key={permission.key} sx={{ mr: 2, minWidth: { xs: '100%', sm: '45%' } }} control={<Checkbox size="small" checked={roleForm.permissions.includes(permission.key)} disabled={!canGrant} onChange={(event) => setRoleForm({ ...roleForm, permissions: event.target.checked ? [...roleForm.permissions, permission.key] : roleForm.permissions.filter((key) => key !== permission.key) })} />} label={<Box><Typography variant="body2" fontWeight={650}>{permission.key}</Typography><Typography variant="caption" color="text.secondary">{permission.description}</Typography></Box>} />;
              })}
            </FormGroup></Box>
          ))}
          {roleError && <Alert severity="error">{roleError}</Alert>}
        </Stack></DialogContent>
        <DialogActions><Button onClick={() => setRoleDialogOpen(false)}>Cancel</Button><Button variant="contained" onClick={saveRole}>{editingRole ? 'Save permissions' : 'Create role'}</Button></DialogActions>
      </Dialog>

      <Snackbar
        open={!!toastMessage}
        autoHideDuration={4000}
        onClose={() => setToastMessage('')}
      >
        <Alert onClose={() => setToastMessage('')} severity={toastError ? 'error' : 'success'} sx={{ width: '100%', borderRadius: 2 }}>
          {toastMessage}
        </Alert>
      </Snackbar>
    </Box>
  );
}
