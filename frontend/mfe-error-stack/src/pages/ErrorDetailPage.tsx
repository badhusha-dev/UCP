import React, { useEffect, useState } from 'react';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  FormControl,
  Grid,
  IconButton,
  InputLabel,
  LinearProgress,
  MenuItem,
  Paper,
  Select,
  Stack,
  Tab,
  Tabs,
  TextField,
  Tooltip,
  Typography,
  Alert,
} from '@mui/material';
import {
  ArrowBackRounded,
  ContentCopyRounded,
  CheckCircleRounded,
  AssignmentIndRounded,
  BugReportRounded,
  TimelineRounded,
  CodeRounded,
  HttpRounded,
  PersonOutlineRounded,
  DevicesRounded,
} from '@mui/icons-material';
import axios from 'axios';
import { ErrorGroup, ErrorRecord, ErrorStatus } from '../types';

interface Props {
  fingerprint: string;
  onBack: () => void;
}

const API_BASE = typeof window !== 'undefined'
  ? `http://${window.location.hostname || 'localhost'}:8080`
  : 'http://localhost:8080';

export default function ErrorDetailPage({ fingerprint, onBack }: Props) {
  const [group, setGroup] = useState<ErrorGroup | null>(null);
  const [occurrences, setOccurrences] = useState<ErrorRecord[]>([]);
  const [selectedRecord, setSelectedRecord] = useState<ErrorRecord | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<'stack' | 'context' | 'breadcrumbs' | 'history'>('stack');

  // Triage state
  const [status, setStatus] = useState<ErrorStatus>('NEW');
  const [assignedTo, setAssignedTo] = useState('');
  const [savingTriage, setSavingTriage] = useState(false);
  const [triageSuccess, setTriageSuccess] = useState(false);

  const fetchDetail = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await axios.get(`${API_BASE}/api/errors/admin/groups/${fingerprint}`);
      const g = res.data.group;
      const occs = res.data.recentOccurrences || [];
      setGroup(g);
      setOccurrences(occs);
      if (occs.length > 0) {
        setSelectedRecord(occs[0]);
      }
      setStatus(g.status);
      setAssignedTo(g.assignedTo || '');
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to load error details');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDetail();
  }, [fingerprint]);

  const handleSaveTriage = async () => {
    setSavingTriage(true);
    setTriageSuccess(false);
    try {
      await axios.patch(`${API_BASE}/api/errors/admin/groups/${fingerprint}/triage`, {
        status,
        assignedTo: assignedTo.trim() || null,
      });
      setTriageSuccess(true);
      setTimeout(() => setTriageSuccess(false), 3000);
      fetchDetail();
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to update triage');
    } finally {
      setSavingTriage(false);
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
  };

  const getSeverityColor = (sev: string) => {
    switch (sev) {
      case 'CRITICAL': return '#EF4444';
      case 'ERROR': return '#F59E0B';
      case 'WARNING': return '#EAB308';
      default: return '#3B82F6';
    }
  };

  return (
    <Box sx={{ p: 3, maxWidth: 1400, mx: 'auto' }}>
      {/* Top Header */}
      <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 3 }}>
        <Button
          startIcon={<ArrowBackRounded />}
          onClick={onBack}
          sx={{ color: '#94A3B8', '&:hover': { color: '#F8FAFC' } }}
        >
          Back to Groups
        </Button>
      </Stack>

      {loading && <LinearProgress sx={{ mb: 2, borderRadius: 1 }} />}
      {error && <Alert severity="error" sx={{ mb: 3 }}>{error}</Alert>}

      {group && (
        <>
          {/* Main Group Header Card */}
          <Paper sx={{ p: 3, mb: 3, bgcolor: '#111827', border: '1px solid #1F2937', borderRadius: 3 }}>
            <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}>
              <Box sx={{ maxWidth: 850 }}>
                <Stack direction="row" spacing={1.5} alignItems="center" sx={{ mb: 1 }}>
                  <Chip
                    size="small"
                    label={group.severity}
                    sx={{
                      border: `1px solid ${getSeverityColor(group.severity)}`,
                      color: getSeverityColor(group.severity),
                      fontWeight: 800,
                      bgcolor: 'transparent',
                    }}
                  />
                  <Chip
                    size="small"
                    label={group.serviceName}
                    sx={{ bgcolor: 'rgba(79,70,229,0.15)', color: '#818CF8', fontWeight: 700 }}
                  />
                  <Chip
                    size="small"
                    label={group.environment}
                    sx={{ bgcolor: 'rgba(255,255,255,0.06)', color: '#94A3B8' }}
                  />
                  <Typography variant="caption" sx={{ color: '#64748B', fontFamily: 'monospace' }}>
                    Fingerprint: {group.fingerprint}
                  </Typography>
                  <Tooltip title="Copy Fingerprint">
                    <IconButton size="small" onClick={() => copyToClipboard(group.fingerprint)} sx={{ color: '#64748B' }}>
                      <ContentCopyRounded fontSize="inherit" />
                    </IconButton>
                  </Tooltip>
                </Stack>

                <Typography variant="h5" sx={{ fontWeight: 900, color: '#F8FAFC', mb: 1 }}>
                  {group.errorType}
                </Typography>
                <Typography variant="body1" sx={{ color: '#CBD5E1', mb: 2 }}>
                  {group.message}
                </Typography>

                <Stack direction="row" spacing={3} sx={{ color: '#94A3B8' }}>
                  <Typography variant="caption">
                    Occurrences: <strong style={{ color: '#F8FAFC' }}>{group.occurrencesCount.toLocaleString()}</strong>
                  </Typography>
                  <Typography variant="caption">
                    First Seen: <strong style={{ color: '#F8FAFC' }}>{new Date(group.firstSeen).toLocaleString()}</strong>
                  </Typography>
                  <Typography variant="caption">
                    Last Seen: <strong style={{ color: '#F8FAFC' }}>{new Date(group.lastSeen).toLocaleString()}</strong>
                  </Typography>
                </Stack>
              </Box>

              {/* Triage Box */}
              <Paper sx={{ p: 2.5, bgcolor: '#0B0F19', border: '1px solid #1F2937', borderRadius: 2.5, minWidth: 260 }}>
                <Typography variant="subtitle2" sx={{ fontWeight: 800, color: '#F8FAFC', mb: 1.5 }}>
                  Triage &amp; Assignment
                </Typography>
                <Stack spacing={2}>
                  <FormControl fullWidth size="small">
                    <InputLabel sx={{ color: '#94A3B8' }}>Status</InputLabel>
                    <Select
                      value={status}
                      label="Status"
                      onChange={(e) => setStatus(e.target.value as ErrorStatus)}
                      sx={{ color: '#F8FAFC', '& fieldset': { borderColor: '#374151' } }}
                    >
                      <MenuItem value="NEW">NEW</MenuItem>
                      <MenuItem value="INVESTIGATING">INVESTIGATING</MenuItem>
                      <MenuItem value="RESOLVED">RESOLVED</MenuItem>
                      <MenuItem value="IGNORED">IGNORED</MenuItem>
                    </Select>
                  </FormControl>

                  <TextField
                    size="small"
                    label="Assignee"
                    placeholder="e.g. admin@ecommerce.com"
                    value={assignedTo}
                    onChange={(e) => setAssignedTo(e.target.value)}
                    sx={{
                      '& .MuiOutlinedInput-root': {
                        color: '#F8FAFC',
                        '& fieldset': { borderColor: '#374151' },
                      },
                      '& .MuiInputLabel-root': { color: '#94A3B8' },
                    }}
                  />

                  <Button
                    variant="contained"
                    onClick={handleSaveTriage}
                    disabled={savingTriage}
                    sx={{ bgcolor: '#4F46E5', fontWeight: 700, '&:hover': { bgcolor: '#4338CA' } }}
                  >
                    {savingTriage ? 'Saving...' : 'Update Triage'}
                  </Button>
                  {triageSuccess && (
                    <Typography variant="caption" sx={{ color: '#10B981', textAlign: 'center' }}>
                      Triage updated successfully!
                    </Typography>
                  )}
                </Stack>
              </Paper>
            </Stack>
          </Paper>

          {/* Occurrence selector & Tabs */}
          <Paper sx={{ mb: 3, bgcolor: '#111827', border: '1px solid #1F2937', borderRadius: 3 }}>
            <Box sx={{ borderBottom: '1px solid #1F2937', px: 2 }}>
              <Tabs
                value={tab}
                onChange={(_, v) => setTab(v)}
                sx={{
                  '& .MuiTab-root': { color: '#94A3B8', fontWeight: 700 },
                  '& .Mui-selected': { color: '#818CF8' },
                  '& .MuiTabs-indicator': { bgcolor: '#4F46E5' },
                }}
              >
                <Tab icon={<CodeRounded />} iconPosition="start" label="Stack Trace" value="stack" />
                <Tab icon={<HttpRounded />} iconPosition="start" label="Diagnostic Context" value="context" />
                <Tab icon={<TimelineRounded />} iconPosition="start" label={`Breadcrumbs (${selectedRecord?.breadcrumbs?.length || 0})`} value="breadcrumbs" />
                <Tab icon={<BugReportRounded />} iconPosition="start" label={`History (${occurrences.length})`} value="history" />
              </Tabs>
            </Box>

            {/* TAB 1: STACK TRACE */}
            {tab === 'stack' && (
              <Box sx={{ p: 3 }}>
                <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1.5 }}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 800, color: '#94A3B8' }}>
                    Sanitized Stack Trace (Sensitive Values Redacted)
                  </Typography>
                  {selectedRecord?.stackTrace && (
                    <Button
                      size="small"
                      startIcon={<ContentCopyRounded />}
                      onClick={() => copyToClipboard(selectedRecord.stackTrace || '')}
                      sx={{ color: '#818CF8' }}
                    >
                      Copy Trace
                    </Button>
                  )}
                </Stack>

                {selectedRecord?.stackTrace ? (
                  <Paper
                    sx={{
                      p: 2.5,
                      bgcolor: '#0B0F19',
                      border: '1px solid #1F2937',
                      borderRadius: 2,
                      fontFamily: 'Consolas, monospace',
                      fontSize: '0.85rem',
                      color: '#F87171',
                      whiteSpace: 'pre-wrap',
                      wordBreak: 'break-all',
                      maxHeight: 500,
                      overflowY: 'auto',
                    }}
                  >
                    {selectedRecord.stackTrace}
                  </Paper>
                ) : (
                  <Typography variant="body2" sx={{ color: '#64748B' }}>
                    No stack trace provided for this occurrence.
                  </Typography>
                )}

                {selectedRecord?.exceptionChain && (
                  <Box sx={{ mt: 3 }}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 800, color: '#94A3B8', mb: 1 }}>
                      Exception Root Cause Chain
                    </Typography>
                    <Paper
                      sx={{
                        p: 2,
                        bgcolor: '#0B0F19',
                        border: '1px solid #1F2937',
                        borderRadius: 2,
                        fontFamily: 'Consolas, monospace',
                        fontSize: '0.82rem',
                        color: '#FBBF24',
                        whiteSpace: 'pre-wrap',
                      }}
                    >
                      {selectedRecord.exceptionChain}
                    </Paper>
                  </Box>
                )}
              </Box>
            )}

            {/* TAB 2: DIAGNOSTIC CONTEXT */}
            {tab === 'context' && selectedRecord && (
              <Box sx={{ p: 3 }}>
                <Grid container spacing={2.5}>
                  <Grid item xs={12} sm={6} md={4}>
                    <Paper sx={{ p: 2, bgcolor: '#0B0F19', border: '1px solid #1F2937', borderRadius: 2 }}>
                      <Typography variant="caption" sx={{ color: '#64748B', fontWeight: 700 }}>HTTP REQUEST</Typography>
                      <Typography variant="body1" sx={{ fontWeight: 700, color: '#F8FAFC', mt: 0.5 }}>
                        {selectedRecord.requestMethod || 'N/A'} {selectedRecord.requestUri || 'N/A'}
                      </Typography>
                      <Typography variant="caption" sx={{ color: '#94A3B8' }}>
                        Response Status: {selectedRecord.responseStatus || 'N/A'}
                      </Typography>
                    </Paper>
                  </Grid>

                  <Grid item xs={12} sm={6} md={4}>
                    <Paper sx={{ p: 2, bgcolor: '#0B0F19', border: '1px solid #1F2937', borderRadius: 2 }}>
                      <Typography variant="caption" sx={{ color: '#64748B', fontWeight: 700 }}>DISTRIBUTED TRACE ID</Typography>
                      <Typography variant="body2" sx={{ fontFamily: 'monospace', color: '#818CF8', mt: 0.5, wordBreak: 'break-all' }}>
                        {selectedRecord.traceId || selectedRecord.correlationId || 'N/A'}
                      </Typography>
                    </Paper>
                  </Grid>

                  <Grid item xs={12} sm={6} md={4}>
                    <Paper sx={{ p: 2, bgcolor: '#0B0F19', border: '1px solid #1F2937', borderRadius: 2 }}>
                      <Typography variant="caption" sx={{ color: '#64748B', fontWeight: 700 }}>CLIENT &amp; OS</Typography>
                      <Typography variant="body2" sx={{ color: '#F8FAFC', mt: 0.5 }}>
                        Browser: {selectedRecord.browser || 'Server Backend'}
                      </Typography>
                      <Typography variant="caption" sx={{ color: '#94A3B8' }}>
                        OS: {selectedRecord.os || 'Linux Container'} | Route: {selectedRecord.frontendRoute || 'N/A'}
                      </Typography>
                    </Paper>
                  </Grid>

                  <Grid item xs={12} sm={6} md={4}>
                    <Paper sx={{ p: 2, bgcolor: '#0B0F19', border: '1px solid #1F2937', borderRadius: 2 }}>
                      <Typography variant="caption" sx={{ color: '#64748B', fontWeight: 700 }}>USER CONTEXT</Typography>
                      <Typography variant="body2" sx={{ color: '#F8FAFC', mt: 0.5 }}>
                        User ID: {selectedRecord.userId ? `#${selectedRecord.userId}` : 'Anonymous / System'}
                      </Typography>
                      <Typography variant="caption" sx={{ color: '#94A3B8' }}>
                        Role: {selectedRecord.userRole || 'NONE'}
                      </Typography>
                    </Paper>
                  </Grid>

                  <Grid item xs={12} sm={6} md={4}>
                    <Paper sx={{ p: 2, bgcolor: '#0B0F19', border: '1px solid #1F2937', borderRadius: 2 }}>
                      <Typography variant="caption" sx={{ color: '#64748B', fontWeight: 700 }}>VERSION &amp; RELEASE</Typography>
                      <Typography variant="body2" sx={{ color: '#F8FAFC', mt: 0.5 }}>
                        Version: {selectedRecord.version || '1.0.0'}
                      </Typography>
                      <Typography variant="caption" sx={{ color: '#94A3B8' }}>
                        Release: {selectedRecord.releaseTag || 'production-v2.0.0'}
                      </Typography>
                    </Paper>
                  </Grid>

                  <Grid item xs={12} sm={6} md={4}>
                    <Paper sx={{ p: 2, bgcolor: '#0B0F19', border: '1px solid #1F2937', borderRadius: 2 }}>
                      <Typography variant="caption" sx={{ color: '#64748B', fontWeight: 700 }}>EVENT TIMESTAMP</Typography>
                      <Typography variant="body2" sx={{ color: '#F8FAFC', mt: 0.5 }}>
                        {new Date(selectedRecord.createdAt).toLocaleString()}
                      </Typography>
                    </Paper>
                  </Grid>
                </Grid>
              </Box>
            )}

            {/* TAB 3: BREADCRUMBS */}
            {tab === 'breadcrumbs' && selectedRecord && (
              <Box sx={{ p: 3 }}>
                {!selectedRecord.breadcrumbs || selectedRecord.breadcrumbs.length === 0 ? (
                  <Typography variant="body2" sx={{ color: '#64748B' }}>
                    No breadcrumb telemetry was recorded prior to this event.
                  </Typography>
                ) : (
                  <Stack spacing={1.5}>
                    {selectedRecord.breadcrumbs.map((b, idx) => (
                      <Paper
                        key={idx}
                        sx={{
                          p: 1.5,
                          bgcolor: '#0B0F19',
                          border: '1px solid #1F2937',
                          borderRadius: 2,
                          display: 'flex',
                          alignItems: 'center',
                          gap: 2,
                        }}
                      >
                        <Chip
                          size="small"
                          label={b.category}
                          sx={{ bgcolor: 'rgba(255,255,255,0.06)', color: '#818CF8', fontWeight: 600 }}
                        />
                        <Typography variant="body2" sx={{ color: '#E2E8F0', flex: 1 }}>
                          {b.message}
                        </Typography>
                        <Typography variant="caption" sx={{ color: '#64748B' }}>
                          {b.timestamp ? new Date(b.timestamp).toLocaleTimeString() : ''}
                        </Typography>
                      </Paper>
                    ))}
                  </Stack>
                )}
              </Box>
            )}

            {/* TAB 4: RECENT OCCURRENCES HISTORY */}
            {tab === 'history' && (
              <Box sx={{ p: 3 }}>
                <Typography variant="subtitle2" sx={{ color: '#94A3B8', fontWeight: 700, mb: 2 }}>
                  Last 20 Occurrences of this Error Fingerprint
                </Typography>
                <Stack spacing={1.5}>
                  {occurrences.map((occ) => (
                    <Paper
                      key={occ.id}
                      onClick={() => setSelectedRecord(occ)}
                      sx={{
                        p: 2,
                        bgcolor: selectedRecord?.id === occ.id ? '#1E1B4B' : '#0B0F19',
                        border: `1px solid ${selectedRecord?.id === occ.id ? '#4F46E5' : '#1F2937'}`,
                        borderRadius: 2,
                        cursor: 'pointer',
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        '&:hover': { borderColor: '#4F46E5' },
                      }}
                    >
                      <Box>
                        <Typography variant="body2" sx={{ fontWeight: 700, color: '#F8FAFC' }}>
                          Record #{occ.id} — {occ.requestMethod || 'INTERNAL'} {occ.requestUri || ''}
                        </Typography>
                        <Typography variant="caption" sx={{ color: '#94A3B8' }}>
                          Trace ID: {occ.traceId || occ.correlationId || 'N/A'} | Environment: {occ.environment}
                        </Typography>
                      </Box>
                      <Typography variant="caption" sx={{ color: '#CBD5E1', fontWeight: 600 }}>
                        {new Date(occ.createdAt).toLocaleString()}
                      </Typography>
                    </Paper>
                  ))}
                </Stack>
              </Box>
            )}
          </Paper>
        </>
      )}
    </Box>
  );
}
