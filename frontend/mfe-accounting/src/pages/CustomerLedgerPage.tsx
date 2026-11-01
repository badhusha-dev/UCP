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
  CardContent,
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
  Switch,
  FormControlLabel,
  Badge,
} from '@mui/material';
import {
  AccountBalanceWalletRounded,
  TrendingUpRounded,
  TrendingDownRounded,
  InfoOutlined,
  FilterListRounded,
  RefreshRounded,
  VisibilityOutlined,
  ReceiptLongRounded,
  CheckCircleRounded,
  RemoveCircleOutlineRounded,
  DownloadRounded,
  PrintRounded,
  CreditCardRounded,
  SettingsRounded,
  AssignmentReturnRounded,
  HourglassEmptyRounded,
  LockRounded,
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

interface AccountOverview {
  userId: number;
  currency: string;
  currentStoreCredit?: number;
  availableStoreCredit?: number;
  lifetimeSpend: number;
  lifetimeRefunds: number;
  totalCredits: number;
  totalDebits: number;
  netActivity: number;
  pendingTransactionsCount: number;
  pendingTransactionsAmount: number;
  calculationNotes: Record<string, string>;
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
  reconciliationStatus?: string;
  reconciliationNotes?: string;
  paymentProvider?: string;
  providerReference?: string;
  idempotencyKey?: string;
  reversalOfId?: number;
  reason?: string;
  createdBy: string;
  createdAt: string;
}

interface InvoiceLineItem {
  productId: number;
  productName: string;
  sku: string;
  unitPrice: number;
  quantity: number;
  subtotal: number;
}

interface OrderInvoice {
  orderId: number;
  orderNumber: string;
  invoiceDate: string;
  paymentStatus: string;
  orderStatus: string;
  currency: string;
  subtotal: number;
  tax: number;
  shippingCost: number;
  discount: number;
  totalAmount: number;
  paymentProvider: string;
  tokenizedPaymentReference: string;
  lineItems: InvoiceLineItem[];
}

interface PaymentMethod {
  id: number;
  provider: string;
  cardBrand: string;
  last4: string;
  expMonth: number;
  expYear: number;
  isDefault: boolean;
  createdAt: string;
}

interface AccountingPreferences {
  userId: number;
  emailReceipts: boolean;
  statementNotifications: boolean;
}

interface RefundRequest {
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
  MANUAL_CREDIT: { label: 'Credit Adjustment', color: 'primary' },
  MANUAL_DEBIT: { label: 'Debit Adjustment', color: 'warning' },
  CORRECTION: { label: 'Correction', color: 'info' },
};

export default function CustomerLedgerPage() {
  const [overview, setOverview] = useState<AccountOverview | null>(null);
  const [transactions, setTransactions] = useState<LedgerTransaction[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [overviewLoading, setOverviewLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // Pagination & Filtering state
  const [page, setPage] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(1);
  const [totalElements, setTotalElements] = useState<number>(0);
  const [selectedType, setSelectedType] = useState<string>('');
  const [selectedStatus, setSelectedStatus] = useState<string>('');
  const [filterReference, setFilterReference] = useState<string>('');
  const [startDate, setStartDate] = useState<string>('');
  const [endDate, setEndDate] = useState<string>('');

  // Modals
  const [selectedTx, setSelectedTx] = useState<LedgerTransaction | null>(null);
  const [detailOpen, setDetailOpen] = useState<boolean>(false);

  // Invoice Modal
  const [invoice, setInvoice] = useState<OrderInvoice | null>(null);
  const [invoiceLoading, setInvoiceLoading] = useState<boolean>(false);
  const [invoiceOpen, setInvoiceOpen] = useState<boolean>(false);

  // Refund Request Modal
  const [refundModalOpen, setRefundModalOpen] = useState<boolean>(false);
  const [refundOrderId, setRefundOrderId] = useState<string>('');
  const [refundAmount, setRefundAmount] = useState<string>('');
  const [refundReason, setRefundReason] = useState<string>('');
  const [refundSubmitting, setRefundSubmitting] = useState<boolean>(false);

  // View Customer Refund Requests
  const [refundRequestsOpen, setRefundRequestsOpen] = useState<boolean>(false);
  const [refundList, setRefundList] = useState<RefundRequest[]>([]);
  const [refundListLoading, setRefundListLoading] = useState<boolean>(false);

  // Payment Methods Modal
  const [paymentMethodsOpen, setPaymentMethodsOpen] = useState<boolean>(false);
  const [paymentMethods, setPaymentMethods] = useState<PaymentMethod[]>([]);
  const [pmLoading, setPmLoading] = useState<boolean>(false);

  // Preferences Modal
  const [prefOpen, setPrefOpen] = useState<boolean>(false);
  const [preferences, setPreferences] = useState<AccountingPreferences>({
    userId: 0,
    emailReceipts: true,
    statementNotifications: false,
  });
  const [prefSaving, setPrefSaving] = useState<boolean>(false);

  // Fetch Account Overview
  const fetchOverview = useCallback(async () => {
    setOverviewLoading(true);
    try {
      const res = await axios.get<AccountOverview>('/api/accounting/overview');
      setOverview(res.data);
    } catch (err: any) {
      console.error('Failed to load overview', err);
    } finally {
      setOverviewLoading(false);
    }
  }, []);

  // Fetch Ledger Transactions
  const fetchLedger = useCallback(async (pageIndex: number = 0) => {
    setLoading(true);
    setError(null);
    try {
      const params: Record<string, any> = {
        page: pageIndex,
        size: 10,
        sortBy: 'createdAt',
        sortDirection: 'DESC',
      };
      if (selectedType) params.type = selectedType;
      if (selectedStatus) params.status = selectedStatus;
      if (filterReference) params.referenceId = filterReference;
      if (startDate) params.startDate = `${startDate}T00:00:00`;
      if (endDate) params.endDate = `${endDate}T23:59:59`;

      const res = await axios.get<{
        content: LedgerTransaction[];
        totalPages: number;
        totalElements: number;
        number: number;
      }>('/api/accounting/ledger', { params });

      setTransactions(res.data.content || []);
      setTotalPages(res.data.totalPages || 1);
      setTotalElements(res.data.totalElements || 0);
      setPage(res.data.number || 0);
    } catch (err: any) {
      setError(err.response?.data?.error || 'Unable to retrieve your transaction ledger.');
    } finally {
      setLoading(false);
    }
  }, [selectedType, selectedStatus, filterReference, startDate, endDate]);

  useEffect(() => {
    fetchOverview();
  }, [fetchOverview]);

  useEffect(() => {
    fetchLedger(0);
  }, [fetchLedger]);

  // Page Change
  const handlePageChange = (_event: React.ChangeEvent<unknown>, value: number) => {
    fetchLedger(value - 1);
  };

  // Reset Filters
  const handleResetFilters = () => {
    setSelectedType('');
    setSelectedStatus('');
    setFilterReference('');
    setStartDate('');
    setEndDate('');
  };

  // Open Invoice Modal
  const handleOpenInvoice = async (orderIdStr: string) => {
    const orderId = parseInt(orderIdStr, 10);
    if (isNaN(orderId)) return;
    setInvoiceLoading(true);
    setInvoiceOpen(true);
    try {
      const res = await axios.get<OrderInvoice>(`/api/accounting/invoices/${orderId}`);
      setInvoice(res.data);
    } catch (err: any) {
      setError(err.response?.data?.error || 'Failed to generate printable order invoice.');
      setInvoiceOpen(false);
    } finally {
      setInvoiceLoading(false);
    }
  };

  // Open Refund Requests
  const handleOpenRefundList = async () => {
    setRefundRequestsOpen(true);
    setRefundListLoading(true);
    try {
      const res = await axios.get<RefundRequest[]>('/api/accounting/refunds');
      setRefundList(res.data || []);
    } catch (err: any) {
      console.error('Failed to load refund requests', err);
    } finally {
      setRefundListLoading(false);
    }
  };

  // Submit Refund Request
  const handleSubmitRefundRequest = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!refundOrderId || !refundAmount || !refundReason) {
      setError('Please fill in Order ID, Amount, and Reason.');
      return;
    }
    setRefundSubmitting(true);
    try {
      await axios.post('/api/accounting/refunds/request', {
        orderId: parseInt(refundOrderId, 10),
        amount: parseFloat(refundAmount),
        currency: 'USD',
        reason: refundReason,
      });
      setSuccessMsg('Refund request submitted successfully. Our accounting team will review it shortly.');
      setRefundModalOpen(false);
      setRefundOrderId('');
      setRefundAmount('');
      setRefundReason('');
    } catch (err: any) {
      setError(err.response?.data?.error || 'Failed to submit refund request.');
    } finally {
      setRefundSubmitting(false);
    }
  };

  // Payment Methods
  const handleOpenPaymentMethods = async () => {
    setPaymentMethodsOpen(true);
    setPmLoading(true);
    try {
      const res = await axios.get<PaymentMethod[]>('/api/accounting/payment-methods');
      setPaymentMethods(res.data || []);
    } catch (err: any) {
      console.error('Failed to load payment methods', err);
    } finally {
      setPmLoading(false);
    }
  };

  // Preferences
  const handleOpenPreferences = async () => {
    setPrefOpen(true);
    try {
      const res = await axios.get<AccountingPreferences>('/api/accounting/preferences');
      setPreferences(res.data);
    } catch (err: any) {
      console.error('Failed to load preferences', err);
    }
  };

  const handleSavePreferences = async () => {
    setPrefSaving(true);
    try {
      const res = await axios.put<AccountingPreferences>('/api/accounting/preferences', preferences);
      setPreferences(res.data);
      setSuccessMsg('Accounting preferences updated.');
      setPrefOpen(false);
    } catch (err: any) {
      setError(err.response?.data?.error || 'Failed to save accounting preferences.');
    } finally {
      setPrefSaving(false);
    }
  };

  // CSV Statement Download
  const handleDownloadStatement = () => {
    const params = new URLSearchParams();
    if (startDate) params.append('startDate', `${startDate}T00:00:00`);
    if (endDate) params.append('endDate', `${endDate}T23:59:59`);
    const token = localStorage.getItem('jwt_token') || '';
    
    // Trigger download via fetch with auth header
    fetch(`/api/accounting/statements/export?${params.toString()}`, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    })
      .then((res) => {
        if (!res.ok) throw new Error('Download failed');
        return res.blob();
      })
      .then((blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `account_statement_${new Date().toISOString().split('T')[0]}.csv`;
        document.body.appendChild(a);
        a.click();
        a.remove();
        window.URL.revokeObjectURL(url);
      })
      .catch((err) => {
        setError('Failed to download account statement CSV.');
      });
  };

  return (
    <Container maxWidth="xl" sx={{ py: 3 }}>
      {/* Notifications */}
      {successMsg && (
        <Alert severity="success" onClose={() => setSuccessMsg(null)} sx={{ mb: 2, borderRadius: 2 }}>
          {successMsg}
        </Alert>
      )}
      {error && (
        <Alert severity="error" onClose={() => setError(null)} sx={{ mb: 2, borderRadius: 2 }}>
          {error}
        </Alert>
      )}

      {/* Header */}
      <Box sx={{ mb: 4, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 2 }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 800, color: '#0F172A', letterSpacing: '-0.02em' }}>
            Customer Accounting &amp; Ledger
          </Typography>
          <Typography variant="body2" sx={{ color: '#64748B', mt: 0.5 }}>
            Personal subledger containing verified immutable order payments, refunds, store credits, and adjustments.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1.5} flexWrap="wrap">
          <Button
            variant="outlined"
            startIcon={<CreditCardRounded />}
            onClick={handleOpenPaymentMethods}
            sx={{ borderRadius: 2, textTransform: 'none', fontWeight: 600 }}
          >
            Payment Methods
          </Button>
          <Button
            variant="outlined"
            startIcon={<SettingsRounded />}
            onClick={handleOpenPreferences}
            sx={{ borderRadius: 2, textTransform: 'none', fontWeight: 600 }}
          >
            Preferences
          </Button>
          <Button
            variant="outlined"
            startIcon={<AssignmentReturnRounded />}
            onClick={handleOpenRefundList}
            sx={{ borderRadius: 2, textTransform: 'none', fontWeight: 600 }}
          >
            Refund Requests
          </Button>
          <Button
            variant="contained"
            color="primary"
            startIcon={<DownloadRounded />}
            onClick={handleDownloadStatement}
            sx={{ borderRadius: 2, textTransform: 'none', fontWeight: 600 }}
          >
            Export Statement (CSV)
          </Button>
          <IconButton
            onClick={() => {
              fetchOverview();
              fetchLedger(page);
            }}
            sx={{ border: '1px solid #CBD5E1', borderRadius: 2 }}
          >
            <RefreshRounded fontSize="small" />
          </IconButton>
        </Stack>
      </Box>

      {/* Account Overview Cards */}
      <Grid container spacing={2.5} sx={{ mb: 4 }}>
        {/* Store Credit Balance */}
        <Grid item xs={12} sm={6} md={3}>
          <Card
            elevation={0}
            sx={{
              p: 2.5,
              borderRadius: 3,
              border: '1px solid #E2E8F0',
              background: 'linear-gradient(135deg, #1E1B4B 0%, #312E81 100%)',
              color: '#FFFFFF',
              boxShadow: '0 8px 20px -4px rgba(49, 46, 129, 0.25)',
              height: '100%',
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'space-between',
            }}
          >
            <Box>
              <Stack direction="row" justifyContent="space-between" alignItems="center">
                <Typography variant="overline" sx={{ color: '#A5B4FC', fontWeight: 700, letterSpacing: '0.08em' }}>
                  AVAILABLE STORE CREDIT
                </Typography>
                <AccountBalanceWalletRounded sx={{ color: '#818CF8', fontSize: 22 }} />
              </Stack>
              {overviewLoading ? (
                <CircularProgress size={24} sx={{ color: '#FFFFFF', my: 2 }} />
              ) : (
                <Typography variant="h4" sx={{ fontWeight: 900, my: 1 }}>
                  ${overview ? Number(overview.availableStoreCredit ?? overview.currentStoreCredit ?? 0).toFixed(2) : '0.00'}
                  <Typography component="span" variant="subtitle2" sx={{ ml: 1, color: '#A5B4FC', fontWeight: 600 }}>
                    {overview?.currency || 'USD'}
                  </Typography>
                </Typography>
              )}
            </Box>
            <Tooltip title={overview?.calculationNotes?.currentStoreCredit || 'Available customer store credit balance.'}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mt: 1 }}>
                <InfoOutlined sx={{ fontSize: 14, color: '#93C5FD' }} />
                <Typography variant="caption" sx={{ color: '#C7D2FE' }}>
                  Net active credits minus debits
                </Typography>
              </Box>
            </Tooltip>
          </Card>
        </Grid>

        {/* Lifetime Spend */}
        <Grid item xs={12} sm={6} md={3}>
          <Card elevation={0} sx={{ p: 2.5, borderRadius: 3, border: '1px solid #E2E8F0', bgcolor: '#FFFFFF', height: '100%' }}>
            <Stack direction="row" justifyContent="space-between" alignItems="center">
              <Typography variant="overline" sx={{ color: '#64748B', fontWeight: 700, letterSpacing: '0.08em' }}>
                LIFETIME SPEND
              </Typography>
              <TrendingDownRounded sx={{ color: '#475569', fontSize: 22 }} />
            </Stack>
            {overviewLoading ? (
              <CircularProgress size={24} sx={{ color: '#0F172A', my: 2 }} />
            ) : (
              <Typography variant="h4" sx={{ fontWeight: 800, color: '#0F172A', my: 1 }}>
                ${overview ? Number(overview.lifetimeSpend).toFixed(2) : '0.00'}
              </Typography>
            )}
            <Tooltip title={overview?.calculationNotes?.lifetimeSpend || 'Total settled order payments.'}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mt: 1 }}>
                <InfoOutlined sx={{ fontSize: 14, color: '#94A3B8' }} />
                <Typography variant="caption" sx={{ color: '#64748B' }}>
                  Settled order payments
                </Typography>
              </Box>
            </Tooltip>
          </Card>
        </Grid>

        {/* Lifetime Refunds */}
        <Grid item xs={12} sm={6} md={3}>
          <Card elevation={0} sx={{ p: 2.5, borderRadius: 3, border: '1px solid #E2E8F0', bgcolor: '#F0FDF4', height: '100%' }}>
            <Stack direction="row" justifyContent="space-between" alignItems="center">
              <Typography variant="overline" sx={{ color: '#166534', fontWeight: 700, letterSpacing: '0.08em' }}>
                LIFETIME REFUNDS
              </Typography>
              <TrendingUpRounded sx={{ color: '#16A34A', fontSize: 22 }} />
            </Stack>
            {overviewLoading ? (
              <CircularProgress size={24} sx={{ color: '#16A34A', my: 2 }} />
            ) : (
              <Typography variant="h4" sx={{ fontWeight: 800, color: '#166534', my: 1 }}>
                +${overview ? Number(overview.lifetimeRefunds).toFixed(2) : '0.00'}
              </Typography>
            )}
            <Tooltip title={overview?.calculationNotes?.lifetimeRefunds || 'Total credited order refunds.'}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mt: 1 }}>
                <InfoOutlined sx={{ fontSize: 14, color: '#16A34A' }} />
                <Typography variant="caption" sx={{ color: '#15803D' }}>
                  Credited order returns
                </Typography>
              </Box>
            </Tooltip>
          </Card>
        </Grid>

        {/* Pending Transactions */}
        <Grid item xs={12} sm={6} md={3}>
          <Card elevation={0} sx={{ p: 2.5, borderRadius: 3, border: '1px solid #E2E8F0', bgcolor: '#FFFBEB', height: '100%' }}>
            <Stack direction="row" justifyContent="space-between" alignItems="center">
              <Typography variant="overline" sx={{ color: '#92400E', fontWeight: 700, letterSpacing: '0.08em' }}>
                PENDING / REVIEW
              </Typography>
              <HourglassEmptyRounded sx={{ color: '#D97706', fontSize: 22 }} />
            </Stack>
            {overviewLoading ? (
              <CircularProgress size={24} sx={{ color: '#D97706', my: 2 }} />
            ) : (
              <Typography variant="h4" sx={{ fontWeight: 800, color: '#92400E', my: 1 }}>
                {overview?.pendingTransactionsCount || 0}
                <Typography component="span" variant="caption" sx={{ ml: 1, color: '#B45309', fontWeight: 600 }}>
                  (${overview ? Number(overview.pendingTransactionsAmount).toFixed(2) : '0.00'})
                </Typography>
              </Typography>
            )}
            <Tooltip title={overview?.calculationNotes?.pendingTransactions || 'Transactions awaiting settlement or review.'}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mt: 1 }}>
                <InfoOutlined sx={{ fontSize: 14, color: '#D97706' }} />
                <Typography variant="caption" sx={{ color: '#B45309' }}>
                  Pending reconciliation
                </Typography>
              </Box>
            </Tooltip>
          </Card>
        </Grid>
      </Grid>

      {/* Filter Toolbar */}
      <Paper elevation={0} sx={{ p: 2.5, mb: 3, borderRadius: 3, border: '1px solid #E2E8F0', bgcolor: '#FFFFFF' }}>
        <Grid container spacing={2} alignItems="center">
          <Grid item xs={12} sm={6} md={2.5}>
            <TextField
              select
              fullWidth
              size="small"
              label="Transaction Type"
              value={selectedType}
              onChange={(e) => setSelectedType(e.target.value)}
            >
              <MenuItem value="">All Types</MenuItem>
              <MenuItem value="ORDER_PAYMENT">Order Payment (Debit)</MenuItem>
              <MenuItem value="ORDER_REFUND">Order Refund (Credit)</MenuItem>
              <MenuItem value="MANUAL_CREDIT">Store Credit</MenuItem>
              <MenuItem value="MANUAL_DEBIT">Debit Charge</MenuItem>
              <MenuItem value="CORRECTION">Correction</MenuItem>
            </TextField>
          </Grid>

          <Grid item xs={12} sm={6} md={2}>
            <TextField
              select
              fullWidth
              size="small"
              label="Status"
              value={selectedStatus}
              onChange={(e) => setSelectedStatus(e.target.value)}
            >
              <MenuItem value="">All Statuses</MenuItem>
              <MenuItem value="POSTED">Posted</MenuItem>
              <MenuItem value="REVERSED">Reversed</MenuItem>
            </TextField>
          </Grid>

          <Grid item xs={12} sm={6} md={2}>
            <TextField
              fullWidth
              size="small"
              label="Order / Ref ID"
              placeholder="e.g. 101"
              value={filterReference}
              onChange={(e) => setFilterReference(e.target.value)}
            />
          </Grid>

          <Grid item xs={12} sm={6} md={2}>
            <TextField
              fullWidth
              size="small"
              type="date"
              label="Start Date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              InputLabelProps={{ shrink: true }}
            />
          </Grid>

          <Grid item xs={12} sm={6} md={2}>
            <TextField
              fullWidth
              size="small"
              type="date"
              label="End Date"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
              InputLabelProps={{ shrink: true }}
            />
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

      {/* Transactions Table */}
      <Paper elevation={0} sx={{ borderRadius: 3, border: '1px solid #E2E8F0', overflow: 'hidden' }}>
        <TableContainer>
          <Table sx={{ minWidth: 800 }}>
            <TableHead sx={{ bgcolor: '#F8FAFC' }}>
              <TableRow>
                <TableCell sx={{ fontWeight: 700, color: '#475569' }}>Date &amp; Time</TableCell>
                <TableCell sx={{ fontWeight: 700, color: '#475569' }}>Description</TableCell>
                <TableCell sx={{ fontWeight: 700, color: '#475569' }}>Reference / Order</TableCell>
                <TableCell sx={{ fontWeight: 700, color: '#475569' }}>Type</TableCell>
                <TableCell align="right" sx={{ fontWeight: 700, color: '#475569' }}>Amount</TableCell>
                <TableCell sx={{ fontWeight: 700, color: '#475569' }}>Status</TableCell>
                <TableCell align="center" sx={{ fontWeight: 700, color: '#475569' }}>Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loading ? (
                <TableRow>
                  <TableCell colSpan={7} align="center" sx={{ py: 6 }}>
                    <CircularProgress size={32} />
                    <Typography variant="body2" sx={{ color: '#64748B', mt: 1 }}>
                      Loading verified subledger entries...
                    </Typography>
                  </TableCell>
                </TableRow>
              ) : transactions.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7} align="center" sx={{ py: 6 }}>
                    <ReceiptLongRounded sx={{ fontSize: 48, color: '#CBD5E1', mb: 1 }} />
                    <Typography variant="subtitle1" sx={{ fontWeight: 700, color: '#475569' }}>
                      No transactions recorded
                    </Typography>
                    <Typography variant="body2" sx={{ color: '#94A3B8' }}>
                      Orders, refunds, and balance adjustments will automatically post here.
                    </Typography>
                  </TableCell>
                </TableRow>
              ) : (
                transactions.map((tx) => {
                  const isCredit = tx.entryType === 'CREDIT';
                  const typeMeta = TYPE_CONFIG[tx.transactionType] || { label: tx.transactionType, color: 'default' };
                  const isOrderRelated = tx.referenceType === 'ORDER' || tx.transactionType.startsWith('ORDER_');

                  return (
                    <TableRow key={tx.id} hover sx={{ '&:last-child td, &:last-child th': { border: 0 } }}>
                      <TableCell sx={{ whiteSpace: 'nowrap', fontSize: '0.85rem' }}>
                        {new Date(tx.createdAt).toLocaleDateString()}{' '}
                        <Typography component="span" variant="caption" sx={{ color: '#94A3B8', display: 'block' }}>
                          {new Date(tx.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                        </Typography>
                      </TableCell>
                      <TableCell sx={{ fontWeight: 600, color: '#1E293B', maxWidth: 280 }}>
                        {tx.description}
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
                        <Chip
                          label={typeMeta.label}
                          size="small"
                          color={typeMeta.color}
                          sx={{ fontWeight: 600, fontSize: '0.75rem' }}
                        />
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
                          <Chip
                            icon={<CheckCircleRounded sx={{ fontSize: '14px !important' }} />}
                            label="POSTED"
                            size="small"
                            sx={{ bgcolor: '#ECFDF5', color: '#059669', fontWeight: 700, fontSize: '0.7rem' }}
                          />
                        ) : (
                          <Chip
                            icon={<RemoveCircleOutlineRounded sx={{ fontSize: '14px !important' }} />}
                            label="REVERSED"
                            size="small"
                            sx={{ bgcolor: '#F1F5F9', color: '#64748B', fontWeight: 700, fontSize: '0.7rem' }}
                          />
                        )}
                      </TableCell>
                      <TableCell align="center">
                        <Stack direction="row" spacing={0.5} justifyContent="center">
                          {isOrderRelated && (
                            <Tooltip title="View Itemized Invoice & Receipt">
                              <IconButton
                                size="small"
                                onClick={() => handleOpenInvoice(tx.referenceId)}
                                sx={{ color: '#0284C7' }}
                              >
                                <PrintRounded fontSize="small" />
                              </IconButton>
                            </Tooltip>
                          )}
                          <Tooltip title="View Transaction Audit Details">
                            <IconButton
                              size="small"
                              onClick={() => {
                                setSelectedTx(tx);
                                setDetailOpen(true);
                              }}
                              sx={{ color: '#4F46E5' }}
                            >
                              <VisibilityOutlined fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        </Stack>
                      </TableCell>
                    </TableRow>
                  );
                })
              )}
            </TableBody>
          </Table>
        </TableContainer>

        {/* Pagination Bar */}
        <Box sx={{ p: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderTop: '1px solid #E2E8F0', flexWrap: 'wrap', gap: 2 }}>
          <Typography variant="caption" sx={{ color: '#64748B' }}>
            Showing {transactions.length} of {totalElements} total entries
          </Typography>
          <Pagination
            count={totalPages}
            page={page + 1}
            onChange={handlePageChange}
            color="primary"
            shape="rounded"
            size="small"
          />
        </Box>
      </Paper>

      {/* Transaction Details Modal */}
      <Dialog open={detailOpen} onClose={() => setDetailOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 800, borderBottom: '1px solid #E2E8F0' }}>
          Transaction #{selectedTx?.id} Details
        </DialogTitle>
        <DialogContent sx={{ pt: 2 }}>
          {selectedTx && (
            <Stack spacing={2} sx={{ mt: 1 }}>
              <Grid container spacing={1}>
                <Grid item xs={5}><Typography variant="body2" sx={{ color: '#64748B', fontWeight: 600 }}>Transaction ID:</Typography></Grid>
                <Grid item xs={7}><Typography variant="body2" sx={{ fontWeight: 700 }}>#{selectedTx.id}</Typography></Grid>

                <Grid item xs={5}><Typography variant="body2" sx={{ color: '#64748B', fontWeight: 600 }}>Recorded Date:</Typography></Grid>
                <Grid item xs={7}><Typography variant="body2">{new Date(selectedTx.createdAt).toLocaleString()}</Typography></Grid>

                <Grid item xs={5}><Typography variant="body2" sx={{ color: '#64748B', fontWeight: 600 }}>Entry / Nature:</Typography></Grid>
                <Grid item xs={7}>
                  <Chip
                    label={`${selectedTx.entryType} (${selectedTx.transactionType})`}
                    size="small"
                    color={selectedTx.entryType === 'CREDIT' ? 'success' : 'error'}
                    sx={{ fontWeight: 700 }}
                  />
                </Grid>

                <Grid item xs={5}><Typography variant="body2" sx={{ color: '#64748B', fontWeight: 600 }}>Amount:</Typography></Grid>
                <Grid item xs={7}>
                  <Typography variant="body1" sx={{ fontWeight: 800, fontFamily: 'monospace' }}>
                    ${Number(selectedTx.amount).toFixed(4)} {selectedTx.currency}
                  </Typography>
                </Grid>

                <Grid item xs={5}><Typography variant="body2" sx={{ color: '#64748B', fontWeight: 600 }}>Status:</Typography></Grid>
                <Grid item xs={7}>
                  <Chip label={selectedTx.status} size="small" sx={{ fontWeight: 700 }} />
                </Grid>

                <Grid item xs={5}><Typography variant="body2" sx={{ color: '#64748B', fontWeight: 600 }}>Reference:</Typography></Grid>
                <Grid item xs={7}><Typography variant="body2">{selectedTx.referenceType} &bull; {selectedTx.referenceId}</Typography></Grid>

                {selectedTx.paymentProvider && (
                  <>
                    <Grid item xs={5}><Typography variant="body2" sx={{ color: '#64748B', fontWeight: 600 }}>Gateway / Provider:</Typography></Grid>
                    <Grid item xs={7}><Typography variant="body2">{selectedTx.paymentProvider} ({selectedTx.providerReference || 'TOKENIZED'})</Typography></Grid>
                  </>
                )}

                {selectedTx.reason && (
                  <>
                    <Grid item xs={5}><Typography variant="body2" sx={{ color: '#64748B', fontWeight: 600 }}>Reason Note:</Typography></Grid>
                    <Grid item xs={7}><Typography variant="body2">{selectedTx.reason}</Typography></Grid>
                  </>
                )}

                {selectedTx.reversalOfId && (
                  <>
                    <Grid item xs={5}><Typography variant="body2" sx={{ color: '#64748B', fontWeight: 600 }}>Reversal Of:</Typography></Grid>
                    <Grid item xs={7}>
                      <Typography variant="body2" sx={{ color: '#DC2626', fontWeight: 700 }}>
                        Compensating for Transaction #{selectedTx.reversalOfId}
                      </Typography>
                    </Grid>
                  </>
                )}

                <Grid item xs={5}><Typography variant="body2" sx={{ color: '#64748B', fontWeight: 600 }}>Recorded By:</Typography></Grid>
                <Grid item xs={7}><Typography variant="body2">{selectedTx.createdBy}</Typography></Grid>
              </Grid>

              <Divider sx={{ my: 1 }} />
              <Alert severity="info" icon={<InfoOutlined fontSize="small" />} sx={{ fontSize: '0.78rem' }}>
                Posted ledger transactions are immutable. Corrections and refunds are posted through linked compensating entries.
              </Alert>
            </Stack>
          )}
        </DialogContent>
        <DialogActions sx={{ p: 2, borderTop: '1px solid #E2E8F0' }}>
          <Button onClick={() => setDetailOpen(false)} variant="contained" sx={{ textTransform: 'none', fontWeight: 600 }}>
            Close
          </Button>
        </DialogActions>
      </Dialog>

      {/* Printable Invoice / Receipt Modal */}
      <Dialog open={invoiceOpen} onClose={() => setInvoiceOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid #E2E8F0' }}>
          <Typography variant="h6" sx={{ fontWeight: 800 }}>
            Official Order Invoice &amp; Receipt
          </Typography>
          <Button
            variant="outlined"
            size="small"
            startIcon={<PrintRounded />}
            onClick={() => window.print()}
            sx={{ textTransform: 'none', fontWeight: 600 }}
          >
            Print Receipt
          </Button>
        </DialogTitle>
        <DialogContent sx={{ p: 4 }}>
          {invoiceLoading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
              <CircularProgress />
            </Box>
          ) : invoice ? (
            <Box id="printable-invoice">
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 3 }}>
                <Box>
                  <Typography variant="h5" sx={{ fontWeight: 900, color: '#1E1B4B' }}>
                    UNIVERSAL COMMERCE
                  </Typography>
                  <Typography variant="caption" sx={{ color: '#64748B', display: 'block' }}>
                    Enterprise Commerce Platform Inc.
                  </Typography>
                  <Typography variant="caption" sx={{ color: '#64748B', display: 'block' }}>
                    Tax ID: US-EIN-987654321 &bull; support@ecommerce.local
                  </Typography>
                </Box>
                <Box sx={{ textAlign: 'right' }}>
                  <Typography variant="h6" sx={{ fontWeight: 800, color: '#0F172A' }}>
                    INVOICE #{invoice.orderNumber}
                  </Typography>
                  <Typography variant="body2" sx={{ color: '#64748B' }}>
                    Date: {invoice.invoiceDate ? new Date(invoice.invoiceDate).toLocaleDateString() : 'N/A'}
                  </Typography>
                  <Chip
                    label={`Payment Status: ${invoice.paymentStatus}`}
                    size="small"
                    color={invoice.paymentStatus === 'PAID' ? 'success' : 'warning'}
                    sx={{ mt: 1, fontWeight: 700 }}
                  />
                </Box>
              </Box>

              <Divider sx={{ my: 2 }} />

              <Box sx={{ mb: 3 }}>
                <Typography variant="overline" sx={{ color: '#64748B', fontWeight: 700 }}>
                  PAYMENT INFORMATION
                </Typography>
                <Typography variant="body2">
                  <strong>Provider:</strong> {invoice.paymentProvider}
                </Typography>
                <Typography variant="body2">
                  <strong>Gateway Token Reference:</strong>{' '}
                  <span style={{ fontFamily: 'monospace' }}>{invoice.tokenizedPaymentReference}</span>
                </Typography>
              </Box>

              {/* Line Items */}
              <TableContainer component={Paper} elevation={0} sx={{ border: '1px solid #E2E8F0', mb: 3 }}>
                <Table size="small">
                  <TableHead sx={{ bgcolor: '#F8FAFC' }}>
                    <TableRow>
                      <TableCell sx={{ fontWeight: 700 }}>Product Description</TableCell>
                      <TableCell sx={{ fontWeight: 700 }}>SKU</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 700 }}>Unit Price</TableCell>
                      <TableCell align="center" sx={{ fontWeight: 700 }}>Qty</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 700 }}>Total</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {invoice.lineItems.map((item, idx) => (
                      <TableRow key={idx}>
                        <TableCell sx={{ fontWeight: 600 }}>{item.productName}</TableCell>
                        <TableCell sx={{ color: '#64748B', fontFamily: 'monospace' }}>{item.sku}</TableCell>
                        <TableCell align="right">${Number(item.unitPrice).toFixed(2)}</TableCell>
                        <TableCell align="center">{item.quantity}</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 700 }}>
                          ${Number(item.subtotal).toFixed(2)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>

              {/* Totals Breakdown */}
              <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 3 }}>
                <Box sx={{ minWidth: 260 }}>
                  <Stack spacing={1}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Typography variant="body2" sx={{ color: '#64748B' }}>Subtotal:</Typography>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>${Number(invoice.subtotal).toFixed(2)}</Typography>
                    </Box>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Typography variant="body2" sx={{ color: '#64748B' }}>Shipping Cost:</Typography>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>${Number(invoice.shippingCost).toFixed(2)}</Typography>
                    </Box>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Typography variant="body2" sx={{ color: '#64748B' }}>
                        Tax (Calculated at Checkout):
                      </Typography>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>${Number(invoice.tax).toFixed(2)}</Typography>
                    </Box>
                    {Number(invoice.discount) > 0 && (
                      <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                        <Typography variant="body2" sx={{ color: '#16A34A' }}>Discount:</Typography>
                        <Typography variant="body2" sx={{ fontWeight: 600, color: '#16A34A' }}>
                          -${Number(invoice.discount).toFixed(2)}
                        </Typography>
                      </Box>
                    )}
                    <Divider />
                    <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Total Paid:</Typography>
                      <Typography variant="subtitle1" sx={{ fontWeight: 900, color: '#1E1B4B' }}>
                        ${Number(invoice.totalAmount).toFixed(2)} {invoice.currency}
                      </Typography>
                    </Box>
                  </Stack>
                </Box>
              </Box>

              <Alert severity="info" icon={<CheckCircleRounded fontSize="small" />} sx={{ fontSize: '0.78rem' }}>
                Itemized invoice generated from verified platform database records. Retain for warranty and tax accounting records.
              </Alert>
            </Box>
          ) : (
            <Typography variant="body2" color="error">
              Unable to locate invoice details for this order.
            </Typography>
          )}
        </DialogContent>
        <DialogActions sx={{ p: 2, borderTop: '1px solid #E2E8F0' }}>
          <Button
            variant="outlined"
            color="secondary"
            onClick={() => {
              if (invoice) {
                setRefundOrderId(String(invoice.orderId));
                setRefundAmount(String(invoice.totalAmount));
                setInvoiceOpen(false);
                setRefundModalOpen(true);
              }
            }}
            sx={{ textTransform: 'none', fontWeight: 600 }}
          >
            Request Refund for Order
          </Button>
          <Button onClick={() => setInvoiceOpen(false)} variant="contained" sx={{ textTransform: 'none', fontWeight: 600 }}>
            Close
          </Button>
        </DialogActions>
      </Dialog>

      {/* Customer Refund Request Modal */}
      <Dialog open={refundModalOpen} onClose={() => setRefundModalOpen(false)} maxWidth="sm" fullWidth>
        <form onSubmit={handleSubmitRefundRequest}>
          <DialogTitle sx={{ fontWeight: 800, borderBottom: '1px solid #E2E8F0' }}>
            Submit Refund Request
          </DialogTitle>
          <DialogContent sx={{ pt: 2 }}>
            <Stack spacing={2.5} sx={{ mt: 1 }}>
              <Typography variant="body2" sx={{ color: '#64748B' }}>
                Refund requests are audited and governed by administrator workflow. Approved refunds post an immutable credit to your ledger.
              </Typography>
              <TextField
                required
                fullWidth
                size="small"
                label="Order ID"
                value={refundOrderId}
                onChange={(e) => setRefundOrderId(e.target.value)}
              />
              <TextField
                required
                fullWidth
                size="small"
                label="Refund Amount (USD)"
                type="number"
                inputProps={{ step: '0.01', min: '0.01' }}
                value={refundAmount}
                onChange={(e) => setRefundAmount(e.target.value)}
              />
              <TextField
                required
                fullWidth
                multiline
                rows={3}
                label="Mandatory Reason for Refund"
                placeholder="e.g. Defective merchandise, duplicate charge, package not received"
                value={refundReason}
                onChange={(e) => setRefundReason(e.target.value)}
              />
              <Alert severity="warning" sx={{ fontSize: '0.78rem' }}>
                Refund claims require review and payment provider confirmation before funds are credited.
              </Alert>
            </Stack>
          </DialogContent>
          <DialogActions sx={{ p: 2, borderTop: '1px solid #E2E8F0' }}>
            <Button onClick={() => setRefundModalOpen(false)} sx={{ textTransform: 'none' }}>
              Cancel
            </Button>
            <Button
              type="submit"
              variant="contained"
              disabled={refundSubmitting}
              sx={{ textTransform: 'none', fontWeight: 600 }}
            >
              {refundSubmitting ? 'Submitting...' : 'Submit Refund Request'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>

      {/* View Customer's Submitted Refund Requests */}
      <Dialog open={refundRequestsOpen} onClose={() => setRefundRequestsOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle sx={{ fontWeight: 800, borderBottom: '1px solid #E2E8F0' }}>
          Your Submitted Refund Requests
        </DialogTitle>
        <DialogContent sx={{ p: 3 }}>
          {refundListLoading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
              <CircularProgress />
            </Box>
          ) : refundList.length === 0 ? (
            <Box sx={{ textAlign: 'center', py: 4 }}>
              <Typography variant="body1" sx={{ color: '#64748B' }}>
                You have no active or historical refund requests.
              </Typography>
            </Box>
          ) : (
            <TableContainer component={Paper} elevation={0} sx={{ border: '1px solid #E2E8F0' }}>
              <Table size="small">
                <TableHead sx={{ bgcolor: '#F8FAFC' }}>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 700 }}>Request ID</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>Order ID</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>Amount</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>Reason</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>Status</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>Decision / Notes</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {refundList.map((req) => (
                    <TableRow key={req.id}>
                      <TableCell sx={{ fontWeight: 700 }}>#{req.id}</TableCell>
                      <TableCell>Order #{req.orderId}</TableCell>
                      <TableCell sx={{ fontWeight: 800, color: '#166534' }}>
                        +${Number(req.amount).toFixed(2)} {req.currency}
                      </TableCell>
                      <TableCell sx={{ maxWidth: 200 }}>{req.reason}</TableCell>
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
                      <TableCell sx={{ color: '#64748B', fontSize: '0.8rem' }}>
                        {req.decisionNotes || 'Pending staff review'}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </DialogContent>
        <DialogActions sx={{ p: 2, borderTop: '1px solid #E2E8F0' }}>
          <Button onClick={() => setRefundRequestsOpen(false)} variant="contained" sx={{ textTransform: 'none' }}>
            Close
          </Button>
        </DialogActions>
      </Dialog>

      {/* Saved Payment Methods Modal */}
      <Dialog open={paymentMethodsOpen} onClose={() => setPaymentMethodsOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 800, borderBottom: '1px solid #E2E8F0' }}>
          Saved Payment Methods (Tokenized)
        </DialogTitle>
        <DialogContent sx={{ p: 3 }}>
          {pmLoading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
              <CircularProgress />
            </Box>
          ) : paymentMethods.length === 0 ? (
            <Box sx={{ textAlign: 'center', py: 4 }}>
              <CreditCardRounded sx={{ fontSize: 44, color: '#CBD5E1', mb: 1 }} />
              <Typography variant="body1" sx={{ color: '#64748B' }}>
                No saved tokenized cards on file.
              </Typography>
            </Box>
          ) : (
            <Stack spacing={2} sx={{ mt: 1 }}>
              {paymentMethods.map((pm) => (
                <Card key={pm.id} variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
                  <Stack direction="row" justifyContent="space-between" alignItems="center">
                    <Stack direction="row" spacing={1.5} alignItems="center">
                      <CreditCardRounded sx={{ color: '#4F46E5', fontSize: 28 }} />
                      <Box>
                        <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                          {pm.cardBrand} &bull;&bull;&bull;&bull; {pm.last4}
                        </Typography>
                        <Typography variant="caption" sx={{ color: '#64748B' }}>
                          Expires: {String(pm.expMonth).padStart(2, '0')}/{pm.expYear} &bull; Provider: {pm.provider}
                        </Typography>
                      </Box>
                    </Stack>
                    {pm.isDefault && (
                      <Chip label="DEFAULT" size="small" color="primary" sx={{ fontWeight: 700, fontSize: '0.65rem' }} />
                    )}
                  </Stack>
                </Card>
              ))}
            </Stack>
          )}

          <Divider sx={{ my: 2 }} />
          <Alert severity="info" icon={<LockRounded fontSize="small" />} sx={{ fontSize: '0.78rem' }}>
            PCI DSS Compliance: Full card numbers and security CVVs are never processed or stored on our servers. Tokens are managed exclusively by the payment gateway.
          </Alert>
        </DialogContent>
        <DialogActions sx={{ p: 2, borderTop: '1px solid #E2E8F0' }}>
          <Button onClick={() => setPaymentMethodsOpen(false)} variant="contained" sx={{ textTransform: 'none' }}>
            Close
          </Button>
        </DialogActions>
      </Dialog>

      {/* Accounting Preferences Modal */}
      <Dialog open={prefOpen} onClose={() => setPrefOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontWeight: 800, borderBottom: '1px solid #E2E8F0' }}>
          Accounting Preferences
        </DialogTitle>
        <DialogContent sx={{ p: 3 }}>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <FormControlLabel
              control={
                <Switch
                  checked={preferences.emailReceipts}
                  onChange={(e) => setPreferences({ ...preferences, emailReceipts: e.target.checked })}
                />
              }
              label={
                <Box>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>Email Order Receipts</Typography>
                  <Typography variant="caption" sx={{ color: '#64748B' }}>Receive itemized receipts automatically after each checkout payment</Typography>
                </Box>
              }
            />
            <FormControlLabel
              control={
                <Switch
                  checked={preferences.statementNotifications}
                  onChange={(e) => setPreferences({ ...preferences, statementNotifications: e.target.checked })}
                />
              }
              label={
                <Box>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>Monthly Statement Notices</Typography>
                  <Typography variant="caption" sx={{ color: '#64748B' }}>Receive monthly account ledger statement notifications</Typography>
                </Box>
              }
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ p: 2, borderTop: '1px solid #E2E8F0' }}>
          <Button onClick={() => setPrefOpen(false)} sx={{ textTransform: 'none' }}>
            Cancel
          </Button>
          <Button
            onClick={handleSavePreferences}
            variant="contained"
            disabled={prefSaving}
            sx={{ textTransform: 'none', fontWeight: 600 }}
          >
            {prefSaving ? 'Saving...' : 'Save Preferences'}
          </Button>
        </DialogActions>
      </Dialog>
    </Container>
  );
}
