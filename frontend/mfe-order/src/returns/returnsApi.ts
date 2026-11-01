import axios from 'axios';
import { idempotencyKey } from './returnsModel';

export const RETURNS_API = '/api/orders/admin/rma';

const client = axios.create({ baseURL: RETURNS_API });
client.interceptors.request.use((config) => {
  const token = typeof window !== 'undefined' ? localStorage.getItem('jwt_token') : null;
  if (token && !config.headers.Authorization) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

export type Row = Record<string, any>;

export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; error?: string } | undefined;
    const msg = data?.message || data?.error;
    if (error.response?.status === 403) return msg || 'You do not have permission for this action.';
    if (error.response?.status === 409) return msg || 'That is not possible in the return\'s current state. Reload and try again.';
    if (error.response?.status === 422) return msg || 'The request is not eligible under the return policy.';
    return msg || error.message;
  }
  return error instanceof Error ? error.message : 'Unexpected error';
}

const d = <T,>(p: Promise<{ data: T }>) => p.then((r) => r.data);
/** Money and stock actions carry an Idempotency-Key so a double click or retry cannot apply twice. */
const keyed = (key?: string) => ({ headers: { 'Idempotency-Key': key ?? idempotencyKey() } });

export const returnsApi = {
  meta: () => d(client.get<Row>('/meta')),
  dashboard: (query: string) => d(client.get<Row>(`/dashboard?${query}`)),
  analytics: (query: string) => d(client.get<Row>(`/analytics?${query}`)),
  audit: (query: string) => d(client.get<Row>(`/audit?${query}`)),
  list: (query: string) => d(client.get<Row>(`/returns?${query}`)),
  exportCsv: (query: string) => d(client.get<string>(`/returns/export?${query}`, { responseType: 'text' })),
  details: (id: number) => d(client.get<Row>(`/returns/${id}`)),
  approve: (id: number, quantities: Record<number, number>, note: string) => d(client.post<Row>(`/returns/${id}/approve`, { quantities, note })),
  reject: (id: number, reason: string) => d(client.post<Row>(`/returns/${id}/reject`, { reason })),
  requestInfo: (id: number, note: string) => d(client.post<Row>(`/returns/${id}/request-info`, { note })),
  addNote: (id: number, body: string) => d(client.post<Row>(`/returns/${id}/notes`, { body })),
  assign: (id: number, operatorId: number | null) => d(client.post<Row>(`/returns/${id}/assign`, { operatorId })),
  transition: (id: number, to: string, reason: string) => d(client.post<Row>(`/returns/${id}/transition`, { to, reason })),
  upload: (id: number, file: File, kind: string, note: string) => {
    const form = new FormData();
    form.append('file', file); form.append('kind', kind); if (note) form.append('note', note);
    return d(client.post<Row>(`/returns/${id}/attachments`, form));
  },
  downloadAttachment: async (id: number, attachmentId: number) => (await client.get<Blob>(`/returns/${id}/attachments/${attachmentId}`, { responseType: 'blob' })).data,
  inspect: (id: number, body: Row) => d(client.post<Row>(`/returns/${id}/inspection`, body)),
  prepareRefund: (id: number, method: string) => d(client.post<Row>(`/returns/${id}/refund`, { method }, keyed())),
  refunds: (query: string) => d(client.get<Row>(`/refunds?${query}`)),
  refund: (id: number) => d(client.get<Row>(`/refunds/${id}`)),
  approveRefund: (id: number) => d(client.post<Row>(`/refunds/${id}/approve`)),
  processRefund: (id: number) => d(client.post<Row>(`/refunds/${id}/process`, null, keyed())),
  proposeExchange: (id: number, body: Row) => d(client.post<Row>(`/returns/${id}/exchanges`, body)),
  exchanges: (query: string) => d(client.get<Row>(`/exchanges?${query}`)),
  decideExchange: (id: number, approve: boolean, note: string) => d(client.post<Row>(`/exchanges/${id}/decision`, { approve, note })),
  fulfillExchange: (id: number) => d(client.post<Row>(`/exchanges/${id}/fulfill`, null, keyed())),
  schedulePickup: (id: number, body: Row) => d(client.post<Row>(`/returns/${id}/pickup`, body)),
  cancelPickup: (id: number, reason: string) => d(client.post<Row>(`/returns/${id}/pickup/cancel`, { reason })),
  exceptions: (query: string) => d(client.get<Row>(`/exceptions?${query}`)),
  exceptionAction: (id: number, action: string, assigneeId: number | null, note: string) => d(client.post<Row>(`/exceptions/${id}/action`, { action, assigneeId, note })),
  scan: (id: number) => d(client.post<Row>(`/returns/${id}/scan`)),
  policies: () => d(client.get<Row[]>('/policies')),
  policyDefaults: (kind: string) => d(client.get<Row>(`/policies/defaults?kind=${kind}`)),
  policy: (id: number) => d(client.get<Row>(`/policies/${id}`)),
  createPolicy: (body: Row) => d(client.post<Row>('/policies', body)),
  saveDraft: (id: number, config: Row, note: string) => d(client.put<Row>(`/policies/${id}/draft`, { config, note })),
  submitPolicy: (id: number, versionId: number) => d(client.post<Row>(`/policies/${id}/versions/${versionId}/submit`)),
  decidePolicy: (id: number, versionId: number, approve: boolean, note: string) => d(client.post<Row>(`/policies/${id}/versions/${versionId}/decision`, { approve, note })),
  publishPolicy: (id: number, versionId: number) => d(client.post<Row>(`/policies/${id}/versions/${versionId}/publish`)),
  rollbackPolicy: (id: number, versionId: number, note: string) => d(client.post<Row>(`/policies/${id}/versions/${versionId}/rollback`, { note })),
  withdrawPolicy: (id: number, versionId: number) => d(client.post<Row>(`/policies/${id}/versions/${versionId}/withdraw`)),
  settings: () => d(client.get<Row>('/settings')),
  saveSettings: (body: Row) => d(client.put<Row>('/settings', body)),
};
