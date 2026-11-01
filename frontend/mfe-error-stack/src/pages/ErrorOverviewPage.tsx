import React, { useEffect, useState } from 'react';
import {
  Box,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Grid,
  IconButton,
  LinearProgress,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
  Alert,
} from '@mui/material';
import {
  BugReportRounded,
  CheckCircleOutlineRounded,
  ErrorOutlineRounded,
  HourglassEmptyRounded,
  RefreshRounded,
  WarningAmberRounded,
  TrendingUpRounded,
  OpenInNewRounded,
} from '@mui/icons-material';
import axios from 'axios';
import { ErrorOverview, ErrorGroup } from '../types';

interface Props {
  onSelectGroup?: (fingerprint: string) => void;
}

const API_BASE = typeof window !== 'undefined'
  ? `http://${window.location.hostname || 'localhost'}:8080`
  : 'http://localhost:8080';

export default function ErrorOverviewPage({ onSelectGroup }: Props) {
  const [data, setData] = useState<ErrorOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchOverview = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await axios.get<ErrorOverview>(`${API_BASE}/api/errors/admin/overview`);
      setData(res.data);
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to load error stack metrics');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOverview();
  }, []);

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
          <Typography variant="h5" sx={{ fontWeight: 800, color: '#F8FAFC', display: 'flex', alignItems: 'center', gap: 1 }}>
            <BugReportRounded sx={{ color: '#EF4444' }} /> Error Stack & Diagnostics Intelligence
          </Typography>
          <Typography variant="body2" sx={{ color: '#94A3B8' }}>
            Authoritative error tracking, root-cause diagnostics, and triage desk for platform microservices
          </Typography>
        </Box>
        <Tooltip title="Refresh metrics">
          <IconButton onClick={fetchOverview} sx={{ bgcolor: 'rgba(255,255,255,0.05)', color: '#94A3B8' }}>
            <RefreshRounded />
          </IconButton>
        </Tooltip>
      </Stack>

      {loading && <LinearProgress sx={{ mb: 2, borderRadius: 1 }} />}
      {error && <Alert severity="error" sx={{ mb: 3 }}>{error}</Alert>}

      {data && (
        <>
          {/* KPI Cards */}
          <Grid container spacing={2.5} sx={{ mb: 3 }}>
            <Grid item xs={12} sm={6} md={2.4}>
              <Card sx={{ bgcolor: '#111827', border: '1px solid #1F2937', borderRadius: 3 }}>
                <CardContent sx={{ p: 2.5 }}>
                  <Typography variant="caption" sx={{ color: '#94A3B8', fontWeight: 700, textTransform: 'uppercase' }}>
                    Total Occurrences
                  </Typography>
                  <Typography variant="h4" sx={{ fontWeight: 900, color: '#F8FAFC', my: 0.5 }}>
                    {data.totalOccurrences.toLocaleString()}
                  </Typography>
                  <Typography variant="caption" sx={{ color: '#64748B' }}>
                    Across all environments
                  </Typography>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} sm={6} md={2.4}>
              <Card sx={{ bgcolor: '#111827', border: '1px solid #1F2937', borderRadius: 3 }}>
                <CardContent sx={{ p: 2.5 }}>
                  <Typography variant="caption" sx={{ color: '#94A3B8', fontWeight: 700, textTransform: 'uppercase' }}>
                    Active Groups
                  </Typography>
                  <Typography variant="h4" sx={{ fontWeight: 900, color: '#818CF8', my: 0.5 }}>
                    {data.totalGroups}
                  </Typography>
                  <Typography variant="caption" sx={{ color: '#64748B' }}>
                    Clustered fingerprints
                  </Typography>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} sm={6} md={2.4}>
              <Card sx={{ bgcolor: '#111827', border: '1px solid #1F2937', borderRadius: 3 }}>
                <CardContent sx={{ p: 2.5 }}>
                  <Typography variant="caption" sx={{ color: '#EF4444', fontWeight: 700, textTransform: 'uppercase' }}>
                    Unresolved Critical
                  </Typography>
                  <Typography variant="h4" sx={{ fontWeight: 900, color: '#EF4444', my: 0.5 }}>
                    {data.unresolvedCriticalCount}
                  </Typography>
                  <Typography variant="caption" sx={{ color: '#64748B' }}>
                    Immediate triage required
                  </Typography>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} sm={6} md={2.4}>
              <Card sx={{ bgcolor: '#111827', border: '1px solid #1F2937', borderRadius: 3 }}>
                <CardContent sx={{ p: 2.5 }}>
                  <Typography variant="caption" sx={{ color: '#F59E0B', fontWeight: 700, textTransform: 'uppercase' }}>
                    New Unassigned
                  </Typography>
                  <Typography variant="h4" sx={{ fontWeight: 900, color: '#F59E0B', my: 0.5 }}>
                    {data.newErrorsCount}
                  </Typography>
                  <Typography variant="caption" sx={{ color: '#64748B' }}>
                    Pending review
                  </Typography>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} sm={6} md={2.4}>
              <Card sx={{ bgcolor: '#111827', border: '1px solid #1F2937', borderRadius: 3 }}>
                <CardContent sx={{ p: 2.5 }}>
                  <Typography variant="caption" sx={{ color: '#10B981', fontWeight: 700, textTransform: 'uppercase' }}>
                    Last 24 Hours
                  </Typography>
                  <Typography variant="h4" sx={{ fontWeight: 900, color: '#10B981', my: 0.5 }}>
                    {data.last24HoursCount}
                  </Typography>
                  <Typography variant="caption" sx={{ color: '#64748B' }}>
                    Recent error velocity
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
          </Grid>

          {/* Breakdown Section */}
          <Grid container spacing={3} sx={{ mb: 3 }}>
            {/* Service Breakdown */}
            <Grid item xs={12} md={6}>
              <Paper sx={{ p: 3, bgcolor: '#111827', border: '1px solid #1F2937', borderRadius: 3, height: '100%' }}>
                <Typography variant="subtitle1" sx={{ fontWeight: 800, color: '#F8FAFC', mb: 2 }}>
                  Errors by Affected Service
                </Typography>
                {Object.keys(data.serviceBreakdown).length === 0 ? (
                  <Typography variant="body2" sx={{ color: '#64748B' }}>No service errors recorded yet.</Typography>
                ) : (
                  <Stack spacing={2}>
                    {Object.entries(data.serviceBreakdown).map(([srv, count]) => {
                      const max = Math.max(...Object.values(data.serviceBreakdown), 1);
                      const pct = Math.round((count / max) * 100);
                      return (
                        <Box key={srv}>
                          <Stack direction="row" justifyContent="space-between" sx={{ mb: 0.5 }}>
                            <Typography variant="body2" sx={{ fontWeight: 600, color: '#E2E8F0' }}>{srv}</Typography>
                            <Typography variant="body2" sx={{ fontWeight: 700, color: '#818CF8' }}>{count} groups</Typography>
                          </Stack>
                          <LinearProgress
                            variant="determinate"
                            value={pct}
                            sx={{
                              height: 8,
                              borderRadius: 4,
                              bgcolor: 'rgba(255,255,255,0.05)',
                              '& .MuiLinearProgress-bar': { bgcolor: '#4F46E5', borderRadius: 4 },
                            }}
                          />
                        </Box>
                      );
                    })}
                  </Stack>
                )}
              </Paper>
            </Grid>

            {/* Severity & Status Distributions */}
            <Grid item xs={12} md={6}>
              <Paper sx={{ p: 3, bgcolor: '#111827', border: '1px solid #1F2937', borderRadius: 3, height: '100%' }}>
                <Typography variant="subtitle1" sx={{ fontWeight: 800, color: '#F8FAFC', mb: 2 }}>
                  Severity &amp; Triage Distribution
                </Typography>
                <Typography variant="caption" sx={{ color: '#94A3B8', fontWeight: 700, textTransform: 'uppercase' }}>
                  By Severity Level
                </Typography>
                <Stack direction="row" spacing={1.5} sx={{ mt: 1, mb: 3, flexWrap: 'wrap', gap: 1 }}>
                  {Object.entries(data.severityBreakdown).map(([sev, count]) => (
                    <Chip
                      key={sev}
                      label={`${sev}: ${count}`}
                      sx={{
                        bgcolor: 'rgba(255,255,255,0.05)',
                        border: `1px solid ${getSeverityColor(sev)}`,
                        color: getSeverityColor(sev),
                        fontWeight: 700,
                      }}
                    />
                  ))}
                </Stack>

                <Typography variant="caption" sx={{ color: '#94A3B8', fontWeight: 700, textTransform: 'uppercase' }}>
                  By Triage Status
                </Typography>
                <Stack direction="row" spacing={1.5} sx={{ mt: 1, flexWrap: 'wrap', gap: 1 }}>
                  {Object.entries(data.statusBreakdown).map(([st, count]) => {
                    const col = getStatusColor(st);
                    return (
                      <Chip
                        key={st}
                        label={`${st}: ${count}`}
                        sx={{
                          bgcolor: col.bg,
                          color: col.text,
                          fontWeight: 700,
                        }}
                      />
                    );
                  })}
                </Stack>
              </Paper>
            </Grid>
          </Grid>

          {/* Recent Error Groups */}
          <Paper sx={{ p: 3, bgcolor: '#111827', border: '1px solid #1F2937', borderRadius: 3 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 800, color: '#F8FAFC', mb: 2 }}>
              Recently Active Error Groups
            </Typography>
            {data.recentGroups.length === 0 ? (
              <Box sx={{ p: 4, textAlign: 'center', color: '#64748B' }}>
                <CheckCircleOutlineRounded sx={{ fontSize: 48, color: '#10B981', mb: 1 }} />
                <Typography variant="body1">Zero errors currently recorded. Platform is healthy!</Typography>
              </Box>
            ) : (
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow sx={{ '& th': { color: '#64748B', fontWeight: 700, borderColor: '#1F2937' } }}>
                      <TableCell>Error Type &amp; Message</TableCell>
                      <TableCell>Service</TableCell>
                      <TableCell>Severity</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell align="right">Occurrences</TableCell>
                      <TableCell align="right">Last Seen</TableCell>
                      <TableCell align="center">Action</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {data.recentGroups.map((g) => {
                      const stCol = getStatusColor(g.status);
                      return (
                        <TableRow key={g.fingerprint} sx={{ '& td': { borderColor: '#1F2937', color: '#E2E8F0' } }}>
                          <TableCell sx={{ maxWidth: 400 }}>
                            <Typography variant="body2" sx={{ fontWeight: 700, color: '#F8FAFC' }}>
                              {g.errorType}
                            </Typography>
                            <Typography variant="caption" sx={{ color: '#94A3B8', display: 'block', textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
                              {g.message}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <Chip size="small" label={g.serviceName} sx={{ bgcolor: 'rgba(79,70,229,0.15)', color: '#818CF8', fontWeight: 600 }} />
                          </TableCell>
                          <TableCell>
                            <Chip size="small" label={g.severity} sx={{ border: `1px solid ${getSeverityColor(g.severity)}`, color: getSeverityColor(g.severity), fontWeight: 700, bgcolor: 'transparent' }} />
                          </TableCell>
                          <TableCell>
                            <Chip size="small" label={g.status} sx={{ bgcolor: stCol.bg, color: stCol.text, fontWeight: 700 }} />
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
                            <IconButton size="small" onClick={() => onSelectGroup?.(g.fingerprint)} sx={{ color: '#818CF8' }}>
                              <OpenInNewRounded fontSize="small" />
                            </IconButton>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Paper>
        </>
      )}
    </Box>
  );
}
