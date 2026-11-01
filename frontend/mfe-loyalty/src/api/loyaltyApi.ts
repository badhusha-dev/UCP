import axios from 'axios';

const API_BASE = '/api/loyalty';

function authHeaders() {
  const token = localStorage.getItem('jwt_token');
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export interface LoyaltyTransaction {
  id: number;
  points: number;
  transactionType: 'EARN' | 'REDEEM' | 'BONUS' | 'ADJUST' | 'EXPIRE';
  referenceId?: string;
  description?: string;
  createdAt: string;
}

export interface LoyaltyAccount {
  id: number;
  userId: number;
  pointsBalance: number;
  lifetimePoints: number;
  tier: 'BRONZE' | 'SILVER' | 'GOLD' | 'PLATINUM';
  cashValue: number;
  pointsToNextTier?: number;
  nextTier?: string;
  tierMultiplier: number;
  updatedAt: string;
  recentTransactions?: LoyaltyTransaction[];
}

export interface AdminStats {
  totalAccounts: number;
  tierBreakdown: Record<string, number>;
  totalPointsOutstanding: number;
  totalLifetimePointsIssued: number;
  estimatedLiabilityUsd: number;
  pointValueUsd: number;
}

export interface LoyaltyTierRule {
  tier: 'BRONZE' | 'SILVER' | 'GOLD' | 'PLATINUM';
  minLifetimePoints: number;
  multiplier: number;
  discountPercentBonus: number;
  perksDescription?: string;
}

export const loyaltyApi = {
  getProfile: (userId: number) =>
    axios.get<LoyaltyAccount>(`${API_BASE}/${userId}`, { headers: authHeaders() }).then(r => r.data),

  getTransactions: (userId: number, page = 0, size = 20) =>
    axios.get<LoyaltyTransaction[]>(`${API_BASE}/${userId}/transactions`, {
      headers: authHeaders(), params: { page, size }
    }).then(r => r.data),

  earnPoints: (userId: number, orderId: string, orderAmount: number) =>
    axios.post(`${API_BASE}/${userId}/earn`, { orderId, orderAmount }, { headers: authHeaders() }).then(r => r.data),

  redeemPoints: (userId: number, points: number, orderId: string) =>
    axios.post(`${API_BASE}/${userId}/redeem`, { points, orderId }, { headers: authHeaders() }).then(r => r.data),

  awardBonus: (userId: number, points: number, reason: string) =>
    axios.post(`${API_BASE}/${userId}/bonus`, { points, reason }, { headers: authHeaders() }).then(r => r.data),

  getAdminStats: () =>
    axios.get<AdminStats>(`${API_BASE}/admin/stats`, { headers: authHeaders() }).then(r => r.data),

  getAllAccounts: () =>
    axios.get<LoyaltyAccount[]>(`${API_BASE}/admin/accounts`, { headers: authHeaders() }).then(r => r.data),

  getTierRules: () =>
    axios.get<LoyaltyTierRule[]>(`${API_BASE}/admin/tiers`, { headers: authHeaders() }).then(r => r.data),

  updateTierRule: (rule: LoyaltyTierRule) =>
    axios.put<LoyaltyTierRule>(`${API_BASE}/admin/tiers/${rule.tier}`, rule, { headers: authHeaders() }).then(r => r.data),
};
