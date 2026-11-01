import axios from 'axios';

export const STOREFRONT_API = 'http://localhost:8080/api/products/admin/storefront';

const client = axios.create({ baseURL: STOREFRONT_API });
client.interceptors.request.use((config) => {
  const token = typeof window !== 'undefined' ? localStorage.getItem('jwt_token') : null;
  if (token && !config.headers.Authorization) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

export type Row = Record<string, any>;

export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; error?: string } | undefined;
    if (error.response?.status === 413) return 'The image is larger than the 5 MB limit.';
    if (error.response?.status === 403) return data?.message || 'You do not have permission for this action.';
    return data?.message || data?.error || error.message;
  }
  return error instanceof Error ? error.message : 'Unexpected error';
}

const d = <T,>(p: Promise<{ data: T }>) => p.then((r) => r.data);

export const sf = {
  list: () => d(client.get<Row[]>('')),
  create: (body: Row) => d(client.post<Row>('', body)),
  rename: (id: number, name: string) => d(client.put<Row>(`/${id}`, { name })),
  dashboard: (id: number) => d(client.get<Row>(`/${id}/dashboard`)),
  audit: (id: number) => d(client.get<Row[]>(`/${id}/audit`)),
  documents: (id: number) => d(client.get<Row[]>(`/${id}/documents`)),
  createPage: (id: number, body: Row) => d(client.post<Row>(`/${id}/pages`, body)),
  versions: (docId: number) => d(client.get<Row[]>(`/documents/${docId}/versions`)),
  version: (docId: number, vid: number) => d(client.get<Row>(`/documents/${docId}/versions/${vid}`)),
  saveDraft: (docId: number, content: unknown, note?: string) => d(client.put<Row>(`/documents/${docId}/draft`, { content, note })),
  submit: (docId: number, vid: number, note?: string) => d(client.post<Row>(`/documents/${docId}/versions/${vid}/submit`, { note })),
  decide: (docId: number, vid: number, approve: boolean, note?: string) => d(client.post<Row>(`/documents/${docId}/versions/${vid}/decision`, { approve, note })),
  withdraw: (docId: number, vid: number) => d(client.post<Row>(`/documents/${docId}/versions/${vid}/withdraw`, {})),
  publish: (docId: number, vid: number, at?: string, note?: string) => d(client.post<Row>(`/documents/${docId}/versions/${vid}/publish`, { at, note })),
  cancelSchedule: (docId: number, vid: number) => d(client.post<Row>(`/documents/${docId}/versions/${vid}/cancel-schedule`)),
  rollback: (docId: number, vid: number, note?: string) => d(client.post<Row>(`/documents/${docId}/versions/${vid}/rollback`, { note })),
  unpublish: (docId: number) => d(client.post<Row>(`/documents/${docId}/unpublish`)),
  compare: (docId: number, from: number, to: number) => d(client.get<Row>(`/documents/${docId}/compare`, { params: { from, to } })),
  preview: (docId: number, versionId?: number) => d(client.get<Row>(`/documents/${docId}/preview`, { params: versionId ? { versionId } : {} })),
  media: (id: number, kind?: string) => d(client.get<Row[]>(`/${id}/media`, { params: kind ? { kind } : {} })),
  upload: (id: number, file: File, kind: string, altText: string) => {
    const form = new FormData();
    form.append('file', file); form.append('kind', kind); form.append('altText', altText);
    return d(client.post<Row>(`/${id}/media`, form));
  },
  deleteMedia: (mediaId: number) => client.delete(`/media/${mediaId}`),
  banners: (id: number) => d(client.get<Row[]>(`/${id}/banners`)),
  createBanner: (id: number, body: Row) => d(client.post<Row>(`/${id}/banners`, body)),
  updateBanner: (bannerId: number, body: Row) => d(client.put<Row>(`/banners/${bannerId}`, body)),
  deleteBanner: (bannerId: number) => client.delete(`/banners/${bannerId}`),
  reorderBanners: (id: number, ids: number[]) => d(client.post<Row>(`/${id}/banners/reorder`, { ids })),
  domains: (id: number) => d(client.get<Row[]>(`/${id}/domains`)),
  addDomain: (id: number, hostname: string) => d(client.post<Row>(`/${id}/domains`, { hostname })),
  verifyDomain: (domainId: number) => d(client.post<Row>(`/domains/${domainId}/verify`)),
  checkSsl: (domainId: number) => d(client.post<Row>(`/domains/${domainId}/check-ssl`)),
  primaryDomain: (domainId: number) => d(client.post<Row>(`/domains/${domainId}/primary`)),
  removeDomain: (domainId: number) => client.delete(`/domains/${domainId}`),
  presets: () => d(client.get<Row[]>('/theme-presets')),
  savePreset: (id: number, name: string, theme: unknown) => d(client.post<Row>(`/${id}/theme-presets`, { name, theme })),
};
