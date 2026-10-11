import { useState } from 'react';
import { isAxiosError } from 'axios';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { AuthApi } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';

export function Account() {
  const { logout } = useAuth();
  const navigate = useNavigate();
  const [current, setCurrent] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  return <form className="card max-w-lg mx-auto space-y-3" onSubmit={async e => {
    e.preventDefault();
    setBusy(true);
    try {
      await AuthApi.changePassword(current, password);
      logout();
      toast.success('Password changed. Sign in again. Other sessions have been revoked.');
      navigate('/login', { replace: true });
    } catch (error: unknown) {
      const message: unknown = isAxiosError(error) ? error.response?.data?.message : undefined;
      toast.error(typeof message === 'string' ? message : 'Could not change password');
    } finally { setBusy(false); }
  }}>
    <h1 className="text-2xl font-bold">Change password</h1>
    <label className="label" htmlFor="current-password">Current password</label>
    <input id="current-password" className="input" type="password" autoComplete="current-password"
      required value={current} onChange={e => setCurrent(e.target.value)} />
    <label className="label" htmlFor="new-password">New password (at least 12 characters)</label>
    <input id="new-password" className="input" type="password" autoComplete="new-password"
      required minLength={12} maxLength={72} value={password} onChange={e => setPassword(e.target.value)} />
    <button className="btn btn-brand" disabled={busy}>{busy ? 'Updating...' : 'Change password'}</button>
  </form>;
}
