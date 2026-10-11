import axios from 'axios';

const baseURL = import.meta.env.VITE_API_URL ?? '';

export const api = axios.create({ baseURL });

api.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('habesh.token');
  if (token && !config.url?.startsWith('/api/auth/')) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (r) => r,
  (err) => {
    if (err?.response?.status === 401) {
      sessionStorage.removeItem('habesh.token');
      if (!err.config?.url?.startsWith('/api/auth/') && !window.location.pathname.startsWith('/login')) {
        window.location.href = '/login';
      }
    }
    return Promise.reject(err);
  }
);
