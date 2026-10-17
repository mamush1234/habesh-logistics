import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';
import type { RegisterRequest } from '../types';

export function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const requestedNext = params.get('next');
  const next = requestedNext?.startsWith('/') && !requestedNext.startsWith('//') ? requestedNext : '/dashboard';
  const [form, setForm] = useState<RegisterRequest>({
    firstName: '', lastName: '', email: '', phone: '', password: '',
    role: params.get('role') === 'TRAVELER' ? 'TRAVELER' : 'SENDER',
  });
  const [loading, setLoading] = useState(false);

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    setLoading(true);
    try {
      await register(form);
      toast.success('Welcome to HabeshaList! Your account is ready.');
      navigate(next, { replace: true });
    } catch (error: unknown) {
      toast.error(isAxiosError<{ message?: string }>(error)
        ? error.response?.data?.message || 'Could not create your account. Please try again.'
        : 'Could not create your account. Please try again.');
    } finally {
      setLoading(false);
    }
  }

  return <div className="card max-w-lg mx-auto">
    <h1 className="text-3xl font-bold">Join the journey</h1>
    <p className="mt-2 text-ink-500">Create an account to send packages or travel with a little extra purpose.</p>
    <form onSubmit={onSubmit} className="mt-6 grid sm:grid-cols-2 gap-4">
      <div className="sm:col-span-2">
        <label className="label" htmlFor="register-role">I want to</label>
        <select id="register-role" className="input" value={form.role}
          onChange={event => setForm(current => ({ ...current, role: event.target.value === 'TRAVELER' ? 'TRAVELER' : 'SENDER' }))}>
          <option value="SENDER">Send packages</option><option value="TRAVELER">Travel and carry packages</option>
        </select>
      </div>
      {(['firstName', 'lastName', 'email', 'phone', 'password'] as const).map(field => <div
        key={field} className={field === 'email' || field === 'password' ? 'sm:col-span-2' : ''}>
        <label className="label" htmlFor={`register-${field}`}>
          {{ firstName: 'First name', lastName: 'Last name', email: 'Email', phone: 'Phone (optional)', password: 'Password (at least 12 characters)' }[field]}
        </label>
        <input id={`register-${field}`} className="input" type={field === 'password' ? 'password' : field === 'email' ? 'email' : field === 'phone' ? 'tel' : 'text'}
          autoComplete={{ firstName: 'given-name', lastName: 'family-name', email: 'email', phone: 'tel', password: 'new-password' }[field]}
          minLength={field === 'password' ? 12 : undefined} maxLength={field === 'password' ? 72 : 255}
          value={form[field]} onChange={event => setForm(current => ({ ...current, [field]: event.target.value }))}
          required={field !== 'phone'} data-testid={`register-${field}`} />
      </div>)}
      <button className="btn btn-primary sm:col-span-2" disabled={loading} data-testid="register-submit">
        {loading ? 'Creating account...' : 'Create account'}
      </button>
    </form>
    <p className="text-sm mt-5">Already a member? <Link to={`/login?next=${encodeURIComponent(next)}`} className="underline">Log in</Link></p>
    <p className="text-xs text-ink-500 mt-3">Agent accounts are created by an administrator.</p>
  </div>;
}
