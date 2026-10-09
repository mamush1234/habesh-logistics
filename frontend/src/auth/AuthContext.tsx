import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { AuthApi } from '../api/endpoints';
import type { User } from '../types';

interface AuthState {
  user: User | null;
  token: string | null;
  login: (email: string, password: string) => Promise<User>;
  register: (data: { firstName: string; lastName: string; email: string; phone?: string; password: string; role?: 'SENDER' | 'TRAVELER' }) => Promise<User>;
  logout: () => void;
}

const Ctx = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => {
    const raw = localStorage.getItem('habesh.user');
    return raw ? JSON.parse(raw) as User : null;
  });
  const [token, setToken] = useState<string | null>(() => localStorage.getItem('habesh.token'));

  useEffect(() => {
    if (token && !user) {
      AuthApi.me().then((u) => {
        setUser(u);
        localStorage.setItem('habesh.user', JSON.stringify(u));
      }).catch(() => logout());
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const resp = await AuthApi.login(email, password);
    localStorage.setItem('habesh.token', resp.token);
    localStorage.setItem('habesh.user', JSON.stringify(resp.user));
    setToken(resp.token);
    setUser(resp.user);
    return resp.user;
  }, []);

  const register = useCallback(async (data: any) => {
    const resp = await AuthApi.register(data);
    localStorage.setItem('habesh.token', resp.token);
    localStorage.setItem('habesh.user', JSON.stringify(resp.user));
    setToken(resp.token);
    setUser(resp.user);
    return resp.user;
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem('habesh.token');
    localStorage.removeItem('habesh.user');
    setUser(null);
    setToken(null);
  }, []);

  const value = useMemo(() => ({ user, token, login, register, logout }), [user, token, login, register, logout]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth(): AuthState {
  const v = useContext(Ctx);
  if (!v) throw new Error('useAuth must be used within AuthProvider');
  return v;
}
