import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { clsx } from 'clsx';
import {
  BadgeDollarSign, LayoutDashboard, LogOut, Map, Menu, MessageSquare, Package, PackagePlus, Plane, Settings,
  ShieldCheck, Star, Users, X,
} from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { PackageNotifications } from '../components/PackageNotifications';
import { Avatar } from './ui';
import { USE_AGENT_MOCKS } from './api';

export const agentNav = [
  { to: '/agent', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/agent/customers', label: 'My Customers', icon: Users },
  { to: '/agent/packages/new', label: 'Create Package', icon: PackagePlus },
  { to: '/agent/trips/new', label: 'Post Trip', icon: Plane },
  { to: '/agent/requests', label: 'Shipment Requests', icon: Package },
  { to: '/agent/tracking', label: 'Tracking', icon: Map },
  { to: '/agent/commissions', label: 'My Commissions', icon: BadgeDollarSign },
  { to: '/my-reviews', label: 'My Reviews', icon: Star },
  { to: '/agent/authorizations', label: 'Authorizations', icon: ShieldCheck },
  { to: '/agent/messages', label: 'Messages', icon: MessageSquare },
  { to: '/agent/settings', label: 'Settings', icon: Settings },
];

function SidebarContent({ onNavigate }: { onNavigate?: () => void }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const name = user ? `${user.firstName} ${user.lastName}` : 'Agent';
  return (
    <div className="flex flex-col h-full">
      <Link to="/agent" onClick={onNavigate} className="flex items-center gap-2 px-5 h-16 shrink-0 text-white">
        <span className="w-9 h-9 rounded-xl bg-emerald-500/20 grid place-items-center" aria-hidden="true"><Plane className="w-5 h-5" /></span>
        <span className="leading-tight">
          <span className="block font-display font-bold">HabeshaList</span>
          <span className="block text-[11px] text-emerald-200">Agent Portal</span>
        </span>
      </Link>
      <nav className="flex-1 overflow-y-auto px-3 py-2" aria-label="Agent navigation">
        <ul className="space-y-1">
          {agentNav.map(({ to, label, icon: Icon, end }) => (
            <li key={to}>
              <NavLink to={to} end={end} onClick={onNavigate}
                className={({ isActive }) => clsx(
                  'flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium outline-none',
                  'focus-visible:ring-2 focus-visible:ring-emerald-300',
                  isActive ? 'bg-emerald-600 text-white' : 'text-emerald-100 hover:bg-white/10 hover:text-white',
                )}>
                <Icon className="w-5 h-5 shrink-0" aria-hidden="true" /> {label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
      <div className="border-t border-white/10 p-4 flex items-center gap-3">
        <Avatar name={name} />
        <div className="min-w-0 flex-1">
          <div className="text-sm font-semibold text-white truncate">{name}</div>
          <div className="text-xs text-emerald-200 truncate">{user?.email}</div>
        </div>
        <button type="button" aria-label="Log out" title="Log out"
          className="p-2 rounded-lg text-emerald-100 hover:bg-white/10 focus-visible:ring-2 focus-visible:ring-emerald-300 outline-none"
          onClick={() => { logout(); navigate('/'); }}>
          <LogOut className="w-4 h-4" aria-hidden="true" />
        </button>
      </div>
    </div>
  );
}

export function AgentLayout({ children }: { children: ReactNode }) {
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
    <div className="min-h-screen bg-[#f4f7f5] lg:pl-64">
      <aside className="hidden lg:block fixed inset-y-0 left-0 w-64 bg-[#0b3d2e]" data-testid="agent-sidebar">
        <SidebarContent />
      </aside>

      <header className="lg:hidden sticky top-0 z-30 h-14 bg-[#0b3d2e] text-white flex items-center justify-between px-3">
        <button type="button" className="p-2 rounded-lg hover:bg-white/10 outline-none focus-visible:ring-2 focus-visible:ring-emerald-300"
          aria-label="Open agent menu" aria-expanded={open} aria-controls="agent-drawer" onClick={() => setOpen(true)}
          data-testid="agent-menu-toggle">
          <Menu className="w-5 h-5" aria-hidden="true" />
        </button>
        <span className="font-display font-bold">Agent Portal</span>
        <PackageNotifications />
      </header>

      {open && (
        <div className="lg:hidden fixed inset-0 z-40" role="dialog" aria-modal="true" aria-label="Agent menu" id="agent-drawer">
          <div className="absolute inset-0 bg-black/40" onClick={() => setOpen(false)} aria-hidden="true" />
          <div className="absolute inset-y-0 left-0 w-72 max-w-[85vw] bg-[#0b3d2e] shadow-xl">
            <button ref={closeRef} type="button" aria-label="Close agent menu"
              className="absolute top-3 right-3 p-2 rounded-lg text-white hover:bg-white/10 outline-none focus-visible:ring-2 focus-visible:ring-emerald-300"
              onClick={() => setOpen(false)}>
              <X className="w-5 h-5" aria-hidden="true" />
            </button>
            <SidebarContent onNavigate={() => setOpen(false)} />
          </div>
        </div>
      )}

      <div className="hidden lg:flex justify-end items-center gap-2 px-8 pt-4">
        <PackageNotifications />
      </div>
      <main className="px-4 py-6 lg:px-8 lg:pt-2 max-w-7xl">
        {USE_AGENT_MOCKS && (
          <p role="note" className="mb-4 rounded-xl bg-amber-50 text-amber-800 ring-1 ring-amber-200 px-4 py-2 text-sm">
            Showing sample agent data (VITE_AGENT_MOCKS=true). Changes are not saved.
          </p>
        )}
        {children}
      </main>
    </div>
  );
}
