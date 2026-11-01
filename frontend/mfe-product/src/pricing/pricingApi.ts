import axios from 'axios';

export const PRICING_API = 'http://localhost:8080/api/products/admin/pricing';

const client = axios.create({ baseURL: PRICING_API });
client.interceptors.request.use((config) => {
  const token = typeof window !== 'undefined' ? localStorage.getItem('jwt_token') : null;
  if (token && !config.headers.Authorization) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

export type Row = Record<string, any>;

/** Turns an Axios failure into text a person can act on; the API returns validation messages in `message`. */
export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; error?: string } | undefined;
    if (error.response?.status === 403) return data?.message || 'You do not have permission for this action.';
    return data?.message || data?.error || error.message;
  }
  return error instanceof Error ? error.message : 'Unexpected error';
}

export const api = {
  dashboard: () => client.get<Row>('/dashboard').then((r) => r.data),
  settings: () => client.get<Row>('/settings').then((r) => r.data),
  saveSettings: (body: Row) => client.put<Row>('/settings', body).then((r) => r.data),
  prices: (productId?: string) => client.get<Row[]>('/prices', { params: productId ? { productId } : {} }).then((r) => r.data),
  createPrice: (body: Row) => client.post<Row>('/prices', body).then((r) => r.data),
  updatePrice: (body: Row) => client.put<Row>('/prices', body).then((r) => r.data),
  bulk: (body: Row) => client.post<Row[]>('/prices/bulk', body).then((r) => r.data),
  history: (productId?: string) => client.get<Row[]>('/prices/history', { params: productId ? { productId } : {} }).then((r) => r.data),
  rollback: (id: number) => client.post<Row>(`/prices/history/${id}/rollback`).then((r) => r.data),
  exportCsv: () => client.get<string>('/prices/export', { responseType: 'text' }).then((r) => r.data),
  importCsv: (csv: string) => client.post<Row>('/prices/import', csv, { headers: { 'Content-Type': 'text/csv' } }).then((r) => r.data),
  scheduled: () => client.get<Row[]>('/scheduled').then((r) => r.data),
  schedule: (body: Row) => client.post<Row>('/scheduled', body).then((r) => r.data),
  decideScheduled: (id: number, approve: boolean, note: string) => client.post<Row>(`/scheduled/${id}/decision`, { approve, note }).then((r) => r.data),
  cancelScheduled: (id: number) => client.post<Row>(`/scheduled/${id}/cancel`).then((r) => r.data),
  campaigns: (params: Row = {}) => client.get<Row[]>('/campaigns', { params }).then((r) => r.data),
  campaign: (id: number) => client.get<Row>(`/campaigns/${id}`).then((r) => r.data),
  createCampaign: (body: Row) => client.post<Row>('/campaigns', body).then((r) => r.data),
  campaignAction: (id: number, action: string, body?: Row) => client.post<Row>(`/campaigns/${id}/${action}`, body).then((r) => r.data),
  decideCampaign: (id: number, approve: boolean, note: string) => client.post<Row>(`/campaigns/${id}/decision`, { approve, note }).then((r) => r.data),
  conflicts: (id: number) => client.get<Row[]>(`/campaigns/${id}/conflicts`).then((r) => r.data),
  calendar: (from: string, to: string) => client.get<Row[]>('/calendar', { params: { from, to } }).then((r) => r.data),
  performance: () => client.get<Row[]>('/performance').then((r) => r.data),
  approvals: (status?: string) => client.get<Row[]>('/approvals', { params: status ? { status } : {} }).then((r) => r.data),
  audit: (entityType?: string) => client.get<Row[]>('/audit', { params: entityType ? { entityType } : {} }).then((r) => r.data),
  coupons: (params: Row = {}) => client.get<Row[]>('/coupons', { params }).then((r) => r.data),
  createCoupons: (body: Row) => client.post<Row[]>('/coupons', body).then((r) => r.data),
  couponAction: (id: number, action: 'pause' | 'activate' | 'revoke') => client.post<Row>(`/coupons/${id}/${action}`).then((r) => r.data),
  redemptions: (couponId?: number) => client.get<Row[]>('/redemptions', { params: couponId ? { couponId } : {} }).then((r) => r.data),
};
