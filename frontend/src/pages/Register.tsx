import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { useNavigate, Link } from 'react-router-dom';
import { toast } from 'sonner';

export function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', phone: '', password: '', role: 'SENDER' as 'SENDER' | 'TRAVELER' });
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    try {
      await register(form);
      toast.success('Account created');
      navigate('/dashboard');
    } catch (err: any) {
      toast.error(err?.response?.data?.message || 'Registration failed');
    } finally {
      setLoading(false);
    }
  }

  const upd = (k: keyof typeof form) => (e: any) => setForm({ ...form, [k]: e.target.value });

  return (
    <div className="max-w-lg mx-auto">
      <div className="card">
        <h1 className="text-2xl font-bold">Create an account</h1>
        <form onSubmit={onSubmit} className="mt-5 grid grid-cols-2 gap-3">
          <div className="col-span-2">
            <label className="label">I am a</label>
            <select className="input" value={form.role} onChange={upd('role')} data-testid="register-role">
              <option value="SENDER">Sender (I want to send packages)</option>
              <option value="TRAVELER">Traveler (I'm traveling and can carry packages)</option>
            </select>
          </div>
          <div><label className="label">First name</label><input className="input" value={form.firstName} onChange={upd('firstName')} required data-testid="register-first" /></div>
          <div><label className="label">Last name</label><input className="input" value={form.lastName} onChange={upd('lastName')} required data-testid="register-last" /></div>
          <div className="col-span-2"><label className="label">Email</label><input className="input" type="email" value={form.email} onChange={upd('email')} required data-testid="register-email" /></div>
          <div className="col-span-2"><label className="label">Phone</label><input className="input" value={form.phone} onChange={upd('phone')} data-testid="register-phone" /></div>
          <div className="col-span-2"><label className="label">Password</label><input className="input" type="password" value={form.password} onChange={upd('password')} required minLength={6} data-testid="register-password" /></div>
          <button className="btn btn-brand col-span-2 mt-2" disabled={loading} data-testid="register-submit">
            {loading ? 'Creating...' : 'Create account'}
          </button>
        </form>
        <p className="text-sm text-ink-500 mt-5">
          Already have an account? <Link to="/login" className="text-brand-700 font-semibold">Login</Link>
        </p>
      </div>
    </div>
  );
}
