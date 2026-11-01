export interface InventoryItem {
  id: number;
  productId: number;
  sku: string;
  stockQuantity: number;
  reservedQuantity: number;
  availableQuantity: number;
  reorderThreshold: number;
  costPrice: number;
  warehouseLocation?: string;
  active: boolean;
  lowStock: boolean;
  updatedAt: string;
}

export interface StockReservation {
  id: number;
  reservationId: string;
  orderId?: number;
  productId: number;
  quantity: number;
  status: 'HOLD' | 'CONFIRMED' | 'RELEASED' | 'EXPIRED';
  expiresAt: string;
  createdAt: string;
}

export interface StockMovement {
  id: number;
  productId: number;
  delta: number;
  resultingStock: number;
  reason: string;
  performedBy: string;
  createdAt: string;
}

export interface StockAdjustmentRequest {
  productId: number;
  quantityDelta: number;
  reason: string;
  warehouseLocation?: string;
  costPrice?: number;
  reorderThreshold?: number;
}

export interface InventoryAnalyticsSummary {
  totalProductsTracked?: number;
  totalPhysicalUnits?: number;
  totalSkus?: number;
  totalUnitsInStock?: number;
  totalReservedUnits?: number;
  totalAvailableUnits?: number;
  lowStockCount: number;
  outOfStockCount: number;
  stockValuation?: number;
  totalValuation?: number;
  timestamp?: string;
}

export interface CsvImportResult {
  totalProcessed: number;
  updatedCount: number;
  createdCount: number;
  failedCount: number;
  errors: string[];
}
