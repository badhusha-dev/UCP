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
import type { StockItem, StockAdjustmentRequest } from '../types/inventory';
import { stockApi } from '../api/stockApi';

interface Props {
  open: boolean;
  item: StockItem | null;
  onClose: () => void;
  onSuccess: (updated: StockItem) => void;
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
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (item) {
      setDelta(0);
      setError(null);
    }
  }, [item]);

  if (!item) return null;

  const resultingStock = Math.max(0, item.stockQuantity + delta);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (delta === 0) {
      onClose();
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const payload: StockAdjustmentRequest = {
        productId: item.productId,
        delta,
        movementType: delta > 0 ? 'RECEIVE' : 'ADJUSTMENT',
        reason,
      };
      const updated = await stockApi.adjustStock(payload);
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
          Adjust Stock &bull; {item.sku}
        </DialogTitle>
        <DialogContent dividers>
          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {error}
            </Alert>
          )}

          <Box sx={{ mb: 2.5, p: 2, bgcolor: '#F8FAFC', borderRadius: 2, border: '1px solid #E2E8F0' }}>
            <Typography variant="body2" sx={{ color: '#64748B' }}>
              Current on-hand: <strong>{item.stockQuantity}</strong> units (Reserved: {item.reservedQuantity}, Available: {item.availableQuantity})
            </Typography>
            <Typography variant="subtitle2" sx={{ mt: 0.5, fontWeight: 700, color: resultingStock < item.reorderThreshold ? '#DC2626' : '#10B981' }}>
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
