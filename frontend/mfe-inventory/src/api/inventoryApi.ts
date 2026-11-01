import axios from 'axios';
import type {
  InventoryItem,
  StockReservation,
  StockMovement,
  StockAdjustmentRequest,
  InventoryAnalyticsSummary,
  CsvImportResult
} from '../types/inventory';

import { readApplicationSession } from '@ecommerce/shared-library/src/auth/applicationSession';

const API_BASE = '/api/inventory';

function currentToken(): string | undefined {
  return readApplicationSession('INVENTORY', sessionStorage)?.token;
}

function getAuthHeaders() {
  const token = currentToken();
  return {
    headers: {
      Authorization: token ? `Bearer ${token}` : '',
      'Content-Type': 'application/json',
    },
  };
}

export const inventoryApi = {
  // Public & Operational
  getItemByProductId: async (productId: number): Promise<InventoryItem> => {
    const res = await axios.get<InventoryItem>(`${API_BASE}/${productId}`, getAuthHeaders());
    return res.data;
  },

  getBatchInventory: async (productIds: number[]): Promise<Record<number, InventoryItem>> => {
    const res = await axios.post<Record<number, InventoryItem>>(`${API_BASE}/batch`, productIds, getAuthHeaders());
    return res.data;
  },

  // Admin & Management
  getAllItems: async (): Promise<InventoryItem[]> => {
    const res = await axios.get<InventoryItem[]>(`${API_BASE}/admin/items`, getAuthHeaders());
    return res.data;
  },

  createMetadata: async (data: Pick<InventoryItem, 'productId' | 'sku' | 'reorderThreshold' | 'costPrice' | 'warehouseLocation'>): Promise<InventoryItem> => {
    const res = await axios.post<InventoryItem>(`${API_BASE}/admin/items`, data, getAuthHeaders());
    return res.data;
  },

  updateMetadata: async (productId: number, data: Pick<InventoryItem, 'productId' | 'sku' | 'reorderThreshold' | 'costPrice' | 'warehouseLocation'>): Promise<InventoryItem> => {
    const res = await axios.put<InventoryItem>(`${API_BASE}/admin/items/${productId}`, data, getAuthHeaders());
    return res.data;
  },

  archiveMetadata: async (productId: number): Promise<void> => {
    await axios.delete(`${API_BASE}/admin/items/${productId}`, getAuthHeaders());
  },

  restoreMetadata: async (productId: number): Promise<InventoryItem> => {
    const res = await axios.patch<InventoryItem>(`${API_BASE}/admin/items/${productId}/restore`, {}, getAuthHeaders());
    return res.data;
  },

  getAnalyticsSummary: async (): Promise<InventoryAnalyticsSummary> => {
    const res = await axios.get<InventoryAnalyticsSummary>(`${API_BASE}/admin/analytics`, getAuthHeaders());
    return res.data;
  },

  adjustStock: async (data: StockAdjustmentRequest): Promise<InventoryItem> => {
    const res = await axios.post<InventoryItem>(`${API_BASE}/admin/adjust`, data, getAuthHeaders());
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
    const token = currentToken();
    const res = await axios.get(`${API_BASE}/admin/export/csv`, {
      headers: {
        Authorization: token ? `Bearer ${token}` : '',
      },
      responseType: 'text',
    });
    return res.data;
  },

  importCsv: async (file: File): Promise<CsvImportResult> => {
    const token = currentToken();
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
