// Shared sign-in and session handling for the independently deployable applications.
// The server issues an application- and tenant-scoped token after checking membership,
// entitlement and grant. The browser never decides access: claims are read here only to
// reject a token issued for another application and to drop expired sessions early.
// Signature, issuer, audience and the current grant are always verified server-side.

import type { ApplicationCode } from '../config/applicationRegistry';

export interface ApplicationSession {
  token: string;
  applicationCode: ApplicationCode;
  tenantId: string;
  tenantSlug: string;
  tenantName: string;
  role: string;
}

export interface ApplicationSignInRequest {
  applicationCode: ApplicationCode;
  tenantSlug: string;
  email: string;
  password: string;
}

export interface SessionStorageLike {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

export class ApplicationSignInError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApplicationSignInError';
    this.status = status;
  }
}

const STORAGE_PREFIX = 'ucp.session.';
const DEFAULT_ERRORS: Record<number, string> = {
  400: 'Enter your client workspace, email and password.',
  401: 'The email or password is incorrect.',
  403: 'Your account does not have access to this application for that client.',
};

export function sessionStorageKey(applicationCode: ApplicationCode): string {
  return STORAGE_PREFIX + applicationCode;
}

/** Decodes a JWT payload without verifying it. Returns null for malformed tokens. */
export function decodeTokenClaims(token: string): Record<string, unknown> | null {
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
    const json = decodeURIComponent(
      Array.from(atob(padded), (char) => '%' + char.charCodeAt(0).toString(16).padStart(2, '0')).join(''),
    );
    const claims = JSON.parse(json);
    return claims && typeof claims === 'object' ? claims : null;
  } catch {
    return null;
  }
}

/**
 * True when the token claims this application and tenant and has not expired.
 * A token without an `exp` claim is treated as invalid.
 */
export function isUsableApplicationToken(
  token: string,
  applicationCode: ApplicationCode,
  tenantId: string,
  nowMs: number = Date.now(),
): boolean {
  const claims = decodeTokenClaims(token);
  if (!claims) return false;
  if (claims.application_code !== applicationCode || claims.tenant_id !== tenantId) return false;
  return typeof claims.exp === 'number' && claims.exp * 1000 > nowMs;
}

/** Signs in to one application for one client workspace. Throws ApplicationSignInError on failure. */
export async function signInToApplication(
  request: ApplicationSignInRequest,
  fetchImpl: typeof fetch = fetch,
  apiBaseUrl = '',
): Promise<ApplicationSession> {
  let response: Response;
  try {
    response = await fetchImpl(`${apiBaseUrl}/api/users/login/application`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email: request.email.trim(),
        password: request.password,
        applicationCode: request.applicationCode,
        tenantSlug: request.tenantSlug.trim().toLowerCase(),
      }),
    });
  } catch {
    throw new ApplicationSignInError('The sign-in service could not be reached. Try again shortly.', 0);
  }

  const body = await response.json().catch(() => null);
  if (!response.ok) {
    const serverMessage = body && typeof body.error === 'string' ? body.error : null;
    throw new ApplicationSignInError(
      DEFAULT_ERRORS[response.status] ?? serverMessage ?? 'Sign in failed. Please try again.',
      response.status,
    );
  }

  const session = toSession(body, request.applicationCode);
  if (!session
      || !isUsableApplicationToken(session.token, request.applicationCode, session.tenantId)) {
    throw new ApplicationSignInError('The sign-in response was not valid for this application.', response.status);
  }
  return session;
}

export function storeApplicationSession(session: ApplicationSession, storage: SessionStorageLike): void {
  storage.setItem(sessionStorageKey(session.applicationCode), JSON.stringify(session));
}

/** Returns the stored session for this application, clearing it if it is malformed, foreign or expired. */
export function readApplicationSession(
  applicationCode: ApplicationCode,
  storage: SessionStorageLike,
  nowMs: number = Date.now(),
): ApplicationSession | null {
  const key = sessionStorageKey(applicationCode);
  let session: ApplicationSession | null = null;
  try {
    const raw = storage.getItem(key);
    session = raw ? toSession(JSON.parse(raw), applicationCode) : null;
  } catch {
    session = null;
  }
  if (session && isUsableApplicationToken(session.token, applicationCode, session.tenantId, nowMs)) {
    return session;
  }
  storage.removeItem(key);
  return null;
}

