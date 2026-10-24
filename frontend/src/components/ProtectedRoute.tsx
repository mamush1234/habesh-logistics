import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import type { Role } from '../types';

export function ProtectedRoute({ children, roles }: { children: React.ReactNode; roles?: Role[] }) {
  const { user, initializing } = useAuth();
  const loc = useLocation();
  if (initializing) return <p role="status">Verifying your session...</p>;
  if (!user) return <Navigate to={`/login?next=${encodeURIComponent(loc.pathname + loc.search + loc.hash)}`} replace />;
  if (user.mustChangePassword && loc.pathname !== '/account') return <Navigate to="/account" replace />;
  if (roles && !roles.includes(user.role)) return <Navigate to="/" replace />;
  return <>{children}</>;
}
