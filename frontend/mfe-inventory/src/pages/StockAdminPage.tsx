import React, { useState, useEffect } from 'react';
import {
  Box,
  Typography,
  Card,
  CardContent,
  Grid,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Button,
  Chip,
  IconButton,
  TextField,
  InputAdornment,
  Tooltip,
  CircularProgress,
  Alert,
  FormControlLabel,
  Checkbox,
  Stack,
} from '@mui/material';
import {
  SearchOutlined,
  FileDownloadOutlined,
  FileUploadOutlined,
  RefreshOutlined,
  EditOutlined,
  WarningAmberRounded,
  CheckCircleOutline,
  Inventory2Outlined,
  AttachMoneyRounded,
  LockClockOutlined,
} from '@mui/icons-material';
import type { InventoryItem, InventoryAnalyticsSummary } from '../types/inventory';
import { inventoryApi } from '../api/inventoryApi';
import { StockAdjustmentModal } from '../components/StockAdjustmentModal';
import { CsvImportModal } from '../components/CsvImportModal';

export const StockAdminPage: React.FC = () => {
  const [items, setItems] = useState<InventoryItem[]>([]);
  const [summary, setSummary] = useState<InventoryAnalyticsSummary | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Search & Filter
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [lowStockOnly, setLowStockOnly] = useState<boolean>(false);

  // Modals
  const [selectedItem, setSelectedItem] = useState<InventoryItem | null>(null);
  const [adjustModalOpen, setAdjustModalOpen] = useState<boolean>(false);
  const [importModalOpen, setImportModalOpen] = useState<boolean>(false);

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [itemsData, summaryData] = await Promise.all([
        inventoryApi.getAllItems(),
        inventoryApi.getAnalyticsSummary(),
      ]);
      setItems(itemsData);
      setSummary(summaryData);
    } catch (err: any) {
      setError(err?.response?.data?.message || err?.message || 'Failed to load inventory data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const handleExportCsv = async () => {
    try {
      const csvData = await inventoryApi.exportCsv();
      const blob = new Blob([csvData], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.setAttribute('href', url);
      link.setAttribute('download', `inventory_catalog_${new Date().toISOString().slice(0, 10)}.csv`);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    } catch (err: any) {
      alert('Failed to export CSV: ' + (err?.message || 'Unknown error'));
    }
  };

  const filteredItems = items.filter((item) => {
    const matchesSearch =
      !searchQuery ||
      item.sku.toLowerCase().includes(searchQuery.toLowerCase()) ||
      String(item.productId).includes(searchQuery) ||
      (item.warehouseLocation && item.warehouseLocation.toLowerCase().includes(searchQuery.toLowerCase()));

    const matchesLowStock = !lowStockOnly || item.lowStock || item.availableQuantity <= 0;

    return matchesSearch && matchesLowStock;
  });

  return (
    <Box>
      {/* Header bar */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3, flexWrap: 'wrap', gap: 2 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 800, color: '#0F172A', letterSpacing: '-0.02em' }}>
            Authoritative Inventory Management
          </Typography>
          <Typography variant="body2" sx={{ color: '#64748B' }}>
            Multi-warehouse stock tracking, real-time reservations, and cycle audit controls.
          </Typography>
        </Box>

        <Stack direction="row" spacing={1.5}>
          <Button
            variant="outlined"
            startIcon={<FileUploadOutlined />}
            onClick={() => setImportModalOpen(true)}
            sx={{ fontWeight: 700, borderRadius: 2 }}
          >
            Import CSV
          </Button>

          <Button
            variant="outlined"
            startIcon={<FileDownloadOutlined />}
            onClick={handleExportCsv}
            sx={{ fontWeight: 700, borderRadius: 2 }}
          >
            Export CSV
          </Button>

          <Button
            variant="contained"
            color="primary"
            startIcon={<RefreshOutlined />}
            onClick={fetchData}
            sx={{ fontWeight: 700, borderRadius: 2 }}
          >
            Refresh
          </Button>
        </Stack>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 3 }}>
          {error}
        </Alert>
      )}

      {/* KPI Cards */}
      {summary && (
        <Grid container spacing={2.5} sx={{ mb: 3 }}>
          <Grid item xs={12} sm={6} md={2.4}>
            <Card sx={{ borderRadius: 3, boxShadow: '0 4px 16px -2px rgba(15, 23, 42, 0.06)' }}>
              <CardContent sx={{ p: 2.5, '&:last-child': { pb: 2.5 } }}>
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
                  <Typography variant="caption" sx={{ fontWeight: 700, color: '#64748B', textTransform: 'uppercase' }}>
                    Active SKUs
                  </Typography>
                  <Inventory2Outlined sx={{ color: '#6366F1', fontSize: 20 }} />
                </Box>
                <Typography variant="h5" sx={{ fontWeight: 900, color: '#0F172A' }}>
                  {summary.totalProductsTracked ?? summary.totalSkus ?? items.length}
                </Typography>
                <Typography variant="caption" sx={{ color: '#94A3B8' }}>
                  Managed Catalog Units
                </Typography>
              </CardContent>
            </Card>
          </Grid>

          <Grid item xs={12} sm={6} md={2.4}>
            <Card sx={{ borderRadius: 3, boxShadow: '0 4px 16px -2px rgba(15, 23, 42, 0.06)' }}>
              <CardContent sx={{ p: 2.5, '&:last-child': { pb: 2.5 } }}>
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
                  <Typography variant="caption" sx={{ fontWeight: 700, color: '#64748B', textTransform: 'uppercase' }}>
                    Physical Stock
                  </Typography>
                  <CheckCircleOutline sx={{ color: '#10B981', fontSize: 20 }} />
                </Box>
                <Typography variant="h5" sx={{ fontWeight: 900, color: '#0F172A' }}>
                  {summary.totalPhysicalUnits ?? summary.totalUnitsInStock ?? items.reduce((acc, i) => acc + (i.stockQuantity || 0), 0)}
                </Typography>
                <Typography variant="caption" sx={{ color: '#10B981', fontWeight: 600 }}>
                  {items.reduce((acc, i) => acc + (i.availableQuantity || 0), 0)} Available for Sale
                </Typography>
              </CardContent>
            </Card>
          </Grid>

          <Grid item xs={12} sm={6} md={2.4}>
            <Card sx={{ borderRadius: 3, boxShadow: '0 4px 16px -2px rgba(15, 23, 42, 0.06)' }}>
              <CardContent sx={{ p: 2.5, '&:last-child': { pb: 2.5 } }}>
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
                  <Typography variant="caption" sx={{ fontWeight: 700, color: '#64748B', textTransform: 'uppercase' }}>
                    Checkout Holds
                  </Typography>
                  <LockClockOutlined sx={{ color: '#F59E0B', fontSize: 20 }} />
                </Box>
                <Typography variant="h5" sx={{ fontWeight: 900, color: '#0F172A' }}>
                  {items.reduce((acc, i) => acc + (i.reservedQuantity || 0), 0)}
                </Typography>
                <Typography variant="caption" sx={{ color: '#F59E0B', fontWeight: 600 }}>
                  Active TTL Locks
                </Typography>
              </CardContent>
            </Card>
          </Grid>

          <Grid item xs={12} sm={6} md={2.4}>
            <Card sx={{ borderRadius: 3, boxShadow: '0 4px 16px -2px rgba(15, 23, 42, 0.06)' }}>
              <CardContent sx={{ p: 2.5, '&:last-child': { pb: 2.5 } }}>
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
                  <Typography variant="caption" sx={{ fontWeight: 700, color: '#64748B', textTransform: 'uppercase' }}>
                    Stock Valuation
                  </Typography>
                  <AttachMoneyRounded sx={{ color: '#0EA5E9', fontSize: 20 }} />
                </Box>
                <Typography variant="h5" sx={{ fontWeight: 900, color: '#0F172A' }}>
                  ${Number(summary.stockValuation ?? summary.totalValuation ?? items.reduce((acc, i) => acc + ((i.stockQuantity || 0) * (i.costPrice || 0)), 0)).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                </Typography>
                <Typography variant="caption" sx={{ color: '#94A3B8' }}>
                  Cost Basis Inventory
                </Typography>
              </CardContent>
            </Card>
          </Grid>

          <Grid item xs={12} sm={6} md={2.4}>
            <Card sx={{ borderRadius: 3, boxShadow: '0 4px 16px -2px rgba(15, 23, 42, 0.06)' }}>
              <CardContent sx={{ p: 2.5, '&:last-child': { pb: 2.5 } }}>
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
                  <Typography variant="caption" sx={{ fontWeight: 700, color: '#64748B', textTransform: 'uppercase' }}>
                    Low / OOS Alerts
                  </Typography>
                  <WarningAmberRounded sx={{ color: summary.lowStockCount > 0 ? '#EF4444' : '#10B981', fontSize: 20 }} />
                </Box>
                <Typography variant="h5" sx={{ fontWeight: 900, color: summary.lowStockCount > 0 ? '#EF4444' : '#0F172A' }}>
                  {summary.lowStockCount + summary.outOfStockCount}
                </Typography>
                <Typography variant="caption" sx={{ color: '#EF4444', fontWeight: 600 }}>
                  {summary.outOfStockCount} Out of Stock
                </Typography>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      )}

      {/* Filter and Search Bar */}
      <Card sx={{ mb: 3, borderRadius: 3, boxShadow: '0 2px 10px rgba(15, 23, 42, 0.04)' }}>
        <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems="center" justifyContent="space-between">
            <TextField
              size="small"
              placeholder="Search by SKU, Product ID, or Warehouse Bin..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              sx={{ width: { xs: '100%', sm: 380 } }}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchOutlined sx={{ color: '#94A3B8' }} />
                  </InputAdornment>
                ),
              }}
            />

            <FormControlLabel
              control={
                <Checkbox
                  checked={lowStockOnly}
                  onChange={(e) => setLowStockOnly(e.target.checked)}
                  color="warning"
                />
              }
              label={
                <Typography variant="body2" sx={{ fontWeight: 600, color: '#334155' }}>
                  Show Low Stock &amp; Out-of-Stock Only
                </Typography>
              }
            />
          </Stack>
        </CardContent>
      </Card>

      {/* Table */}
      <TableContainer component={Paper} sx={{ borderRadius: 3, boxShadow: '0 4px 16px -2px rgba(15, 23, 42, 0.06)' }}>
        <Table>
          <TableHead sx={{ bgcolor: '#F8FAFC' }}>
            <TableRow>
              <TableCell sx={{ fontWeight: 700, color: '#475569' }}>PID</TableCell>
              <TableCell sx={{ fontWeight: 700, color: '#475569' }}>SKU Code</TableCell>
              <TableCell sx={{ fontWeight: 700, color: '#475569' }}>Warehouse Location</TableCell>
              <TableCell align="right" sx={{ fontWeight: 700, color: '#475569' }}>Physical On-Hand</TableCell>
              <TableCell align="right" sx={{ fontWeight: 700, color: '#475569' }}>Reserved</TableCell>
              <TableCell align="right" sx={{ fontWeight: 700, color: '#475569' }}>Available</TableCell>
              <TableCell align="right" sx={{ fontWeight: 700, color: '#475569' }}>Reorder Threshold</TableCell>
              <TableCell align="right" sx={{ fontWeight: 700, color: '#475569' }}>Cost Price</TableCell>
              <TableCell align="center" sx={{ fontWeight: 700, color: '#475569' }}>Stock Health</TableCell>
              <TableCell align="center" sx={{ fontWeight: 700, color: '#475569' }}>Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={10} align="center" sx={{ py: 6 }}>
                  <CircularProgress size={36} />
                  <Typography variant="body2" sx={{ mt: 1, color: '#64748B' }}>
                    Loading authoritative stock records...
                  </Typography>
                </TableCell>
              </TableRow>
            ) : filteredItems.length === 0 ? (
              <TableRow>
                <TableCell colSpan={10} align="center" sx={{ py: 6 }}>
                  <Typography variant="subtitle1" sx={{ fontWeight: 700, color: '#64748B' }}>
                    No matching inventory items found
                  </Typography>
                  <Typography variant="caption" sx={{ color: '#94A3B8' }}>
                    Try clearing search criteria or import new SKU stock levels.
                  </Typography>
                </TableCell>
              </TableRow>
            ) : (
              filteredItems.map((item) => {
                const isOutOfStock = item.availableQuantity <= 0;
                const isLowStock = item.lowStock;

                return (
                  <TableRow key={item.id} hover sx={{ '&:last-child td, &:last-child th': { border: 0 } }}>
                    <TableCell sx={{ fontWeight: 600, color: '#64748B' }}>#{item.productId}</TableCell>
                    <TableCell sx={{ fontWeight: 700, color: '#0F172A' }}>{item.sku}</TableCell>
                    <TableCell>
                      <Chip
                        label={item.warehouseLocation || 'UNASSIGNED'}
                        size="small"
                        sx={{ bgcolor: '#F1F5F9', fontWeight: 600, fontSize: '0.75rem' }}
                      />
                    </TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>{item.stockQuantity}</TableCell>
                    <TableCell align="right" sx={{ color: item.reservedQuantity > 0 ? '#F59E0B' : '#94A3B8', fontWeight: 600 }}>
                      {item.reservedQuantity}
                    </TableCell>
                    <TableCell align="right" sx={{ fontWeight: 800, color: isOutOfStock ? '#DC2626' : '#10B981' }}>
                      {item.availableQuantity}
                    </TableCell>
                    <TableCell align="right" sx={{ color: '#64748B' }}>{item.reorderThreshold}</TableCell>
                    <TableCell align="right" sx={{ color: '#475569', fontWeight: 600 }}>
                      ${Number(item.costPrice).toFixed(2)}
                    </TableCell>
                    <TableCell align="center">
                      {isOutOfStock ? (
                        <Chip label="OUT OF STOCK" size="small" color="error" sx={{ fontWeight: 800, height: 22 }} />
                      ) : isLowStock ? (
                        <Chip label="LOW STOCK" size="small" color="warning" sx={{ fontWeight: 800, height: 22 }} />
                      ) : (
                        <Chip label="HEALTHY" size="small" color="success" sx={{ fontWeight: 800, height: 22 }} />
                      )}
                    </TableCell>
                    <TableCell align="center">
                      <Tooltip title="Quick Adjust Stock & Bin">
                        <IconButton
                          size="small"
                          color="primary"
                          onClick={() => {
                            setSelectedItem(item);
                            setAdjustModalOpen(true);
                          }}
                        >
                          <EditOutlined fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                );
              })
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Adjust Modal */}
      <StockAdjustmentModal
        open={adjustModalOpen}
        item={selectedItem}
        onClose={() => {
          setAdjustModalOpen(false);
          setSelectedItem(null);
        }}
        onSuccess={(updated) => {
          setItems((prev) => prev.map((i) => (i.id === updated.id ? updated : i)));
          fetchData();
        }}
      />

      {/* Import Modal */}
      <CsvImportModal
        open={importModalOpen}
        onClose={() => setImportModalOpen(false)}
        onSuccess={() => {
          fetchData();
        }}
      />
    </Box>
  );
};
