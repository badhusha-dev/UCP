export interface StockItem {
  id: number;
  productId: number;
  sku: string;
  stockQuantity: number;
  reservedQuantity: number;
  availableQuantity: number;
  reorderThreshold: number;
  costPrice: number;
  warehouseLocation?: string;
  lowStock: boolean;
  updatedAt: string;
}

export interface StockReservation {
  id: number;
  reservationId: string;
  productId: number;
  quantity: number;
  orderId?: number;
  status: 'HOLD' | 'CONFIRMED' | 'RELEASED' | 'EXPIRED';
  expiresAt: string;
  createdAt: string;
}

export interface StockMovement {
  id: number;
  productId: number;
  delta: number;
  movementType: string;
  reason: string;
  performedBy: string;
  createdAt: string;
}

export interface StockAdjustmentRequest {
  productId: number;
  delta: number;
  movementType: string;
  reason: string;
}

export interface StockAnalyticsSummary {
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
