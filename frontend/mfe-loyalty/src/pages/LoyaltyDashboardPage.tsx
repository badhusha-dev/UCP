import React, { useEffect, useState } from 'react';
import {
  Box, Card, CardContent, Typography, Chip, LinearProgress,
  Table, TableBody, TableCell, TableHead, TableRow, Paper,
  Grid, Skeleton, Alert, Divider, Tooltip
} from '@mui/material';
import { loyaltyApi, LoyaltyAccount, LoyaltyTransaction } from '../api/loyaltyApi';

const TIER_COLORS: Record<string, string> = {
  BRONZE: '#CD7F32',
  SILVER: '#C0C0C0',
  GOLD: '#FFD700',
  PLATINUM: '#E5E4E2',
};

const TIER_GRADIENTS: Record<string, string> = {
  BRONZE: 'linear-gradient(135deg, #7c4a0a 0%, #CD7F32 100%)',
  SILVER: 'linear-gradient(135deg, #555 0%, #C0C0C0 100%)',
  GOLD: 'linear-gradient(135deg, #8B6914 0%, #FFD700 100%)',
  PLATINUM: 'linear-gradient(135deg, #374151 0%, #9CA3AF 100%)',
};

function TierBadge({ tier }: { tier: string }) {
  return (
    <Box sx={{
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: TIER_GRADIENTS[tier] || '#333',
      borderRadius: '50%',
      width: 96,
      height: 96,
      boxShadow: `0 0 24px ${TIER_COLORS[tier]}66`,
      mb: 1,
    }}>
      <Typography variant="h6" sx={{ color: '#fff', fontWeight: 700, letterSpacing: 1 }}>
        {tier}
      </Typography>
    </Box>
  );
}

function txColor(type: string) {
  if (type === 'EARN' || type === 'BONUS') return '#4ade80';
  if (type === 'REDEEM') return '#f87171';
  return '#94a3b8';
}

interface Props {
  userId?: number;
}

