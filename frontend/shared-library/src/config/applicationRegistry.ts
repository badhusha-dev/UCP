// Application registry for the ten independently deployable frontends.
// Application codes and statuses mirror user-service's application_registry
// (Flyway V6/V7); a token's application_code claim uses these exact values.
// Origins come from runtime/build configuration, never from request data, and
// redirects are accepted only when they resolve to an origin registered here.

export const APPLICATION_CODES = [
  'SUPER_ADMIN',
  'ECOMMERCE_ADMIN',
  'STOREFRONT',
  'INVENTORY',
  'ACCOUNTING',
  'DELIVERY_OPS',
  'VENDOR_PORTAL',
  'CRM',
  'QUALITY_CENTER',
  'CUSTOMER_SUPPORT',
] as const;

export type ApplicationCode = (typeof APPLICATION_CODES)[number];

export type ApplicationStatus = 'ACTIVE' | 'PLANNED';

export interface ApplicationDefinition {
  code: ApplicationCode;
  displayName: string;
  status: ApplicationStatus;
  /** Public origin, e.g. https://inventory.example.com. Empty while PLANNED. */
  origin: string;
  /** Path of the application's own login page. */
  loginPath: string;
}

export const APPLICATION_REGISTRY: Readonly<Record<ApplicationCode, ApplicationDefinition>> = {
  SUPER_ADMIN: { code: 'SUPER_ADMIN', displayName: 'Super Admin Portal', status: 'ACTIVE', origin: '', loginPath: '/login' },
  ECOMMERCE_ADMIN: { code: 'ECOMMERCE_ADMIN', displayName: 'E-commerce Admin', status: 'PLANNED', origin: '', loginPath: '/login' },
  STOREFRONT: { code: 'STOREFRONT', displayName: 'Customer Storefront', status: 'PLANNED', origin: '', loginPath: '/login' },
  INVENTORY: { code: 'INVENTORY', displayName: 'Inventory Management', status: 'ACTIVE', origin: '', loginPath: '/login' },
  ACCOUNTING: { code: 'ACCOUNTING', displayName: 'Accounting and Finance', status: 'PLANNED', origin: '', loginPath: '/login' },
  DELIVERY_OPS: { code: 'DELIVERY_OPS', displayName: 'Delivery Operations', status: 'ACTIVE', origin: '', loginPath: '/login' },
  VENDOR_PORTAL: { code: 'VENDOR_PORTAL', displayName: 'Vendor and Restaurant Portal', status: 'PLANNED', origin: '', loginPath: '/login' },
  CRM: { code: 'CRM', displayName: 'CRM', status: 'PLANNED', origin: '', loginPath: '/login' },
  QUALITY_CENTER: { code: 'QUALITY_CENTER', displayName: 'Tester / Quality Center', status: 'ACTIVE', origin: '', loginPath: '/login' },
  CUSTOMER_SUPPORT: { code: 'CUSTOMER_SUPPORT', displayName: 'Customer Support', status: 'PLANNED', origin: '', loginPath: '/login' },
};

export function isApplicationCode(value: unknown): value is ApplicationCode {
  return typeof value === 'string' && (APPLICATION_CODES as readonly string[]).includes(value);
}

/**
 * Builds the registry from runtime-supplied origins. Only ACTIVE applications
 * need an origin; a missing or non-HTTPS origin for an ACTIVE application
 * (outside localhost) is rejected so misconfiguration fails at startup.
 */
export function buildApplicationRegistry(
  origins: Partial<Record<ApplicationCode, string>>,
): Readonly<Record<ApplicationCode, ApplicationDefinition>> {
  const registry = { ...APPLICATION_REGISTRY };
  for (const code of APPLICATION_CODES) {
    const definition = APPLICATION_REGISTRY[code];
    if (definition.status !== 'ACTIVE') continue;
    const origin = origins[code];
    if (!origin) {
      throw new Error(`Missing origin for active application ${code}`);
    }
    const normalized = normalizeOrigin(origin);
    if (!normalized) {
      throw new Error(`Invalid origin for application ${code}`);
    }
    registry[code] = { ...definition, origin: normalized };
  }
  return registry;
}

/** Returns the origin (scheme://host[:port]) when it is HTTPS or localhost HTTP, otherwise null. */
export function normalizeOrigin(value: string): string | null {
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    return null;
  }
  const isLocal = url.hostname === 'localhost' || url.hostname === '127.0.0.1';
  if (url.protocol !== 'https:' && !(url.protocol === 'http:' && isLocal)) return null;
  if (url.username || url.password || url.search || url.hash) return null;
  if (url.pathname !== '/' && url.pathname !== '') return null;
  return url.origin;
}

/**
 * Accepts a post-login redirect only when it points at the registered origin of
 * the application, or is a relative path on the current application. Anything
 * else returns null so the caller falls back to the application home page.
 */
export function resolveSafeRedirect(
  target: string | null | undefined,
  applicationCode: ApplicationCode,
  registry: Readonly<Record<ApplicationCode, ApplicationDefinition>>,
): string | null {
  if (!target) return null;
  const app = registry[applicationCode];
  if (!app.origin) return null;
  if (target.startsWith('/') && !target.startsWith('//') && !target.includes('\\')) {
    return new URL(target, app.origin).toString();
  }
  let url: URL;
  try {
    url = new URL(target);
  } catch {
    return null;
  }
  return url.origin === app.origin ? url.toString() : null;
}
