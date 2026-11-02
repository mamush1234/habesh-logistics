import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { clsx } from 'clsx';
import {
  BadgeDollarSign, BarChart3, BookOpenCheck, Briefcase, CalendarRange, ClipboardList, CreditCard, ExternalLink,
  Gavel, KeyRound, LayoutDashboard, Link2, LogOut, Luggage, Map, Menu, Package, Plane, Send, Settings, ShieldCheck,
  Star, UserRound, Users, Wrench, X,
} from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { PackageNotifications } from '../components/PackageNotifications';
import { Avatar } from '../agent/ui';
import { DateRangeProvider, useDateRange, type RangePreset } from './dateRange';

export const adminNav = [
  { to: '/admin', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/admin/users', label: 'Users', icon: Users },
  { to: '/admin/travelers', label: 'Travelers', icon: Plane },
  { to: '/admin/senders', label: 'Senders', icon: Send },
  { to: '/admin/agents', label: 'Agents', icon: Briefcase },
  { to: '/admin/trips', label: 'Trips', icon: Map, end: true },
  { to: '/admin/packages', label: 'Packages', icon: Package },
  { to: '/admin/requests', label: 'Shipment Requests', icon: ClipboardList },
  { to: '/admin/bookings', label: 'Bookings', icon: BookOpenCheck },
  { to: '/admin/payments', label: 'Payments', icon: CreditCard },
  { to: '/admin/commissions', label: 'Commissions', icon: BadgeDollarSign },
  { to: '/admin/tracking', label: 'Tracking', icon: ShieldCheck },
  { to: '/admin/reviews', label: 'Reviews', icon: Star },
  { to: '/admin/disputes', label: 'Disputes', icon: Gavel },
  { to: '/admin/reports', label: 'Reports', icon: BarChart3 },
  { to: '/admin/settings', label: 'Settings', icon: Settings },
];

const toolsNav = [
  { to: '/admin/operations', label: 'Operations (classic)', icon: Wrench },
  { to: '/admin/luggage', label: 'Luggage', icon: Luggage },
  { to: '/admin/guest-links', label: 'Guest links', icon: Link2 },
  { to: '/admin/access', label: 'Account access', icon: KeyRound },
];

const linkClass = ({ isActive }: { isActive: boolean }) => clsx(
  'flex items-center gap-3 rounded-xl px-3 py-2 text-sm font-medium outline-none',
  'focus-visible:ring-2 focus-visible:ring-violet-300',
  isActive ? 'bg-violet-600 text-white shadow-sm' : 'text-violet-100 hover:bg-white/10 hover:text-white',
);

function SidebarContent({ onNavigate }: { onNavigate?: () => void }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const name = user ? `${user.firstName} ${user.lastName}` : 'Administrator';
  return (
    <div className="flex flex-col h-full">
      <Link to="/admin" onClick={onNavigate} className="flex items-center gap-2 px-5 h-16 shrink-0 text-white">
        <span className="w-9 h-9 rounded-xl bg-violet-500/30 grid place-items-center" aria-hidden="true"><Plane className="w-5 h-5" /></span>
        <span className="leading-tight">
          <span className="block font-display font-bold">HabeshaList</span>
          <span className="block text-[11px] text-violet-200">Admin Console</span>
        </span>
      </Link>
      <nav className="flex-1 overflow-y-auto px-3 py-2" aria-label="Admin navigation">
        <ul className="space-y-0.5">
          {adminNav.map(({ to, label, icon: Icon, end }) => (
            <li key={to}>
              <NavLink to={to} end={end} onClick={onNavigate} className={linkClass}>
                <Icon className="w-[18px] h-[18px] shrink-0" aria-hidden="true" /> {label}
              </NavLink>
            </li>
          ))}
        </ul>
        <p className="px-3 pt-5 pb-1 text-[11px] font-semibold uppercase tracking-wider text-violet-300">Tools</p>
        <ul className="space-y-0.5">
          {toolsNav.map(({ to, label, icon: Icon }) => (
            <li key={to}>
              <NavLink to={to} onClick={onNavigate} className={linkClass}>
                <Icon className="w-[18px] h-[18px] shrink-0" aria-hidden="true" /> {label}
              </NavLink>
            </li>
          ))}
          <li>
            <Link to="/marketplace" onClick={onNavigate} className={linkClass({ isActive: false })}>
              <ExternalLink className="w-[18px] h-[18px] shrink-0" aria-hidden="true" /> Marketplace site
            </Link>
          </li>
        </ul>
      </nav>
      <div className="border-t border-white/10 p-4 flex items-center gap-3">
        <Avatar name={name} />
        <div className="min-w-0 flex-1">
          <div className="text-sm font-semibold text-white truncate">{name}</div>
          <div className="text-xs text-violet-200 truncate">{user?.email}</div>
        </div>
        <button type="button" aria-label="Log out" title="Log out"
          className="p-2 rounded-lg text-violet-100 hover:bg-white/10 focus-visible:ring-2 focus-visible:ring-violet-300 outline-none"
          onClick={() => { logout(); navigate('/'); }}>
          <LogOut className="w-4 h-4" aria-hidden="true" />
        </button>
      </div>
    </div>
  );
}

const presets: { value: Exclude<RangePreset, 'custom'>; label: string }[] = [
  { value: '7d', label: 'Last 7 days' },
  { value: '30d', label: 'Last 30 days' },
  { value: '90d', label: 'Last 90 days' },
  { value: 'ytd', label: 'Year to date' },
];

function DateRangeControl() {
  const { range, preset, setPreset, setCustom } = useDateRange();
  const id = useId();
  return (
    <div className="flex flex-wrap items-center gap-2" role="group" aria-label="Report date range">
      <CalendarRange className="w-4 h-4 text-violet-700 hidden sm:block" aria-hidden="true" />
      <label htmlFor={`${id}-preset`} className="sr-only">Date range</label>
      <select id={`${id}-preset`} className="input !w-auto !py-1.5 !text-sm" value={preset}
        onChange={e => e.target.value === 'custom'
          ? setCustom(range)
          : setPreset(e.target.value as Exclude<RangePreset, 'custom'>)}>
        {presets.map(p => <option key={p.value} value={p.value}>{p.label}</option>)}
        <option value="custom">Custom range</option>
      </select>
      {preset === 'custom' && <>
        <label htmlFor={`${id}-from`} className="sr-only">From</label>
        <input id={`${id}-from`} type="date" className="input !w-auto !py-1.5 !text-sm" value={range.from} max={range.to}
          onChange={e => e.target.value && setCustom({ ...range, from: e.target.value })} />
        <span className="text-ink-500 text-sm" aria-hidden="true">–</span>
        <label htmlFor={`${id}-to`} className="sr-only">To</label>
        <input id={`${id}-to`} type="date" className="input !w-auto !py-1.5 !text-sm" value={range.to} min={range.from}
          onChange={e => e.target.value && setCustom({ ...range, to: e.target.value })} />
      </>}
    </div>
  );
}

function UserBadge() {
  const { user } = useAuth();
  const name = user ? `${user.firstName} ${user.lastName}` : 'Administrator';
  return (
    <div className="flex items-center gap-2" data-testid="admin-user-badge">
      <Avatar name={name} size="sm" />
      <div className="hidden md:block leading-tight">
        <div className="text-sm font-semibold text-ink-900">{name}</div>
        <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-violet-700">
          <UserRound className="w-3 h-3" aria-hidden="true" /> Administrator
        </span>
      </div>
    </div>
  );
}

function AdminShell({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false);
  const { pathname } = useLocation();
  const closeRef = useRef<HTMLButtonElement>(null);

  useEffect(() => setOpen(false), [pathname]);
  useEffect(() => {
    if (!open) return;
    closeRef.current?.focus();
    const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape') setOpen(false); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open]);

  return (
    <div className="min-h-screen bg-[#f6f5fb] lg:pl-64">
      <aside className="hidden lg:block fixed inset-y-0 left-0 w-64 bg-[#2e1a5e]" data-testid="admin-sidebar">
        <SidebarContent />
      </aside>

      {open && (
        <div className="lg:hidden fixed inset-0 z-40" role="dialog" aria-modal="true" aria-label="Admin menu" id="admin-drawer">
          <div className="absolute inset-0 bg-black/40" onClick={() => setOpen(false)} aria-hidden="true" />
          <div className="absolute inset-y-0 left-0 w-72 max-w-[85vw] bg-[#2e1a5e] shadow-xl">
            <button ref={closeRef} type="button" aria-label="Close admin menu"
              className="absolute top-3 right-3 p-2 rounded-lg text-white hover:bg-white/10 outline-none focus-visible:ring-2 focus-visible:ring-violet-300"
              onClick={() => setOpen(false)}>
              <X className="w-5 h-5" aria-hidden="true" />
            </button>
            <SidebarContent onNavigate={() => setOpen(false)} />
          </div>
        </div>
      )}

      <header className="sticky top-0 z-30 bg-white/95 backdrop-blur border-b border-ink-300/60">
        <div className="flex flex-wrap items-center justify-between gap-3 px-3 sm:px-6 lg:px-8 py-3">
          <div className="flex items-center gap-2">
            <button type="button" className="lg:hidden p-2 rounded-lg text-violet-900 hover:bg-violet-50 outline-none focus-visible:ring-2 focus-visible:ring-violet-400"
              aria-label="Open admin menu" aria-expanded={open} aria-controls="admin-drawer" onClick={() => setOpen(true)}
              data-testid="admin-menu-toggle">
              <Menu className="w-5 h-5" aria-hidden="true" />
            </button>
            <DateRangeControl />
          </div>
          <div className="flex items-center gap-2 sm:gap-3">
            <PackageNotifications />
            <UserBadge />
          </div>
        </div>
      </header>

      <main className="px-4 py-6 lg:px-8 max-w-7xl">{children}</main>
    </div>
  );
}

export function AdminLayout({ children }: { children: ReactNode }) {
  return <DateRangeProvider><AdminShell>{children}</AdminShell></DateRangeProvider>;
}