export default function LoyaltyDashboardPage({ userId: propUserId }: Props) {
  const userId = propUserId ?? (() => {
    const explicitId = localStorage.getItem('userId');
    if (explicitId && Number(explicitId) > 0) return Number(explicitId);
    try {
      const token = localStorage.getItem('jwt_token');
      if (!token) return 1;
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      return Number(payload.id ?? payload.userId ?? 1);
    } catch { return 1; }
  })();
  const [account, setAccount] = useState<LoyaltyAccount | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!userId) { setError('Not logged in'); setLoading(false); return; }
    loyaltyApi.getProfile(userId)
      .then(setAccount)
      .catch(e => setError(e.response?.data?.message || 'Failed to load loyalty profile'))
      .finally(() => setLoading(false));
  }, [userId]);

  if (loading) return <Box p={4}><Skeleton variant="rectangular" height={300} /></Box>;
  if (error) return <Box p={4}><Alert severity="error">{error}</Alert></Box>;
  if (!account) return null;

  const progressToNext = account.nextTier && account.pointsToNextTier !== undefined
    ? Math.min(100, ((account.lifetimePoints) / (account.lifetimePoints + account.pointsToNextTier)) * 100)
    : 100;

  return (
    <Box sx={{ bgcolor: '#0B0F19', minHeight: '100vh', p: 3 }}>
      <Typography variant="h4" sx={{ color: '#fff', fontWeight: 700, mb: 3 }}>
        🎖 My Loyalty Rewards
      </Typography>

      <Grid container spacing={3}>
        {/* Tier card */}
        <Grid item xs={12} md={4}>
          <Card sx={{ bgcolor: '#111827', border: `1px solid ${TIER_COLORS[account.tier]}44`, borderRadius: 3 }}>
            <CardContent sx={{ textAlign: 'center', py: 4 }}>
              <TierBadge tier={account.tier} />
              <Typography variant="h5" sx={{ color: TIER_COLORS[account.tier], fontWeight: 700, mt: 1 }}>
                {account.tier} MEMBER
              </Typography>
              <Typography sx={{ color: '#94a3b8', mt: 0.5, fontSize: 13 }}>
                {account.tierMultiplier}x points multiplier
              </Typography>
              {account.nextTier && (
                <Box sx={{ mt: 2 }}>
                  <Typography sx={{ color: '#6b7280', fontSize: 12, mb: 0.5 }}>
                    {account.pointsToNextTier?.toLocaleString()} pts to {account.nextTier}
                  </Typography>
                  <LinearProgress
                    variant="determinate"
                    value={progressToNext}
                    sx={{
                      height: 8, borderRadius: 4,
                      bgcolor: '#1E293B',
                      '& .MuiLinearProgress-bar': { bgcolor: TIER_COLORS[account.tier] }
                    }}
                  />
                </Box>
              )}
              {!account.nextTier && (
                <Chip label="MAX TIER 🏆" sx={{ mt: 2, bgcolor: '#FFD70022', color: '#FFD700' }} />
              )}
            </CardContent>
          </Card>
        </Grid>

        {/* Balance cards */}
        <Grid item xs={12} md={8}>
          <Grid container spacing={2}>
            <Grid item xs={12} sm={6}>
              <Card sx={{ bgcolor: '#111827', borderRadius: 3, border: '1px solid #1E293B' }}>
                <CardContent>
                  <Typography sx={{ color: '#94a3b8', fontSize: 13, mb: 1 }}>Current Balance</Typography>
                  <Typography variant="h3" sx={{ color: '#4F46E5', fontWeight: 700 }}>
                    {account.pointsBalance.toLocaleString()}
                  </Typography>
                  <Typography sx={{ color: '#06B6D4', fontSize: 13 }}>points</Typography>
                </CardContent>
              </Card>
            </Grid>
            <Grid item xs={12} sm={6}>
              <Card sx={{ bgcolor: '#111827', borderRadius: 3, border: '1px solid #1E293B' }}>
                <CardContent>
                  <Typography sx={{ color: '#94a3b8', fontSize: 13, mb: 1 }}>Cash Value</Typography>
                  <Typography variant="h3" sx={{ color: '#10b981', fontWeight: 700 }}>
                    ${Number(account.cashValue).toFixed(2)}
                  </Typography>
                  <Typography sx={{ color: '#6b7280', fontSize: 13 }}>redeemable value</Typography>
                </CardContent>
              </Card>
            </Grid>
            <Grid item xs={12} sm={6}>
              <Card sx={{ bgcolor: '#111827', borderRadius: 3, border: '1px solid #1E293B' }}>
                <CardContent>
                  <Typography sx={{ color: '#94a3b8', fontSize: 13, mb: 1 }}>Lifetime Points</Typography>
                  <Typography variant="h4" sx={{ color: '#F59E0B', fontWeight: 700 }}>
                    {account.lifetimePoints.toLocaleString()}
                  </Typography>
                  <Typography sx={{ color: '#6b7280', fontSize: 13 }}>total earned</Typography>
                </CardContent>
              </Card>
            </Grid>
            <Grid item xs={12} sm={6}>
              <Card sx={{ bgcolor: '#111827', borderRadius: 3, border: '1px solid #1E293B' }}>
                <CardContent>
                  <Typography sx={{ color: '#94a3b8', fontSize: 13, mb: 1 }}>Points Rate</Typography>
                  <Typography variant="h4" sx={{ color: '#a78bfa', fontWeight: 700 }}>
                    10
                  </Typography>
                  <Typography sx={{ color: '#6b7280', fontSize: 13 }}>pts per $1 spent × {account.tierMultiplier}x</Typography>
                </CardContent>
              </Card>
            </Grid>
          </Grid>
        </Grid>

        {/* Transaction history */}
        <Grid item xs={12}>
          <Paper sx={{ bgcolor: '#111827', borderRadius: 3, border: '1px solid #1E293B', overflow: 'hidden' }}>
            <Box sx={{ p: 2, borderBottom: '1px solid #1E293B' }}>
              <Typography variant="h6" sx={{ color: '#fff', fontWeight: 600 }}>Recent Transactions</Typography>
            </Box>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ color: '#6b7280', borderColor: '#1E293B' }}>Date</TableCell>
                  <TableCell sx={{ color: '#6b7280', borderColor: '#1E293B' }}>Type</TableCell>
                  <TableCell sx={{ color: '#6b7280', borderColor: '#1E293B' }}>Description</TableCell>
                  <TableCell align="right" sx={{ color: '#6b7280', borderColor: '#1E293B' }}>Points</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {(account.recentTransactions || []).length === 0 && (
                  <TableRow>
                    <TableCell colSpan={4} sx={{ color: '#4b5563', textAlign: 'center', borderColor: '#1E293B' }}>
                      No transactions yet. Start shopping to earn points!
                    </TableCell>
                  </TableRow>
                )}
                {(account.recentTransactions || []).map((tx: LoyaltyTransaction) => (
                  <TableRow key={tx.id} hover sx={{ '&:hover': { bgcolor: '#1E293B33' } }}>
                    <TableCell sx={{ color: '#9ca3af', fontSize: 12, borderColor: '#1E293B' }}>
                      {new Date(tx.createdAt).toLocaleDateString()}
                    </TableCell>
                    <TableCell sx={{ borderColor: '#1E293B' }}>
                      <Chip
                        label={tx.transactionType}
                        size="small"
                        sx={{ bgcolor: `${txColor(tx.transactionType)}22`, color: txColor(tx.transactionType), fontSize: 10 }}
                      />
                    </TableCell>
                    <TableCell sx={{ color: '#d1d5db', fontSize: 13, borderColor: '#1E293B' }}>
                      {tx.description || tx.referenceId || '—'}
                    </TableCell>
                    <TableCell align="right" sx={{ borderColor: '#1E293B' }}>
                      <Typography
                        sx={{
                          color: txColor(tx.transactionType),
                          fontWeight: 700,
                          fontSize: 14
                        }}
                      >
                        {tx.points > 0 ? '+' : ''}{tx.points.toLocaleString()}
                      </Typography>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
}
