import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { toast } from 'sonner';

export function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [sp] = useSearchParams();
  const requestedNext = sp.get('next');
  const next = requestedNext?.startsWith('/') && !requestedNext.startsWith('//') ? requestedNext : '/dashboard';
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    try {
      const u = await login(email, password);
      toast.success(`Welcome, ${u.firstName}`);
      navigate(u.mustChangePassword ? '/account' : next, { replace: true });
    } catch (err: any) {
      toast.error(err?.response?.data?.message || 'Invalid credentials');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="max-w-md mx-auto">
      <div className="card">
        <h1 className="text-2xl font-bold">Login</h1>
        <p className="text-ink-500 text-sm mt-1">Welcome back. Your next connection starts here.</p>
        <form onSubmit={onSubmit} className="mt-5 space-y-4">
          <div>
            <label className="label">Email</label>
            <input className="input" type="email" value={email} onChange={e => setEmail(e.target.value)} required data-testid="login-email" />
          </div>
          <div>
            <label className="label">Password</label>
            <input className="input" type="password" value={password} onChange={e => setPassword(e.target.value)} required data-testid="login-password" />
          </div>
          <button className="btn btn-brand w-full" disabled={loading} data-testid="login-submit">
            {loading ? 'Signing in...' : 'Sign in'}
          </button>
        </form>
        <p className="text-sm text-ink-500 mt-5">
          New here? <Link to={`/register?next=${encodeURIComponent(next)}`} className="underline font-semibold">Create an account</Link>
        </p>
      </div>
    </div>
  );
}
