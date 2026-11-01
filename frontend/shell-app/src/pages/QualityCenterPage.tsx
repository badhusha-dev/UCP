import React, { useCallback, useEffect, useMemo, useState } from 'react';
import axios from 'axios';
import {
  Alert, Box, Button, Card, CardContent, Chip, CircularProgress, Divider, FormControl,
  InputLabel, MenuItem, Paper, Select, Snackbar, Stack, Tab, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, Tabs, TextField, Tooltip, Typography,
} from '@mui/material';
import {
  AccessTimeRounded, BugReportRounded,
  DownloadRounded, ErrorOutlineRounded, FactCheckRounded, PlayArrowRounded,
  RefreshRounded, StopCircleOutlined, TaskAltRounded,
} from '@mui/icons-material';

type Page<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };
type TestMapping = { testId: string; name: string; testClass: string; layer: string; caseCount: number; permission?: string | null };
type Feature = {
  id: string; name: string; domain: string; frontendModule: string; backendService: string;
  endpoints: string[]; databases: string[]; permissions: string[]; coverageStatus: string;
  coverageNote: string; automatedTestCount: number; lastRunStatus: string; lastRunAt?: string | null;
  tests: TestMapping[];
};
type Suite = { id: string; name: string; description: string; executable: boolean };
type Run = {
  id: string; suiteId: string; featureId?: string | null; environment: string; sourceVersion: string;
  requestedByEmail: string; status: string; phase: string; currentTest?: string | null;
  totalTests: number; completedTests: number; passedTests: number; failedTests: number;
  skippedTests: number; blockedTests: number; infrastructureFailures: number; errorType?: string | null;
  errorMessage?: string | null; createdAt: string; startedAt?: string | null; finishedAt?: string | null;
  durationMs?: number | null; retryOf?: string | null;
};
type TestResult = {
  id: number; runId: string; testId: string; testName: string; featureId?: string | null;
  serviceName: string; layer: string; roleName?: string | null; permission?: string | null;
  status: string; durationMs: number; expected?: string | null; actual?: string | null;
  errorMessage?: string | null; logExcerpt?: string | null; evidenceReference?: string | null; executedAt: string;
};
type Overview = {
  totalFeatures: number; featuresWithTests: number; featuresWithoutTests: number; registeredTestCases: number;
  totalDiscoveredEndpoints: number; endpointCoverageStatus: string; latestRunStatus: string;
  passed: number; failed: number; skipped: number; blocked: number; infrastructureFailures: number;
  rbacCoverageStatus: string; registeredRbacCases: number; lastRunRbacCases: number;
  lastSuccessfulFullRunAt?: string | null; recentRuns: Run[];
};
type RbacCell = {
  role: string; featureId: string; featureName: string; permission: string; endpoints: string[];
  allowed: boolean | null; testCoverage: string;
};
type Coverage = {
  features: Array<{ featureId: string; name: string; backendService: string; registeredTests: number;
    functionalCoverage: string; discoveredEndpoints: number; endpointCoverage: string; rbacTests: number; rbacCoverage: string }>;
  registeredTestCases: number; endpointsDiscovered: number; codeCoverageStatus: string; browserE2eStatus: string;
};

