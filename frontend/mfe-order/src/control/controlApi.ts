import axios from 'axios';
import { idempotencyKey } from './controlModel';

export const ORDER_ADMIN_API = 'http://localhost:8080/api/orders/admin';

const client = axios.create({ baseURL: ORDER_ADMIN_API });
client.interceptors.request.use((config) => {
  const token = typeof window !== 'undefined' ? localStorage.getItem('jwt_token') : null;
  if (token && !config.headers.Authorization) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

export type Row = Record<string, any>;

export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; error?: string } | undefined;
    if (error.response?.status === 403) return data?.message || data?.error || 'You do not have permission for this action.';
    if (error.response?.status === 409) return data?.message || data?.error || 'That is not possible in the order\'s current state. Reload and try again.';
    return data?.message || data?.error || error.message;
  }
  return error instanceof Error ? error.message : 'Unexpected error';
}

const d = <T,>(p: Promise<{ data: T }>) => p.then((r) => r.data);
/** Sensitive actions carry an Idempotency-Key so a double click or retry cannot apply twice. */
const keyed = (key?: string) => ({ headers: { 'Idempotency-Key': key ?? idempotencyKey() } });

export const orderApi = {
  meta: () => d(client.get<Row>('/meta')),
  dashboard: (query: string) => d(client.get<Row>(`/dashboard?${query}`)),
  list: (query: string) => d(client.get<Row>(`/orders?${query}`)),
  viewCounts: () => d(client.get<Record<string, number>>('/view-counts')),
  exportCsv: (query: string) => d(client.get<string>(`/orders/export?${query}`, { responseType: 'text' })),
  details: (id: number) => d(client.get<Row>(`/orders/${id}`)),
  timeline: (id: number) => d(client.get<Row>(`/orders/${id}/timeline`)),
  transition: (id: number, to: string, reason: string, expectedVersion?: number) => d(client.post<Row>(`/orders/${id}/transition`, { to, reason, expectedVersion }, keyed())),
  hold: (id: number, reason: string) => d(client.post<Row>(`/orders/${id}/hold`, { reason }, keyed())),
  release: (id: number, reason: string) => d(client.post<Row>(`/orders/${id}/release`, { reason }, keyed())),
  requestCancellation: (id: number, reason: string) => d(client.post<Row>(`/orders/${id}/request-cancellation`, { reason }, keyed())),
  cancel: (id: number, reason: string) => d(client.post<Row>(`/orders/${id}/cancel`, { reason }, keyed())),
  restaurant: (id: number, action: string, body: Row) => d(client.post<Row>(`/orders/${id}/restaurant/${action}`, body, keyed())),
  addNote: (id: number, kind: string, body: string) => d(client.post<Row>(`/orders/${id}/notes`, { kind, body })),
  assign: (id: number, operatorId: number | null) => d(client.post<Row>(`/orders/${id}/assign`, { operatorId })),
  fulfillment: (id: number) => d(client.get<Row>(`/orders/${id}/fulfillment`)),
  createFulfillment: (id: number, body: Row) => d(client.post<Row>(`/orders/${id}/fulfillments`, body, keyed())),
  packing: (fid: number, packingStatus: string) => d(client.post<Row>(`/fulfillments/${fid}/packing`, { packingStatus })),
  ship: (fid: number, carrier: string, trackingReference: string) => d(client.post<Row>(`/fulfillments/${fid}/ship`, { carrier, trackingReference }, keyed())),
  cancelFulfillment: (fid: number) => d(client.post<Row>(`/fulfillments/${fid}/cancel`)),
  exceptions: (query: string) => d(client.get<Row>(`/exceptions?${query}`)),
  exception: (id: number) => d(client.get<Row>(`/exceptions/${id}`)),
  scanExceptions: () => d(client.post<Row>('/exceptions/scan')),
  exceptionAction: (id: number, action: string, body: Row) => d(client.post<Row>(`/exceptions/${id}/${action}`, body)),
  bulkPreview: (action: string, orderIds: number[], params: Row) => d(client.post<Row>('/bulk/preview', { action, orderIds, params })),
  bulkExecute: (jobId: number) => d(client.post<Row>(`/bulk/${jobId}/execute`, null, keyed())),
  savedViews: () => d(client.get<Row[]>('/saved-views')),
  saveView: (name: string, filters: Row) => d(client.post<Row>('/saved-views', { name, filters })),
  deleteView: (id: number) => client.delete(`/saved-views/${id}`),
  settings: () => d(client.get<Row>('/settings')),
  saveSettings: (body: Row) => d(client.put<Row>('/settings', body)),
  returns: (status: string, page: number) => d(client.get<Row>(`/returns?status=${encodeURIComponent(status)}&page=${page}&size=25`)),
  reports: (groupBy: string, from: string, to: string) => d(client.get<Row>(`/reports/summary?groupBy=${groupBy}${from ? `&from=${from}` : ''}${to ? `&to=${to}` : ''}`)),
  audit: (orderId?: string, action?: string) => d(client.get<Row[]>(`/audit?${orderId ? `orderId=${orderId}&` : ''}${action ? `action=${action}` : ''}`)),
};
