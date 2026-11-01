import React, { useState, useEffect } from 'react';
import {
  Box,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Button,
  Chip,
  CircularProgress,
  Alert,
  Tooltip,
  IconButton,
} from '@mui/material';
import {
  LockClockOutlined,
  RefreshOutlined,
  LockOpenOutlined,
  CheckCircleOutline,
} from '@mui/icons-material';
import type { StockReservation } from '../types/inventory';
import { stockApi } from '../api/stockApi';

export const ReservationsInspector: React.FC = () => {
  const [reservations, setReservations] = useState<StockReservation[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  const fetchReservations = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await stockApi.getReservations();
      setReservations(data);
    } catch (err: any) {
      setError(err?.response?.data?.message || err?.message || 'Failed to load reservations');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchReservations();
  }, []);

  const handleRelease = async (reservationId: string) => {
    if (!window.confirm(`Force release reservation ${reservationId} back to available stock?`)) return;

    setActionLoading(reservationId);
    try {
      await stockApi.releaseReservation(reservationId);
      await fetchReservations();
    } catch (err: any) {
      alert('Failed to release reservation: ' + (err?.message || 'Unknown error'));
    } finally {
      setActionLoading(null);
    }
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 800, color: '#0F172A', letterSpacing: '-0.02em' }}>
            Active Stock Reservations &amp; TTL Holds
          </Typography>
          <Typography variant="body2" sx={{ color: '#64748B' }}>
            Real-time isolation of items locked by active customer checkout sessions. Locks automatically expire after 15 minutes.
          </Typography>
        </Box>

        <Button
          variant="contained"
          color="primary"
          startIcon={<RefreshOutlined />}
          onClick={fetchReservations}
          sx={{ fontWeight: 700, borderRadius: 2 }}
        >
          Refresh Holds
        </Button>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 3 }}>
          {error}
        </Alert>
      )}

      <TableContainer component={Paper} sx={{ borderRadius: 3, boxShadow: '0 4px 16px -2px rgba(15, 23, 42, 0.06)' }}>
        <Table>
          <TableHead sx={{ bgcolor: '#F8FAFC' }}>
            <TableRow>
              <TableCell sx={{ fontWeight: 700, color: '#475569' }}>Hold ID</TableCell>
              <TableCell sx={{ fontWeight: 700, color: '#475569' }}>Cart Token / Session</TableCell>
              <TableCell sx={{ fontWeight: 700, color: '#475569' }}>Product ID</TableCell>
              <TableCell align="right" sx={{ fontWeight: 700, color: '#475569' }}>Reserved Units</TableCell>
              <TableCell align="center" sx={{ fontWeight: 700, color: '#475569' }}>Status</TableCell>
              <TableCell sx={{ fontWeight: 700, color: '#475569' }}>Created At</TableCell>
              <TableCell sx={{ fontWeight: 700, color: '#475569' }}>Expires At (TTL)</TableCell>
              <TableCell align="center" sx={{ fontWeight: 700, color: '#475569' }}>Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={8} align="center" sx={{ py: 6 }}>
                  <CircularProgress size={36} />
                  <Typography variant="body2" sx={{ mt: 1, color: '#64748B' }}>
                    Scanning authoritative reservation ledger...
                  </Typography>
                </TableCell>
              </TableRow>
            ) : reservations.length === 0 ? (
              <TableRow>
                <TableCell colSpan={8} align="center" sx={{ py: 6 }}>
                  <CheckCircleOutline sx={{ fontSize: 40, color: '#10B981', mb: 1 }} />
                  <Typography variant="subtitle1" sx={{ fontWeight: 700, color: '#0F172A' }}>
                    No Active Checkout Holds
                  </Typography>
                  <Typography variant="caption" sx={{ color: '#94A3B8' }}>
                    All stock is currently unreserved and available for purchase.
                  </Typography>
                </TableCell>
              </TableRow>
            ) : (
              reservations.map((r) => {
                const isPending = r.status === 'HOLD';
                const isExpired = new Date(r.expiresAt).getTime() < Date.now();

                return (
                  <TableRow key={r.reservationId} hover>
                    <TableCell sx={{ fontWeight: 700 }}>{r.reservationId}</TableCell>
                    <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.8rem', color: '#6366F1' }}>
                      {r.orderId ? `Order #${r.orderId}` : 'Checkout hold'}
                    </TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>Product #{r.productId}</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 800 }}>
                      {r.quantity}
                    </TableCell>
                    <TableCell align="center">
                      <Chip
                        label={isPending && isExpired ? 'EXPIRED' : r.status}
                        size="small"
                        color={
                          r.status === 'CONFIRMED'
                            ? 'success'
                            : r.status === 'RELEASED' || isExpired
                            ? 'default'
                            : 'warning'
                        }
                        sx={{ fontWeight: 800, height: 22 }}
                      />
                    </TableCell>
                    <TableCell sx={{ color: '#64748B', fontSize: '0.8rem' }}>
                      {new Date(r.createdAt).toLocaleString()}
                    </TableCell>
                    <TableCell sx={{ color: isExpired ? '#DC2626' : '#64748B', fontSize: '0.8rem', fontWeight: isExpired ? 700 : 400 }}>
                      {new Date(r.expiresAt).toLocaleString()}
                    </TableCell>
                    <TableCell align="center">
                      {isPending && (
                        <Tooltip title="Force Release Lock">
                          <span>
                            <IconButton
                              size="small"
                              color="error"
                              disabled={actionLoading === r.reservationId}
                              onClick={() => handleRelease(r.reservationId)}
                            >
                              {actionLoading === r.reservationId ? <CircularProgress size={16} /> : <LockOpenOutlined fontSize="small" />}
                            </IconButton>
                          </span>
                        </Tooltip>
                      )}
                    </TableCell>
                  </TableRow>
                );
              })
            )}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  );
};