const host = typeof window !== 'undefined' ? window.location.hostname : 'localhost';
const api = axios.create({ baseURL: `http://${host}:8080`, timeout: 15_000 });
api.interceptors.request.use((config) => {
  const token = typeof window !== 'undefined' ? localStorage.getItem('jwt_token') : null;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

const activeStatuses = new Set(['QUEUED', 'PREFLIGHT', 'RUNNING', 'FINALIZING']);
const statusColor = (status?: string): 'success' | 'error' | 'warning' | 'info' | 'default' => {
  switch (status) {
    case 'PASSED': case 'ASSERTION_PASSED': return 'success';
    case 'FAILED': case 'ASSERTION_FAILED': return 'error';
    case 'BLOCKED': case 'INFRASTRUCTURE_FAILURE': case 'TIMED_OUT': case 'CANCELLED': return 'warning';
    case 'RUNNING': case 'PREFLIGHT': case 'QUEUED': case 'FINALIZING': return 'info';
    default: return 'default';
  }
};
const pretty = (value?: string | null) => value ? value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, char => char.toUpperCase()) : '—';
const dateTime = (value?: string | null) => value ? new Date(value).toLocaleString() : '—';
const messageOf = (error: any) => error?.response?.data?.detail || error?.response?.data?.message || error?.message || 'Request failed';

export default function QualityCenterPage() {
  const [tab, setTab] = useState(0);
  const [overview, setOverview] = useState<Overview | null>(null);
  const [features, setFeatures] = useState<Feature[]>([]);
  const [suites, setSuites] = useState<Suite[]>([]);
  const [runs, setRuns] = useState<Run[]>([]);
  const [rbac, setRbac] = useState<RbacCell[]>([]);
  const [coverage, setCoverage] = useState<Coverage | null>(null);
  const [selectedFeature, setSelectedFeature] = useState<Feature | null>(null);
  const [selectedRunId, setSelectedRunId] = useState<string | null>(null);
  const [selectedRun, setSelectedRun] = useState<Run | null>(null);
  const [results, setResults] = useState<TestResult[]>([]);
  const [runLog, setRunLog] = useState('');
  const [search, setSearch] = useState('');
  const [serviceFilter, setServiceFilter] = useState('');
  const [coverageFilter, setCoverageFilter] = useState('');
  const [featureRoleFilter, setFeatureRoleFilter] = useState('');
  const [featureRunFilter, setFeatureRunFilter] = useState('');
  const [layerFilter, setLayerFilter] = useState('');
  const [permissionFilter, setPermissionFilter] = useState('');
  const [runStatusFilter, setRunStatusFilter] = useState('');
  const [rbacRoleFilter, setRbacRoleFilter] = useState('');
  const [rbacPermissionFilter, setRbacPermissionFilter] = useState('');
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState(false);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');

  const loadOverview = useCallback(async () => {
    const response = await api.get<Overview>('/api/admin/qa/overview');
    setOverview(response.data);
  }, []);

  const loadFeatures = useCallback(async () => {
    const params: Record<string, string> = {};
    if (search.trim()) params.search = search.trim();
    if (serviceFilter) params.service = serviceFilter;
    if (coverageFilter) params.coverage = coverageFilter;
    const response = await api.get<Page<Feature>>('/api/admin/qa/features', { params: { page: 0, size: 100, ...params } });
    setFeatures(response.data.content);
  }, [search, serviceFilter, coverageFilter]);

  const loadSuites = useCallback(async () => {
    const response = await api.get<Suite[]>('/api/admin/qa/suites');
    setSuites(response.data);
  }, []);

  const loadRuns = useCallback(async () => {
    const params = runStatusFilter ? { status: runStatusFilter } : {};
    const response = await api.get<Page<Run>>('/api/admin/qa/runs', { params: { page: 0, size: 50, ...params } });
    setRuns(response.data.content);
    setSelectedRunId(current => current || response.data.content[0]?.id || null);
  }, [runStatusFilter]);

  const loadRbac = useCallback(async () => {
    const response = await api.get<RbacCell[]>('/api/admin/qa/rbac/matrix');
    setRbac(response.data);
  }, []);

  const loadCoverage = useCallback(async () => {
    const response = await api.get<Coverage>('/api/admin/qa/coverage');
    setCoverage(response.data);
  }, []);

  const loadRunDetails = useCallback(async (runId: string) => {
    const [runResponse, resultResponse, logResponse] = await Promise.all([
      api.get<Run>(`/api/admin/qa/runs/${runId}`),
      api.get<Page<TestResult>>(`/api/admin/qa/runs/${runId}/results`, { params: { page: 0, size: 500 } }),
      api.get<{ runId: string; log: string }>(`/api/admin/qa/runs/${runId}/logs`),
    ]);
    setSelectedRun(runResponse.data);
    setResults(resultResponse.data.content);
    setRunLog(logResponse.data.log || '');
  }, []);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      await Promise.all([loadOverview(), loadFeatures(), loadSuites(), loadRuns(), loadRbac(), loadCoverage()]);
    } catch (requestError) {
      setError(messageOf(requestError));
    } finally {
      setLoading(false);
    }
  }, [loadOverview, loadFeatures, loadSuites, loadRuns, loadRbac, loadCoverage]);

  useEffect(() => { void refresh(); }, [refresh]);
  useEffect(() => {
    if (!selectedRunId) { setSelectedRun(null); setResults([]); setRunLog(''); return; }
    void loadRunDetails(selectedRunId).catch(requestError => setError(messageOf(requestError)));
  }, [selectedRunId, loadRunDetails]);

  const hasActiveRun = useMemo(() => runs.some(run => activeStatuses.has(run.status)), [runs]);
  useEffect(() => {
    if (!hasActiveRun) return undefined;
    const timer = window.setInterval(() => {
      void Promise.all([loadOverview(), loadRuns()]).catch(requestError => setError(messageOf(requestError)));
      if (selectedRunId) void loadRunDetails(selectedRunId).catch(() => undefined);
    }, 2000);
    return () => window.clearInterval(timer);
  }, [hasActiveRun, selectedRunId, loadOverview, loadRuns, loadRunDetails]);

  const startRun = async (scope: { suiteId?: string; featureId?: string }, label: string) => {
    setWorking(true);
    setError('');
    try {
      const response = await api.post<Run>('/api/admin/qa/runs', { ...scope, environment: 'LOCAL_ISOLATED' });
      setSelectedRunId(response.data.id);
      setSelectedRun(response.data);
      setNotice(`${label} queued · run ${response.data.id.slice(0, 8)}`);
      setTab(2);
      await Promise.all([loadOverview(), loadRuns()]);
    } catch (requestError) {
      setError(messageOf(requestError));
    } finally {
      setWorking(false);
    }
  };

  const cancelRun = async () => {
    if (!selectedRunId) return;
    setWorking(true);
    try {
      await api.post(`/api/admin/qa/runs/${selectedRunId}/cancel`);
      setNotice('Cancellation requested. The runner is stopping safely.');
      await Promise.all([loadOverview(), loadRuns(), loadRunDetails(selectedRunId)]);
    } catch (requestError) { setError(messageOf(requestError)); }
    finally { setWorking(false); }
  };

  const rerunFailures = async () => {
    if (!selectedRunId) return;
    setWorking(true);
    try {
      const response = await api.post<Run>(`/api/admin/qa/runs/${selectedRunId}/rerun-failures`);
      setSelectedRunId(response.data.id);
      setNotice(`Failed tests queued · run ${response.data.id.slice(0, 8)}`);
      await loadRuns();
    } catch (requestError) { setError(messageOf(requestError)); }
    finally { setWorking(false); }
  };

  const chooseFeature = async (featureId: string) => {
    try {
      const response = await api.get<Feature>(`/api/admin/qa/features/${featureId}`);
      setSelectedFeature(response.data);
    } catch (requestError) { setError(messageOf(requestError)); }
  };

  const downloadReport = async (format: 'json' | 'junit') => {
    if (!selectedRunId) return;
    try {
      const response = await api.get(`/api/admin/qa/runs/${selectedRunId}/report`, {
        params: { format }, responseType: format === 'junit' ? 'blob' : 'json',
      });
      const blob = format === 'junit' ? response.data as Blob : new Blob([JSON.stringify(response.data, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `ucp-qa-${selectedRunId}.${format === 'junit' ? 'xml' : 'json'}`;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (requestError) { setError(messageOf(requestError)); }
  };

  const uniqueServices = useMemo(() => Array.from(new Set(features.map(feature => feature.backendService.split(',')[0].trim()))).sort(), [features]);
  const featureRows = features.filter(feature => {
    const roleMatches = !featureRoleFilter || rbac.some(cell => cell.featureId === feature.id && cell.role === featureRoleFilter && cell.allowed === true);
    const runMatches = !featureRunFilter || feature.lastRunStatus === featureRunFilter;
    return roleMatches && runMatches;
  });
  const uniqueRoles = Array.from(new Set(rbac.map(cell => cell.role))).sort();
  const featureTests = selectedFeature?.tests.filter(test =>
    (!layerFilter || test.layer === layerFilter) && (!permissionFilter || (test.permission || '').includes(permissionFilter))) || [];
  const filteredRbac = rbac.filter(cell => (!rbacRoleFilter || cell.role === rbacRoleFilter)
    && (!rbacPermissionFilter || cell.permission.includes(rbacPermissionFilter)));

  if (loading && !overview) return <Box sx={{ minHeight: 360, display: 'grid', placeItems: 'center' }}><CircularProgress /></Box>;

  return (
    <Box sx={{ pb: 5 }}>
      <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" alignItems={{ xs: 'stretch', md: 'center' }} gap={2} sx={{ mb: 3 }}>
        <Box>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.75 }}>
            <Chip size="small" label="UCP QUALITY CENTER" sx={{ bgcolor: '#EDE9FE', color: '#5B21B6', fontWeight: 800 }} />
            <Typography variant="body2" color="text.secondary">Test inventory · execution · access control</Typography>
          </Stack>
          <Typography variant="h4" sx={{ fontWeight: 850, color: '#101828', letterSpacing: '-.03em' }}>Quality Center</Typography>
          <Typography color="text.secondary" sx={{ mt: 0.5 }}>Run trusted local suites and inspect persisted evidence from one place.</Typography>
        </Box>
        <Stack direction="row" gap={1} flexWrap="wrap">
          <Button variant="outlined" startIcon={<RefreshRounded />} onClick={() => void refresh()} disabled={loading}>Refresh</Button>
          <Button variant="contained" color="primary" startIcon={working ? <CircularProgress size={18} color="inherit" /> : <PlayArrowRounded />}
            onClick={() => void startRun({ suiteId: 'all' }, 'Run All Tests')} disabled={working || hasActiveRun} sx={{ fontWeight: 800, px: 2.5 }}>
            Run All Tests
          </Button>
        </Stack>
      </Stack>

      <Paper variant="outlined" sx={{ px: 1, mb: 3, borderRadius: 3, borderColor: '#E4E7EC' }}>
        <Tabs value={tab} onChange={(_, value) => setTab(value)} variant="scrollable" scrollButtons="auto" aria-label="Quality Center sections">
          <Tab label="Overview" />
          <Tab label="Feature catalog" />
          <Tab label="Runs & results" />
          <Tab label="RBAC matrix" />
          <Tab label="Coverage explorer" />
        </Tabs>
      </Paper>

      {error && <Alert severity="error" onClose={() => setError('')} sx={{ mb: 2 }}>{error}</Alert>}

      {tab === 0 && <OverviewTab overview={overview} suites={suites} runs={runs} working={working || hasActiveRun}
        onRun={suite => void startRun({ suiteId: suite.id }, suite.name)} onSelectRun={id => { setSelectedRunId(id); setTab(2); }} />}

      {tab === 1 && <Box>
        <Card variant="outlined" sx={{ borderRadius: 3, mb: 2 }}>
          <CardContent>
            <Stack direction={{ xs: 'column', lg: 'row' }} alignItems={{ xs: 'stretch', lg: 'center' }} gap={1.5}>
              <TextField size="small" label="Search features" value={search} onChange={event => setSearch(event.target.value)} sx={{ minWidth: 240 }} />
              <FormControl size="small" sx={{ minWidth: 180 }}><InputLabel>Service</InputLabel><Select label="Service" value={serviceFilter} onChange={event => setServiceFilter(event.target.value)}>
                <MenuItem value="">All services</MenuItem>{uniqueServices.map(service => <MenuItem value={service} key={service}>{service}</MenuItem>)}
              </Select></FormControl>
              <FormControl size="small" sx={{ minWidth: 175 }}><InputLabel>Coverage</InputLabel><Select label="Coverage" value={coverageFilter} onChange={event => setCoverageFilter(event.target.value)}>
                <MenuItem value="">All coverage</MenuItem><MenuItem value="PARTIAL">Partial</MenuItem><MenuItem value="NOT_COVERED">Not covered</MenuItem>
              </Select></FormControl>
              <FormControl size="small" sx={{ minWidth: 160 }}><InputLabel>Allowed role</InputLabel><Select label="Allowed role" value={featureRoleFilter} onChange={event => setFeatureRoleFilter(event.target.value)}>
                <MenuItem value="">All roles</MenuItem>{uniqueRoles.map(role => <MenuItem value={role} key={role}>{pretty(role)}</MenuItem>)}
              </Select></FormControl>
              <FormControl size="small" sx={{ minWidth: 145 }}><InputLabel>Last result</InputLabel><Select label="Last result" value={featureRunFilter} onChange={event => setFeatureRunFilter(event.target.value)}>
                <MenuItem value="">Any result</MenuItem>{['NOT_RUN', 'PASSED', 'FAILED', 'BLOCKED'].map(status => <MenuItem value={status} key={status}>{pretty(status)}</MenuItem>)}
              </Select></FormControl>
              <Typography variant="body2" color="text.secondary" sx={{ ml: { lg: 'auto' } }}>{featureRows.length} catalog entries</Typography>
            </Stack>
          </CardContent>
        </Card>
        <Card variant="outlined" sx={{ borderRadius: 3 }}>
          <TableContainer sx={{ maxHeight: 520 }}><Table stickyHeader size="small">
            <TableHead><TableRow><TableCell>Feature</TableCell><TableCell>Domain</TableCell><TableCell>Frontend</TableCell><TableCell>Backend</TableCell><TableCell align="right">Tests</TableCell><TableCell>Coverage</TableCell><TableCell>Last run</TableCell><TableCell align="right">Action</TableCell></TableRow></TableHead>
            <TableBody>{featureRows.map(feature => <TableRow hover key={feature.id} selected={selectedFeature?.id === feature.id}>
              <TableCell><Button onClick={() => void chooseFeature(feature.id)} sx={{ px: 0, textTransform: 'none', fontWeight: 700 }}>{feature.name}</Button></TableCell>
              <TableCell>{feature.domain}</TableCell><TableCell>{feature.frontendModule}</TableCell><TableCell>{feature.backendService}</TableCell>
              <TableCell align="right">{feature.automatedTestCount}</TableCell>
              <TableCell><Chip size="small" label={pretty(feature.coverageStatus)} color={feature.coverageStatus === 'NOT_COVERED' ? 'warning' : 'info'} variant="outlined" /></TableCell>
              <TableCell><Chip size="small" label={pretty(feature.lastRunStatus)} color={statusColor(feature.lastRunStatus)} /></TableCell>
              <TableCell align="right"><Button size="small" startIcon={<PlayArrowRounded />} disabled={!feature.automatedTestCount || working || hasActiveRun} onClick={() => void startRun({ featureId: feature.id }, feature.name)}>Run</Button></TableCell>
            </TableRow>)}
              {!featureRows.length && <TableRow><TableCell colSpan={8} align="center" sx={{ py: 5, color: 'text.secondary' }}>No catalog entries match these filters.</TableCell></TableRow>}
            </TableBody>
          </Table></TableContainer>
        </Card>
        {selectedFeature && <Card variant="outlined" sx={{ mt: 2.5, borderRadius: 3 }}>
          <CardContent>
            <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" alignItems={{ xs: 'stretch', md: 'flex-start' }} gap={2}>
              <Box><Typography variant="h6" fontWeight={800}>{selectedFeature.name}</Typography><Typography color="text.secondary" variant="body2" sx={{ mt: .5 }}>{selectedFeature.coverageNote}</Typography></Box>
              <Button variant="contained" startIcon={<PlayArrowRounded />} disabled={!selectedFeature.automatedTestCount || working || hasActiveRun}
                onClick={() => void startRun({ featureId: selectedFeature.id }, selectedFeature.name)}>Run selected feature</Button>
            </Stack>
            <Stack direction="row" gap={1} flexWrap="wrap" sx={{ mt: 2 }}>
              {selectedFeature.endpoints.map(endpoint => <Chip key={endpoint} size="small" variant="outlined" label={endpoint} />)}
              {selectedFeature.permissions.map(permission => <Chip key={permission} size="small" color="secondary" label={permission} />)}
            </Stack>
            <Divider sx={{ my: 2 }} />
            <Stack direction={{ xs: 'column', sm: 'row' }} gap={1} sx={{ mb: 1.5 }}>
              <FormControl size="small" sx={{ minWidth: 170 }}><InputLabel>Test layer</InputLabel><Select label="Test layer" value={layerFilter} onChange={event => setLayerFilter(event.target.value)}>
                <MenuItem value="">All layers</MenuItem>{Array.from(new Set(selectedFeature.tests.map(test => test.layer))).sort().map(layer => <MenuItem value={layer} key={layer}>{pretty(layer)}</MenuItem>)}
              </Select></FormControl>
              <TextField size="small" label="Permission" value={permissionFilter} onChange={event => setPermissionFilter(event.target.value)} placeholder="e.g. accounting:read" />
            </Stack>
            <TableContainer><Table size="small"><TableHead><TableRow><TableCell>Registered test</TableCell><TableCell>Class / file</TableCell><TableCell>Layer</TableCell><TableCell>Permission</TableCell><TableCell align="right">Cases</TableCell></TableRow></TableHead>
              <TableBody>{featureTests.map(test => <TableRow key={test.testId}><TableCell>{test.name}</TableCell><TableCell>{test.testClass}</TableCell><TableCell>{pretty(test.layer)}</TableCell><TableCell>{test.permission || '—'}</TableCell><TableCell align="right">{test.caseCount}</TableCell></TableRow>)}
                {!featureTests.length && <TableRow><TableCell colSpan={5} align="center">No registered test mapping matches these filters.</TableCell></TableRow>}
              </TableBody></Table></TableContainer>
          </CardContent>
        </Card>}
      </Box>}

      {tab === 2 && <RunResultsTab runs={runs} selectedRun={selectedRun} selectedRunId={selectedRunId} results={results} log={runLog}
        statusFilter={runStatusFilter} onStatusFilter={setRunStatusFilter} onSelectRun={setSelectedRunId}
        onCancel={() => void cancelRun()} onRetry={() => void rerunFailures()} onReport={format => void downloadReport(format)} working={working} />}

      {tab === 3 && <RbacTab cells={filteredRbac} roles={Array.from(new Set(rbac.map(cell => cell.role))).sort()} roleFilter={rbacRoleFilter}
        permissionFilter={rbacPermissionFilter} onRole={setRbacRoleFilter} onPermission={setRbacPermissionFilter} />}

      {tab === 4 && <CoverageTab coverage={coverage} />}

      <Snackbar open={Boolean(notice)} autoHideDuration={4500} onClose={() => setNotice('')} anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}>
        <Alert severity="success" variant="filled" onClose={() => setNotice('')}>{notice}</Alert>
      </Snackbar>
    </Box>
  );
}

