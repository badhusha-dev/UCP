import React, { useEffect, useState } from 'react';
import {
  Box, Typography, Card, CardContent, Grid, Table, TableBody,
  TableCell, TableHead, TableRow, Paper, Chip, LinearProgress,
  Alert, Skeleton, Button, Dialog, DialogTitle, DialogContent,
  DialogActions, TextField
} from '@mui/material';
import EmojiEventsIcon from '@mui/icons-material/EmojiEvents';
import PeopleIcon from '@mui/icons-material/People';
import MonetizationOnIcon from '@mui/icons-material/MonetizationOn';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import { loyaltyApi, AdminStats, LoyaltyAccount, LoyaltyTierRule } from '../api/loyaltyApi';

const TIER_COLORS: Record<string, string> = {
  BRONZE: '#CD7F32', SILVER: '#C0C0C0', GOLD: '#FFD700', PLATINUM: '#E5E4E2'
};

function StatCard({ title, value, sub, icon, color }: {
  title: string; value: string; sub?: string; icon: React.ReactNode; color: string;
}) {
  return (
    <Card sx={{ bgcolor: '#111827', borderRadius: 3, border: `1px solid ${color}33`, height: '100%' }}>
      <CardContent>
        <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
          <Box sx={{ color, mr: 1 }}>{icon}</Box>
          <Typography sx={{ color: '#6b7280', fontSize: 13 }}>{title}</Typography>
        </Box>
        <Typography variant="h4" sx={{ color, fontWeight: 700 }}>{value}</Typography>
        {sub && <Typography sx={{ color: '#4b5563', fontSize: 12, mt: 0.5 }}>{sub}</Typography>}
      </CardContent>
    </Card>
  );
}

