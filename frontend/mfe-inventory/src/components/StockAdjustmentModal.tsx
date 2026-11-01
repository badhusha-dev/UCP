import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Grid,
  MenuItem,
  Typography,
  Box,
  Alert,
  CircularProgress,
} from '@mui/material';
import type { InventoryItem, StockAdjustmentRequest } from '../types/inventory';
import { inventoryApi } from '../api/inventoryApi';

interface Props {
  open: boolean;
  item: InventoryItem | null;
  onClose: () => void;
  onSuccess: (updated: InventoryItem) => void;
}

const REASONS = [
  'RESTOCK_SUPPLIER',
  'CYCLE_COUNT_AUDIT',
  'DAMAGED_EXPIRED',
  'RETURN_INSPECTION',
  'INTERNAL_TRANSFER',
  'PROMOTIONAL_SAMPLE',
];

export const StockAdjustmentModal: React.FC<Props> = ({ open, item, onClose, onSuccess }) => {
  const [delta, setDelta] = useState<number>(0);
  const [reason, setReason] = useState<string>('RESTOCK_SUPPLIER');
  const [location, setLocation] = useState<string>('');
  const [costPrice, setCostPrice] = useState<number>(0);
  const [threshold, setThreshold] = useState<number>(10);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (item) {
      setDelta(0);
      setLocation(item.warehouseLocation || '');
      setCostPrice(item.costPrice || 0);
      setThreshold(item.reorderThreshold || 10);
      setError(null);
    }
  }, [item]);

  if (!item) return null;

  const resultingStock = Math.max(0, item.stockQuantity + delta);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (delta === 0 && !location && !costPrice && !threshold) {
      onClose();
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const payload: StockAdjustmentRequest = {
        productId: item.productId,
        quantityDelta: delta,
        reason,
        warehouseLocation: location,
        costPrice: Number(costPrice),
        reorderThreshold: Number(threshold),
      };
      const updated = await inventoryApi.adjustStock(payload);
      onSuccess(updated);
      onClose();
    } catch (err: any) {
      setError(err?.response?.data?.message || err?.message || 'Failed to apply adjustment');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit}>
        <DialogTitle sx={{ fontWeight: 800, pb: 1 }}>
          Adjust Inventory &bull; {item.sku}
        </DialogTitle>
        <DialogContent dividers>
          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {error}
            </Alert>
          )}

          <Box sx={{ mb: 2.5, p: 2, bgcolor: '#F8FAFC', borderRadius: 2, border: '1px solid #E2E8F0' }}>
            <Typography variant="body2" sx={{ color: '#64748B' }}>
              Current Authoritative Stock: <strong>{item.stockQuantity}</strong> units (Reserved: {item.reservedQuantity}, Available: {item.availableQuantity})
            </Typography>
            <Typography variant="subtitle2" sx={{ mt: 0.5, fontWeight: 700, color: resultingStock < threshold ? '#DC2626' : '#10B981' }}>
              Projected Stock After Adjustment: {resultingStock} units
            </Typography>
          </Box>

          <Grid container spacing={2}>
            <Grid item xs={12} sm={6}>
              <TextField
                label="Stock Delta (+/- units)"
                type="number"
                fullWidth
                value={delta}
                onChange={(e) => setDelta(parseInt(e.target.value, 10) || 0)}
                helperText="Use positive for receipt, negative for loss/shrinkage"
                required
              />
            </Grid>

            <Grid item xs={12} sm={6}>
              <TextField
                select
                label="Adjustment Reason"
                fullWidth
                value={reason}
                onChange={(e) => setReason(e.target.value)}
              >
                {REASONS.map((r) => (
                  <MenuItem key={r} value={r}>
                    {r.replace(/_/g, ' ')}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>

            <Grid item xs={12} sm={6}>
              <TextField
                label="Warehouse Bin / Location"
                fullWidth
                value={location}
                onChange={(e) => setLocation(e.target.value)}
                placeholder="e.g. WH-B-04-12"
              />
            </Grid>

            <Grid item xs={12} sm={6}>
              <TextField
                label="Unit Cost Price ($)"
                type="number"
                inputProps={{ step: '0.01', min: '0' }}
                fullWidth
                value={costPrice}
                onChange={(e) => setCostPrice(parseFloat(e.target.value) || 0)}
              />
            </Grid>

            <Grid item xs={12} sm={6}>
              <TextField
                label="Low-Stock Alert Threshold"
                type="number"
                inputProps={{ min: '0' }}
                fullWidth
                value={threshold}
                onChange={(e) => setThreshold(parseInt(e.target.value, 10) || 0)}
                helperText="Triggers back-in-stock / low-stock notices"
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={onClose} disabled={loading} color="inherit">
            Cancel
          </Button>
          <Button
            type="submit"
            variant="contained"
            color="primary"
            disabled={loading}
            startIcon={loading ? <CircularProgress size={18} color="inherit" /> : null}
            sx={{ fontWeight: 700, borderRadius: 2 }}
          >
            Apply Adjustment
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
};
