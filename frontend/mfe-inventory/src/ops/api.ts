import axios from 'axios';
import { readApplicationSession } from '@ecommerce/shared-library/src/auth/applicationSession';

const INV = '/api/inventory/ops';
const PROC = '/api/procurement/ucp';

export function currentToken(): string | undefined {
  return readApplicationSession('INVENTORY', sessionStorage)?.token;
}

/** Display-only: the backend re-verifies every request. Used to hide screens the user cannot open. */
export function tokenPermissions(token?: string): string[] {
  try {
    if (!token) return [];
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    return Array.isArray(payload.permissions) ? payload.permissions.map(String) : [];
  } catch { return []; }
}

const headers = () => ({ headers: { Authorization: `Bearer ${currentToken() ?? ''}` } });

async function get<T = any>(url: string, params?: Record<string, unknown>): Promise<T> { return (await axios.get<T>(url, { ...headers(), params })).data; }
async function post<T = any>(url: string, body?: unknown, extra?: Record<string, string>): Promise<T> {
  return (await axios.post<T>(url, body ?? {}, { headers: { ...headers().headers, ...(extra ?? {}) } })).data;
}

export function errorMessage(e: unknown): string {
  const ax = e as { response?: { data?: { message?: string; error?: string }; status?: number }; message?: string };
  return ax.response?.data?.message || ax.response?.data?.error || (ax.response?.status ? `Request failed (${ax.response.status})` : ax.message) || 'Request failed';
}

export const opsApi = {
  dashboard: (warehouseId?: number) => get(`${INV}/dashboard`, { warehouseId }),
  items: (p: Record<string, unknown>) => get(`${INV}/items`, p),
  valuation: () => get(`${INV}/valuation`),
  forecast: () => get(`${INV}/forecast`),
  reconciliation: () => get(`${INV}/reservations/reconciliation`),
  audit: () => get(`${INV}/audit`),
  config: () => get(`${INV}/config`),
  saveConfig: (body: unknown) => axios.put(`${INV}/config`, body, headers()).then(r => r.data),
  warehouses: () => get(`${INV}/warehouses`),
  createWarehouse: (b: unknown) => post(`${INV}/warehouses`, b),
  addLocation: (id: number, b: unknown) => post(`${INV}/warehouses/${id}/locations`, b),
  locations: (id: number) => get(`${INV}/warehouses/${id}/locations`),
  warehouseStock: (warehouseId?: number) => get(`${INV}/warehouse-stock`, { warehouseId }),
  transfers: () => get(`${INV}/transfers`),
  createTransfer: (b: unknown) => post(`${INV}/transfers`, b),
  transferAction: (id: number, action: string, body?: unknown) => post(`${INV}/transfers/${id}/${action}`, body),
  adjustments: () => get(`${INV}/adjustments`),
  requestAdjustment: (b: unknown) => post(`${INV}/adjustments`, b),
  decideAdjustment: (id: number, approve: boolean) => post(`${INV}/adjustments/${id}/${approve ? 'approve' : 'reject'}`),
  reservations: () => get('/api/inventory/reservations'),
  movements: () => get('/api/inventory/movements'),
  exportCsvUrl: `${INV}/export`,
  exportCsv: async () => (await axios.get<string>(`${INV}/export`, { ...headers(), responseType: 'text' })).data,
};

export const procApi = {
  suppliers: (p?: Record<string, unknown>) => get(`${PROC}/suppliers`, p),
  createSupplier: (b: unknown) => post(`${PROC}/suppliers`, b),
  supplierAction: (id: number, a: string) => post(`${PROC}/suppliers/${id}/${a}`),
  items: (id: number) => get(`${PROC}/suppliers/${id}/items`),
  upsertItem: (id: number, b: unknown) => post(`${PROC}/suppliers/${id}/items`, b),
  performance: (id: number) => get(`${PROC}/suppliers/${id}/performance`),
  pos: (p?: Record<string, unknown>) => get(`${PROC}/purchase-orders`, p),
  po: (id: number) => get(`${PROC}/purchase-orders/${id}`),
  createPo: (b: unknown) => post(`${PROC}/purchase-orders`, b),
  poAction: (id: number, a: string, body?: unknown) => post(`${PROC}/purchase-orders/${id}/${a}`, body),
  receive: (id: number, body: unknown, key: string) => post(`${PROC}/purchase-orders/${id}/receipts`, body, { 'Idempotency-Key': key }),
  receipts: () => get(`${PROC}/receipts`),
  returns: () => get(`${PROC}/supplier-returns`),
  createReturn: (b: unknown) => post(`${PROC}/supplier-returns`, b),
  shipReturn: (id: number) => post(`${PROC}/supplier-returns/${id}/ship`),
  creditReturn: (id: number) => post(`${PROC}/supplier-returns/${id}/credit`),
  replenishment: () => get(`${PROC}/replenishment`),
  draftPo: (b: unknown) => post(`${PROC}/replenishment/draft-po`, b),
  analytics: () => get(`${PROC}/analytics`),
  documentUrl: (id: number) => `${PROC}/purchase-orders/${id}/document`,
  fetchDocument: async (id: number) => (await axios.get<string>(`${PROC}/purchase-orders/${id}/document`, { ...headers(), responseType: 'text' })).data,
};