function OverviewTab({ overview, suites, runs, working, onRun, onSelectRun }: {
  overview: Overview | null; suites: Suite[]; runs: Run[]; working: boolean;
  onRun: (suite: Suite) => void; onSelectRun: (id: string) => void;
}) {
  if (!overview) return <Alert severity="info">The QA inventory is not available yet.</Alert>;
  const metrics = [
    { label: 'Discovered features', value: overview.totalFeatures, detail: `${overview.featuresWithTests} with tests · ${overview.featuresWithoutTests} without`, icon: <FactCheckRounded />, color: '#5B4BDB' },
    { label: 'Registered test cases', value: overview.registeredTestCases, detail: 'Catalog mappings; execution counts appear per run', icon: <TaskAltRounded />, color: '#087E70' },
    { label: 'Last run', value: pretty(overview.latestRunStatus), detail: `${overview.passed} passed · ${overview.failed} failed · ${overview.skipped} skipped`, icon: <AccessTimeRounded />, color: statusColor(overview.latestRunStatus) === 'success' ? '#087E70' : '#D97706' },
    { label: 'API inventory', value: overview.totalDiscoveredEndpoints, detail: 'Endpoint-specific test coverage is not measured', icon: <BugReportRounded />, color: '#2563EB' },
  ];
  return <Stack spacing={2.5}>
    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0,1fr))', xl: 'repeat(4, minmax(0,1fr))' }, gap: 2 }}>
      {metrics.map(metric => <Card key={metric.label} variant="outlined" sx={{ borderRadius: 3, borderColor: '#E4E7EC' }}><CardContent>
        <Stack direction="row" alignItems="center" justifyContent="space-between"><Typography color="text.secondary" variant="body2" fontWeight={700}>{metric.label}</Typography><Box sx={{ color: metric.color, display: 'flex' }}>{metric.icon}</Box></Stack>
        <Typography variant="h4" fontWeight={850} sx={{ mt: 1, color: '#101828' }}>{metric.value}</Typography><Typography variant="caption" color="text.secondary">{metric.detail}</Typography>
      </CardContent></Card>)}
    </Box>

    <Card variant="outlined" sx={{ borderRadius: 3 }}><CardContent>
      <Stack direction={{ xs: 'column', lg: 'row' }} justifyContent="space-between" alignItems={{ xs: 'stretch', lg: 'center' }} gap={2}>
        <Box><Typography variant="h6" fontWeight={800}>Approved test plans</Typography><Typography variant="body2" color="text.secondary" sx={{ mt: .5 }}>Runs use the local isolated environment. Browser E2E and smoke plans stay disabled until their suites exist.</Typography></Box>
        <Stack direction="row" gap={1} flexWrap="wrap">{suites.filter(suite => ['backend', 'frontend', 'rbac', 'integration', 'regression', 'e2e', 'smoke'].includes(suite.id)).map(suite =>
          <Tooltip key={suite.id} title={suite.executable ? suite.description : suite.description}>
            <span><Button size="small" variant={suite.id === 'rbac' ? 'outlined' : 'text'} disabled={!suite.executable || working}
              startIcon={suite.executable ? <PlayArrowRounded /> : <ErrorOutlineRounded />} onClick={() => onRun(suite)}>{suite.name}</Button></span>
          </Tooltip>)}</Stack>
      </Stack>
      <Divider sx={{ my: 2 }} />
      <Stack direction={{ xs: 'column', md: 'row' }} gap={2}>
        <InfoLine label="RBAC registered cases" value={`${overview.registeredRbacCases}`} detail={`Latest RBAC run: ${overview.lastRunRbacCases} result rows · ${pretty(overview.rbacCoverageStatus)}`} />
        <InfoLine label="Last successful full run" value={dateTime(overview.lastSuccessfulFullRunAt)} detail="Only persisted PASSED all/regression runs count." />
        <InfoLine label="Coverage instrumentation" value="Not collected" detail="Functional inventory, endpoint coverage, and code coverage are separate." />
      </Stack>
    </CardContent></Card>

    <Card variant="outlined" sx={{ borderRadius: 3 }}><CardContent sx={{ pb: 0 }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1.5 }}><Typography variant="h6" fontWeight={800}>Recent runs</Typography><Typography variant="caption" color="text.secondary">Persisted execution history</Typography></Stack>
      <TableContainer><Table size="small"><TableHead><TableRow><TableCell>Run</TableCell><TableCell>Scope</TableCell><TableCell>Status</TableCell><TableCell>Progress</TableCell><TableCell>Started</TableCell></TableRow></TableHead><TableBody>
        {runs.slice(0, 6).map(run => <TableRow hover key={run.id} onClick={() => onSelectRun(run.id)} sx={{ cursor: 'pointer' }}>
          <TableCell sx={{ fontFamily: 'monospace' }}>{run.id.slice(0, 8)}</TableCell><TableCell>{run.featureId || run.suiteId}</TableCell><TableCell><Chip size="small" color={statusColor(run.status)} label={pretty(run.status)} /></TableCell>
          <TableCell>{run.completedTests}/{run.totalTests} · {run.passedTests} passed, {run.failedTests} failed</TableCell><TableCell>{dateTime(run.startedAt || run.createdAt)}</TableCell>
        </TableRow>)}
        {!runs.length && <TableRow><TableCell colSpan={5} align="center" sx={{ py: 4, color: 'text.secondary' }}>No test executions have been recorded yet.</TableCell></TableRow>}
      </TableBody></Table></TableContainer>
    </CardContent></Card>
  </Stack>;
}

