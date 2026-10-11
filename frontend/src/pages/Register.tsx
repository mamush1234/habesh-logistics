import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { useNavigate, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';
import { AuthApi } from '../api/endpoints';

export function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [token] = useState(() => new URLSearchParams(window.location.hash.slice(1)).get('invite') ?? '');
  const invitation = useQuery({
    queryKey: ['invitation'],
    queryFn: () => AuthApi.invitation(token),
    enabled: token.length === 48,
    retry: false,
    staleTime: 0,
    gcTime: 0,
  });
  const [form, setForm] = useState({ firstName: '', lastName: '', phone: '', password: '' });
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!invitation.data) return;
    setLoading(true);
    try {
      await register({ ...form, email: invitation.data.email, invitationToken: token });
      window.history.replaceState(null, '', '/register');
      toast.success('Account created');
      navigate('/dashboard', { replace: true });
    } catch (error: unknown) {
      const message: unknown = isAxiosError(error) ? error.response?.data?.message : undefined;
      toast.error(typeof message === 'string' ? message : 'Could not create account. Contact your administrator.');
    } finally {
      setLoading(false);
    }
  }

  if (token.length !== 48) return <div className="card max-w-lg mx-auto">
    <h1 className="text-2xl font-bold">Invitation required</h1>
    <p className="mt-3">Registration is private. Ask your administrator for a personal invitation link.</p>
    <Link to="/login" className="btn btn-ghost mt-3">Login</Link>
  </div>;
  if (invitation.isPending) return <p role="status">Checking invitation...</p>;
  if (invitation.isError) return <div className="card max-w-lg mx-auto" role="alert">
    <h1 className="text-2xl font-bold">Invitation unavailable</h1>
    <p className="mt-3">This invitation may be expired, revoked or already used. Contact your administrator.</p>
    <button type="button" className="btn btn-ghost mt-3" onClick={() => invitation.refetch()}>Retry</button>
    <Link to="/login" className="btn btn-ghost mt-3">Login</Link>
  </div>;

  return <div className="card max-w-lg mx-auto">
    <h1 className="text-2xl font-bold">Accept invitation</h1>
    <p className="mt-3">{invitation.data.email} - {invitation.data.role}</p>
    <form onSubmit={onSubmit} className="mt-5 grid grid-cols-2 gap-3">
      {(['firstName', 'lastName', 'phone', 'password'] as const).map(field => <div key={field}>
        <label className="label" htmlFor={`register-${field}`}>
          {field === 'firstName' ? 'First name' : field === 'lastName' ? 'Last name' : field === 'phone' ? 'Phone' : 'Password (at least 12 characters)'}
        </label>
        <input id={`register-${field}`} className="input" type={field === 'password' ? 'password' : 'text'}
          autoComplete={field === 'password' ? 'new-password' : undefined}
          minLength={field === 'password' ? 12 : undefined} maxLength={field === 'password' ? 72 : undefined}
          value={form[field]} onChange={e => setForm(current => ({ ...current, [field]: e.target.value }))}
          required={field !== 'phone'} data-testid={`register-${field}`} />
      </div>)}
      <button className="btn btn-brand col-span-2" disabled={loading} data-testid="register-submit">
        {loading ? 'Creating...' : 'Create account'}
      </button>
    </form>
    <p className="text-sm mt-5">Already registered? <Link to="/login" className="underline">Login</Link></p>
  </div>;
}