export default function LoyaltyAdminPage() {
  const [stats, setStats] = useState<AdminStats | null>(null);
  const [accounts, setAccounts] = useState<LoyaltyAccount[]>([]);
  const [tierRules, setTierRules] = useState<LoyaltyTierRule[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [bonusDialog, setBonusDialog] = useState<{ open: boolean; userId: number | null }>({ open: false, userId: null });
  const [bonusPoints, setBonusPoints] = useState('');
  const [bonusReason, setBonusReason] = useState('');
  const [bonusSuccess, setBonusSuccess] = useState('');
  const [editingTier, setEditingTier] = useState<LoyaltyTierRule | null>(null);
  const [tierForm, setTierForm] = useState({ minLifetimePoints: '', multiplier: '', discountPercentBonus: '', perksDescription: '' });
  const [tierSaving, setTierSaving] = useState(false);

  const load = () => {
    setLoading(true);
    Promise.all([loyaltyApi.getAdminStats(), loyaltyApi.getAllAccounts(), loyaltyApi.getTierRules()])
      .then(([s, a, tiers]) => { setStats(s); setAccounts(a); setTierRules(tiers); })
      .catch(e => setError(e.response?.data?.message || 'Failed to load admin data'))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const handleBonus = async () => {
    if (!bonusDialog.userId || !bonusPoints) return;
    try {
      await loyaltyApi.awardBonus(bonusDialog.userId, Number(bonusPoints), bonusReason);
      setBonusSuccess(`Successfully awarded ${bonusPoints} bonus points`);
      setBonusDialog({ open: false, userId: null });
      setBonusPoints(''); setBonusReason('');
      load();
    } catch (e: any) {
      setError(e.response?.data?.message || 'Failed to award bonus');
    }
  };

  const startTierEdit = (rule: LoyaltyTierRule) => {
    setEditingTier(rule);
    setTierForm({ minLifetimePoints: String(rule.minLifetimePoints), multiplier: String(rule.multiplier), discountPercentBonus: String(rule.discountPercentBonus || 0), perksDescription: rule.perksDescription || '' });
  };
  const saveTier = async () => {
    if (!editingTier) return;
    setTierSaving(true); setError('');
    try {
      await loyaltyApi.updateTierRule({ ...editingTier, minLifetimePoints: Number(tierForm.minLifetimePoints), multiplier: Number(tierForm.multiplier), discountPercentBonus: Number(tierForm.discountPercentBonus), perksDescription: tierForm.perksDescription });
      setEditingTier(null); load();
    } catch (e: any) { setError(e.response?.data?.message || 'Failed to update tier rule'); }
    finally { setTierSaving(false); }
  };

  if (loading) return <Box p={4}><Skeleton variant="rectangular" height={400} /></Box>;
  if (error) return <Box p={4}><Alert severity="error">{error}</Alert></Box>;

  const totalTiers = stats ? Object.values(stats.tierBreakdown).reduce((a, b) => a + b, 0) : 1;

  return (
    <Box sx={{ bgcolor: '#0B0F19', minHeight: '100vh', p: 3 }}>
      <Typography variant="h4" sx={{ color: '#fff', fontWeight: 700, mb: 3 }}>
        🏆 Loyalty Program Administration
      </Typography>

      {bonusSuccess && <Alert severity="success" onClose={() => setBonusSuccess('')} sx={{ mb: 2 }}>{bonusSuccess}</Alert>}

      {/* KPI Cards */}
      {stats && (
        <Grid container spacing={2} sx={{ mb: 3 }}>
          <Grid item xs={12} sm={6} md={3}>
            <StatCard title="Total Accounts" value={stats.totalAccounts.toLocaleString()} icon={<PeopleIcon />} color="#4F46E5" />
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <StatCard
              title="Points Outstanding"
              value={stats.totalPointsOutstanding.toLocaleString()}
              sub={`$${Number(stats.estimatedLiabilityUsd).toFixed(2)} liability`}
              icon={<MonetizationOnIcon />}
              color="#F59E0B"
            />
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <StatCard
              title="Lifetime Points Issued"
              value={stats.totalLifetimePointsIssued.toLocaleString()}
              icon={<TrendingUpIcon />}
              color="#06B6D4"
            />
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <StatCard
              title="Point Value"
              value={`$${Number(stats.pointValueUsd).toFixed(2)}`}
              sub="per point"
              icon={<EmojiEventsIcon />}
              color="#10b981"
            />
          </Grid>
        </Grid>
      )}

      <Paper sx={{ bgcolor: '#111827', border: '1px solid #1E293B', borderRadius: 3, p: 2, mb: 3 }}>
        <Box sx={{ mb: 1.5 }}><Typography variant="h6" sx={{ color: '#fff', fontWeight: 600 }}>Tier rules</Typography><Typography variant="body2" sx={{ color: '#9ca3af' }}>Adjust thresholds and benefits. Tier identities are built into the program.</Typography></Box>
        <Table size="small"><TableHead><TableRow><TableCell sx={{ color: '#6b7280', borderColor: '#1E293B' }}>Tier</TableCell><TableCell align="right" sx={{ color: '#6b7280', borderColor: '#1E293B' }}>Lifetime points</TableCell><TableCell align="right" sx={{ color: '#6b7280', borderColor: '#1E293B' }}>Earn multiplier</TableCell><TableCell align="right" sx={{ color: '#6b7280', borderColor: '#1E293B' }}>Discount bonus</TableCell><TableCell sx={{ color: '#6b7280', borderColor: '#1E293B' }}>Perks</TableCell><TableCell align="right" sx={{ color: '#6b7280', borderColor: '#1E293B' }}>Action</TableCell></TableRow></TableHead><TableBody>{tierRules.map(rule => <TableRow key={rule.tier} hover><TableCell sx={{ color: TIER_COLORS[rule.tier], borderColor: '#1E293B', fontWeight: 700 }}>{rule.tier}</TableCell><TableCell align="right" sx={{ color: '#d1d5db', borderColor: '#1E293B' }}>{rule.minLifetimePoints.toLocaleString()}</TableCell><TableCell align="right" sx={{ color: '#d1d5db', borderColor: '#1E293B' }}>{Number(rule.multiplier).toFixed(2)}×</TableCell><TableCell align="right" sx={{ color: '#d1d5db', borderColor: '#1E293B' }}>{Number(rule.discountPercentBonus || 0).toFixed(2)}%</TableCell><TableCell sx={{ color: '#9ca3af', borderColor: '#1E293B' }}>{rule.perksDescription || '—'}</TableCell><TableCell align="right" sx={{ borderColor: '#1E293B' }}><Button size="small" onClick={() => startTierEdit(rule)}>Edit</Button></TableCell></TableRow>)}</TableBody></Table>
      </Paper>

      {/* Tier Distribution */}
      {stats && (
        <Paper sx={{ bgcolor: '#111827', border: '1px solid #1E293B', borderRadius: 3, p: 3, mb: 3 }}>
          <Typography variant="h6" sx={{ color: '#fff', fontWeight: 600, mb: 2 }}>Tier Distribution</Typography>
          <Grid container spacing={2}>
            {Object.entries(stats.tierBreakdown).map(([tier, count]) => (
              <Grid item xs={6} sm={3} key={tier}>
                <Box sx={{ mb: 0.5, display: 'flex', justifyContent: 'space-between' }}>
                  <Typography sx={{ color: TIER_COLORS[tier], fontSize: 13, fontWeight: 600 }}>{tier}</Typography>
                  <Typography sx={{ color: '#9ca3af', fontSize: 13 }}>{count} / {totalTiers}</Typography>
                </Box>
                <LinearProgress
                  variant="determinate"
                  value={(count / totalTiers) * 100}
                  sx={{
                    height: 10, borderRadius: 5, bgcolor: '#1E293B',
                    '& .MuiLinearProgress-bar': { bgcolor: TIER_COLORS[tier] }
                  }}
                />
              </Grid>
            ))}
          </Grid>
        </Paper>
      )}

      {/* Accounts Table */}
      <Paper sx={{ bgcolor: '#111827', border: '1px solid #1E293B', borderRadius: 3, overflow: 'hidden' }}>
        <Box sx={{ p: 2, borderBottom: '1px solid #1E293B' }}>
          <Typography variant="h6" sx={{ color: '#fff', fontWeight: 600 }}>All Loyalty Accounts</Typography>
        </Box>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ color: '#6b7280', borderColor: '#1E293B' }}>User ID</TableCell>
              <TableCell sx={{ color: '#6b7280', borderColor: '#1E293B' }}>Tier</TableCell>
              <TableCell align="right" sx={{ color: '#6b7280', borderColor: '#1E293B' }}>Balance</TableCell>
              <TableCell align="right" sx={{ color: '#6b7280', borderColor: '#1E293B' }}>Lifetime</TableCell>
              <TableCell align="right" sx={{ color: '#6b7280', borderColor: '#1E293B' }}>Cash Value</TableCell>
              <TableCell align="center" sx={{ color: '#6b7280', borderColor: '#1E293B' }}>Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {accounts.map(acc => (
              <TableRow key={acc.id} hover sx={{ '&:hover': { bgcolor: '#1E293B33' } }}>
                <TableCell sx={{ color: '#d1d5db', borderColor: '#1E293B' }}>{acc.userId}</TableCell>
                <TableCell sx={{ borderColor: '#1E293B' }}>
                  <Chip
                    label={acc.tier}
                    size="small"
                    sx={{ bgcolor: `${TIER_COLORS[acc.tier]}22`, color: TIER_COLORS[acc.tier], fontWeight: 600 }}
                  />
                </TableCell>
                <TableCell align="right" sx={{ color: '#a78bfa', fontWeight: 700, borderColor: '#1E293B' }}>
                  {acc.pointsBalance.toLocaleString()}
                </TableCell>
                <TableCell align="right" sx={{ color: '#06B6D4', borderColor: '#1E293B' }}>
                  {acc.lifetimePoints.toLocaleString()}
                </TableCell>
                <TableCell align="right" sx={{ color: '#10b981', borderColor: '#1E293B' }}>
                  ${Number(acc.cashValue).toFixed(2)}
                </TableCell>
                <TableCell align="center" sx={{ borderColor: '#1E293B' }}>
                  <Button
                    size="small"
                    variant="outlined"
                    onClick={() => setBonusDialog({ open: true, userId: acc.userId })}
                    sx={{ color: '#F59E0B', borderColor: '#F59E0B', fontSize: 11 }}
                  >
                    Award Bonus
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>

      {/* Bonus Dialog */}
      <Dialog open={editingTier !== null} onClose={() => !tierSaving && setEditingTier(null)} PaperProps={{ sx: { bgcolor: '#111827', border: '1px solid #1E293B' } }}>
        <DialogTitle sx={{ color: '#fff' }}>Edit {editingTier?.tier} tier rule</DialogTitle>
        <DialogContent><TextField label="Lifetime points threshold" type="number" fullWidth margin="normal" value={tierForm.minLifetimePoints} onChange={e => setTierForm({ ...tierForm, minLifetimePoints: e.target.value })} /><TextField label="Earn multiplier" type="number" inputProps={{ min: 0.01, step: 0.01 }} fullWidth margin="normal" value={tierForm.multiplier} onChange={e => setTierForm({ ...tierForm, multiplier: e.target.value })} /><TextField label="Discount bonus (%)" type="number" inputProps={{ min: 0, max: 100, step: 0.01 }} fullWidth margin="normal" value={tierForm.discountPercentBonus} onChange={e => setTierForm({ ...tierForm, discountPercentBonus: e.target.value })} /><TextField label="Perks" fullWidth multiline minRows={2} margin="normal" value={tierForm.perksDescription} onChange={e => setTierForm({ ...tierForm, perksDescription: e.target.value })} /></DialogContent>
        <DialogActions><Button disabled={tierSaving} onClick={() => setEditingTier(null)} sx={{ color: '#6b7280' }}>Cancel</Button><Button disabled={tierSaving} onClick={saveTier} variant="contained">{tierSaving ? 'Saving…' : 'Save tier rule'}</Button></DialogActions>
      </Dialog>
      <Dialog
        open={bonusDialog.open}
        onClose={() => setBonusDialog({ open: false, userId: null })}
        PaperProps={{ sx: { bgcolor: '#111827', border: '1px solid #1E293B' } }}
      >
        <DialogTitle sx={{ color: '#fff' }}>Award Bonus Points — User #{bonusDialog.userId}</DialogTitle>
        <DialogContent>
          <TextField
            label="Points" type="number" fullWidth margin="normal"
            value={bonusPoints} onChange={e => setBonusPoints(e.target.value)}
            InputLabelProps={{ sx: { color: '#6b7280' } }}
            InputProps={{ sx: { color: '#fff' } }}
            sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: '#374151' } }}
          />
          <TextField
            label="Reason" fullWidth margin="normal"
            value={bonusReason} onChange={e => setBonusReason(e.target.value)}
            InputLabelProps={{ sx: { color: '#6b7280' } }}
            InputProps={{ sx: { color: '#fff' } }}
            sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: '#374151' } }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setBonusDialog({ open: false, userId: null })} sx={{ color: '#6b7280' }}>Cancel</Button>
          <Button onClick={handleBonus} variant="contained" sx={{ bgcolor: '#F59E0B', color: '#000' }}>Award</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
