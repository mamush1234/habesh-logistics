import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';
import { AuthApi } from '../api/endpoints';
import type { AuthResponse, RegisterRequest, User } from '../types';

interface AuthState {
  user: User | null;
  initializing: boolean;
  login: (email: string, password: string) => Promise<User>;
  register: (data: RegisterRequest) => Promise<User>;
  logout: () => void;
}

const Ctx = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient();
  const [user, setUser] = useState<User | null>(null);
  const [initializing, setInitializing] = useState(true);

  const logout = useCallback(() => {
    sessionStorage.removeItem('habesh.token');
    localStorage.removeItem('habesh.token');
    localStorage.removeItem('habesh.user');
    qc.clear();
    setUser(null);
  }, [qc]);

  useEffect(() => {
    let cancelled = false;
    localStorage.removeItem('habesh.token');
    localStorage.removeItem('habesh.user');
    if (!sessionStorage.getItem('habesh.token')) {
      setInitializing(false);
      return;
    }
    AuthApi.me().then(current => {
      if (!cancelled) setUser(current);
    }).catch((error: unknown) => {
      if (cancelled) return;
      if (!isAxiosError(error) || error.response?.status !== 401) {
        toast.error('Could not verify your session. Please sign in again.');
      }
      logout();
    }).finally(() => {
      if (!cancelled) setInitializing(false);
    });
    return () => { cancelled = true; };
  }, [logout]);

  const authenticate = useCallback((response: AuthResponse) => {
    qc.clear();
    sessionStorage.setItem('habesh.token', response.token);
    setUser(response.user);
    return response.user;
  }, [qc]);

  const login = useCallback(async (email: string, password: string) =>
    authenticate(await AuthApi.login(email, password)), [authenticate]);
  const register = useCallback(async (data: RegisterRequest) =>
    authenticate(await AuthApi.register(data)), [authenticate]);

  const value = useMemo(() => ({ user, initializing, login, register, logout }),
    [user, initializing, login, register, logout]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth(): AuthState {
  const value = useContext(Ctx);
  if (!value) throw new Error('useAuth must be used within AuthProvider');
  return value;
}
