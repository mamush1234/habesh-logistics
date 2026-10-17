import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Luggage, Plane, LogOut, Menu, X } from 'lucide-react';
import { useState } from 'react';
import { clsx } from 'clsx';
import { PackageNotifications } from './PackageNotifications';

const navItems = [
  { to: '/', label: 'Home', public: true },
  { to: '/send', label: 'Send Package', public: true },
  { to: '/my-shipments', label: 'My packages', roles: ['SENDER'] },
  { to: '/track', label: 'Track', public: true },
  { to: '/account', label: 'Account', public: true },
  { to: '/traveler', label: 'My trips', roles: ['TRAVELER', 'ADMIN'] },
  { to: '/agent', label: 'Agent', roles: ['AGENT', 'ADMIN'] },
  { to: '/marketplace', label: 'Marketplace', roles: ['SENDER', 'AGENT', 'TRAVELER', 'ADMIN'] },
  { to: '/admin', label: 'Admin', roles: ['ADMIN'] },
];

export function Layout({ children }: { children: React.ReactNode }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const landing = pathname === '/';
  const [open, setOpen] = useState(false);

  const visible = navItems.filter(i => user && (i.public || i.roles?.includes(user.role)))
    .filter(i => i.to !== '/send' || user?.role === 'SENDER');

  return (
    <div className={`min-h-screen flex flex-col ${landing ? 'landing-shell' : ''}`}>
      <header className={`sticky top-0 z-20 backdrop-blur-md ${landing ? 'landing-header' : 'bg-white/90 border-b border-ink-300/60'}`}>
        <div className="max-w-7xl mx-auto px-3 sm:px-4 h-16 flex items-center justify-between">
          <Link to="/" className="flex items-center gap-2" data-testid="brand-link">
            <div className="brand-mark">
              <Luggage className="w-9 h-9" />
              <Plane className="brand-plane" />
            </div>
            <div className="leading-tight">
              <div className="font-display font-bold tracking-tight text-base sm:text-xl text-blue-800 whitespace-nowrap">HabeshaList</div>
              <div className="hidden sm:block text-[10px] text-blue-950 tracking-wide whitespace-nowrap">Connect · Ship · Bring Closer</div>
            </div>
          </Link>

          <nav className="hidden xl:flex items-center gap-1" data-testid="main-nav">
            {!user && <>
              <a href="/#home" className="landing-nav-link">Home</a>
              <a href="/#how-it-works" className="landing-nav-link">How It Works</a>
              <a href="/#about" className="landing-nav-link">About</a>
              <a href="/#help" className="landing-nav-link">Help</a>
            </>}
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

          <div className="flex items-center gap-1 sm:gap-2">
            <PackageNotifications />
            {user ? (
              <>
                <div className="hidden lg:flex flex-col items-end leading-tight mr-1">
                  <span className="text-sm font-semibold">{user.firstName} {user.lastName}</span>
                  <span className="text-[11px] text-ink-500">{user.role}</span>
                </div>
                <button className="btn btn-ghost !px-2 sm:!px-4" aria-label="Logout" onClick={() => { logout(); navigate('/'); }} data-testid="logout-btn">
                  <LogOut className="w-4 h-4" /> <span className="hidden sm:inline">Logout</span>
                </button>
              </>
            ) : (
              <>
                <Link to="/login" className="btn btn-ghost !hidden sm:!inline-flex !text-blue-700 whitespace-nowrap" data-testid="login-link">Log in</Link>
                <Link to="/register" className="btn bg-blue-600 text-white hover:bg-blue-700 !px-3 sm:!px-4 whitespace-nowrap" data-testid="signup-link">Sign up</Link>
              </>
            )}
            <button className="xl:hidden btn btn-ghost !px-2" aria-label={open ? 'Close navigation' : 'Open navigation'}
              aria-expanded={open} onClick={() => setOpen(!open)} data-testid="menu-toggle">
              {open ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
            </button>
          </div>
        </div>
        {open && (
          <div className="xl:hidden border-t border-ink-300/60 bg-white">
            <div className="max-w-7xl mx-auto px-4 py-2 flex flex-col">
              {!user && ['Home', 'How It Works', 'About', 'Help'].map((label, index) => (
                <a key={label} href={`/#${['home', 'how-it-works', 'about', 'help'][index]}`}
                  className="py-2 px-3 rounded-lg text-blue-950" onClick={() => setOpen(false)}>{label}</a>
              ))}
              {!user && <Link to="/login" className="sm:hidden py-2 px-3 rounded-lg text-blue-950" onClick={() => setOpen(false)}>Log in</Link>}
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

      <main className={landing ? 'flex-1 w-full' : 'flex-1 max-w-7xl mx-auto w-full px-4 py-6 md:py-10'}>
        {children}
      </main>

      <footer className={`border-t border-ink-300/60 ${landing ? 'bg-blue-50' : 'bg-white'}`}>
        <div className="max-w-7xl mx-auto px-4 py-6 text-sm text-ink-500 flex flex-wrap gap-2 items-center justify-between">
          <span>© {new Date().getFullYear()} Habesh Logistics</span>
          {user && <span>Sender → Shipment → Luggage → Trip → Traveler</span>}
        </div>
      </footer>
    </div>
  );
}
