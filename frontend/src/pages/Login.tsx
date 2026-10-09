import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { toast } from 'sonner';

export function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [sp] = useSearchParams();
  const next = sp.get('next') || '/dashboard';
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    try {
      const u = await login(email, password);
      toast.success(`Welcome, ${u.firstName}`);
      navigate(next, { replace: true });
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
        <p className="text-ink-500 text-sm mt-1">Access your dashboard.</p>
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
          No account? <Link to="/register" className="text-brand-700 font-semibold">Create one</Link>
        </p>
        <div className="mt-5 rounded-xl bg-ink-100 p-3 text-xs text-ink-700">
          <div className="font-semibold mb-1">Demo accounts</div>
          <div>admin@habeshlist.com / Admin@123</div>
          <div>traveler@example.com / Traveler@123</div>
          <div>agent@example.com / Agent@123</div>
          <div>sender@example.com / Sender@123</div>
        </div>
      </div>
    </div>
  );
}
