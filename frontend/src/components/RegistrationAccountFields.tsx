import type { RegisterRequest } from '../types';

export type RegistrationAccount = Omit<RegisterRequest, 'role'>;

export function RegistrationAccountFields({ account, onChange, prefix }: {
  account: RegistrationAccount; onChange: (account: RegistrationAccount) => void; prefix: string;
}) {
  return <>{(['firstName', 'lastName', 'email', 'phone', 'password'] as const).map(field => <label key={field}
    className={`label ${field === 'email' || field === 'password' ? 'sm:col-span-2' : ''}`} htmlFor={`${prefix}-${field}`}>
    {{ firstName: 'First name', lastName: 'Last name', email: 'Email', phone: 'Phone',
      password: 'Password (at least 12 characters)' }[field]}
    <input id={`${prefix}-${field}`} className="input" required
      type={field === 'password' ? 'password' : field === 'email' ? 'email' : field === 'phone' ? 'tel' : 'text'}
      autoComplete={{ firstName: 'given-name', lastName: 'family-name', email: 'email', phone: 'tel', password: 'new-password' }[field]}
      minLength={field === 'password' ? 12 : undefined}
      maxLength={field === 'password' ? 72 : field === 'phone' ? 40 : field === 'email' ? 254 : 255}
      pattern={field === 'phone' ? '(?=(?:\\D*\\d){7})\\+?[0-9 \\(\\)\\-]{7,40}' : field === 'firstName' || field === 'lastName' ? '.*\\S.*' : undefined}
      title={field === 'phone' ? 'Enter a phone number with country code, digits, spaces, parentheses or hyphens.' : undefined}
      value={account[field] ?? ''} onChange={event => onChange({ ...account, [field]: event.target.value })} />
  </label>)}</>;
}
