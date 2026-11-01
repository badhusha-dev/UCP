import React, { useEffect, useState, useCallback } from 'react';
import {
  Typography,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  CircularProgress,
  Box,
  Container,
  Grid,
  Stack,
  Card,
  Chip,
  TextField,
  MenuItem,
  IconButton,
  Tooltip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Pagination,
  Alert,
  Divider,
  Tabs,
  Tab,
} from '@mui/material';
import {
  AdminPanelSettingsRounded,
  AddCircleOutlineRounded,
  HistoryRounded,
  UndoRounded,
  RefreshRounded,
  FilterListRounded,
  SearchRounded,
  CheckCircleRounded,
  RemoveCircleOutlineRounded,
  WarningAmberRounded,
  VisibilityOutlined,
  ReceiptLongRounded,
  DownloadRounded,
  DashboardRounded,
  GavelRounded,
  AssessmentRounded,
  FlagRounded,
  DoneAllRounded,
  InfoOutlined,
} from '@mui/icons-material';
import axios from 'axios';

axios.interceptors.request.use((config) => {
  if (config.url && !config.url.startsWith('http')) {
    const host = typeof window !== 'undefined' && window.location.hostname ? window.location.hostname : 'localhost';
    config.url = `http://${host}:8080${config.url.startsWith('/') ? '' : '/'}${config.url}`;
  }
  const token = typeof window !== 'undefined' ? localStorage.getItem('jwt_token') : null;
  if (token && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

interface AccountingDashboard {
  currency: string;
  grossSales: number;
  totalRefunds: number;
  netRecordedActivity: number;
  totalCredits: number;
  totalDebits: number;
  failedPaymentsCount: number;
  pendingTransactionsCount: number;
  pendingAmount: number;
  flaggedDiscrepanciesCount: number;
  unreconciledCount: number;
  disclaimer: string;
}

interface LedgerTransaction {
  id: number;
  userId: number;
  customerEmail?: string;
  transactionType: string;
  entryType: 'CREDIT' | 'DEBIT';
  amount: number;
  currency: string;
  status: 'POSTED' | 'REVERSED';
  description: string;
  referenceType: string;
  referenceId: string;
  reconciliationStatus?: 'RECONCILED' | 'PENDING_REVIEW' | 'FLAGGED_DISCREPANCY';
  reconciliationNotes?: string;
  paymentProvider?: string;
  providerReference?: string;
  idempotencyKey?: string;
  reversalOfId?: number;
  reason?: string;
  createdBy: string;
  createdAt: string;
}

interface AuditLog {
  id: number;
  transactionId: number;
  adminId: number;
  adminEmail: string;
  action: string;
  reason: string;
  beforeValue?: string;
  afterValue?: string;
  createdAt: string;
}

interface AdminRefundRequest {
  id: number;
  orderId: number;
  userId: number;
  amount: number;
  currency: string;
  reason: string;
  status: 'REQUESTED' | 'APPROVED' | 'REJECTED' | 'PROCESSED';
  decisionNotes?: string;
  reviewedBy?: string;
  reviewedAt?: string;
  createdAt: string;
}

const TYPE_CONFIG: Record<string, { label: string; color: 'default' | 'primary' | 'secondary' | 'error' | 'info' | 'success' | 'warning' }> = {
  ORDER_PAYMENT: { label: 'Order Payment', color: 'error' },
  ORDER_REFUND: { label: 'Order Refund', color: 'success' },
  MANUAL_CREDIT: { label: 'Manual Credit', color: 'primary' },
  MANUAL_DEBIT: { label: 'Manual Debit', color: 'warning' },
  CORRECTION: { label: 'Correction', color: 'info' },
};

export default function AdminLedgerPage() {
  const [currentTab, setCurrentTab] = useState<number>(0);

  // Status Alerts
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  // Tab 0: Dashboard State
  const [dashboard, setDashboard] = useState<AccountingDashboard | null>(null);
  const [loadingDashboard, setLoadingDashboard] = useState<boolean>(true);

  // Tab 1: Transactions State
  const [transactions, setTransactions] = useState<LedgerTransaction[]>([]);
  const [loadingTx, setLoadingTx] = useState<boolean>(true);
  const [pageTx, setPageTx] = useState<number>(0);
  const [totalPagesTx, setTotalPagesTx] = useState<number>(1);
  const [totalTxElements, setTotalTxElements] = useState<number>(0);

  // Filters
  const [filterUserId, setFilterUserId] = useState<string>('');
  const [filterEmail, setFilterEmail] = useState<string>('');
  const [filterRefId, setFilterRefId] = useState<string>('');
  const [filterType, setFilterType] = useState<string>('');
  const [filterStatus, setFilterStatus] = useState<string>('');
  const [filterReconciliation, setFilterReconciliation] = useState<string>('');
  const [startDate, setStartDate] = useState<string>('');
  const [endDate, setEndDate] = useState<string>('');

  // Tab 2: Refund Governance State
  const [refundRequests, setRefundRequests] = useState<AdminRefundRequest[]>([]);
  const [loadingRefunds, setLoadingRefunds] = useState<boolean>(false);
  const [filterRefundStatus, setFilterRefundStatus] = useState<string>('');
  const [processModalOpen, setProcessModalOpen] = useState<boolean>(false);
  const [targetRefund, setTargetRefund] = useState<AdminRefundRequest | null>(null);
  const [refundDecision, setRefundDecision] = useState<'APPROVED' | 'REJECTED'>('APPROVED');
  const [decisionNotes, setDecisionNotes] = useState<string>('');
  const [processingRefund, setProcessingRefund] = useState<boolean>(false);

  // Tab 3: Audit Logs State
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [loadingAudit, setLoadingAudit] = useState<boolean>(false);
  const [pageAudit, setPageAudit] = useState<number>(0);
  const [totalPagesAudit, setTotalPagesAudit] = useState<number>(1);
  const [totalAuditElements, setTotalAuditElements] = useState<number>(0);

  // Modals
  const [adjustmentOpen, setAdjustmentOpen] = useState<boolean>(false);
  const [adjustUserId, setAdjustUserId] = useState<string>('');
  const [adjustType, setAdjustType] = useState<string>('MANUAL_CREDIT');
  const [adjustAmount, setAdjustAmount] = useState<string>('');
  const [adjustCurrency, setAdjustCurrency] = useState<string>('USD');
  const [adjustRefType, setAdjustRefType] = useState<string>('SUPPORT_TICKET');
  const [adjustRefId, setAdjustRefId] = useState<string>('');
  const [adjustReason, setAdjustReason] = useState<string>('');
  const [adjustLoading, setAdjustLoading] = useState<boolean>(false);

  // Reversal Modal
  const [reversalOpen, setReversalOpen] = useState<boolean>(false);
  const [reversalTargetTx, setReversalTargetTx] = useState<LedgerTransaction | null>(null);
  const [reversalReason, setReversalReason] = useState<string>('');
  const [reversalLoading, setReversalLoading] = useState<boolean>(false);

  // Reconciliation Update Modal
  const [reconModalOpen, setReconModalOpen] = useState<boolean>(false);
  const [reconTargetTx, setReconTargetTx] = useState<LedgerTransaction | null>(null);
  const [reconStatus, setReconStatus] = useState<string>('RECONCILED');
  const [reconNotes, setReconNotes] = useState<string>('');
  const [reconLoading, setReconLoading] = useState<boolean>(false);

  // Detail Modal
  const [detailTx, setDetailTx] = useState<LedgerTransaction | null>(null);

  // Fetch Dashboard
  const fetchDashboard = useCallback(async () => {
    setLoadingDashboard(true);
    try {
      const res = await axios.get<AccountingDashboard>('/api/accounting/admin/dashboard');
      setDashboard(res.data);
    } catch (err: any) {
      console.error('Failed to load dashboard', err);
    } finally {
      setLoadingDashboard(false);
    }
  }, []);

  // Fetch Transactions
  const fetchTransactions = useCallback(async (pageIndex: number = 0) => {
    setLoadingTx(true);
    setActionError(null);
    try {
      const params: Record<string, any> = {
        page: pageIndex,
        size: 15,
        sortBy: 'createdAt',
        sortDirection: 'DESC',
      };
      if (filterUserId.trim()) params.userId = parseInt(filterUserId.trim(), 10);
      if (filterEmail.trim()) params.customerEmail = filterEmail.trim();
      if (filterRefId.trim()) params.referenceId = filterRefId.trim();
      if (filterType) params.type = filterType;
      if (filterStatus) params.status = filterStatus;
      if (filterReconciliation) params.reconciliationStatus = filterReconciliation;
      if (startDate) params.startDate = `${startDate}T00:00:00`;
      if (endDate) params.endDate = `${endDate}T23:59:59`;

      const res = await axios.get<{
        content: LedgerTransaction[];
        totalPages: number;
        totalElements: number;
        number: number;
      }>('/api/accounting/admin/ledger', { params });

      setTransactions(res.data.content || []);
      setTotalPagesTx(res.data.totalPages || 1);
      setTotalTxElements(res.data.totalElements || 0);
      setPageTx(res.data.number || 0);
    } catch (err: any) {
      setActionError(err.response?.data?.error || 'Failed to retrieve transactions.');
    } finally {
      setLoadingTx(false);
    }
  }, [filterUserId, filterEmail, filterRefId, filterType, filterStatus, filterReconciliation, startDate, endDate]);

  // Fetch Refund Requests
  const fetchRefundRequests = useCallback(async () => {
    setLoadingRefunds(true);
    try {
      const params: Record<string, any> = {};
      if (filterRefundStatus) params.status = filterRefundStatus;
      const res = await axios.get<AdminRefundRequest[]>('/api/accounting/admin/refunds', { params });
      setRefundRequests(res.data || []);
    } catch (err: any) {
      console.error('Failed to load refund requests', err);
    } finally {
      setLoadingRefunds(false);
    }
  }, [filterRefundStatus]);

  // Fetch Audit Logs
  const fetchAuditLogs = useCallback(async (pageIndex: number = 0) => {
    setLoadingAudit(true);
    try {
      const res = await axios.get<{
        content: AuditLog[];
        totalPages: number;
        totalElements: number;
        number: number;
      }>('/api/accounting/admin/audit-logs', {
        params: { page: pageIndex, size: 20 },
      });
      setAuditLogs(res.data.content || []);
      setTotalPagesAudit(res.data.totalPages || 1);
      setTotalAuditElements(res.data.totalElements || 0);
      setPageAudit(res.data.number || 0);
    } catch (err: any) {
      setActionError(err.response?.data?.error || 'Failed to retrieve audit log trail.');
    } finally {
      setLoadingAudit(false);
    }
  }, []);

  useEffect(() => {
    if (currentTab === 0) fetchDashboard();
    if (currentTab === 1) fetchTransactions(0);
    if (currentTab === 2) fetchRefundRequests();
    if (currentTab === 3) fetchAuditLogs(0);
  }, [currentTab, fetchDashboard, fetchTransactions, fetchRefundRequests, fetchAuditLogs]);

  // Handle Tab Switch
  const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
    setCurrentTab(newValue);
    setActionSuccess(null);
    setActionError(null);
  };

  // Reset Filters
  const handleResetFilters = () => {
    setFilterUserId('');
    setFilterEmail('');
    setFilterRefId('');
    setFilterType('');
    setFilterStatus('');
    setFilterReconciliation('');
    setStartDate('');
    setEndDate('');
  };

  // Manual Adjustment Submit
  const handleCreateAdjustment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!adjustUserId || !adjustAmount || !adjustReason) {
      setActionError('User ID, Amount, and Reason are mandatory.');
      return;
    }
    setAdjustLoading(true);
    setActionError(null);
    try {
      await axios.post('/api/accounting/admin/adjustments', {
        userId: parseInt(adjustUserId, 10),
        transactionType: adjustType,
        amount: parseFloat(adjustAmount),
        currency: adjustCurrency,
        referenceType: adjustRefType,
        referenceId: adjustRefId || `MANUAL-${Date.now()}`,
        reason: adjustReason,
      });
      setActionSuccess('Manual balance adjustment posted successfully.');
      setAdjustmentOpen(false);
      setAdjustUserId('');
      setAdjustAmount('');
      setAdjustReason('');
      setAdjustRefId('');
      fetchTransactions(pageTx);
      fetchDashboard();
    } catch (err: any) {
      setActionError(err.response?.data?.error || 'Failed to post adjustment.');
    } finally {
      setAdjustLoading(false);
    }
  };

  // Reversal Submit
  const handleCreateReversal = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reversalTargetTx || !reversalReason.trim()) {
      setActionError('Reversal reason is mandatory.');
      return;
    }
    setReversalLoading(true);
    setActionError(null);
    try {
      await axios.post(`/api/accounting/admin/reversals/${reversalTargetTx.id}`, {
        reason: reversalReason,
      });
      setActionSuccess(`Transaction #${reversalTargetTx.id} reversed with compensating entry.`);
      setReversalOpen(false);
      setReversalTargetTx(null);
      setReversalReason('');
      fetchTransactions(pageTx);
      fetchDashboard();
    } catch (err: any) {
      setActionError(err.response?.data?.error || 'Failed to execute reversal.');
    } finally {
      setReversalLoading(false);
    }
  };

  // Reconciliation Submit
  const handleUpdateReconciliation = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reconTargetTx) return;
    setReconLoading(true);
    setActionError(null);
    try {
      await axios.put(`/api/accounting/admin/reconciliation/${reconTargetTx.id}`, {
        status: reconStatus,
        notes: reconNotes,
      });
      setActionSuccess(`Reconciliation status updated for transaction #${reconTargetTx.id}.`);
      setReconModalOpen(false);
      setReconTargetTx(null);
      setReconNotes('');
      fetchTransactions(pageTx);
      fetchDashboard();
    } catch (err: any) {
      setActionError(err.response?.data?.error || 'Failed to update reconciliation status.');
    } finally {
      setReconLoading(false);
    }
  };

  // Process Refund Submit (Approve/Reject)
  const handleProcessRefund = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!targetRefund || !decisionNotes.trim()) {
      setActionError('Decision notes are mandatory.');
      return;
    }
    setProcessingRefund(true);
    setActionError(null);
    try {
      await axios.post(`/api/accounting/admin/refunds/${targetRefund.id}/process`, {
        approved: refundDecision === 'APPROVED',
        decisionNotes: decisionNotes,
      });
      setActionSuccess(
        `Refund #${targetRefund.id} ${refundDecision.toLowerCase()} successfully. Subledger updated.`
      );
      setProcessModalOpen(false);
      setTargetRefund(null);
      setDecisionNotes('');
      fetchRefundRequests();
      fetchDashboard();
    } catch (err: any) {
      setActionError(err.response?.data?.error || 'Failed to process refund request.');
    } finally {
      setProcessingRefund(false);
    }
  };

  // Export Master Ledger CSV
  const handleExportCsv = () => {
    const params = new URLSearchParams();
    if (filterUserId.trim()) params.append('userId', filterUserId.trim());
    if (filterEmail.trim()) params.append('customerEmail', filterEmail.trim());
    if (filterType) params.append('type', filterType);
    if (filterStatus) params.append('status', filterStatus);
    if (filterReconciliation) params.append('reconciliationStatus', filterReconciliation);
    if (startDate) params.append('startDate', `${startDate}T00:00:00`);
    if (endDate) params.append('endDate', `${endDate}T23:59:59`);
    const token = localStorage.getItem('jwt_token') || '';

    fetch(`/api/accounting/admin/export?${params.toString()}`, {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((res) => {
        if (!res.ok) throw new Error('Export failed');
        return res.blob();
      })
      .then((blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `master_accounting_ledger_${new Date().toISOString().split('T')[0]}.csv`;
        document.body.appendChild(a);
        a.click();
        a.remove();
        window.URL.revokeObjectURL(url);
        setActionSuccess('Master ledger CSV exported and audit logged.');
      })
      .catch(() => {
        setActionError('Failed to export master ledger CSV.');
      });
  };

  return (
    <Container maxWidth="xl" sx={{ py: 3 }}>
      {/* Alert Banners */}
      {actionSuccess && (
        <Alert severity="success" onClose={() => setActionSuccess(null)} sx={{ mb: 2, borderRadius: 2 }}>
          {actionSuccess}
        </Alert>
      )}
      {actionError && (
        <Alert severity="error" onClose={() => setActionError(null)} sx={{ mb: 2, borderRadius: 2 }}>
          {actionError}
        </Alert>
      )}

      {/* Header */}
      <Box sx={{ mb: 3, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 2 }}>
        <Box>
          <Stack direction="row" spacing={1.5} alignItems="center">
            <AdminPanelSettingsRounded sx={{ color: '#4F46E5', fontSize: 32 }} />
            <Typography variant="h4" sx={{ fontWeight: 800, color: '#0F172A', letterSpacing: '-0.02em' }}>
              Platform Accounting &amp; Ledger Governance
            </Typography>
          </Stack>
          <Typography variant="body2" sx={{ color: '#64748B', mt: 0.5 }}>
            Enterprise financial subledger oversight, discrepancy reconciliation, refund governance, and audit logs.
          </Typography>
        </Box>

        <Stack direction="row" spacing={1.5} flexWrap="wrap">
          <Button
            variant="outlined"
            startIcon={<DownloadRounded />}
            onClick={handleExportCsv}
            sx={{ borderRadius: 2, textTransform: 'none', fontWeight: 600 }}
          >
            Export Ledger (CSV)
          </Button>
          <Button
            variant="contained"
            color="primary"
            startIcon={<AddCircleOutlineRounded />}
            onClick={() => setAdjustmentOpen(true)}
            sx={{ borderRadius: 2, textTransform: 'none', fontWeight: 600 }}
          >
            Post Adjustment
          </Button>
        </Stack>
      </Box>

      {/* Navigation Tabs */}
      <Paper elevation={0} sx={{ borderBottom: 1, borderColor: 'divider', mb: 3, bgcolor: 'transparent' }}>
        <Tabs
          value={currentTab}
          onChange={handleTabChange}
          textColor="primary"
          indicatorColor="primary"
          sx={{ '& .MuiTab-root': { fontWeight: 700, textTransform: 'none', fontSize: '0.95rem' } }}
        >
          <Tab icon={<DashboardRounded sx={{ fontSize: 20 }} />} iconPosition="start" label="Executive Dashboard" />
          <Tab icon={<AssessmentRounded sx={{ fontSize: 20 }} />} iconPosition="start" label="Master Customer Ledger" />
          <Tab icon={<GavelRounded sx={{ fontSize: 20 }} />} iconPosition="start" label="Refund Governance" />
          <Tab icon={<HistoryRounded sx={{ fontSize: 20 }} />} iconPosition="start" label="Immutable Audit Trail" />
        </Tabs>
      </Paper>

      {/* TAB 0: EXECUTIVE DASHBOARD */}
      {currentTab === 0 && (
        <Box>
          {/* Statutory Disclaimer Box */}
          <Alert
            severity="info"
            icon={<InfoOutlined fontSize="medium" />}
            sx={{
              mb: 3,
              borderRadius: 2.5,
              border: '1px solid #BFDBFE',
              bgcolor: '#EFF6FF',
              color: '#1E3A8A',
            }}
          >
            <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
              PLATFORM SUBLEDGER TOTALS — NOT AUDITED FINANCIAL STATEMENTS
            </Typography>
            <Typography variant="body2" sx={{ mt: 0.5 }}>
              {dashboard?.disclaimer ||
                'Figures shown reflect internally recorded payment events and balance adjustments in the platform database. They do not constitute audited financial statements or tax filings.'}
            </Typography>
          </Alert>

          {loadingDashboard ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
              <CircularProgress />
            </Box>
          ) : dashboard ? (
            <Grid container spacing={2.5}>
              {/* Gross Sales */}
              <Grid item xs={12} sm={6} md={3}>
                <Card elevation={0} sx={{ p: 3, borderRadius: 3, border: '1px solid #E2E8F0', bgcolor: '#F8FAFC' }}>
                  <Typography variant="overline" sx={{ color: '#64748B', fontWeight: 700, letterSpacing: '0.08em' }}>
                    PLATFORM GROSS SALES
                  </Typography>
                  <Typography variant="h4" sx={{ fontWeight: 900, color: '#0F172A', my: 1 }}>
                    ${Number(dashboard.grossSales).toFixed(2)}
                    <Typography component="span" variant="subtitle2" sx={{ ml: 1, color: '#64748B' }}>
                      {dashboard.currency}
                    </Typography>
                  </Typography>
                  <Typography variant="caption" sx={{ color: '#64748B' }}>
                    Cumulative posted customer order debits
                  </Typography>
                </Card>
              </Grid>

              {/* Total Refunds */}
              <Grid item xs={12} sm={6} md={3}>
                <Card elevation={0} sx={{ p: 3, borderRadius: 3, border: '1px solid #E2E8F0', bgcolor: '#FEF2F2' }}>
                  <Typography variant="overline" sx={{ color: '#991B1B', fontWeight: 700, letterSpacing: '0.08em' }}>
                    TOTAL REFUNDS ISSUED
                  </Typography>
                  <Typography variant="h4" sx={{ fontWeight: 900, color: '#991B1B', my: 1 }}>
                    -${Number(dashboard.totalRefunds).toFixed(2)}
                  </Typography>
                  <Typography variant="caption" sx={{ color: '#B91C1C' }}>
                    Approved and settled customer refund credits
                  </Typography>
                </Card>
              </Grid>

              {/* Net Recorded Activity */}
              <Grid item xs={12} sm={6} md={3}>
                <Card
                  elevation={0}
                  sx={{
                    p: 3,
                    borderRadius: 3,
                    border: '1px solid #E2E8F0',
                    background: 'linear-gradient(135deg, #1E1B4B 0%, #312E81 100%)',
                    color: '#FFFFFF',
                  }}
                >
                  <Typography variant="overline" sx={{ color: '#A5B4FC', fontWeight: 700, letterSpacing: '0.08em' }}>
                    NET RECORDED ACTIVITY
                  </Typography>
                  <Typography variant="h4" sx={{ fontWeight: 900, my: 1 }}>
                    ${Number(dashboard.netRecordedActivity).toFixed(2)}
                  </Typography>
                  <Typography variant="caption" sx={{ color: '#C7D2FE' }}>
                    Total Credits minus Total Debits
                  </Typography>
                </Card>
              </Grid>

              {/* Store Credits Issued */}
              <Grid item xs={12} sm={6} md={3}>
                <Card elevation={0} sx={{ p: 3, borderRadius: 3, border: '1px solid #E2E8F0', bgcolor: '#F0FDF4' }}>
                  <Typography variant="overline" sx={{ color: '#166534', fontWeight: 700, letterSpacing: '0.08em' }}>
                    STORE CREDITS ISSUED
                  </Typography>
                  <Typography variant="h4" sx={{ fontWeight: 900, color: '#166534', my: 1 }}>
                    +${Number(dashboard.totalCredits).toFixed(2)}
                  </Typography>
                  <Typography variant="caption" sx={{ color: '#15803D' }}>
                    Manual adjustments &amp; goodwill balances
                  </Typography>
                </Card>
              </Grid>

              {/* Discrepancies & Reconciliation */}
              <Grid item xs={12} sm={6} md={4}>
                <Card elevation={0} sx={{ p: 3, borderRadius: 3, border: '1px solid #FCD34D', bgcolor: '#FFFBEB' }}>
                  <Stack direction="row" justifyContent="space-between" alignItems="center">
                    <Typography variant="overline" sx={{ color: '#92400E', fontWeight: 700 }}>
                      FLAGGED DISCREPANCIES
                    </Typography>
                    <FlagRounded sx={{ color: '#D97706' }} />
                  </Stack>
                  <Typography variant="h4" sx={{ fontWeight: 900, color: '#92400E', my: 1 }}>
                    {dashboard.flaggedDiscrepanciesCount} Entries
                  </Typography>
                  <Typography variant="caption" sx={{ color: '#B45309' }}>
                    Transactions marked for audit investigation
                  </Typography>
                </Card>
              </Grid>

              {/* Pending Transactions */}
              <Grid item xs={12} sm={6} md={4}>
                <Card elevation={0} sx={{ p: 3, borderRadius: 3, border: '1px solid #E2E8F0', bgcolor: '#FFFFFF' }}>
                  <Typography variant="overline" sx={{ color: '#64748B', fontWeight: 700 }}>
                    PENDING SETTLEMENTS
                  </Typography>
                  <Typography variant="h4" sx={{ fontWeight: 900, color: '#0F172A', my: 1 }}>
                    {dashboard.pendingTransactionsCount} Pending
                    <Typography component="span" variant="subtitle2" sx={{ ml: 1, color: '#64748B' }}>
                      (${Number(dashboard.pendingAmount).toFixed(2)})
                    </Typography>
                  </Typography>
                  <Typography variant="caption" sx={{ color: '#64748B' }}>
                    Payment intents awaiting gateway webhook confirmation
                  </Typography>
                </Card>
              </Grid>

              {/* Unreconciled Count */}
              <Grid item xs={12} sm={6} md={4}>
                <Card elevation={0} sx={{ p: 3, borderRadius: 3, border: '1px solid #E2E8F0', bgcolor: '#FFFFFF' }}>
                  <Typography variant="overline" sx={{ color: '#64748B', fontWeight: 700 }}>
                    UNRECONCILED RECORDS
                  </Typography>
                  <Typography variant="h4" sx={{ fontWeight: 900, color: '#0F172A', my: 1 }}>
                    {dashboard.unreconciledCount} Records
                  </Typography>
                  <Typography variant="caption" sx={{ color: '#64748B' }}>
                    Awaiting monthly subledger sign-off
                  </Typography>
                </Card>
              </Grid>
            </Grid>
          ) : null}
        </Box>
      )}

      {/* TAB 1: MASTER CUSTOMER LEDGER */}
      {currentTab === 1 && (
        <Box>
          {/* Search & Filter Toolbar */}
          <Paper elevation={0} sx={{ p: 2.5, mb: 3, borderRadius: 3, border: '1px solid #E2E8F0', bgcolor: '#FFFFFF' }}>
            <Grid container spacing={2} alignItems="center">
              <Grid item xs={12} sm={6} md={2}>
                <TextField
                  fullWidth
                  size="small"
                  label="Customer ID"
                  placeholder="e.g. 1"
                  value={filterUserId}
                  onChange={(e) => setFilterUserId(e.target.value)}
                />
              </Grid>

              <Grid item xs={12} sm={6} md={2}>
                <TextField
                  fullWidth
                  size="small"
                  label="Customer Email"
                  placeholder="e.g. user@example.com"
                  value={filterEmail}
                  onChange={(e) => setFilterEmail(e.target.value)}
                />
              </Grid>

              <Grid item xs={12} sm={6} md={1.5}>
                <TextField
                  fullWidth
                  size="small"
                  label="Ref / Order ID"
                  placeholder="e.g. 101"
                  value={filterRefId}
                  onChange={(e) => setFilterRefId(e.target.value)}
                />
              </Grid>

              <Grid item xs={12} sm={6} md={2}>
                <TextField
                  select
                  fullWidth
                  size="small"
                  label="Type"
                  value={filterType}
                  onChange={(e) => setFilterType(e.target.value)}
                >
                  <MenuItem value="">All Types</MenuItem>
                  <MenuItem value="ORDER_PAYMENT">Order Payment</MenuItem>
                  <MenuItem value="ORDER_REFUND">Order Refund</MenuItem>
                  <MenuItem value="MANUAL_CREDIT">Manual Credit</MenuItem>
                  <MenuItem value="MANUAL_DEBIT">Manual Debit</MenuItem>
                  <MenuItem value="CORRECTION">Correction</MenuItem>
                </TextField>
              </Grid>

              <Grid item xs={12} sm={6} md={1.5}>
                <TextField
                  select
                  fullWidth
                  size="small"
                  label="Status"
                  value={filterStatus}
                  onChange={(e) => setFilterStatus(e.target.value)}
                >
                  <MenuItem value="">All</MenuItem>
                  <MenuItem value="POSTED">POSTED</MenuItem>
                  <MenuItem value="REVERSED">REVERSED</MenuItem>
                </TextField>
              </Grid>

              <Grid item xs={12} sm={6} md={1.5}>
                <TextField
                  select
                  fullWidth
                  size="small"
                  label="Recon Status"
                  value={filterReconciliation}
                  onChange={(e) => setFilterReconciliation(e.target.value)}
                >
                  <MenuItem value="">All</MenuItem>
                  <MenuItem value="RECONCILED">Reconciled</MenuItem>
                  <MenuItem value="PENDING_REVIEW">Pending Review</MenuItem>
                  <MenuItem value="FLAGGED_DISCREPANCY">Discrepancy</MenuItem>
                </TextField>
              </Grid>

              <Grid item xs={12} md={1.5}>
                <Button
                  fullWidth
                  variant="text"
                  startIcon={<FilterListRounded />}
                  onClick={handleResetFilters}
                  sx={{ textTransform: 'none', color: '#64748B', fontWeight: 600 }}
                >
                  Reset
                </Button>
              </Grid>
            </Grid>
          </Paper>

          {/* Ledger Table */}
          <Paper elevation={0} sx={{ borderRadius: 3, border: '1px solid #E2E8F0', overflow: 'hidden' }}>
            <TableContainer>
              <Table sx={{ minWidth: 950 }}>
                <TableHead sx={{ bgcolor: '#F8FAFC' }}>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 700, color: '#475569' }}>Tx ID</TableCell>
                    <TableCell sx={{ fontWeight: 700, color: '#475569' }}>Customer</TableCell>
                    <TableCell sx={{ fontWeight: 700, color: '#475569' }}>Date &amp; Time</TableCell>
                    <TableCell sx={{ fontWeight: 700, color: '#475569' }}>Reference</TableCell>
                    <TableCell sx={{ fontWeight: 700, color: '#475569' }}>Type</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700, color: '#475569' }}>Amount</TableCell>
                    <TableCell sx={{ fontWeight: 700, color: '#475569' }}>Status</TableCell>
                    <TableCell sx={{ fontWeight: 700, color: '#475569' }}>Reconciliation</TableCell>
                    <TableCell align="center" sx={{ fontWeight: 700, color: '#475569' }}>Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {loadingTx ? (
                    <TableRow>
                      <TableCell colSpan={9} align="center" sx={{ py: 6 }}>
                        <CircularProgress size={32} />
                        <Typography variant="body2" sx={{ color: '#64748B', mt: 1 }}>
                          Querying master immutable ledger...
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ) : transactions.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={9} align="center" sx={{ py: 6 }}>
                        <ReceiptLongRounded sx={{ fontSize: 44, color: '#CBD5E1', mb: 1 }} />
                        <Typography variant="subtitle1" sx={{ fontWeight: 700, color: '#475569' }}>
                          No transactions found
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ) : (
                    transactions.map((tx) => {
                      const isCredit = tx.entryType === 'CREDIT';
                      const typeMeta = TYPE_CONFIG[tx.transactionType] || { label: tx.transactionType, color: 'default' };

                      return (
                        <TableRow key={tx.id} hover>
                          <TableCell sx={{ fontWeight: 700, color: '#4F46E5' }}>#{tx.id}</TableCell>
                          <TableCell>
                            <Typography variant="body2" sx={{ fontWeight: 600 }}>
                              User #{tx.userId}
                            </Typography>
                            {tx.customerEmail && (
                              <Typography variant="caption" sx={{ color: '#64748B', display: 'block' }}>
                                {tx.customerEmail}
                              </Typography>
                            )}
                          </TableCell>
                          <TableCell sx={{ whiteSpace: 'nowrap', fontSize: '0.85rem' }}>
                            {new Date(tx.createdAt).toLocaleDateString()}{' '}
                            <Typography component="span" variant="caption" sx={{ color: '#94A3B8', display: 'block' }}>
                              {new Date(tx.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <Chip
                              label={`${tx.referenceType}: ${tx.referenceId}`}
                              size="small"
                              variant="outlined"
                              sx={{ fontSize: '0.75rem', fontWeight: 600 }}
                            />
                          </TableCell>
                          <TableCell>
                            <Chip label={typeMeta.label} size="small" color={typeMeta.color} sx={{ fontWeight: 600, fontSize: '0.75rem' }} />
                          </TableCell>
                          <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                            <Typography
                              variant="body2"
                              sx={{
                                fontWeight: 800,
                                fontFamily: 'monospace',
                                color: tx.status === 'REVERSED' ? '#94A3B8' : isCredit ? '#16A34A' : '#DC2626',
                                textDecoration: tx.status === 'REVERSED' ? 'line-through' : 'none',
                              }}
                            >
                              {isCredit ? '+' : '-'}${Number(tx.amount).toFixed(2)} {tx.currency}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            {tx.status === 'POSTED' ? (
                              <Chip label="POSTED" size="small" sx={{ bgcolor: '#ECFDF5', color: '#059669', fontWeight: 700, fontSize: '0.7rem' }} />
                            ) : (
                              <Chip label="REVERSED" size="small" sx={{ bgcolor: '#F1F5F9', color: '#64748B', fontWeight: 700, fontSize: '0.7rem' }} />
                            )}
                          </TableCell>
                          <TableCell>
                            <Chip
                              label={tx.reconciliationStatus || 'PENDING_REVIEW'}
                              size="small"
                              color={
                                tx.reconciliationStatus === 'RECONCILED'
                                  ? 'success'
                                  : tx.reconciliationStatus === 'FLAGGED_DISCREPANCY'
                                  ? 'error'
                                  : 'warning'
                              }
                              sx={{ fontWeight: 700, fontSize: '0.68rem' }}
                            />
                          </TableCell>
                          <TableCell align="center">
                            <Stack direction="row" spacing={0.5} justifyContent="center">
                              <Tooltip title="View Transaction Audit Snapshot">
                                <IconButton size="small" onClick={() => setDetailTx(tx)} sx={{ color: '#4F46E5' }}>
                                  <VisibilityOutlined fontSize="small" />
                                </IconButton>
                              </Tooltip>
                              <Tooltip title="Update Reconciliation / Discrepancy Note">
                                <IconButton
                                  size="small"
                                  onClick={() => {
                                    setReconTargetTx(tx);
                                    setReconStatus(tx.reconciliationStatus || 'RECONCILED');
                                    setReconNotes(tx.reconciliationNotes || '');
                                    setReconModalOpen(true);
                                  }}
                                  sx={{ color: '#D97706' }}
                                >
                                  <FlagRounded fontSize="small" />
                                </IconButton>
                              </Tooltip>
                              {tx.status === 'POSTED' && (
                                <Tooltip title="Reverse Transaction (Compensating Entry)">
                                  <IconButton
                                    size="small"
                                    onClick={() => {
                                      setReversalTargetTx(tx);
                                      setReversalReason('');
                                      setReversalOpen(true);
                                    }}
                                    sx={{ color: '#DC2626' }}
                                  >
                                    <UndoRounded fontSize="small" />
                                  </IconButton>
                                </Tooltip>
                              )}
                            </Stack>
                          </TableCell>
                        </TableRow>
                      );
                    })
                  )}
                </TableBody>
              </Table>
            </TableContainer>

            {/* Pagination */}
            <Box sx={{ p: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderTop: '1px solid #E2E8F0', flexWrap: 'wrap', gap: 2 }}>
              <Typography variant="caption" sx={{ color: '#64748B' }}>
                Showing {transactions.length} of {totalTxElements} total ledger records
              </Typography>
              <Pagination
                count={totalPagesTx}
                page={pageTx + 1}
                onChange={(_e, v) => fetchTransactions(v - 1)}
                color="primary"
                shape="rounded"
                size="small"
              />
            </Box>
          </Paper>
        </Box>
      )}

      {/* TAB 2: REFUND GOVERNANCE */}
      {currentTab === 2 && (
        <Box>
          <Box sx={{ mb: 3, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 2 }}>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 800 }}>
                Customer Refund Requests &amp; Review Workflow
              </Typography>
              <Typography variant="body2" sx={{ color: '#64748B' }}>
                Review customer return claims, inspect reasons, and execute approved compensations to the ledger.
              </Typography>
            </Box>
            <TextField
              select
              size="small"
              label="Status Filter"
              value={filterRefundStatus}
              onChange={(e) => setFilterRefundStatus(e.target.value)}
              sx={{ minWidth: 160 }}
            >
              <MenuItem value="">All Statuses</MenuItem>
              <MenuItem value="REQUESTED">REQUESTED</MenuItem>
              <MenuItem value="APPROVED">APPROVED</MenuItem>
              <MenuItem value="REJECTED">REJECTED</MenuItem>
              <MenuItem value="PROCESSED">PROCESSED</MenuItem>
            </TextField>
          </Box>

          <Paper elevation={0} sx={{ borderRadius: 3, border: '1px solid #E2E8F0', overflow: 'hidden' }}>
            <TableContainer>
              <Table sx={{ minWidth: 850 }}>
                <TableHead sx={{ bgcolor: '#F8FAFC' }}>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 700 }}>Request ID</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>Order ID</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>Customer ID</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>Amount</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>Customer Reason</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>Status</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>Staff Decision &amp; Notes</TableCell>
                    <TableCell align="center" sx={{ fontWeight: 700 }}>Action</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {loadingRefunds ? (
                    <TableRow>
                      <TableCell colSpan={8} align="center" sx={{ py: 6 }}>
                        <CircularProgress size={32} />
                      </TableCell>
                    </TableRow>
                  ) : refundRequests.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={8} align="center" sx={{ py: 6 }}>
                        <Typography variant="body2" sx={{ color: '#64748B' }}>
                          No refund requests found.
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ) : (
                    refundRequests.map((req) => (
                      <TableRow key={req.id} hover>
                        <TableCell sx={{ fontWeight: 700 }}>#{req.id}</TableCell>
                        <TableCell sx={{ fontWeight: 600 }}>Order #{req.orderId}</TableCell>
                        <TableCell>User #{req.userId}</TableCell>
                        <TableCell sx={{ fontWeight: 800, color: '#166534' }}>
                          +${Number(req.amount).toFixed(2)} {req.currency}
                        </TableCell>
                        <TableCell sx={{ maxWidth: 220 }}>{req.reason}</TableCell>
                        <TableCell>
                          <Chip
                            label={req.status}
                            size="small"
                            color={
                              req.status === 'PROCESSED' || req.status === 'APPROVED'
                                ? 'success'
                                : req.status === 'REJECTED'
                                ? 'error'
                                : 'warning'
                            }
                            sx={{ fontWeight: 700, fontSize: '0.7rem' }}
                          />
                        </TableCell>
                        <TableCell sx={{ color: '#64748B', fontSize: '0.82rem', maxWidth: 200 }}>
                          {req.decisionNotes ? (
                            <>
                              <div>{req.decisionNotes}</div>
                              <Typography variant="caption" sx={{ color: '#94A3B8' }}>
                                By {req.reviewedBy} at {req.reviewedAt ? new Date(req.reviewedAt).toLocaleDateString() : ''}
                              </Typography>
                            </>
                          ) : (
                            'Pending Review'
                          )}
                        </TableCell>
                        <TableCell align="center">
                          {req.status === 'REQUESTED' ? (
                            <Button
                              variant="outlined"
                              size="small"
                              onClick={() => {
                                setTargetRefund(req);
                                setRefundDecision('APPROVED');
                                setDecisionNotes('');
                                setProcessModalOpen(true);
                              }}
                              sx={{ textTransform: 'none', fontWeight: 600 }}
                            >
                              Review &amp; Decide
                            </Button>
                          ) : (
                            <Typography variant="caption" sx={{ color: '#94A3B8', fontWeight: 600 }}>
                              DECIDED
                            </Typography>
                          )}
                        </TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          </Paper>
        </Box>
      )}

      {/* TAB 3: IMMUTABLE AUDIT TRAIL */}
      {currentTab === 3 && (
        <Paper elevation={0} sx={{ borderRadius: 3, border: '1px solid #E2E8F0', overflow: 'hidden' }}>
          <TableContainer>
            <Table sx={{ minWidth: 800 }}>
              <TableHead sx={{ bgcolor: '#F8FAFC' }}>
                <TableRow>
                  <TableCell sx={{ fontWeight: 700, color: '#475569' }}>Audit ID</TableCell>
                  <TableCell sx={{ fontWeight: 700, color: '#475569' }}>Timestamp</TableCell>
                  <TableCell sx={{ fontWeight: 700, color: '#475569' }}>Admin User</TableCell>
                  <TableCell sx={{ fontWeight: 700, color: '#475569' }}>Action Performed</TableCell>
                  <TableCell sx={{ fontWeight: 700, color: '#475569' }}>Target Tx</TableCell>
                  <TableCell sx={{ fontWeight: 700, color: '#475569' }}>Mandatory Reason</TableCell>
                  <TableCell sx={{ fontWeight: 700, color: '#475569' }}>State Snapshot</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {loadingAudit ? (
                  <TableRow>
                    <TableCell colSpan={7} align="center" sx={{ py: 6 }}>
                      <CircularProgress size={32} />
                    </TableCell>
                  </TableRow>
                ) : auditLogs.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={7} align="center" sx={{ py: 6 }}>
                      <Typography variant="body2" sx={{ color: '#64748B' }}>
                        No audit events recorded yet.
                      </Typography>
                    </TableCell>
                  </TableRow>
                ) : (
                  auditLogs.map((log) => (
                    <TableRow key={log.id} hover>
                      <TableCell sx={{ fontWeight: 700, color: '#4F46E5' }}>#{log.id}</TableCell>
                      <TableCell sx={{ whiteSpace: 'nowrap', fontSize: '0.85rem' }}>
                        {new Date(log.createdAt).toLocaleString()}
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>{log.adminEmail}</Typography>
                        <Typography variant="caption" sx={{ color: '#94A3B8' }}>ID: #{log.adminId}</Typography>
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={log.action}
                          size="small"
                          color={
                            log.action.includes('REVERSAL')
                              ? 'error'
                              : log.action.includes('REFUND')
                              ? 'warning'
                              : 'primary'
                          }
                          sx={{ fontWeight: 700, fontSize: '0.72rem' }}
                        />
                      </TableCell>
                      <TableCell>
                        {log.transactionId ? `Tx #${log.transactionId}` : 'Platform'}
                      </TableCell>
                      <TableCell sx={{ maxWidth: 250 }}>{log.reason}</TableCell>
                      <TableCell sx={{ fontSize: '0.75rem', fontFamily: 'monospace', maxWidth: 240, wordBreak: 'break-all' }}>
                        {log.afterValue || log.beforeValue || '—'}
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </TableContainer>

          <Box sx={{ p: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderTop: '1px solid #E2E8F0' }}>
            <Typography variant="caption" sx={{ color: '#64748B' }}>
              Showing {auditLogs.length} of {totalAuditElements} audit logs
            </Typography>
            <Pagination
              count={totalPagesAudit}
              page={pageAudit + 1}
              onChange={(_e, v) => fetchAuditLogs(v - 1)}
              color="primary"
              shape="rounded"
              size="small"
            />
          </Box>
        </Paper>
      )}

      {/* Manual Balance Adjustment Modal */}
      <Dialog open={adjustmentOpen} onClose={() => setAdjustmentOpen(false)} maxWidth="sm" fullWidth>
        <form onSubmit={handleCreateAdjustment}>
          <DialogTitle sx={{ fontWeight: 800, borderBottom: '1px solid #E2E8F0' }}>
            Post Manual Subledger Adjustment
          </DialogTitle>
          <DialogContent sx={{ pt: 2 }}>
            <Stack spacing={2.5} sx={{ mt: 1 }}>
              <Alert severity="warning" icon={<WarningAmberRounded fontSize="small" />} sx={{ fontSize: '0.78rem' }}>
                All manual ledger entries are permanent and create an immutable audit record linking your administrator credentials.
              </Alert>

              <TextField
                required
                fullWidth
                size="small"
                label="Customer User ID"
                placeholder="e.g. 1"
                value={adjustUserId}
                onChange={(e) => setAdjustUserId(e.target.value)}
              />

              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <TextField
                    select
                    fullWidth
                    size="small"
                    label="Adjustment Type"
                    value={adjustType}
                    onChange={(e) => setAdjustType(e.target.value)}
                  >
                    <MenuItem value="MANUAL_CREDIT">Manual Credit (Increase Balance)</MenuItem>
                    <MenuItem value="MANUAL_DEBIT">Manual Debit (Charge Customer)</MenuItem>
                    <MenuItem value="CORRECTION">Correction Entry</MenuItem>
                  </TextField>
                </Grid>
                <Grid item xs={6}>
                  <TextField
                    required
                    fullWidth
                    size="small"
                    type="number"
                    inputProps={{ step: '0.0001', min: '0.0001' }}
                    label="Amount (USD)"
                    value={adjustAmount}
                    onChange={(e) => setAdjustAmount(e.target.value)}
                  />
                </Grid>
              </Grid>

              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <TextField
                    select
                    fullWidth
                    size="small"
                    label="Reference Type"
                    value={adjustRefType}
                    onChange={(e) => setAdjustRefType(e.target.value)}
                  >
                    <MenuItem value="SUPPORT_TICKET">Support Ticket</MenuItem>
                    <MenuItem value="GOODWILL_GESTURE">Goodwill Gesture</MenuItem>
                    <MenuItem value="BILLING_DISPUTE">Billing Dispute</MenuItem>
                    <MenuItem value="AUDIT_CORRECTION">Audit Correction</MenuItem>
                  </TextField>
                </Grid>
                <Grid item xs={6}>
                  <TextField
                    fullWidth
                    size="small"
                    label="Reference Identifier"
                    placeholder="e.g. TICKET-492"
                    value={adjustRefId}
                    onChange={(e) => setAdjustRefId(e.target.value)}
                  />
                </Grid>
              </Grid>

              <TextField
                required
                fullWidth
                multiline
                rows={3}
                label="Mandatory Reason for Adjustment"
                placeholder="Detailed business explanation justifying this ledger entry"
                value={adjustReason}
                onChange={(e) => setAdjustReason(e.target.value)}
              />
            </Stack>
          </DialogContent>
          <DialogActions sx={{ p: 2, borderTop: '1px solid #E2E8F0' }}>
            <Button onClick={() => setAdjustmentOpen(false)} sx={{ textTransform: 'none' }}>
              Cancel
            </Button>
            <Button
              type="submit"
              variant="contained"
              disabled={adjustLoading}
              sx={{ textTransform: 'none', fontWeight: 600 }}
            >
              {adjustLoading ? 'Posting Entry...' : 'Confirm & Post Entry'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>

      {/* Transaction Reversal Modal */}
      <Dialog open={reversalOpen} onClose={() => setReversalOpen(false)} maxWidth="sm" fullWidth>
        <form onSubmit={handleCreateReversal}>
          <DialogTitle sx={{ fontWeight: 800, borderBottom: '1px solid #E2E8F0', color: '#DC2626' }}>
            Reverse Transaction #{reversalTargetTx?.id}
          </DialogTitle>
          <DialogContent sx={{ pt: 2 }}>
            <Stack spacing={2} sx={{ mt: 1 }}>
              <Alert severity="error" sx={{ fontSize: '0.8rem' }}>
                Reversals do not delete or modify historical records. A new compensating transaction with opposite entry type will be posted to neutralize the balance.
              </Alert>

              {reversalTargetTx && (
                <Paper variant="outlined" sx={{ p: 2, borderRadius: 2, bgcolor: '#F8FAFC' }}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                    Target Transaction: #{reversalTargetTx.id} &bull; {reversalTargetTx.transactionType}
                  </Typography>
                  <Typography variant="body2" sx={{ color: '#64748B', mt: 0.5 }}>
                    Customer: User #{reversalTargetTx.userId} &bull; Amount: ${Number(reversalTargetTx.amount).toFixed(2)} {reversalTargetTx.currency}
                  </Typography>
                </Paper>
              )}

              <TextField
                required
                fullWidth
                multiline
                rows={3}
                label="Mandatory Reason for Reversal"
                placeholder="State why this transaction is being reversed (e.g. Duplicate processing, chargeback dispute)"
                value={reversalReason}
                onChange={(e) => setReversalReason(e.target.value)}
              />
            </Stack>
          </DialogContent>
          <DialogActions sx={{ p: 2, borderTop: '1px solid #E2E8F0' }}>
            <Button onClick={() => setReversalOpen(false)} sx={{ textTransform: 'none' }}>
              Cancel
            </Button>
            <Button
              type="submit"
              variant="contained"
              color="error"
              disabled={reversalLoading}
              sx={{ textTransform: 'none', fontWeight: 600 }}
            >
              {reversalLoading ? 'Processing Reversal...' : 'Confirm Reversal'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>

      {/* Reconciliation Update Modal */}
      <Dialog open={reconModalOpen} onClose={() => setReconModalOpen(false)} maxWidth="xs" fullWidth>
        <form onSubmit={handleUpdateReconciliation}>
          <DialogTitle sx={{ fontWeight: 800, borderBottom: '1px solid #E2E8F0' }}>
            Update Reconciliation Status
          </DialogTitle>
          <DialogContent sx={{ pt: 2 }}>
            <Stack spacing={2} sx={{ mt: 1 }}>
              <Typography variant="body2" sx={{ color: '#64748B' }}>
                Transaction #{reconTargetTx?.id} ({reconTargetTx?.referenceType}: {reconTargetTx?.referenceId})
              </Typography>

              <TextField
                select
                fullWidth
                size="small"
                label="Reconciliation Status"
                value={reconStatus}
                onChange={(e) => setReconStatus(e.target.value)}
              >
                <MenuItem value="RECONCILED">RECONCILED (Verified against gateway)</MenuItem>
                <MenuItem value="PENDING_REVIEW">PENDING_REVIEW (Awaiting statement)</MenuItem>
                <MenuItem value="FLAGGED_DISCREPANCY">FLAGGED_DISCREPANCY (Audit needed)</MenuItem>
              </TextField>

              <TextField
                fullWidth
                multiline
                rows={3}
                size="small"
                label="Auditor Reconciliation Notes"
                placeholder="e.g. Gateway settlement batch ID #849 confirmed."
                value={reconNotes}
                onChange={(e) => setReconNotes(e.target.value)}
              />
            </Stack>
          </DialogContent>
          <DialogActions sx={{ p: 2, borderTop: '1px solid #E2E8F0' }}>
            <Button onClick={() => setReconModalOpen(false)} sx={{ textTransform: 'none' }}>
              Cancel
            </Button>
            <Button type="submit" variant="contained" disabled={reconLoading} sx={{ textTransform: 'none', fontWeight: 600 }}>
              {reconLoading ? 'Saving...' : 'Update Status'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>

      {/* Refund Process Modal (Approve/Reject) */}
      <Dialog open={processModalOpen} onClose={() => setProcessModalOpen(false)} maxWidth="sm" fullWidth>
        <form onSubmit={handleProcessRefund}>
          <DialogTitle sx={{ fontWeight: 800, borderBottom: '1px solid #E2E8F0' }}>
            Refund Governance Review: Request #{targetRefund?.id}
          </DialogTitle>
          <DialogContent sx={{ pt: 2 }}>
            <Stack spacing={2} sx={{ mt: 1 }}>
              {targetRefund && (
                <Paper variant="outlined" sx={{ p: 2, borderRadius: 2, bgcolor: '#F8FAFC' }}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                    Order #{targetRefund.orderId} &bull; User #{targetRefund.userId}
                  </Typography>
                  <Typography variant="body2" sx={{ color: '#166534', fontWeight: 800 }}>
                    Requested Amount: ${Number(targetRefund.amount).toFixed(2)} {targetRefund.currency}
                  </Typography>
                  <Typography variant="body2" sx={{ color: '#64748B', mt: 1 }}>
                    <strong>Customer Reason:</strong> {targetRefund.reason}
                  </Typography>
                </Paper>
              )}

              <TextField
                select
                fullWidth
                size="small"
                label="Governance Decision"
                value={refundDecision}
                onChange={(e) => setRefundDecision(e.target.value as 'APPROVED' | 'REJECTED')}
              >
                <MenuItem value="APPROVED">APPROVE REFUND (Post Credit to Subledger)</MenuItem>
                <MenuItem value="REJECTED">REJECT REFUND (Deny Claim)</MenuItem>
              </TextField>

              <TextField
                required
                fullWidth
                multiline
                rows={3}
                label="Mandatory Staff Decision Notes"
                placeholder="Explain the rationale for approval or rejection"
                value={decisionNotes}
                onChange={(e) => setDecisionNotes(e.target.value)}
              />

              <Alert severity="info" sx={{ fontSize: '0.78rem' }}>
                Simulated Payment Gateway: Approving will post a linked order refund credit to the customer ledger and set order status to REFUNDED.
              </Alert>
            </Stack>
          </DialogContent>
          <DialogActions sx={{ p: 2, borderTop: '1px solid #E2E8F0' }}>
            <Button onClick={() => setProcessModalOpen(false)} sx={{ textTransform: 'none' }}>
              Cancel
            </Button>
            <Button
              type="submit"
              variant="contained"
              color={refundDecision === 'APPROVED' ? 'success' : 'error'}
              disabled={processingRefund}
              sx={{ textTransform: 'none', fontWeight: 600 }}
            >
              {processingRefund ? 'Processing...' : `Confirm ${refundDecision}`}
            </Button>
          </DialogActions>
        </form>
      </Dialog>

      {/* Transaction Audit Detail Modal */}
      <Dialog open={Boolean(detailTx)} onClose={() => setDetailTx(null)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 800, borderBottom: '1px solid #E2E8F0' }}>
          Transaction #{detailTx?.id} Audit Snapshot
        </DialogTitle>
        <DialogContent sx={{ pt: 2 }}>
          {detailTx && (
            <Stack spacing={2} sx={{ mt: 1 }}>
              <Grid container spacing={1}>
                <Grid item xs={5}><Typography variant="body2" sx={{ color: '#64748B', fontWeight: 600 }}>Transaction ID:</Typography></Grid>
                <Grid item xs={7}><Typography variant="body2" sx={{ fontWeight: 700 }}>#{detailTx.id}</Typography></Grid>

                <Grid item xs={5}><Typography variant="body2" sx={{ color: '#64748B', fontWeight: 600 }}>Customer:</Typography></Grid>
                <Grid item xs={7}><Typography variant="body2">User #{detailTx.userId} ({detailTx.customerEmail || 'N/A'})</Typography></Grid>

                <Grid item xs={5}><Typography variant="body2" sx={{ color: '#64748B', fontWeight: 600 }}>Created Date:</Typography></Grid>
                <Grid item xs={7}><Typography variant="body2">{new Date(detailTx.createdAt).toLocaleString()}</Typography></Grid>

                <Grid item xs={5}><Typography variant="body2" sx={{ color: '#64748B', fontWeight: 600 }}>Entry Type:</Typography></Grid>
                <Grid item xs={7}>
                  <Chip
                    label={`${detailTx.entryType} (${detailTx.transactionType})`}
                    size="small"
                    color={detailTx.entryType === 'CREDIT' ? 'success' : 'error'}
                    sx={{ fontWeight: 700 }}
                  />
                </Grid>

                <Grid item xs={5}><Typography variant="body2" sx={{ color: '#64748B', fontWeight: 600 }}>Amount:</Typography></Grid>
                <Grid item xs={7}>
                  <Typography variant="body1" sx={{ fontWeight: 800, fontFamily: 'monospace' }}>
                    ${Number(detailTx.amount).toFixed(4)} {detailTx.currency}
                  </Typography>
                </Grid>

                <Grid item xs={5}><Typography variant="body2" sx={{ color: '#64748B', fontWeight: 600 }}>Status:</Typography></Grid>
                <Grid item xs={7}>
                  <Chip label={detailTx.status} size="small" sx={{ fontWeight: 700 }} />
                </Grid>

                <Grid item xs={5}><Typography variant="body2" sx={{ color: '#64748B', fontWeight: 600 }}>Reconciliation:</Typography></Grid>
                <Grid item xs={7}>
                  <Chip
                    label={detailTx.reconciliationStatus || 'PENDING_REVIEW'}
                    size="small"
                    color={
                      detailTx.reconciliationStatus === 'RECONCILED'
                        ? 'success'
                        : detailTx.reconciliationStatus === 'FLAGGED_DISCREPANCY'
                        ? 'error'
                        : 'warning'
                    }
                    sx={{ fontWeight: 700 }}
                  />
                </Grid>

                <Grid item xs={5}><Typography variant="body2" sx={{ color: '#64748B', fontWeight: 600 }}>Reference:</Typography></Grid>
                <Grid item xs={7}><Typography variant="body2">{detailTx.referenceType} &bull; {detailTx.referenceId}</Typography></Grid>

                {detailTx.paymentProvider && (
                  <>
                    <Grid item xs={5}><Typography variant="body2" sx={{ color: '#64748B', fontWeight: 600 }}>Gateway / Provider:</Typography></Grid>
                    <Grid item xs={7}><Typography variant="body2">{detailTx.paymentProvider} ({detailTx.providerReference || 'TOKENIZED'})</Typography></Grid>
                  </>
                )}

                {detailTx.reason && (
                  <>
                    <Grid item xs={5}><Typography variant="body2" sx={{ color: '#64748B', fontWeight: 600 }}>Reason:</Typography></Grid>
                    <Grid item xs={7}><Typography variant="body2">{detailTx.reason}</Typography></Grid>
                  </>
                )}

                {detailTx.reversalOfId && (
                  <>
                    <Grid item xs={5}><Typography variant="body2" sx={{ color: '#64748B', fontWeight: 600 }}>Reversal Of:</Typography></Grid>
                    <Grid item xs={7}><Typography variant="body2" sx={{ color: '#DC2626', fontWeight: 700 }}>Tx #{detailTx.reversalOfId}</Typography></Grid>
                  </>
                )}

                <Grid item xs={5}><Typography variant="body2" sx={{ color: '#64748B', fontWeight: 600 }}>Created By:</Typography></Grid>
                <Grid item xs={7}><Typography variant="body2">{detailTx.createdBy}</Typography></Grid>

                {detailTx.idempotencyKey && (
                  <>
                    <Grid item xs={5}><Typography variant="body2" sx={{ color: '#64748B', fontWeight: 600 }}>Idempotency Key:</Typography></Grid>
                    <Grid item xs={7}>
                      <Typography variant="caption" sx={{ fontFamily: 'monospace', color: '#64748B', wordBreak: 'break-all' }}>
                        {detailTx.idempotencyKey}
                      </Typography>
                    </Grid>
                  </>
                )}
              </Grid>
            </Stack>
          )}
        </DialogContent>
        <DialogActions sx={{ p: 2, borderTop: '1px solid #E2E8F0' }}>
          <Button onClick={() => setDetailTx(null)} variant="contained" sx={{ textTransform: 'none' }}>
            Close
          </Button>
        </DialogActions>
      </Dialog>
    </Container>
  );
}
