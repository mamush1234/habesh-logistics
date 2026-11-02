import { useState } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { Package, Plane, Users, ArrowLeft } from 'lucide-react';
import type { RegisterRequest } from '../types';
import { SenderRegistrationWizard } from './SenderRegistrationWizard';
import { TravelerRegistrationWizard } from './TravelerRegistrationWizard';

const accountTypes = [
  { role: 'SENDER', title: 'I am a Sender', icon: Package, description: 'Send packages to Ethiopia with someone already traveling.' },
  { role: 'TRAVELER', title: 'I am a Traveler', icon: Plane, description: 'Post your trip and share your available luggage space.' },
  { role: 'AGENT', title: 'I am an Agent', icon: Users, description: 'Coordinate packages and trips for your own customers. Administrator approval required.' },
] as const;

export function Register() {
  const [params] = useSearchParams();
  const requestedNext = params.get('next');
  const next = requestedNext?.startsWith('/') && !requestedNext.startsWith('//') ? requestedNext : '/dashboard';
  const requestedRole = params.get('role');
  const role = accountTypes.find(type => type.role === requestedRole)?.role;
  const roleLink = (selected?: typeof accountTypes[number]['role']) => {
    const query = new URLSearchParams(params);
    if (selected) query.set('role', selected);
    else query.delete('role');
    return `/register${query.size ? `?${query}` : ''}`;
  };
  const [form, setForm] = useState<Omit<RegisterRequest, 'role'>>({
    firstName: '', lastName: '', email: '', phone: '', password: '',
  });

  return <div className={`${role ? 'max-w-lg' : 'max-w-4xl'} mx-auto`}>
    {!role ? <>
      <div className="text-center">
        <h1 className="text-3xl font-bold">Join HabeshaList Travel</h1>
        <p className="mt-2 text-ink-500">Choose how you want to connect with the community.</p>
      </div>
      <div className="mt-6 grid md:grid-cols-3 gap-4" aria-label="Choose your account type">
        {accountTypes.map(type => <Link key={type.role} to={roleLink(type.role)}
          className="card flex flex-col gap-4 hover:ring-emerald-600 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-emerald-700">
          <type.icon className="h-10 w-10 text-emerald-700" aria-hidden="true" />
          <h2 className="text-xl font-bold">{type.title}</h2>
          <p className="text-sm text-ink-500 flex-1">{type.description}</p>
          <span className="text-sm font-semibold text-emerald-800">{type.role === 'AGENT' ? 'Learn about agent access' : 'Continue'} &rarr;</span>
        </Link>)}
      </div>
    </> : <div className="card">
      <Link className="inline-flex items-center gap-2 text-sm text-emerald-800 underline" to={roleLink()}>
        <ArrowLeft className="w-4 h-4" aria-hidden="true" /> Change account type
      </Link>
      <h1 className="text-3xl font-bold mt-4">{role === 'AGENT' ? 'Become an Agent' : `Create your ${role.toLowerCase()} account`}</h1>
      <p className="mt-2 text-ink-500">{role === 'TRAVELER' ? 'Join the community to post trips and share your luggage space.'
        : role === 'SENDER' ? 'Join the community to post packages and find a traveler.' : 'Help your customers coordinate packages and trips.'}</p>
      {role === 'AGENT' && <div role="note" className="mt-4 rounded-xl bg-emerald-50 p-4 text-sm text-emerald-900 space-y-3">
        <p>Agent accounts require administrator approval. Contact our team to request agent access.</p>
        <p>Already approved? Log in with the account provided by your administrator. Selecting this option does not create an account or submit an application.</p>
        <Link to={`/login?next=${encodeURIComponent('/agent')}`} className="btn bg-emerald-700 text-white hover:bg-emerald-800">Agent login</Link>
      </div>}
      {role === 'SENDER' && <SenderRegistrationWizard account={form} onChange={setForm} next={next} />}
      {role === 'TRAVELER' && <TravelerRegistrationWizard account={form} onChange={setForm} next={next} />}
    </div>}
    <p className="text-sm mt-5 text-center">Already a member? <Link to={`/login?next=${encodeURIComponent(next)}`} className="underline text-emerald-800">Log in</Link></p>
  </div>;
}
