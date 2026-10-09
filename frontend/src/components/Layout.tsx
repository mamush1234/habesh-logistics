import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Package, LogOut, Menu, X } from 'lucide-react';
import { useState } from 'react';
import { clsx } from 'clsx';

const navItems = [
  { to: '/', label: 'Home', public: true },
  { to: '/send', label: 'Send Package', public: true },
  { to: '/track', label: 'Track', public: true },
  { to: '/traveler', label: 'Traveler', roles: ['TRAVELER', 'ADMIN'] },
  { to: '/agent', label: 'Agent', roles: ['AGENT', 'ADMIN'] },
  { to: '/admin', label: 'Admin', roles: ['ADMIN'] },
];

export function Layout({ children }: { children: React.ReactNode }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);

  const visible = navItems.filter(i => i.public || (user && i.roles?.includes(user.role)));

  return (
    <div className="min-h-screen flex flex-col">
      <header className="sticky top-0 z-20 bg-white/80 backdrop-blur-md border-b border-ink-300/60">
        <div className="max-w-7xl mx-auto px-4 h-16 flex items-center justify-between">
          <Link to="/" className="flex items-center gap-2" data-testid="brand-link">
            <div className="w-9 h-9 rounded-xl bg-ink-900 text-white grid place-items-center">
              <Package className="w-5 h-5" />
            </div>
            <div className="leading-tight">
              <div className="font-display font-bold tracking-tight">HABESH LIST</div>
              <div className="text-[11px] text-ink-500 -mt-0.5">Habesh Logistics</div>
            </div>
          </Link>

          <nav className="hidden md:flex items-center gap-1" data-testid="main-nav">
            {visible.map(item => (
              <NavLink key={item.to} to={item.to}
                className={({ isActive }) => clsx(
                  'px-3 py-2 rounded-full text-sm font-medium',
                  isActive ? 'bg-ink-900 text-white' : 'text-ink-800 hover:bg-ink-100'
                )}>
                {item.label}
              </NavLink>
            ))}
          </nav>

          <div className="flex items-center gap-2">
            {user ? (
              <>
                <div className="hidden sm:flex flex-col items-end leading-tight mr-1">
                  <span className="text-sm font-semibold">{user.firstName} {user.lastName}</span>
                  <span className="text-[11px] text-ink-500">{user.role}</span>
                </div>
                <button className="btn btn-ghost" onClick={() => { logout(); navigate('/'); }} data-testid="logout-btn">
                  <LogOut className="w-4 h-4" /> <span className="hidden sm:inline">Logout</span>
                </button>
              </>
            ) : (
              <>
                <Link to="/login" className="btn btn-ghost" data-testid="login-link">Login</Link>
                <Link to="/register" className="btn btn-brand hidden sm:inline-flex" data-testid="register-link">Sign up</Link>
              </>
            )}
            <button className="md:hidden btn btn-ghost !px-2" onClick={() => setOpen(!open)} data-testid="menu-toggle">
              {open ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
            </button>
          </div>
        </div>
        {open && (
          <div className="md:hidden border-t border-ink-300/60 bg-white">
            <div className="max-w-7xl mx-auto px-4 py-2 flex flex-col">
              {visible.map(i => (
                <NavLink key={i.to} to={i.to} onClick={() => setOpen(false)}
                  className={({ isActive }) => clsx(
                    'py-2 px-3 rounded-lg', isActive ? 'bg-ink-900 text-white' : 'text-ink-800'
                  )}>
                  {i.label}
                </NavLink>
              ))}
            </div>
          </div>
        )}
      </header>

      <main className="flex-1 max-w-7xl mx-auto w-full px-4 py-6 md:py-10">
        {children}
      </main>

      <footer className="border-t border-ink-300/60 bg-white">
        <div className="max-w-7xl mx-auto px-4 py-6 text-sm text-ink-500 flex flex-wrap gap-2 items-center justify-between">
          <span>© {new Date().getFullYear()} Habesh Logistics</span>
          <span>Sender → Shipment → Luggage → Trip → Traveler</span>
        </div>
      </footer>
    </div>
  );
}