export function clearApplicationSession(applicationCode: ApplicationCode, storage: SessionStorageLike): void {
  storage.removeItem(sessionStorageKey(applicationCode));
}

// Platform (not tenant-scoped) sessions, used by the Super Admin portal. The token comes from
// the platform login and must not carry a tenant; the server re-checks the role on every request.

export interface PlatformSession {
  token: string;
  email: string;
  role: string;
}

const PLATFORM_KEY = STORAGE_PREFIX + 'PLATFORM';

/** True when the token is an unexpired platform token (no tenant or application scope). */
export function isUsablePlatformToken(token: string, nowMs: number = Date.now()): boolean {
  const claims = decodeTokenClaims(token);
  if (!claims || claims.tenant_id !== undefined || claims.application_code !== undefined) return false;
  return typeof claims.exp === 'number' && claims.exp * 1000 > nowMs;
}

/**
 * Signs in through the platform login. When requiredRole is given, a token for another role is
 * rejected with 403 so the portal can explain the restriction before making protected calls.
 */
export async function signInToPlatform(
  request: { email: string; password: string },
  requiredRole?: string,
  fetchImpl: typeof fetch = fetch,
  apiBaseUrl = '',
): Promise<PlatformSession> {
  let response: Response;
  try {
    response = await fetchImpl(`${apiBaseUrl}/api/users/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: request.email.trim(), password: request.password }),
    });
  } catch {
    throw new ApplicationSignInError('The sign-in service could not be reached. Try again shortly.', 0);
  }
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    const messages: Record<number, string> = { ...DEFAULT_ERRORS, 403: 'This account is deactivated.' };
    throw new ApplicationSignInError(messages[response.status] ?? 'Sign in failed. Please try again.', response.status);
  }
  const token = body && typeof body.token === 'string' ? body.token : '';
  if (!isUsablePlatformToken(token)) {
    throw new ApplicationSignInError('The sign-in response was not a valid platform session.', response.status);
  }
  const session = toPlatformSession(token);
  if (requiredRole && session.role !== requiredRole) {
    throw new ApplicationSignInError('This portal is limited to platform administrators.', 403);
  }
  return session;
}

export function storePlatformSession(session: PlatformSession, storage: SessionStorageLike): void {
  storage.setItem(PLATFORM_KEY, session.token);
}

/** Returns the stored platform session, clearing it if it is malformed, tenant-scoped or expired. */
export function readPlatformSession(storage: SessionStorageLike, nowMs: number = Date.now()): PlatformSession | null {
  const token = storage.getItem(PLATFORM_KEY);
  if (token && isUsablePlatformToken(token, nowMs)) return toPlatformSession(token);
  storage.removeItem(PLATFORM_KEY);
  return null;
}

export function clearPlatformSession(storage: SessionStorageLike): void {
  storage.removeItem(PLATFORM_KEY);
}

function toPlatformSession(token: string): PlatformSession {
  const claims = decodeTokenClaims(token) ?? {};
  return {
    token,
    email: typeof claims.sub === 'string' ? claims.sub : '',
    role: typeof claims.role === 'string' ? claims.role : '',
  };
}

/** Parses a session payload, accepting it only when it is for the expected application. */
function toSession(value: unknown, expectedCode: ApplicationCode): ApplicationSession | null {
  if (!value || typeof value !== 'object') return null;
  const v = value as Record<string, unknown>;
  if (typeof v.token !== 'string' || v.applicationCode !== expectedCode || typeof v.tenantId !== 'string') return null;
  return {
    token: v.token,
    applicationCode: expectedCode,
    tenantId: v.tenantId,
    tenantSlug: typeof v.tenantSlug === 'string' ? v.tenantSlug : '',
    tenantName: typeof v.tenantName === 'string' ? v.tenantName : '',
    role: typeof v.role === 'string' ? v.role : '',
  };
}
