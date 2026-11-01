import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import authReducer, {
  loginSuccess,
  logout,
  type AuthState,
} from '../src/store/authSlice.ts';

// Helper to create a valid unexpired JWT token for testing
function createMockJwt(payload: Record<string, unknown>): string {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const body = Buffer.from(JSON.stringify(payload)).toString('base64url');
  const signature = 'test_signature';
  return `${header}.${body}.${signature}`;
}

describe('authSlice reducer tests', () => {
  const initialLoggedOutState: AuthState = {
    token: null,
    user: null,
    isAuthenticated: false,
  };

  it('should initialize with logged out state when no storage is present', () => {
    const state = authReducer(undefined, { type: '@@INIT' });
    assert.equal(state.isAuthenticated, false);
    assert.equal(state.token, null);
    assert.equal(state.user, null);
  });

  it('should authenticate user and store decoded payload on loginSuccess', () => {
    const futureExp = Math.floor(Date.now() / 1000) + 3600;
    const token = createMockJwt({
      sub: 'alice@example.com',
      id: 5,
      email: 'alice@example.com',
      name: 'Alice Admin',
      role: 'ADMIN',
      exp: futureExp,
    });

    const nextState = authReducer(initialLoggedOutState, loginSuccess(token));

    assert.equal(nextState.isAuthenticated, true);
    assert.equal(nextState.token, token);
    assert.notEqual(nextState.user, null);
    assert.equal(nextState.user?.sub, 'alice@example.com');
    assert.equal(nextState.user?.id, 5);
    assert.equal(nextState.user?.role, 'ADMIN');
  });

  it('should clear authentication state on logout', () => {
    const loggedInState: AuthState = {
      token: 'some.valid.token',
      user: {
        sub: 'alice@example.com',
        id: 5,
        email: 'alice@example.com',
        name: 'Alice Admin',
        role: 'ADMIN',
        exp: 2000000000,
      },
      isAuthenticated: true,
    };

    const nextState = authReducer(loggedInState, logout());

    assert.equal(nextState.isAuthenticated, false);
    assert.equal(nextState.token, null);
    assert.equal(nextState.user, null);
  });

  it('should handle invalid JWT token safely without crashing', () => {
    const nextState = authReducer(initialLoggedOutState, loginSuccess('invalid.token.string'));

    // Should not crash and should remain unauthenticated
    assert.equal(nextState.isAuthenticated, false);
    assert.equal(nextState.token, null);
    assert.equal(nextState.user, null);
  });
});
