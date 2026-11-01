export type ErrorSeverity = 'CRITICAL' | 'ERROR' | 'WARNING' | 'INFO';
export type ErrorStatus = 'NEW' | 'INVESTIGATING' | 'RESOLVED' | 'IGNORED';

export interface Breadcrumb {
  timestamp: string;
  category: string;
  message: string;
  level?: string;
  data?: Record<string, any>;
}

export interface ErrorGroup {
  id: number;
  fingerprint: string;
  serviceName: string;
  environment: string;
  errorType: string;
  message: string;
  severity: ErrorSeverity;
  status: ErrorStatus;
  assignedTo?: string | null;
  occurrencesCount: number;
  firstSeen: string;
  lastSeen: string;
}

export interface ErrorRecord {
  id: number;
  groupId: number;
  fingerprint: string;
  serviceName: string;
  version?: string;
  environment: string;
  releaseTag?: string;
  errorType: string;
  message: string;
  stackTrace?: string;
  exceptionChain?: string;
  requestMethod?: string;
  requestUri?: string;
  responseStatus?: number;
  traceId?: string;
  correlationId?: string;
  userId?: number;
  userRole?: string;
  browser?: string;
  os?: string;
  frontendRoute?: string;
  severity: ErrorSeverity;
  metadata?: Record<string, any>;
  breadcrumbs?: Breadcrumb[];
  createdAt: string;
}

export interface ErrorOverview {
  totalOccurrences: number;
  totalGroups: number;
  newErrorsCount: number;
  unresolvedCriticalCount: number;
  last24HoursCount: number;
  serviceBreakdown: Record<string, number>;
  severityBreakdown: Record<string, number>;
  statusBreakdown: Record<string, number>;
  hourlyTrend24h: Record<string, number>;
  recentGroups: ErrorGroup[];
}
