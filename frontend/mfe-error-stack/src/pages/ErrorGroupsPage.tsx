import React, { useEffect, useState, useCallback } from 'react';
import {
  Box,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  FormControl,
  Grid,
  IconButton,
  InputLabel,
  LinearProgress,
  MenuItem,
  Pagination,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
  Alert,
  Menu,
  Button,
} from '@mui/material';
import {
  SearchRounded,
  FilterListRounded,
  RefreshRounded,
  OpenInNewRounded,
  MoreVertRounded,
  AssignmentIndRounded,
  CheckCircleRounded,
  CancelRounded,
} from '@mui/icons-material';
import axios from 'axios';
import { ErrorGroup, ErrorSeverity, ErrorStatus } from '../types';

interface Props {
  onSelectGroup?: (fingerprint: string) => void;
}

const API_BASE = typeof window !== 'undefined'
  ? `http://${window.location.hostname || 'localhost'}:8080`
  : 'http://localhost:8080';

export default function ErrorGroupsPage({ onSelectGroup }: Props) {
  const [groups, setGroups] = useState<ErrorGroup[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [search, setSearch] = useState('');
  const [service, setService] = useState('');
  const [severity, setSeverity] = useState('');
  const [status, setStatus] = useState('');
  const [timeRange, setTimeRange] = useState('LAST_7_DAYS');
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  // Triage menu state
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const [selectedFingerprint, setSelectedFingerprint] = useState<string | null>(null);

  const fetchGroups = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params: Record<string, any> = {
        page: page - 1,
        size: 15,
        timeRange,
        sortBy: 'lastSeen',
        sortDir: 'DESC',
      };
      if (search.trim()) params.search = search.trim();
      if (service) params.service = service;
      if (severity) params.severity = severity;
      if (status) params.status = status;

      const res = await axios.get(`${API_BASE}/api/errors/admin/groups`, { params });
      setGroups(res.data.content || []);
      setTotalPages(res.data.totalPages || 1);
      setTotalElements(res.data.totalElements || 0);
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to query error groups');
    } finally {
      setLoading(false);
    }
  }, [page, search, service, severity, status, timeRange]);

  useEffect(() => {
    fetchGroups();
  }, [fetchGroups]);

  const handleTriage = async (newStatus: ErrorStatus) => {
    if (!selectedFingerprint) return;
    try {
      await axios.patch(`${API_BASE}/api/errors/admin/groups/${selectedFingerprint}/triage`, {
        status: newStatus,
      });
      setAnchorEl(null);
      fetchGroups();
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to update error triage status');
    }
  };

  const getSeverityColor = (sev: string) => {
    switch (sev) {
      case 'CRITICAL': return '#EF4444';
      case 'ERROR': return '#F59E0B';
      case 'WARNING': return '#EAB308';
      default: return '#3B82F6';
    }
  };

  const getStatusColor = (st: string) => {
    switch (st) {
      case 'NEW': return { bg: '#FEE2E2', text: '#DC2626' };
      case 'INVESTIGATING': return { bg: '#FEF3C7', text: '#D97706' };
      case 'RESOLVED': return { bg: '#DCFCE7', text: '#16A34A' };
      case 'IGNORED': return { bg: '#F1F5F9', text: '#64748B' };
      default: return { bg: '#E2E8F0', text: '#475569' };
    }
  };

  return (
    <Box sx={{ p: 3, maxWidth: 1400, mx: 'auto' }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 3 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 800, color: '#F8FAFC' }}>
            Error Groups &amp; Triage Registry
          </Typography>
          <Typography variant="body2" sx={{ color: '#94A3B8' }}>
            {totalElements} clustered error fingerprints detected
          </Typography>
        </Box>
        <Tooltip title="Refresh list">
          <IconButton onClick={fetchGroups} sx={{ bgcolor: 'rgba(255,255,255,0.05)', color: '#94A3B8' }}>
            <RefreshRounded />
          </IconButton>
        </Tooltip>
      </Stack>

      {/* Filter Bar */}
      <Paper sx={{ p: 2.5, mb: 3, bgcolor: '#111827', border: '1px solid #1F2937', borderRadius: 3 }}>
        <Grid container spacing={2} alignItems="center">
          <Grid item xs={12} md={3.5}>
            <TextField
              fullWidth
              size="small"
              placeholder="Search error message, type, service..."
              value={search}
              onChange={(e) => { setSearch(e.target.value); setPage(1); }}
              InputProps={{
                startAdornment: <SearchRounded sx={{ color: '#64748B', mr: 1 }} fontSize="small" />,
              }}
              sx={{
                '& .MuiOutlinedInput-root': {
                  bgcolor: '#0B0F19',
                  color: '#F8FAFC',
                  '& fieldset': { borderColor: '#1F2937' },
                },
              }}
            />
          </Grid>

          <Grid item xs={6} sm={3} md={2}>
            <FormControl fullWidth size="small">
              <InputLabel sx={{ color: '#94A3B8' }}>Service</InputLabel>
              <Select
                value={service}
                label="Service"
                onChange={(e) => { setService(e.target.value); setPage(1); }}
                sx={{
                  bgcolor: '#0B0F19',
                  color: '#F8FAFC',
                  '& fieldset': { borderColor: '#1F2937' },
                }}
              >
                <MenuItem value="">All Services</MenuItem>
                <MenuItem value="order-service">order-service</MenuItem>
                <MenuItem value="user-service">user-service</MenuItem>
                <MenuItem value="product-service">product-service</MenuItem>
                <MenuItem value="cart-service">cart-service</MenuItem>
                <MenuItem value="inventory-service">inventory-service</MenuItem>
                <MenuItem value="loyalty-service">loyalty-service</MenuItem>
                <MenuItem value="accounting-service">accounting-service</MenuItem>
                <MenuItem value="analytics-service">analytics-service</MenuItem>
                <MenuItem value="shell-app">shell-app (frontend)</MenuItem>
              </Select>
            </FormControl>
          </Grid>

          <Grid item xs={6} sm={3} md={2}>
            <FormControl fullWidth size="small">
              <InputLabel sx={{ color: '#94A3B8' }}>Severity</InputLabel>
              <Select
                value={severity}
                label="Severity"
                onChange={(e) => { setSeverity(e.target.value); setPage(1); }}
                sx={{
                  bgcolor: '#0B0F19',
                  color: '#F8FAFC',
                  '& fieldset': { borderColor: '#1F2937' },
                }}
              >
                <MenuItem value="">All Severities</MenuItem>
                <MenuItem value="CRITICAL">Critical</MenuItem>
                <MenuItem value="ERROR">Error</MenuItem>
                <MenuItem value="WARNING">Warning</MenuItem>
                <MenuItem value="INFO">Info</MenuItem>
              </Select>
            </FormControl>
          </Grid>

          <Grid item xs={6} sm={3} md={2}>
            <FormControl fullWidth size="small">
              <InputLabel sx={{ color: '#94A3B8' }}>Status</InputLabel>
              <Select
                value={status}
                label="Status"
                onChange={(e) => { setStatus(e.target.value); setPage(1); }}
                sx={{
                  bgcolor: '#0B0F19',
                  color: '#F8FAFC',
                  '& fieldset': { borderColor: '#1F2937' },
                }}
              >
                <MenuItem value="">All Statuses</MenuItem>
                <MenuItem value="NEW">New</MenuItem>
                <MenuItem value="INVESTIGATING">Investigating</MenuItem>
                <MenuItem value="RESOLVED">Resolved</MenuItem>
                <MenuItem value="IGNORED">Ignored</MenuItem>
              </Select>
            </FormControl>
          </Grid>

          <Grid item xs={6} sm={3} md={2.5}>
            <FormControl fullWidth size="small">
              <InputLabel sx={{ color: '#94A3B8' }}>Time Window</InputLabel>
              <Select
                value={timeRange}
                label="Time Window"
                onChange={(e) => { setTimeRange(e.target.value); setPage(1); }}
                sx={{
                  bgcolor: '#0B0F19',
                  color: '#F8FAFC',
                  '& fieldset': { borderColor: '#1F2937' },
                }}
              >
                <MenuItem value="LAST_1_HOUR">Last 1 Hour</MenuItem>
                <MenuItem value="LAST_24_HOURS">Last 24 Hours</MenuItem>
                <MenuItem value="LAST_7_DAYS">Last 7 Days</MenuItem>
                <MenuItem value="LAST_30_DAYS">Last 30 Days</MenuItem>
              </Select>
            </FormControl>
          </Grid>
        </Grid>
      </Paper>

      {loading && <LinearProgress sx={{ mb: 2, borderRadius: 1 }} />}
      {error && <Alert severity="error" sx={{ mb: 3 }}>{error}</Alert>}

      {/* Table */}
      <Paper sx={{ bgcolor: '#111827', border: '1px solid #1F2937', borderRadius: 3, overflow: 'hidden' }}>
        {groups.length === 0 && !loading ? (
          <Box sx={{ p: 6, textAlign: 'center', color: '#64748B' }}>
            <Typography variant="body1">No error groups match the specified filters.</Typography>
          </Box>
        ) : (
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow sx={{ '& th': { color: '#64748B', fontWeight: 700, borderColor: '#1F2937' } }}>
                  <TableCell>Error Signature</TableCell>
                  <TableCell>Service &amp; Env</TableCell>
                  <TableCell>Severity</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Assignee</TableCell>
                  <TableCell align="right">Occurrences</TableCell>
                  <TableCell align="right">Last Active</TableCell>
                  <TableCell align="center">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {groups.map((g) => {
                  const stCol = getStatusColor(g.status);
                  return (
                    <TableRow key={g.fingerprint} hover sx={{ '& td': { borderColor: '#1F2937', color: '#E2E8F0' } }}>
                      <TableCell sx={{ maxWidth: 420 }}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 800, color: '#F8FAFC' }}>
                          {g.errorType}
                        </Typography>
                        <Typography variant="body2" sx={{ color: '#94A3B8', textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
                          {g.message}
                        </Typography>
                        <Typography variant="caption" sx={{ color: '#64748B', fontFamily: 'monospace' }}>
                          #{g.fingerprint.substring(0, 12)}
                        </Typography>
                      </TableCell>

                      <TableCell>
                        <Stack spacing={0.5}>
                          <Chip size="small" label={g.serviceName} sx={{ bgcolor: 'rgba(79,70,229,0.15)', color: '#818CF8', fontWeight: 600, width: 'fit-content' }} />
                          <Typography variant="caption" sx={{ color: '#64748B' }}>{g.environment}</Typography>
                        </Stack>
                      </TableCell>

                      <TableCell>
                        <Chip size="small" label={g.severity} sx={{ border: `1px solid ${getSeverityColor(g.severity)}`, color: getSeverityColor(g.severity), fontWeight: 700, bgcolor: 'transparent' }} />
                      </TableCell>

                      <TableCell>
                        <Chip size="small" label={g.status} sx={{ bgcolor: stCol.bg, color: stCol.text, fontWeight: 700 }} />
                      </TableCell>

                      <TableCell>
                        <Typography variant="caption" sx={{ color: g.assignedTo ? '#E2E8F0' : '#64748B' }}>
                          {g.assignedTo || 'Unassigned'}
                        </Typography>
                      </TableCell>

                      <TableCell align="right">
                        <Chip size="small" label={g.occurrencesCount.toLocaleString()} sx={{ bgcolor: 'rgba(255,255,255,0.08)', fontWeight: 800 }} />
                      </TableCell>

                      <TableCell align="right">
                        <Typography variant="caption" sx={{ color: '#94A3B8' }}>
                          {new Date(g.lastSeen).toLocaleString()}
                        </Typography>
                      </TableCell>

                      <TableCell align="center">
                        <Stack direction="row" spacing={0.5} justifyContent="center">
                          <Tooltip title="View Occurrence Details">
                            <IconButton size="small" onClick={() => onSelectGroup?.(g.fingerprint)} sx={{ color: '#818CF8' }}>
                              <OpenInNewRounded fontSize="small" />
                            </IconButton>
                          </Tooltip>

                          <Tooltip title="Triage Status">
                            <IconButton
                              size="small"
                              onClick={(e) => {
                                setAnchorEl(e.currentTarget);
                                setSelectedFingerprint(g.fingerprint);
                              }}
                              sx={{ color: '#94A3B8' }}
                            >
                              <MoreVertRounded fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        </Stack>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <Box sx={{ p: 2, display: 'flex', justifyContent: 'center', borderTop: '1px solid #1F2937' }}>
            <Pagination
              count={totalPages}
              page={page}
              onChange={(_, p) => setPage(p)}
              sx={{
                '& .MuiPaginationItem-root': { color: '#94A3B8' },
                '& .Mui-selected': { bgcolor: '#4F46E5 !important', color: '#fff' },
              }}
            />
          </Box>
        )}
      </Paper>

      {/* Triage Menu */}
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={() => setAnchorEl(null)}
        PaperProps={{ sx: { bgcolor: '#1F2937', color: '#F8FAFC', border: '1px solid #374151' } }}
      >
        <MenuItem onClick={() => handleTriage('INVESTIGATING')}>
          Mark as INVESTIGATING
        </MenuItem>
        <MenuItem onClick={() => handleTriage('RESOLVED')}>
          Mark as RESOLVED
        </MenuItem>
        <MenuItem onClick={() => handleTriage('IGNORED')}>
          Mark as IGNORED
        </MenuItem>
        <MenuItem onClick={() => handleTriage('NEW')}>
          Reopen as NEW
        </MenuItem>
      </Menu>
    </Box>
  );
}