function InfoLine({ label, value, detail }: { label: string; value: string; detail: string }) {
  return <Box sx={{ flex: 1, minWidth: 180, p: 1.5, bgcolor: '#F8FAFC', borderRadius: 2 }}><Typography variant="caption" color="text.secondary" fontWeight={700}>{label}</Typography><Typography fontWeight={800} sx={{ mt: .5 }}>{value}</Typography><Typography variant="caption" color="text.secondary">{detail}</Typography></Box>;
}

function RunResultsTab({ runs, selectedRun, selectedRunId, results, log, statusFilter, onStatusFilter, onSelectRun, onCancel, onRetry, onReport, working }: {
  runs: Run[]; selectedRun: Run | null; selectedRunId: string | null; results: TestResult[]; log: string;
  statusFilter: string; onStatusFilter: (status: string) => void; onSelectRun: (id: string) => void;
  onCancel: () => void; onRetry: () => void; onReport: (format: 'json' | 'junit') => void; working: boolean;
}) {
  return <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: 'minmax(280px, .8fr) minmax(0, 2fr)' }, gap: 2 }}>
    <Card variant="outlined" sx={{ borderRadius: 3, alignSelf: 'start' }}><CardContent>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1.5 }}><Typography variant="h6" fontWeight={800}>Run history</Typography><FormControl size="small" sx={{ minWidth: 130 }}>
        <InputLabel>Status</InputLabel><Select label="Status" value={statusFilter} onChange={event => onStatusFilter(event.target.value)}><MenuItem value="">All</MenuItem>
          {['QUEUED', 'RUNNING', 'PASSED', 'FAILED', 'BLOCKED', 'CANCELLED'].map(status => <MenuItem key={status} value={status}>{pretty(status)}</MenuItem>)}
        </Select></FormControl></Stack>
      <Stack spacing={1}>{runs.map(run => <Paper key={run.id} variant="outlined" onClick={() => onSelectRun(run.id)} sx={{ p: 1.5, borderRadius: 2, cursor: 'pointer', borderColor: selectedRunId === run.id ? 'primary.main' : '#EAECF0', bgcolor: selectedRunId === run.id ? '#F8F7FF' : '#FFF' }}>
        <Stack direction="row" justifyContent="space-between" gap={1}><Typography fontWeight={800} variant="body2">{run.featureId || pretty(run.suiteId)}</Typography><Chip size="small" color={statusColor(run.status)} label={pretty(run.status)} /></Stack>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: .5 }}>{run.id.slice(0, 8)} · {dateTime(run.createdAt)}</Typography>
        <Typography variant="caption" color="text.secondary">{run.completedTests}/{run.totalTests} complete</Typography>
      </Paper>)}
        {!runs.length && <Typography variant="body2" color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>No runs match this filter.</Typography>}
      </Stack>
    </CardContent></Card>

    <Stack spacing={2}>
      {selectedRun ? <>
        <Card variant="outlined" sx={{ borderRadius: 3 }}><CardContent>
          <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" gap={2}>
            <Box><Stack direction="row" gap={1} alignItems="center"><Typography variant="h6" fontWeight={800}>Run {selectedRun.id.slice(0, 8)}</Typography><Chip size="small" color={statusColor(selectedRun.status)} label={pretty(selectedRun.status)} /></Stack>
              <Typography variant="body2" color="text.secondary" sx={{ mt: .5 }}>{selectedRun.featureId || pretty(selectedRun.suiteId)} · {selectedRun.environment} · {selectedRun.sourceVersion}</Typography>
            </Box>
            <Stack direction="row" gap={1} flexWrap="wrap">
              {activeStatuses.has(selectedRun.status) && <Button size="small" color="warning" variant="outlined" startIcon={<StopCircleOutlined />} disabled={working} onClick={onCancel}>Cancel run</Button>}
              {(selectedRun.failedTests > 0 || selectedRun.infrastructureFailures > 0) && <Button size="small" variant="outlined" disabled={working} onClick={onRetry}>Re-run failures</Button>}
              <Button size="small" startIcon={<DownloadRounded />} onClick={() => onReport('json')}>JSON</Button>
              <Button size="small" startIcon={<DownloadRounded />} onClick={() => onReport('junit')}>JUnit</Button>
            </Stack>
          </Stack>
          <Box sx={{ mt: 2, p: 1.5, borderRadius: 2, bgcolor: '#F8FAFC' }}>
            <Stack direction="row" justifyContent="space-between" alignItems="center"><Typography variant="body2" fontWeight={700}>{pretty(selectedRun.phase)} {selectedRun.currentTest ? `· ${selectedRun.currentTest}` : ''}</Typography><Typography variant="caption" color="text.secondary">{selectedRun.completedTests}/{selectedRun.totalTests} completed</Typography></Stack>
            <Box sx={{ height: 7, mt: 1, bgcolor: '#E4E7EC', borderRadius: 8, overflow: 'hidden' }}><Box sx={{ width: `${selectedRun.totalTests ? Math.min(100, selectedRun.completedTests / selectedRun.totalTests * 100) : 0}%`, height: '100%', bgcolor: selectedRun.failedTests ? '#D92D20' : '#5B4BDB', transition: 'width .3s' }} /></Box>
          </Box>
          <Stack direction="row" gap={1} flexWrap="wrap" sx={{ mt: 1.5 }}>
            <CountChip label="Passed" count={selectedRun.passedTests} color="success" /><CountChip label="Failed" count={selectedRun.failedTests} color="error" />
            <CountChip label="Skipped" count={selectedRun.skippedTests} color="default" /><CountChip label="Blocked" count={selectedRun.blockedTests} color="warning" />
            <CountChip label="Infrastructure" count={selectedRun.infrastructureFailures} color="warning" />
          </Stack>
          {selectedRun.errorMessage && <Alert severity={selectedRun.status === 'FAILED' ? 'error' : 'warning'} sx={{ mt: 1.5 }}><b>{pretty(selectedRun.errorType)}:</b> {selectedRun.errorMessage}</Alert>}
        </CardContent></Card>

        <Card variant="outlined" sx={{ borderRadius: 3 }}><CardContent sx={{ pb: 0 }}>
          <Typography variant="h6" fontWeight={800} sx={{ mb: 1 }}>Test results</Typography>
          <TableContainer sx={{ maxHeight: 430 }}><Table stickyHeader size="small"><TableHead><TableRow><TableCell>Test</TableCell><TableCell>Feature / service</TableCell><TableCell>Layer / permission</TableCell><TableCell>Status</TableCell><TableCell align="right">Duration</TableCell></TableRow></TableHead>
            <TableBody>{results.map(result => <TableRow key={`${result.id}-${result.testId}`}>
              <TableCell sx={{ maxWidth: 280 }}><Typography variant="body2" fontWeight={700}>{result.testName}</Typography><Typography variant="caption" color="text.secondary" sx={{ wordBreak: 'break-word' }}>{result.testId}</Typography>
                {(result.errorMessage || result.expected || result.actual) && <Box sx={{ mt: 1, p: 1, bgcolor: '#FFF7F6', borderRadius: 1.5 }}>
                  {result.expected && <Typography variant="caption" display="block"><b>Expected:</b> {result.expected}</Typography>}
                  {result.actual && <Typography variant="caption" display="block"><b>Actual:</b> {result.actual}</Typography>}
                  {result.errorMessage && <Typography variant="caption" display="block" color="error.main" sx={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word', mt: .5 }}>{result.errorMessage}</Typography>}
                </Box>}
              </TableCell>
              <TableCell>{result.featureId || '—'}<Typography variant="caption" display="block" color="text.secondary">{result.serviceName}</Typography></TableCell>
              <TableCell>{pretty(result.layer)}<Typography variant="caption" display="block" color="text.secondary">{result.permission || result.roleName || '—'}</Typography></TableCell>
              <TableCell><Chip size="small" label={pretty(result.status)} color={statusColor(result.status)} /></TableCell>
              <TableCell align="right">{result.durationMs} ms</TableCell>
            </TableRow>)}
              {!results.length && <TableRow><TableCell colSpan={5} align="center" sx={{ py: 4, color: 'text.secondary' }}>{activeStatuses.has(selectedRun.status) ? 'Waiting for test reports. Progress updates are saved as this run executes.' : 'No test cases were reported. Check the blocked/infrastructure result and sanitized log.'}</TableCell></TableRow>}
            </TableBody></Table></TableContainer>
        </CardContent></Card>
        <Card variant="outlined" sx={{ borderRadius: 3 }}><CardContent>
          <Typography variant="subtitle1" fontWeight={800}>Sanitized execution log</Typography>
          <Box component="pre" sx={{ mt: 1, mb: 0, p: 1.5, maxHeight: 280, overflow: 'auto', whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', bgcolor: '#101828', color: '#D0D5DD', borderRadius: 2, fontSize: 12, fontFamily: 'ui-monospace, SFMono-Regular, Consolas, monospace' }}>{log || 'No execution output has been persisted yet.'}</Box>
        </CardContent></Card>
      </> : <Card variant="outlined" sx={{ borderRadius: 3 }}><CardContent sx={{ p: 5, textAlign: 'center' }}><AccessTimeRounded color="disabled" sx={{ fontSize: 40 }} /><Typography variant="h6" fontWeight={800} sx={{ mt: 1 }}>No run selected</Typography><Typography color="text.secondary">Choose a saved execution or start a suite from the overview.</Typography></CardContent></Card>}
    </Stack>
  </Box>;
}

function CountChip({ label, count, color }: { label: string; count: number; color: 'success' | 'error' | 'warning' | 'default' }) {
  return <Chip size="small" variant="outlined" color={color} label={`${label}: ${count}`} />;
}

function RbacTab({ cells, roles, roleFilter, permissionFilter, onRole, onPermission }: {
  cells: RbacCell[]; roles: string[]; roleFilter: string; permissionFilter: string;
  onRole: (role: string) => void; onPermission: (permission: string) => void;
}) {
  return <Stack spacing={2}>
    <Alert severity="info">This matrix is the persisted default-role snapshot from user-service Flyway migrations. Custom runtime roles and unmapped service policies remain marked unresolved. A passing test means the registered authorization assertion passed, not that the whole feature is covered.</Alert>
    <Card variant="outlined" sx={{ borderRadius: 3 }}><CardContent>
      <Stack direction={{ xs: 'column', md: 'row' }} gap={1.5} sx={{ mb: 2 }}>
        <FormControl size="small" sx={{ minWidth: 180 }}><InputLabel>Role</InputLabel><Select label="Role" value={roleFilter} onChange={event => onRole(event.target.value)}><MenuItem value="">All roles</MenuItem>{roles.map(role => <MenuItem key={role} value={role}>{pretty(role)}</MenuItem>)}</Select></FormControl>
        <TextField size="small" label="Permission" value={permissionFilter} onChange={event => onPermission(event.target.value)} placeholder="e.g. qa:run" />
        <Typography variant="body2" color="text.secondary" sx={{ alignSelf: 'center', ml: { md: 'auto' } }}>{cells.length} role/feature permission rows</Typography>
      </Stack>
      <TableContainer sx={{ maxHeight: 620 }}><Table stickyHeader size="small"><TableHead><TableRow><TableCell>Role</TableCell><TableCell>Feature</TableCell><TableCell>Permission</TableCell><TableCell>Allowed by default</TableCell><TableCell>Latest test evidence</TableCell><TableCell>Endpoint family</TableCell></TableRow></TableHead>
        <TableBody>{cells.map((cell, index) => <TableRow key={`${cell.role}-${cell.featureId}-${cell.permission}-${index}`}>
          <TableCell>{pretty(cell.role)}</TableCell><TableCell>{cell.featureName}</TableCell><TableCell><Chip size="small" variant="outlined" label={cell.permission} /></TableCell>
          <TableCell>{cell.allowed === null ? <Chip size="small" color="warning" label="Unresolved" /> : <Chip size="small" color={cell.allowed ? 'success' : 'default'} label={cell.allowed ? 'Allow' : 'Deny'} />}</TableCell>
          <TableCell><Chip size="small" color={cell.testCoverage.startsWith('ASSERTION_PASSED') ? 'success' : 'default'} label={pretty(cell.testCoverage)} /></TableCell>
          <TableCell>{cell.endpoints.join(', ')}</TableCell>
        </TableRow>)}
          {!cells.length && <TableRow><TableCell colSpan={6} align="center" sx={{ py: 4 }}>No RBAC rows match these filters.</TableCell></TableRow>}
        </TableBody></Table></TableContainer>
    </CardContent></Card>
  </Stack>;
}

function CoverageTab({ coverage }: { coverage: Coverage | null }) {
  if (!coverage) return <Alert severity="info">Coverage inventory is not available yet.</Alert>;
  return <Stack spacing={2}>
    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(4,minmax(0,1fr))' }, gap: 2 }}>
      <CoverageMetric label="Registered test cases" value={coverage.registeredTestCases} detail="Feature inventory mappings" />
      <CoverageMetric label="Discovered API families" value={coverage.endpointsDiscovered} detail="Endpoint-specific execution coverage is not measured" />
      <CoverageMetric label="Code coverage" value="Not collected" detail="No coverage instrumentation is configured" />
      <CoverageMetric label="Browser E2E" value="Not registered" detail="No Playwright/browser suite exists yet" />
    </Box>
    <Card variant="outlined" sx={{ borderRadius: 3 }}><CardContent sx={{ pb: 0 }}>
      <Typography variant="h6" fontWeight={800} sx={{ mb: 1.5 }}>Feature coverage inventory</Typography>
      <TableContainer><Table size="small"><TableHead><TableRow><TableCell>Feature</TableCell><TableCell>Backend</TableCell><TableCell align="right">Tests</TableCell><TableCell>Functional status</TableCell><TableCell align="right">API families</TableCell><TableCell>Endpoint tests</TableCell><TableCell>RBAC mappings</TableCell></TableRow></TableHead>
        <TableBody>{coverage.features.map(feature => <TableRow key={feature.featureId}><TableCell>{feature.name}</TableCell><TableCell>{feature.backendService}</TableCell>
          <TableCell align="right">{feature.registeredTests}</TableCell><TableCell><Chip size="small" color={feature.registeredTests ? 'info' : 'warning'} label={pretty(feature.functionalCoverage)} /></TableCell>
          <TableCell align="right">{feature.discoveredEndpoints}</TableCell><TableCell><Chip size="small" label={pretty(feature.endpointCoverage)} /></TableCell>
          <TableCell>{feature.rbacTests} mapped · {pretty(feature.rbacCoverage)}</TableCell></TableRow>)}
        </TableBody></Table></TableContainer>
    </CardContent></Card>
  </Stack>;
}

function CoverageMetric({ label, value, detail }: { label: string; value: string | number; detail: string }) {
  return <Card variant="outlined" sx={{ borderRadius: 3 }}><CardContent><Typography variant="body2" color="text.secondary" fontWeight={700}>{label}</Typography><Typography variant="h5" fontWeight={850} sx={{ my: .75 }}>{value}</Typography><Typography variant="caption" color="text.secondary">{detail}</Typography></CardContent></Card>;
}
