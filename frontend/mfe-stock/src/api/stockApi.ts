import axios from 'axios';
import type {
  StockItem,
  StockReservation,
  StockMovement,
  StockAdjustmentRequest,
  StockAnalyticsSummary,
  CsvImportResult
} from '../types/inventory';

const API_BASE = '/api/stock';

function getAuthHeaders() {
  const token = localStorage.getItem('jwt_token') || localStorage.getItem('token');
  return {
    headers: {
      Authorization: token ? `Bearer ${token}` : '',
      'Content-Type': 'application/json',
    },
  };
}

export const stockApi = {
  // Public & Operational
  getItemByProductId: async (productId: number): Promise<StockItem> => {
    const res = await axios.get<StockItem>(`${API_BASE}/${productId}`);
    return res.data;
  },

  getBatchInventory: async (productIds: number[]): Promise<Record<number, StockItem>> => {
    const res = await axios.post<Record<number, StockItem>>(`${API_BASE}/batch`, productIds);
    return res.data;
  },

  // Admin & Management
  getAllItems: async (): Promise<StockItem[]> => {
    const res = await axios.get<StockItem[]>(`${API_BASE}/admin/items`, getAuthHeaders());
    return res.data;
  },

  getAnalyticsSummary: async (): Promise<StockAnalyticsSummary> => {
    const res = await axios.get<StockAnalyticsSummary>(`${API_BASE}/admin/analytics`, getAuthHeaders());
    return res.data;
  },

  adjustStock: async (data: StockAdjustmentRequest): Promise<StockItem> => {
    const res = await axios.post<StockItem>(`${API_BASE}/adjustments`, data, getAuthHeaders());
    return res.data;
  },

  getMovements: async (productId?: number): Promise<StockMovement[]> => {
    const url = productId ? `${API_BASE}/admin/movements?productId=${productId}` : `${API_BASE}/admin/movements`;
    const res = await axios.get<StockMovement[]>(url, getAuthHeaders());
    return res.data;
  },

  getReservations: async (): Promise<StockReservation[]> => {
    const res = await axios.get<StockReservation[]>(`${API_BASE}/admin/reservations`, getAuthHeaders());
    return res.data;
  },

  releaseReservation: async (reservationId: string): Promise<void> => {
    await axios.post(`${API_BASE}/reservations/${reservationId}/release`, {}, getAuthHeaders());
  },

  exportCsv: async (): Promise<string> => {
    const token = localStorage.getItem('jwt_token') || localStorage.getItem('token');
    const res = await axios.get(`${API_BASE}/admin/export/csv`, {
      headers: {
        Authorization: token ? `Bearer ${token}` : '',
      },
      responseType: 'text',
    });
    return res.data;
  },

  importCsv: async (file: File): Promise<CsvImportResult> => {
    const token = localStorage.getItem('jwt_token') || localStorage.getItem('token');
    const formData = new FormData();
    formData.append('file', file);
    const res = await axios.post<CsvImportResult>(`${API_BASE}/admin/import/csv`, formData, {
      headers: {
        Authorization: token ? `Bearer ${token}` : '',
        'Content-Type': 'multipart/form-data',
      },
    });
    return res.data;
  },
};
