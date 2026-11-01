import { createSlice, type PayloadAction } from '@reduxjs/toolkit';
import { jwtDecode } from 'jwt-decode';
import type { UserPayload } from '../services/AuthService';

export interface AuthState {
  token: string | null;
  user: UserPayload | null;
  isAuthenticated: boolean;
}

const getInitialAuth = (): AuthState => {
  try {
    if (typeof window === 'undefined' || !window.localStorage) {
      return { token: null, user: null, isAuthenticated: false };
    }
    const token = localStorage.getItem('jwt_token');
    if (!token) {
      return { token: null, user: null, isAuthenticated: false };
    }
    const decoded = jwtDecode<UserPayload>(token);
    // Expired check (buffer 5 seconds)
    if (decoded.exp && decoded.exp * 1000 < Date.now() + 5000) {
      localStorage.removeItem('jwt_token');
      return { token: null, user: null, isAuthenticated: false };
    }
    return {
      token,
      user: decoded,
      isAuthenticated: true,
    };
  } catch (err) {
    console.warn('Error reading or decoding auth token from storage:', err);
    try {
      localStorage.removeItem('jwt_token');
    } catch {}
    return { token: null, user: null, isAuthenticated: false };
  }
};

const initialState: AuthState = getInitialAuth();

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    loginSuccess: (state, action: PayloadAction<string>) => {
      try {
        const decoded = jwtDecode<UserPayload>(action.payload);
        state.token = action.payload;
        state.user = decoded;
        state.isAuthenticated = true;
        if (typeof window !== 'undefined' && window.localStorage) {
          localStorage.setItem('jwt_token', action.payload);
        }
      } catch (err) {
        console.error('Failed to decode JWT token:', err);
      }
    },
    logout: (state) => {
      state.token = null;
      state.user = null;
      state.isAuthenticated = false;
      try {
        if (typeof window !== 'undefined' && window.localStorage) {
          localStorage.removeItem('jwt_token');
        }
      } catch {}
    },
  },
});

export const { loginSuccess, logout } = authSlice.actions;
export default authSlice.reducer;
