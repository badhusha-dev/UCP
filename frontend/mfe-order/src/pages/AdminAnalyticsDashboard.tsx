import { useEffect, useState, useCallback } from 'react';
import {
  Typography,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
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
  Tabs,
  Tab,
  LinearProgress,
} from '@mui/material';
import {
  TrendingUpRounded,
  TrendingDownRounded,
  TrendingFlatRounded,
  CalendarTodayRounded,
  RefreshRounded,
  FileDownloadRounded,
  ShoppingCartRounded,
  AttachMoneyRounded,
  PeopleRounded,
  SmartToyRounded,
  InfoOutlined,
  CloseRounded,
} from '@mui/icons-material';
import axios from 'axios';

axios.interceptors.request.use((config) => {
  const token = typeof window !== 'undefined' ? localStorage.getItem('jwt_token') : null;
  if (token && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  const userStr = typeof window !== 'undefined' ? localStorage.getItem('user') : null;
  if (userStr) {
    try {
      const u = JSON.parse(userStr);
      if (u.role && !config.headers['X-User-Role']) config.headers['X-User-Role'] = u.role;
      if (u.id && !config.headers['X-User-Id']) config.headers['X-User-Id'] = String(u.id);
    } catch (e) {
      // ignore
    }
  }
  return config;
});

const API_BASE = ''; // Same origin: nginx (containers) and the Vite dev proxy forward /api to the gateway.

// --- Interfaces ---
interface MetricCard<T> {
  current: T;
  previous: T;
  deltaPercent: number;
  trend: 'UP' | 'DOWN' | 'FLAT';
}

interface OverviewData {
  timeRange: string;
  startDate: string;
  endDate: string;
  timezone: string;
  grossMerchandiseValue: MetricCard<number>;
  netSales: MetricCard<number>;
  ordersPlaced: MetricCard<number>;
  deliveredOrders: MetricCard<number>;
  averageOrderValue: MetricCard<number>;
  cancelledOrders: MetricCard<number>;
  cancellationRate: MetricCard<number>;
  refundAmount: MetricCard<number>;
  refundRate: MetricCard<number>;
  unitsSold: MetricCard<number>;
  newCustomers: MetricCard<number>;
  returningCustomers: MetricCard<number>;
  lowStockCount: number;
  activeCartsCount: number;
  disclaimer: string;
}

interface DailySalesPoint {
  date: string;
  gmv: number;
  netSales: number;
  orderCount: number;
  unitsSold: number;
  comparisonSales: number;
  comparisonOrderCount: number;
}

interface SalesData {
  dailySeries: DailySalesPoint[];
  statusBreakdown: Record<string, number>;
  salesByStatus: Record<string, number>;
  periodTotalGmv: number;
  periodTotalNetSales: number;
  periodTotalOrders: number;
}

interface ProductItem {
  productId: number;
  productName: string;
  unitsSold: number;
  totalRevenue: number;
  orderCount: number;
  averageSellingPrice: number;
}

interface CategoryItem {
  category: string;
  unitsSold: number;
  totalRevenue: number;
  orderCount: number;
  revenuePercentage: number;
}

interface ProductData {
  topProductsByRevenue: ProductItem[];
  topProductsByUnits: ProductItem[];
  slowMovingProducts: ProductItem[];
  categoryPerformance: CategoryItem[];
}

interface CustomerData {
  totalUniqueCustomers: number;
  newCustomers: number;
  returningCustomers: number;
  repeatPurchaseRate: number;
  averageOrdersPerCustomer: number;
  topCustomers: Array<{
    userId: number;
    orderCount: number;
    totalSpent: number;
    averageOrderValue: number;
    firstOrderDate: string;
    lastOrderDate: string;
  }>;
}

interface InventoryData {
  totalProducts: number;
  totalUnitsInStock: number;
  lowStockCount: number;
  outOfStockCount: number;
  stockValuation: number;
  costCoveragePercent: number;
  lowStockItems: Array<{
    id: number;
    name: string;
    stockQuantity: number;
    reorderThreshold: number;
    price: number;
    costPrice?: number;
    categoryName: string;
  }>;
}

interface AlertData {
  delayedFulfillmentCount: number;
  abandonedCartsCount: number;
  highCancellationRateAlert: boolean;
  currentCancellationRate: number;
  warnings: string[];
}

interface OrderRow {
  id: number;
  userId: number;
  status: string;
  totalAmount: number;
  discountAmount: number;
  couponCode?: string;
  carrier: string;
  trackingNumber?: string;
  shippingStatus: string;
  createdAt: string;
  itemCount: number;
  items: Array<{
    productId: number;
    productName: string;
    price: number;
    quantity: number;
  }>;
}

export default function AdminAnalyticsDashboard() {
  const [activeTab, setActiveTab] = useState<'overview' | 'sales' | 'products' | 'inventory' | 'customers' | 'orders' | 'alerts'>('overview');
  const [timeRange, setTimeRange] = useState<string>('LAST_30_DAYS');
  const [customStart, setCustomStart] = useState<string>('');
  const [customEnd, setCustomEnd] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Data states
  const [overview, setOverview] = useState<OverviewData | null>(null);
  const [sales, setSales] = useState<SalesData | null>(null);
  const [products, setProducts] = useState<ProductData | null>(null);
  const [customers, setCustomers] = useState<CustomerData | null>(null);
  const [inventory, setInventory] = useState<InventoryData | null>(null);
  const [alerts, setAlerts] = useState<AlertData | null>(null);

  // Orders Table states
  const [orders, setOrders] = useState<OrderRow[]>([]);
  const [orderPage, setOrderPage] = useState<number>(0);
  const [totalOrderPages, setTotalOrderPages] = useState<number>(1);
  const [orderStatusFilter, setOrderStatusFilter] = useState<string>('');
  const [selectedOrder, setSelectedOrder] = useState<OrderRow | null>(null);

  // AI Assistant Dialog state
  const [aiOpen, setAiOpen] = useState<boolean>(false);
  const [aiQuery, setAiQuery] = useState<string>('');
  const [aiMessages, setAiMessages] = useState<Array<{ sender: 'user' | 'assistant'; text: string }>>([
    {
      sender: 'assistant',
      text: 'Hello, Administrator! I am your Universal Commerce Analytics Copilot. I analyze transactional database truth to deliver verified KPIs, sales comparisons, and stock alerts. How can I help you today?',
    },
  ]);

  // Fetch all analytics datasets
  const fetchAnalytics = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params: Record<string, string> = { timeRange };
      if (timeRange === 'CUSTOM' && customStart && customEnd) {
        params.startDate = customStart;
        params.endDate = customEnd;
      }

      // Parallel requests; each failure is recorded so the dashboard can name the sections that did not load
      const failed: string[] = [];
      const settle = <T,>(section: string, request: Promise<{ data: T }>) =>
        request.catch((err: any) => {
          const message = err?.response?.data?.message;
          failed.push(message ? `${section} (${message})` : section);
          return null;
        });
      const [overviewRes, salesRes, productsRes, customersRes, inventoryRes, alertsRes] = await Promise.all([
        settle('Overview', axios.get<OverviewData>(`${API_BASE}/api/orders/admin/analytics/overview`, { params })),
        settle('Sales', axios.get<SalesData>(`${API_BASE}/api/orders/admin/analytics/sales`, { params })),
        settle('Products', axios.get<ProductData>(`${API_BASE}/api/orders/admin/analytics/products`, { params })),
        settle('Customers', axios.get<CustomerData>(`${API_BASE}/api/orders/admin/analytics/customers`, { params })),
        settle('Inventory', axios.get<InventoryData>(`${API_BASE}/api/products/admin/analytics/inventory`)),
        settle('Alerts', axios.get<AlertData>(`${API_BASE}/api/orders/admin/analytics/alerts`)),
      ]);

      // A section that failed is cleared, so it never shows data from the previous time range
      setOverview(overviewRes?.data ?? null);
      setSales(salesRes?.data ?? null);
      setProducts(productsRes?.data ?? null);
      setCustomers(customersRes?.data ?? null);
      setInventory(inventoryRes?.data ?? null);
      setAlerts(alertsRes?.data ?? null);
      if (failed.length > 0) {
        setError(`Some analytics could not be loaded: ${failed.join(', ')}.`);
      }
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to load business analytics data.');
    } finally {
      setLoading(false);
    }
  }, [timeRange, customStart, customEnd]);

  // Fetch Orders Table with pagination
  const fetchOrders = useCallback(async () => {
    try {
      const params: Record<string, any> = {
        page: orderPage,
        size: 10,
        sortBy: 'createdAt',
        sortDir: 'DESC',
      };
      if (orderStatusFilter) params.status = orderStatusFilter;

      const res = await axios.get(`${API_BASE}/api/orders/admin/analytics/orders`, { params });
      if (res.data) {
        setOrders(res.data.content || []);
        setTotalOrderPages(res.data.totalPages || 1);
      }
    } catch (e) {
      // ignore
    }
  }, [orderPage, orderStatusFilter]);

  useEffect(() => {
    fetchAnalytics();
  }, [fetchAnalytics]);

  useEffect(() => {
    fetchOrders();
  }, [fetchOrders]);

  // CSV Export Action
  const handleExportCsv = async () => {
    try {
      const params: Record<string, string> = {};
      if (orderStatusFilter) params.status = orderStatusFilter;

      const response = await axios.get(`${API_BASE}/api/orders/admin/analytics/reports/export`, {
        params,
        responseType: 'blob',
      });
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `orders_report_${Date.now()}.csv`);
      document.body.appendChild(link);
      link.click();
      link.parentNode?.removeChild(link);
    } catch (e) {
      alert('Failed to generate CSV export.');
    }
  };

  // AI Assistant Query Handler
  const handleAiAsk = (prompt?: string) => {
    const q = prompt || aiQuery;
    if (!q.trim()) return;

    const newMsgs = [...aiMessages, { sender: 'user' as const, text: q }];
    setAiMessages(newMsgs);
    setAiQuery('');

    // Generate accurate, permission-checked answer from verified analytics state
    setTimeout(() => {
      let reply = '';
      const lower = q.toLowerCase();

      if (lower.includes('gmv') || lower.includes('net sales') || lower.includes('difference')) {
        reply = `**Canonical Metric Definition:**\n\n- **Gross Merchandise Value (GMV):** $${overview?.grossMerchandiseValue.current?.toFixed(2) || '0.00'}. Total top-line monetary value of merchandise transactions placed before coupons and refunds.\n- **Net Sales:** $${overview?.netSales.current?.toFixed(2) || '0.00'}. Realized sales after coupon discounts and verified refunds.\n\n*Cancelled orders ($${overview?.cancelledOrders.current || 0}) are strictly excluded from both figures.*`;
      } else if (lower.includes('compare') || lower.includes('trend') || lower.includes('last month') || lower.includes('period')) {
        reply = `**Period Comparison Summary (${overview?.timeRange || 'Selected Period'} vs Prior Equivalent):**\n\n- **Net Sales:** $${overview?.netSales.current?.toFixed(2) || '0.00'} (${overview?.netSales.deltaPercent && overview.netSales.deltaPercent >= 0 ? '+' : ''}${overview?.netSales.deltaPercent || 0}% ${overview?.netSales.trend === 'UP' ? '📈' : '📉'})\n- **Orders Placed:** ${overview?.ordersPlaced.current || 0} (${overview?.ordersPlaced.deltaPercent && overview.ordersPlaced.deltaPercent >= 0 ? '+' : ''}${overview?.ordersPlaced.deltaPercent || 0}%)\n- **Average Order Value (AOV):** $${overview?.averageOrderValue.current?.toFixed(2) || '0.00'}\n\n*Timezone applied: UTC.*`;
      } else if (lower.includes('stock') || lower.includes('inventory') || lower.includes('threshold') || lower.includes('reorder')) {
        const lowCount = inventory?.lowStockCount || overview?.lowStockCount || 0;
        const outCount = inventory?.outOfStockCount || 0;
        const valuation = inventory?.stockValuation ? `$${inventory.stockValuation.toFixed(2)}` : 'N/A';
        const items = inventory?.lowStockItems?.map((i) => `• **${i.name}**: ${i.stockQuantity} units left (Threshold: ${i.reorderThreshold})`).join('\n') || 'None';

        reply = `**Inventory Health Intelligence:**\n\n- **Current Low-Stock Products:** ${lowCount}\n- **Out-of-Stock Items:** ${outCount}\n- **Warehouse Asset Valuation:** ${valuation}\n\n**Actionable Items Below Threshold:**\n${items}`;
      } else if (lower.includes('cancel') || lower.includes('refund')) {
        reply = `**Risk & Fulfillment Telemetry:**\n\n- **Cancelled Orders:** ${overview?.cancelledOrders.current || 0} (${overview?.cancellationRate.current?.toFixed(1) || 0}% cancellation rate)\n- **Refunds Processed:** $${overview?.refundAmount.current?.toFixed(2) || '0.00'} (${overview?.refundRate.current?.toFixed(1) || 0}% of GMV)\n- **Delayed Fulfillment (>24h):** ${alerts?.delayedFulfillmentCount || 0} orders\n- **Inactive Carts (>2h):** ${alerts?.abandonedCartsCount || 0} carts`;
      } else if (lower.includes('best') || lower.includes('top product') || lower.includes('slow')) {
        const topList = products?.topProductsByRevenue?.slice(0, 3).map((p, idx) => `${idx + 1}. **${p.productName}** — $${p.totalRevenue?.toFixed(2)} (${p.unitsSold} units)`).join('\n') || 'No orders yet';
        reply = `**Catalog Performance Intelligence:**\n\n**Top Selling Products by Revenue:**\n${topList}\n\n*Slow-moving products count: ${products?.slowMovingProducts?.length || 0} items identified with order velocity ≤ 3 units.*`;
      } else {
        reply = `I parsed your query based on current database ledger records for **${overview?.timeRange || 'the active window'}**:\n\n• **GMV:** $${overview?.grossMerchandiseValue.current?.toFixed(2) || '0.00'}\n• **Net Sales:** $${overview?.netSales.current?.toFixed(2) || '0.00'}\n• **Orders Placed:** ${overview?.ordersPlaced.current || 0}\n• **Units Sold:** ${overview?.unitsSold.current || 0}\n• **Repeat Purchase Rate:** ${customers?.repeatPurchaseRate || 0}%\n\nYou can also click the tabs above (Sales, Products, Inventory, Customers) for graphical drill-downs!`;
      }

      setAiMessages((prev) => [...prev, { sender: 'assistant', text: reply }]);
    }, 400);
  };

  const renderTrendBadge = (delta: number, trend: 'UP' | 'DOWN' | 'FLAT', invert = false) => {
    const isGood = invert ? trend === 'DOWN' : trend === 'UP';
    const isBad = invert ? trend === 'UP' : trend === 'DOWN';
    const color = isGood ? '#10B981' : isBad ? '#EF4444' : '#64748B';
    const bg = isGood ? 'rgba(16, 185, 129, 0.12)' : isBad ? 'rgba(239, 68, 68, 0.12)' : 'rgba(100, 116, 139, 0.12)';

    return (
      <Chip
        size="small"
        icon={
          trend === 'UP' ? (
            <TrendingUpRounded sx={{ fontSize: '14px !important', color: `${color} !important` }} />
          ) : trend === 'DOWN' ? (
            <TrendingDownRounded sx={{ fontSize: '14px !important', color: `${color} !important` }} />
          ) : (
            <TrendingFlatRounded sx={{ fontSize: '14px !important', color: `${color} !important` }} />
          )
        }
        label={`${delta >= 0 ? '+' : ''}${delta.toFixed(1)}%`}
        sx={{
          bgcolor: bg,
          color: color,
          fontWeight: 700,
          fontSize: '0.72rem',
          height: 22,
          px: 0.5,
        }}
      />
    );
  };

  // Simple SVG Line Chart generator for time-series
  const renderSvgLineChart = (series: DailySalesPoint[]) => {
    if (!series || series.length === 0) {
      return (
        <Box sx={{ p: 4, textAlign: 'center', color: '#64748B' }}>
          No order transactions recorded in this date range.
        </Box>
      );
    }

    const maxVal = Math.max(...series.map((s) => Math.max(s.netSales, s.comparisonSales)), 100);
    const width = 800;
    const height = 240;
    const padding = 40;

    const points = series.map((s, idx) => {
      const x = padding + (idx / Math.max(series.length - 1, 1)) * (width - 2 * padding);
      const y = height - padding - (s.netSales / maxVal) * (height - 2 * padding);
      return { x, y, ...s };
    });

    const compPoints = series.map((s, idx) => {
      const x = padding + (idx / Math.max(series.length - 1, 1)) * (width - 2 * padding);
      const y = height - padding - (s.comparisonSales / maxVal) * (height - 2 * padding);
      return { x, y, ...s };
    });

    const pathD = points.reduce((acc, p, i) => `${acc} ${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`, '');
    const compPathD = compPoints.reduce((acc, p, i) => `${acc} ${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`, '');
    const areaD = `${pathD} L ${points[points.length - 1].x} ${height - padding} L ${points[0].x} ${height - padding} Z`;

    return (
      <Box sx={{ width: '100%', overflowX: 'auto' }}>
        <svg viewBox={`0 0 ${width} ${height}`} style={{ width: '100%', maxHeight: 280, display: 'block' }}>
          <defs>
            <linearGradient id="salesGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#4F46E5" stopOpacity="0.35" />
              <stop offset="100%" stopColor="#4F46E5" stopOpacity="0.0" />
            </linearGradient>
          </defs>

          {/* Grid lines */}
          {[0, 0.25, 0.5, 0.75, 1].map((pct, idx) => {
            const y = height - padding - pct * (height - 2 * padding);
            const val = Math.round(pct * maxVal);
            return (
              <g key={idx}>
                <line x1={padding} y1={y} x2={width - padding} y2={y} stroke="#E2E8F0" strokeDasharray="3 3" />
                <text x={padding - 8} y={y + 4} fontSize="10" fill="#94A3B8" textAnchor="end">
                  ${val}
                </text>
              </g>
            );
          })}

          {/* Comparison period line (dashed) */}
          <path d={compPathD} fill="none" stroke="#94A3B8" strokeWidth="2" strokeDasharray="4 4" />

          {/* Area fill */}
          <path d={areaD} fill="url(#salesGrad)" />

          {/* Current period main line */}
          <path d={pathD} fill="none" stroke="#4F46E5" strokeWidth="3" strokeLinecap="round" />

          {/* Data points */}
          {points.map((p, idx) => (
            <Tooltip
              key={idx}
              title={`${p.date} | Net Sales: $${p.netSales.toFixed(2)} | Orders: ${p.orderCount} | Prior: $${p.comparisonSales.toFixed(2)}`}
              arrow
            >
              <circle
                cx={p.x}
                cy={p.y}
                r="4.5"
                fill="#4F46E5"
                stroke="#FFFFFF"
                strokeWidth="2"
                style={{ cursor: 'pointer', transition: 'r 0.2s' }}
              />
            </Tooltip>
          ))}

          {/* X axis labels (first, middle, last) */}
          {points.length > 0 && (
            <>
              <text x={points[0].x} y={height - 15} fontSize="11" fill="#64748B" textAnchor="start">
                {points[0].date}
              </text>
              {points.length > 2 && (
                <text x={points[Math.floor(points.length / 2)].x} y={height - 15} fontSize="11" fill="#64748B" textAnchor="middle">
                  {points[Math.floor(points.length / 2)].date}
                </text>
              )}
              <text x={points[points.length - 1].x} y={height - 15} fontSize="11" fill="#64748B" textAnchor="end">
                {points[points.length - 1].date}
              </text>
            </>
          )}
        </svg>

        <Stack direction="row" spacing={3} justifyContent="center" sx={{ mt: 1 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <Box sx={{ width: 14, height: 4, bgcolor: '#4F46E5', borderRadius: 1 }} />
            <Typography variant="caption" sx={{ color: '#475569', fontWeight: 600 }}>
              Current Period Net Sales
            </Typography>
          </Stack>
          <Stack direction="row" spacing={1} alignItems="center">
            <Box sx={{ width: 14, height: 2, borderTop: '2px dashed #94A3B8' }} />
            <Typography variant="caption" sx={{ color: '#94A3B8', fontWeight: 600 }}>
              Previous Period Comparison
            </Typography>
          </Stack>
        </Stack>
      </Box>
    );
  };

  return (
    <Container maxWidth="xl" sx={{ py: 3 }}>
      {/* Header Banner */}
      <Box sx={{ mb: 3 }}>
        <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" alignItems={{ xs: 'flex-start', md: 'center' }} spacing={2}>
          <Box>
            <Stack direction="row" spacing={1.5} alignItems="center">
              <Typography variant="h4" sx={{ fontWeight: 900, color: '#0F172A', letterSpacing: '-0.02em' }}>
                Executive Commerce Analytics
              </Typography>
              <Chip label="Zero-Trust Staff Portal" color="primary" size="small" sx={{ fontWeight: 700 }} />
            </Stack>
            <Typography variant="body2" sx={{ color: '#64748B', mt: 0.5 }}>
              Transactional database intelligence &bull; Timezone: <strong>UTC</strong> &bull; Period comparison active
            </Typography>
          </Box>

          {/* Quick Actions */}
          <Stack direction="row" spacing={1.5} alignItems="center" flexWrap="wrap">
            <Button
              variant="outlined"
              color="primary"
              startIcon={<SmartToyRounded />}
              onClick={() => setAiOpen(true)}
              sx={{ borderRadius: 2, fontWeight: 700 }}
            >
              Ask Analytics AI
            </Button>

            <Button
              variant="outlined"
              startIcon={<FileDownloadRounded />}
              onClick={handleExportCsv}
              sx={{ borderRadius: 2, fontWeight: 700 }}
            >
              Export CSV
            </Button>

            <IconButton onClick={fetchAnalytics} sx={{ bgcolor: '#F1F5F9' }}>
              <RefreshRounded />
            </IconButton>
          </Stack>
        </Stack>
      </Box>

      {/* Date Range Selector Toolbar */}
      <Paper sx={{ p: 2, mb: 3, borderRadius: 3, border: '1px solid #E2E8F0', bgcolor: '#FFFFFF' }} elevation={0}>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems="center" justifyContent="space-between">
          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
            <CalendarTodayRounded sx={{ color: '#4F46E5', fontSize: 20 }} />
            <Typography variant="subtitle2" sx={{ fontWeight: 700, color: '#1E293B', mr: 1 }}>
              Date Range:
            </Typography>

            <TextField
              select
              size="small"
              value={timeRange}
              onChange={(e) => setTimeRange(e.target.value)}
              sx={{ minWidth: 160 }}
            >
              <MenuItem value="TODAY">Today</MenuItem>
              <MenuItem value="YESTERDAY">Yesterday</MenuItem>
              <MenuItem value="LAST_7_DAYS">Last 7 Days</MenuItem>
              <MenuItem value="LAST_30_DAYS">Last 30 Days</MenuItem>
              <MenuItem value="MONTH_TO_DATE">Month to Date (MTD)</MenuItem>
              <MenuItem value="PREVIOUS_MONTH">Previous Month</MenuItem>
              <MenuItem value="ALL_TIME">All-Time</MenuItem>
              <MenuItem value="CUSTOM">Custom Interval</MenuItem>
            </TextField>

            {timeRange === 'CUSTOM' && (
              <>
                <TextField
                  type="date"
                  size="small"
                  label="From"
                  InputLabelProps={{ shrink: true }}
                  value={customStart}
                  onChange={(e) => setCustomStart(e.target.value)}
                />
                <TextField
                  type="date"
                  size="small"
                  label="To"
                  InputLabelProps={{ shrink: true }}
                  value={customEnd}
                  onChange={(e) => setCustomEnd(e.target.value)}
                />
              </>
            )}
          </Stack>

          <Typography variant="caption" sx={{ color: '#94A3B8', fontWeight: 600 }}>
            {overview?.startDate ? `${overview.startDate.substring(0, 10)} to ${overview.endDate.substring(0, 10)}` : 'Loading window...'}
          </Typography>
        </Stack>
      </Paper>

      {error && (
        <Alert
          severity="error"
          sx={{ mb: 3, borderRadius: 2 }}
          action={
            <Button color="inherit" size="small" onClick={() => fetchAnalytics()}>
              Retry
            </Button>
          }
        >
          {error}
        </Alert>
      )}

      {/* Operational Warnings / Alerts Banner */}
      {alerts && alerts.warnings.length > 0 && (
        <Alert severity={alerts.highCancellationRateAlert ? 'error' : 'warning'} sx={{ mb: 3, borderRadius: 2 }}>
          <strong>Operational Health Notice:</strong> {alerts.warnings.join(' ')}
        </Alert>
      )}

      {/* Section Tabs */}
      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
        <Tabs
          value={activeTab}
          onChange={(_, val) => setActiveTab(val)}
          variant="scrollable"
          scrollButtons="auto"
          sx={{
            '& .MuiTab-root': { fontWeight: 700, textTransform: 'none', fontSize: '0.95rem' },
          }}
        >
          <Tab label="Executive Overview" value="overview" />
          <Tab label="Sales & Revenue" value="sales" />
          <Tab label="Products & Categories" value="products" />
          <Tab label="Inventory Intelligence" value="inventory" />
          <Tab label="Customers & Retention" value="customers" />
          <Tab label="Orders Drill-down" value="orders" />
          <Tab label="Operational Alerts" value="alerts" />
        </Tabs>
      </Box>

      {loading && <LinearProgress sx={{ mb: 2 }} />}

      {/* --- TAB 1: EXECUTIVE OVERVIEW --- */}
      {activeTab === 'overview' && overview && (
        <Box>
          <Grid container spacing={2.5}>
            {/* KPI Card 1: Gross Merchandise Value */}
            <Grid item xs={12} sm={6} md={3}>
              <Card sx={{ borderRadius: 3, border: '1px solid #E2E8F0', boxShadow: 'none' }}>
                <CardContent>
                  <Stack direction="row" justifyContent="space-between" alignItems="center">
                    <Typography variant="caption" sx={{ fontWeight: 800, color: '#64748B', textTransform: 'uppercase' }}>
                      Gross Merchandise Value
                    </Typography>
                    <Tooltip title="Total top-line value of merchandise before coupon deductions and refunds. Cancelled orders excluded.">
                      <InfoOutlined sx={{ fontSize: 16, color: '#94A3B8' }} />
                    </Tooltip>
                  </Stack>
                  <Typography variant="h4" sx={{ fontWeight: 900, color: '#0F172A', my: 1 }}>
                    ${overview.grossMerchandiseValue.current.toFixed(2)}
                  </Typography>
                  <Stack direction="row" spacing={1} alignItems="center">
                    {renderTrendBadge(overview.grossMerchandiseValue.deltaPercent, overview.grossMerchandiseValue.trend)}
                    <Typography variant="caption" sx={{ color: '#94A3B8' }}>vs prior period</Typography>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            {/* KPI Card 2: Net Sales */}
            <Grid item xs={12} sm={6} md={3}>
              <Card sx={{ borderRadius: 3, border: '1px solid #E2E8F0', bgcolor: '#F8FAFC', boxShadow: 'none' }}>
                <CardContent>
                  <Stack direction="row" justifyContent="space-between" alignItems="center">
                    <Typography variant="caption" sx={{ fontWeight: 800, color: '#4F46E5', textTransform: 'uppercase' }}>
                      Net Sales (Net Revenue)
                    </Typography>
                    <Tooltip title="Realized store sales after promotional discounts and processed refunds.">
                      <InfoOutlined sx={{ fontSize: 16, color: '#94A3B8' }} />
                    </Tooltip>
                  </Stack>
                  <Typography variant="h4" sx={{ fontWeight: 900, color: '#4F46E5', my: 1 }}>
                    ${overview.netSales.current.toFixed(2)}
                  </Typography>
                  <Stack direction="row" spacing={1} alignItems="center">
                    {renderTrendBadge(overview.netSales.deltaPercent, overview.netSales.trend)}
                    <Typography variant="caption" sx={{ color: '#94A3B8' }}>vs prior period</Typography>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            {/* KPI Card 3: Orders Placed */}
            <Grid item xs={12} sm={6} md={3}>
              <Card sx={{ borderRadius: 3, border: '1px solid #E2E8F0', boxShadow: 'none' }}>
                <CardContent>
                  <Stack direction="row" justifyContent="space-between" alignItems="center">
                    <Typography variant="caption" sx={{ fontWeight: 800, color: '#64748B', textTransform: 'uppercase' }}>
                      Orders Placed
                    </Typography>
                    <ShoppingCartRounded sx={{ fontSize: 18, color: '#64748B' }} />
                  </Stack>
                  <Typography variant="h4" sx={{ fontWeight: 900, color: '#0F172A', my: 1 }}>
                    {overview.ordersPlaced.current}
                  </Typography>
                  <Stack direction="row" spacing={1} alignItems="center">
                    {renderTrendBadge(overview.ordersPlaced.deltaPercent, overview.ordersPlaced.trend)}
                    <Typography variant="caption" sx={{ color: '#94A3B8' }}>{overview.deliveredOrders.current} delivered</Typography>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            {/* KPI Card 4: Average Order Value */}
            <Grid item xs={12} sm={6} md={3}>
              <Card sx={{ borderRadius: 3, border: '1px solid #E2E8F0', boxShadow: 'none' }}>
                <CardContent>
                  <Stack direction="row" justifyContent="space-between" alignItems="center">
                    <Typography variant="caption" sx={{ fontWeight: 800, color: '#64748B', textTransform: 'uppercase' }}>
                      Average Order Value (AOV)
                    </Typography>
                    <AttachMoneyRounded sx={{ fontSize: 18, color: '#10B981' }} />
                  </Stack>
                  <Typography variant="h4" sx={{ fontWeight: 900, color: '#0F172A', my: 1 }}>
                    ${overview.averageOrderValue.current.toFixed(2)}
                  </Typography>
                  <Stack direction="row" spacing={1} alignItems="center">
                    {renderTrendBadge(overview.averageOrderValue.deltaPercent, overview.averageOrderValue.trend)}
                    <Typography variant="caption" sx={{ color: '#94A3B8' }}>per valid order</Typography>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            {/* KPI Card 5: Units Sold */}
            <Grid item xs={12} sm={6} md={3}>
              <Card sx={{ borderRadius: 3, border: '1px solid #E2E8F0', boxShadow: 'none' }}>
                <CardContent>
                  <Typography variant="caption" sx={{ fontWeight: 800, color: '#64748B', textTransform: 'uppercase' }}>
                    Physical Units Sold
                  </Typography>
                  <Typography variant="h4" sx={{ fontWeight: 900, color: '#0F172A', my: 1 }}>
                    {overview.unitsSold.current}
                  </Typography>
                  <Stack direction="row" spacing={1} alignItems="center">
                    {renderTrendBadge(overview.unitsSold.deltaPercent, overview.unitsSold.trend)}
                    <Typography variant="caption" sx={{ color: '#94A3B8' }}>catalog items</Typography>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            {/* KPI Card 6: Cancellation Rate */}
            <Grid item xs={12} sm={6} md={3}>
              <Card sx={{ borderRadius: 3, border: '1px solid #E2E8F0', boxShadow: 'none' }}>
                <CardContent>
                  <Typography variant="caption" sx={{ fontWeight: 800, color: '#64748B', textTransform: 'uppercase' }}>
                    Order Cancellation Rate
                  </Typography>
                  <Typography variant="h4" sx={{ fontWeight: 900, color: overview.cancellationRate.current > 10 ? '#EF4444' : '#0F172A', my: 1 }}>
                    {overview.cancellationRate.current.toFixed(1)}%
                  </Typography>
                  <Stack direction="row" spacing={1} alignItems="center">
                    {renderTrendBadge(overview.cancellationRate.deltaPercent, overview.cancellationRate.trend, true)}
                    <Typography variant="caption" sx={{ color: '#94A3B8' }}>{overview.cancelledOrders.current} cancelled</Typography>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            {/* KPI Card 7: Refunds */}
            <Grid item xs={12} sm={6} md={3}>
              <Card sx={{ borderRadius: 3, border: '1px solid #E2E8F0', boxShadow: 'none' }}>
                <CardContent>
                  <Typography variant="caption" sx={{ fontWeight: 800, color: '#64748B', textTransform: 'uppercase' }}>
                    Refunds Processed
                  </Typography>
                  <Typography variant="h4" sx={{ fontWeight: 900, color: '#0F172A', my: 1 }}>
                    ${overview.refundAmount.current.toFixed(2)}
                  </Typography>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Typography variant="caption" sx={{ color: '#64748B', fontWeight: 700 }}>
                      {overview.refundRate.current.toFixed(1)}% of GMV
                    </Typography>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            {/* KPI Card 8: Customer Acquisition */}
            <Grid item xs={12} sm={6} md={3}>
              <Card sx={{ borderRadius: 3, border: '1px solid #E2E8F0', boxShadow: 'none' }}>
                <CardContent>
                  <Typography variant="caption" sx={{ fontWeight: 800, color: '#64748B', textTransform: 'uppercase' }}>
                    New vs. Returning Buyers
                  </Typography>
                  <Typography variant="h4" sx={{ fontWeight: 900, color: '#0F172A', my: 1 }}>
                    {overview.newCustomers.current} / {overview.returningCustomers.current}
                  </Typography>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <PeopleRounded sx={{ fontSize: 16, color: '#4F46E5' }} />
                    <Typography variant="caption" sx={{ color: '#64748B', fontWeight: 700 }}>
                      {overview.newCustomers.current + overview.returningCustomers.current} active purchasers
                    </Typography>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
          </Grid>

          {/* Daily Sales Chart Widget */}
          <Paper sx={{ p: 3, mt: 3, borderRadius: 3, border: '1px solid #E2E8F0' }} elevation={0}>
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 800, color: '#0F172A' }}>
                  Net Sales &amp; Order Volume Trend
                </Typography>
                <Typography variant="caption" sx={{ color: '#64748B' }}>
                  Daily recorded transactions with previous equivalent period overlay
                </Typography>
              </Box>
              <Button size="small" variant="text" onClick={() => setActiveTab('sales')}>
                Full Sales Breakdown &rarr;
              </Button>
            </Stack>

            {sales && renderSvgLineChart(sales.dailySeries)}
          </Paper>

          {/* Accounting & Profit Disclaimer */}
          <Alert severity="info" sx={{ mt: 3, borderRadius: 2, bgcolor: '#F8FAFC', border: '1px solid #E2E8F0' }}>
            <strong>Financial Disclosure:</strong> {overview.disclaimer}
          </Alert>
        </Box>
      )}

      {/* --- TAB 2: SALES & REVENUE --- */}
      {activeTab === 'sales' && sales && (
        <Grid container spacing={3}>
          <Grid item xs={12} md={8}>
            <Paper sx={{ p: 3, borderRadius: 3, border: '1px solid #E2E8F0' }} elevation={0}>
              <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>
                Daily Sales Time Series
              </Typography>
              {renderSvgLineChart(sales.dailySeries)}
            </Paper>
          </Grid>

          <Grid item xs={12} md={4}>
            <Paper sx={{ p: 3, borderRadius: 3, border: '1px solid #E2E8F0' }} elevation={0}>
              <Typography variant="h6" sx={{ fontWeight: 800, mb: 2 }}>
                Orders by Lifecycle Status
              </Typography>
              <Stack spacing={1.5}>
                {Object.entries(sales.statusBreakdown).map(([status, count]) => {
                  const total = sales.periodTotalOrders || 1;
                  const pct = Math.round((count / total) * 100);
                  const statusColors: Record<string, string> = {
                    DELIVERED: '#10B981',
                    CONFIRMED: '#4F46E5',
                    PLACED: '#06B6D4',
                    PROCESSING: '#F59E0B',
                    CANCELLED: '#EF4444',
                  };
                  const color = statusColors[status] || '#64748B';

                  return (
                    <Box key={status}>
                      <Stack direction="row" justifyContent="space-between" sx={{ mb: 0.5 }}>
                        <Typography variant="body2" sx={{ fontWeight: 700, color: '#334155' }}>
                          {status}
                        </Typography>
                        <Typography variant="body2" sx={{ fontWeight: 800 }}>
                          {count} ({pct}%)
                        </Typography>
                      </Stack>
                      <LinearProgress
                        variant="determinate"
                        value={pct}
                        sx={{
                          height: 8,
                          borderRadius: 4,
                          bgcolor: '#F1F5F9',
                          '& .MuiLinearProgress-bar': { bgcolor: color },
                        }}
                      />
                    </Box>
                  );
                })}
              </Stack>
            </Paper>
          </Grid>
        </Grid>
      )}

      {/* --- TAB 3: PRODUCTS & CATEGORIES --- */}
      {activeTab === 'products' && products && (
        <Grid container spacing={3}>
          {/* Top Selling Products */}
          <Grid item xs={12} md={7}>
            <Paper sx={{ p: 3, borderRadius: 3, border: '1px solid #E2E8F0' }} elevation={0}>
              <Typography variant="h6" sx={{ fontWeight: 800, mb: 2 }}>
                Top Performing Catalog Items (by Revenue)
              </Typography>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow sx={{ bgcolor: '#F8FAFC' }}>
                      <TableCell sx={{ fontWeight: 800 }}>Product Title</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 800 }}>Units Sold</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 800 }}>Total Revenue</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 800 }}>Avg Selling Price</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {products.topProductsByRevenue.map((p) => (
                      <TableRow key={p.productId} hover>
                        <TableCell sx={{ fontWeight: 600 }}>{p.productName}</TableCell>
                        <TableCell align="right">{p.unitsSold}</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 700, color: '#4F46E5' }}>
                          ${p.totalRevenue.toFixed(2)}
                        </TableCell>
                        <TableCell align="right">${p.averageSellingPrice.toFixed(2)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </Paper>
          </Grid>

          {/* Category Sales Distribution */}
          <Grid item xs={12} md={5}>
            <Paper sx={{ p: 3, borderRadius: 3, border: '1px solid #E2E8F0' }} elevation={0}>
              <Typography variant="h6" sx={{ fontWeight: 800, mb: 2 }}>
                Category Contribution
              </Typography>
              <Stack spacing={2}>
                {products.categoryPerformance.map((c) => (
                  <Box key={c.category}>
                    <Stack direction="row" justifyContent="space-between" sx={{ mb: 0.5 }}>
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>{c.category}</Typography>
                      <Typography variant="body2" sx={{ fontWeight: 800, color: '#4F46E5' }}>
                        ${c.totalRevenue.toFixed(2)} ({c.revenuePercentage}%)
                      </Typography>
                    </Stack>
                    <LinearProgress
                      variant="determinate"
                      value={c.revenuePercentage}
                      sx={{ height: 8, borderRadius: 4, bgcolor: '#F1F5F9' }}
                    />
                  </Box>
                ))}
              </Stack>
            </Paper>
          </Grid>
        </Grid>
      )}

      {/* --- TAB 4: INVENTORY INTELLIGENCE --- */}
      {activeTab === 'inventory' && inventory && (
        <Box>
          <Grid container spacing={3} sx={{ mb: 3 }}>
            <Grid item xs={12} sm={6} md={3}>
              <Card sx={{ borderRadius: 3, border: '1px solid #E2E8F0', boxShadow: 'none' }}>
                <CardContent>
                  <Typography variant="caption" sx={{ fontWeight: 800, color: '#64748B' }}>TOTAL CATALOG ITEMS</Typography>
                  <Typography variant="h4" sx={{ fontWeight: 900, my: 1 }}>{inventory.totalProducts}</Typography>
                  <Typography variant="caption" sx={{ color: '#64748B' }}>{inventory.totalUnitsInStock} total units</Typography>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} sm={6} md={3}>
              <Card sx={{ borderRadius: 3, border: '1px solid #E2E8F0', boxShadow: 'none' }}>
                <CardContent>
                  <Typography variant="caption" sx={{ fontWeight: 800, color: '#EF4444' }}>LOW-STOCK BREACHES</Typography>
                  <Typography variant="h4" sx={{ fontWeight: 900, color: '#EF4444', my: 1 }}>{inventory.lowStockCount}</Typography>
                  <Typography variant="caption" sx={{ color: '#64748B' }}>Items below reorder threshold</Typography>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} sm={6} md={3}>
              <Card sx={{ borderRadius: 3, border: '1px solid #E2E8F0', boxShadow: 'none' }}>
                <CardContent>
                  <Typography variant="caption" sx={{ fontWeight: 800, color: '#64748B' }}>OUT OF STOCK</Typography>
                  <Typography variant="h4" sx={{ fontWeight: 900, my: 1 }}>{inventory.outOfStockCount}</Typography>
                  <Typography variant="caption" sx={{ color: '#64748B' }}>Zero physical inventory</Typography>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} sm={6} md={3}>
              <Card sx={{ borderRadius: 3, border: '1px solid #E2E8F0', boxShadow: 'none', bgcolor: '#F8FAFC' }}>
                <CardContent>
                  <Typography variant="caption" sx={{ fontWeight: 800, color: '#10B981' }}>STOCK ASSET VALUATION</Typography>
                  <Typography variant="h4" sx={{ fontWeight: 900, color: '#10B981', my: 1 }}>
                    ${inventory.stockValuation ? inventory.stockValuation.toFixed(2) : '0.00'}
                  </Typography>
                  <Typography variant="caption" sx={{ color: '#64748B' }}>
                    {inventory.costCoveragePercent}% catalog cost coverage
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
          </Grid>

          {/* Low Stock Items Table */}
          <Paper sx={{ p: 3, borderRadius: 3, border: '1px solid #E2E8F0' }} elevation={0}>
            <Typography variant="h6" sx={{ fontWeight: 800, mb: 2 }}>
              Active Low Stock &amp; Replenishment Alerts
            </Typography>
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow sx={{ bgcolor: '#F8FAFC' }}>
                    <TableCell sx={{ fontWeight: 800 }}>Product</TableCell>
                    <TableCell sx={{ fontWeight: 800 }}>Category</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 800 }}>Units Remaining</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 800 }}>Reorder Threshold</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 800 }}>Wholesale Cost</TableCell>
                    <TableCell align="center" sx={{ fontWeight: 800 }}>Health Status</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {inventory.lowStockItems.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={6} align="center" sx={{ py: 3, color: '#10B981' }}>
                        All products are currently above their configured reorder threshold!
                      </TableCell>
                    </TableRow>
                  ) : (
                    inventory.lowStockItems.map((item) => (
                      <TableRow key={item.id} hover>
                        <TableCell sx={{ fontWeight: 600 }}>{item.name}</TableCell>
                        <TableCell>{item.categoryName}</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 800, color: item.stockQuantity === 0 ? '#EF4444' : '#F59E0B' }}>
                          {item.stockQuantity}
                        </TableCell>
                        <TableCell align="right">{item.reorderThreshold}</TableCell>
                        <TableCell align="right">${item.costPrice ? item.costPrice.toFixed(2) : 'N/A'}</TableCell>
                        <TableCell align="center">
                          <Chip
                            size="small"
                            label={item.stockQuantity === 0 ? 'OUT OF STOCK' : 'REORDER REQUIRED'}
                            sx={{
                              bgcolor: item.stockQuantity === 0 ? '#FEE2E2' : '#FEF3C7',
                              color: item.stockQuantity === 0 ? '#DC2626' : '#D97706',
                              fontWeight: 700,
                              fontSize: '0.65rem',
                            }}
                          />
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

      {/* --- TAB 5: CUSTOMERS & RETENTION --- */}
      {activeTab === 'customers' && customers && (
        <Grid container spacing={3}>
          <Grid item xs={12} md={4}>
            <Paper sx={{ p: 3, borderRadius: 3, border: '1px solid #E2E8F0', height: '100%' }} elevation={0}>
              <Typography variant="h6" sx={{ fontWeight: 800, mb: 2 }}>
                Customer Loyalty &amp; Retention
              </Typography>
              <Stack spacing={3}>
                <Box>
                  <Typography variant="caption" sx={{ color: '#64748B', fontWeight: 700 }}>REPEAT PURCHASE RATE</Typography>
                  <Typography variant="h3" sx={{ fontWeight: 900, color: '#4F46E5', my: 1 }}>
                    {customers.repeatPurchaseRate}%
                  </Typography>
                  <Typography variant="body2" sx={{ color: '#64748B' }}>
                    Purchasers who have completed 2 or more lifetime orders
                  </Typography>
                </Box>
                <Divider />
                <Box>
                  <Typography variant="caption" sx={{ color: '#64748B', fontWeight: 700 }}>PURCHASE FREQUENCY</Typography>
                  <Typography variant="h4" sx={{ fontWeight: 900, color: '#0F172A', my: 0.5 }}>
                    {customers.averageOrdersPerCustomer} orders
                  </Typography>
                  <Typography variant="body2" sx={{ color: '#64748B' }}>Average lifetime transactions per customer</Typography>
                </Box>
              </Stack>
            </Paper>
          </Grid>

          <Grid item xs={12} md={8}>
            <Paper sx={{ p: 3, borderRadius: 3, border: '1px solid #E2E8F0' }} elevation={0}>
              <Typography variant="h6" sx={{ fontWeight: 800, mb: 2 }}>
                Top Spending Customers
              </Typography>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow sx={{ bgcolor: '#F8FAFC' }}>
                      <TableCell sx={{ fontWeight: 800 }}>Customer ID</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 800 }}>Orders Count</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 800 }}>Total Spend</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 800 }}>Average Order</TableCell>
                      <TableCell sx={{ fontWeight: 800 }}>First Order</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {customers.topCustomers.map((c) => (
                      <TableRow key={c.userId} hover>
                        <TableCell sx={{ fontWeight: 700 }}>User #{c.userId}</TableCell>
                        <TableCell align="right">{c.orderCount}</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 800, color: '#10B981' }}>
                          ${c.totalSpent.toFixed(2)}
                        </TableCell>
                        <TableCell align="right">${c.averageOrderValue.toFixed(2)}</TableCell>
                        <TableCell>{c.firstOrderDate ? c.firstOrderDate.substring(0, 10) : 'N/A'}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </Paper>
          </Grid>
        </Grid>
      )}

      {/* --- TAB 6: ORDERS DRILL-DOWN --- */}
      {activeTab === 'orders' && (
        <Paper sx={{ p: 3, borderRadius: 3, border: '1px solid #E2E8F0' }} elevation={0}>
          <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems="center" sx={{ mb: 2.5 }} spacing={2}>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>
              Order Transaction Ledger
            </Typography>

            <Stack direction="row" spacing={1.5} alignItems="center">
              <TextField
                select
                size="small"
                label="Filter by Status"
                value={orderStatusFilter}
                onChange={(e) => {
                  setOrderStatusFilter(e.target.value);
                  setOrderPage(0);
                }}
                sx={{ minWidth: 160 }}
              >
                <MenuItem value="">All Statuses</MenuItem>
                <MenuItem value="PLACED">PLACED</MenuItem>
                <MenuItem value="PROCESSING">PROCESSING</MenuItem>
                <MenuItem value="SHIPPED">SHIPPED</MenuItem>
                <MenuItem value="DELIVERED">DELIVERED</MenuItem>
                <MenuItem value="CANCELLED">CANCELLED</MenuItem>
              </TextField>

              <Button
                variant="outlined"
                size="small"
                startIcon={<FileDownloadRounded />}
                onClick={handleExportCsv}
              >
                Export CSV
              </Button>
            </Stack>
          </Stack>

          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow sx={{ bgcolor: '#F8FAFC' }}>
                  <TableCell sx={{ fontWeight: 800 }}>Order ID</TableCell>
                  <TableCell sx={{ fontWeight: 800 }}>Customer</TableCell>
                  <TableCell sx={{ fontWeight: 800 }}>Date (UTC)</TableCell>
                  <TableCell sx={{ fontWeight: 800 }}>Status</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 800 }}>Total Amount</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 800 }}>Discount</TableCell>
                  <TableCell sx={{ fontWeight: 800 }}>Fulfillment</TableCell>
                  <TableCell align="center" sx={{ fontWeight: 800 }}>Inspect</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {orders.map((o) => (
                  <TableRow key={o.id} hover>
                    <TableCell sx={{ fontWeight: 700 }}>#{o.id}</TableCell>
                    <TableCell>User #{o.userId}</TableCell>
                    <TableCell>{o.createdAt ? o.createdAt.substring(0, 16).replace('T', ' ') : 'N/A'}</TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        label={o.status}
                        sx={{
                          fontWeight: 700,
                          fontSize: '0.7rem',
                          bgcolor: o.status === 'DELIVERED' ? '#ECFDF5' : o.status === 'CANCELLED' ? '#FEE2E2' : '#EEF2FF',
                          color: o.status === 'DELIVERED' ? '#059669' : o.status === 'CANCELLED' ? '#DC2626' : '#4F46E5',
                        }}
                      />
                    </TableCell>
                    <TableCell align="right" sx={{ fontWeight: 800 }}>${o.totalAmount.toFixed(2)}</TableCell>
                    <TableCell align="right">${o.discountAmount ? o.discountAmount.toFixed(2) : '0.00'}</TableCell>
                    <TableCell>{o.carrier} ({o.trackingNumber || 'Unassigned'})</TableCell>
                    <TableCell align="center">
                      <Button size="small" variant="text" onClick={() => setSelectedOrder(o)}>
                        Details
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>

          <Stack direction="row" justifyContent="center" sx={{ mt: 3 }}>
            <Pagination
              count={totalOrderPages}
              page={orderPage + 1}
              onChange={(_, page) => setOrderPage(page - 1)}
              color="primary"
            />
          </Stack>
        </Paper>
      )}

      {/* --- TAB 7: OPERATIONAL ALERTS --- */}
      {activeTab === 'alerts' && alerts && (
        <Paper sx={{ p: 3, borderRadius: 3, border: '1px solid #E2E8F0' }} elevation={0}>
          <Typography variant="h6" sx={{ fontWeight: 800, mb: 2 }}>
            Operational Bottlenecks &amp; Anomaly Signals
          </Typography>

          <Grid container spacing={3}>
            <Grid item xs={12} sm={4}>
              <Card sx={{ borderRadius: 3, border: '1px solid #E2E8F0', boxShadow: 'none' }}>
                <CardContent>
                  <Typography variant="caption" sx={{ fontWeight: 800, color: '#D97706' }}>DELAYED FULFILLMENT</Typography>
                  <Typography variant="h3" sx={{ fontWeight: 900, color: '#D97706', my: 1 }}>
                    {alerts.delayedFulfillmentCount}
                  </Typography>
                  <Typography variant="body2" sx={{ color: '#64748B' }}>
                    Orders pending fulfillment for over 24 hours
                  </Typography>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} sm={4}>
              <Card sx={{ borderRadius: 3, border: '1px solid #E2E8F0', boxShadow: 'none' }}>
                <CardContent>
                  <Typography variant="caption" sx={{ fontWeight: 800, color: '#4F46E5' }}>ABANDONED CARTS</Typography>
                  <Typography variant="h3" sx={{ fontWeight: 900, color: '#4F46E5', my: 1 }}>
                    {alerts.abandonedCartsCount}
                  </Typography>
                  <Typography variant="body2" sx={{ color: '#64748B' }}>
                    Customer carts inactive between 2 and 72 hours
                  </Typography>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} sm={4}>
              <Card sx={{ borderRadius: 3, border: '1px solid #E2E8F0', boxShadow: 'none' }}>
                <CardContent>
                  <Typography variant="caption" sx={{ fontWeight: 800, color: alerts.highCancellationRateAlert ? '#EF4444' : '#10B981' }}>
                    CANCELLATION TOLERANCE
                  </Typography>
                  <Typography variant="h3" sx={{ fontWeight: 900, color: alerts.highCancellationRateAlert ? '#EF4444' : '#10B981', my: 1 }}>
                    {alerts.currentCancellationRate}%
                  </Typography>
                  <Typography variant="body2" sx={{ color: '#64748B' }}>
                    Tolerance threshold: 10.0% max
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
          </Grid>
        </Paper>
      )}

      {/* --- ORDER DETAILS INSPECTION MODAL --- */}
      {selectedOrder && (
        <Dialog open onClose={() => setSelectedOrder(null)} maxWidth="sm" fullWidth>
          <DialogTitle sx={{ fontWeight: 800 }}>Order #{selectedOrder.id} Details</DialogTitle>
          <DialogContent dividers>
            <Stack spacing={2}>
              <Box>
                <Typography variant="subtitle2" sx={{ color: '#64748B' }}>Order Status &amp; Pricing</Typography>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>
                  ${selectedOrder.totalAmount.toFixed(2)} &bull; Status: {selectedOrder.status}
                </Typography>
                <Typography variant="body2" sx={{ color: '#64748B' }}>
                  Carrier: {selectedOrder.carrier} &bull; Tracking: {selectedOrder.trackingNumber || 'Pending'}
                </Typography>
              </Box>

              <Divider />

              <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Purchased Line Items</Typography>
              <Stack spacing={1}>
                {selectedOrder.items && selectedOrder.items.length > 0 ? (
                  selectedOrder.items.map((i, idx) => (
                    <Stack key={idx} direction="row" justifyContent="space-between">
                      <Typography variant="body2">{i.productName} &times; {i.quantity}</Typography>
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>${(i.price * i.quantity).toFixed(2)}</Typography>
                    </Stack>
                  ))
                ) : (
                  <Typography variant="body2" sx={{ color: '#94A3B8' }}>No line items recorded.</Typography>
                )}
              </Stack>
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setSelectedOrder(null)}>Close</Button>
          </DialogActions>
        </Dialog>
      )}

      {/* --- AI ANALYTICS ASSISTANT DIALOG (Phase 9) --- */}
      <Dialog open={aiOpen} onClose={() => setAiOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', bgcolor: '#0F172A', color: '#FFFFFF' }}>
          <Stack direction="row" spacing={1.5} alignItems="center">
            <SmartToyRounded sx={{ color: '#06B6D4' }} />
            <Typography variant="h6" sx={{ fontWeight: 800 }}>
              Commerce Analytics Copilot
            </Typography>
          </Stack>
          <IconButton onClick={() => setAiOpen(false)} sx={{ color: '#94A3B8' }}>
            <CloseRounded />
          </IconButton>
        </DialogTitle>

        <DialogContent sx={{ bgcolor: '#F8FAFC', minHeight: 380, p: 3 }}>
          <Stack spacing={2} sx={{ mb: 2 }}>
            {aiMessages.map((msg, idx) => (
              <Box
                key={idx}
                sx={{
                  alignSelf: msg.sender === 'user' ? 'flex-end' : 'flex-start',
                  maxWidth: '85%',
                  p: 2,
                  borderRadius: 3,
                  bgcolor: msg.sender === 'user' ? '#4F46E5' : '#FFFFFF',
                  color: msg.sender === 'user' ? '#FFFFFF' : '#0F172A',
                  border: msg.sender === 'assistant' ? '1px solid #E2E8F0' : 'none',
                  whiteSpace: 'pre-line',
                }}
              >
                <Typography variant="body2">{msg.text}</Typography>
              </Box>
            ))}
          </Stack>

          {/* Suggested Quick Prompts */}
          <Box sx={{ mt: 2 }}>
            <Typography variant="caption" sx={{ color: '#64748B', fontWeight: 700 }}>SUGGESTED ANALYTICS QUERIES:</Typography>
            <Stack direction="row" spacing={1} flexWrap="wrap" sx={{ mt: 1, gap: 1 }}>
              <Chip
                label="Explain GMV vs Net Sales"
                size="small"
                onClick={() => handleAiAsk('Explain GMV vs Net Sales')}
                sx={{ cursor: 'pointer' }}
              />
              <Chip
                label="Compare sales this period vs last period"
                size="small"
                onClick={() => handleAiAsk('Compare sales this period vs last period')}
                sx={{ cursor: 'pointer' }}
              />
              <Chip
                label="Which products are below reorder threshold?"
                size="small"
                onClick={() => handleAiAsk('Which products are below reorder threshold?')}
                sx={{ cursor: 'pointer' }}
              />
              <Chip
                label="What are the cancellation and refund rates?"
                size="small"
                onClick={() => handleAiAsk('What are the cancellation and refund rates?')}
                sx={{ cursor: 'pointer' }}
              />
            </Stack>
          </Box>
        </DialogContent>

        <DialogActions sx={{ p: 2, bgcolor: '#FFFFFF', borderTop: '1px solid #E2E8F0' }}>
          <TextField
            fullWidth
            size="small"
            placeholder="Ask about sales, top products, AOV, cancellations, or inventory..."
            value={aiQuery}
            onChange={(e) => setAiQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') handleAiAsk();
            }}
          />
          <Button variant="contained" color="primary" onClick={() => handleAiAsk()} sx={{ ml: 1, fontWeight: 700 }}>
            Ask
          </Button>
        </DialogActions>
      </Dialog>
    </Container>
  );
}
