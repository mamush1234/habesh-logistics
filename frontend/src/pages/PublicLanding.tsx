import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowRight, CreditCard, MapPin, Menu, Package, Plane, ShieldCheck, Users, X } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import './PublicLanding.css';

const actions = [
  { title: 'Send a Package', description: 'Find a traveler to send your items', icon: Package, role: 'SENDER' },
  { title: 'Post a Trip', description: 'List your travel and earn money', icon: Plane, role: 'TRAVELER' },
  { title: 'Become an Agent', description: 'Manage shipments for your customers', icon: Users, role: 'AGENT' },
] as const;

const trustItems = [
  { title: 'Trusted Travelers', icon: ShieldCheck },
  { title: 'Secure Payments', icon: CreditCard },
  { title: 'Track Your Shipment', icon: MapPin },
  { title: 'Ethiopian Community', icon: Users },
];

function actionHref(role: typeof actions[number]['role'], currentRole?: string) {
  if (!currentRole) return `/register?role=${role}&next=${encodeURIComponent(role === 'SENDER' ? '/send' : role === 'TRAVELER' ? '/traveler' : '/agent')}`;
  if (role === 'SENDER') return currentRole === 'SENDER' ? '/send' : '/browse-travelers';
  if (role === 'TRAVELER') return currentRole === 'SENDER' ? '/my-shipments' : '/traveler';
  return currentRole === 'AGENT' ? '/agent' : '/register?role=AGENT';
}

function cityName(value: string) {
  return value.replace(/\s*\([^)]*\)\s*$/, '').trim();
}

export function PublicLanding() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const [from, setFrom] = useState('Washington (IAD)');
  const [to, setTo] = useState('Addis Ababa (ADD)');
  const [date, setDate] = useState('');
  const [heroLoaded, setHeroLoaded] = useState(true);

  function searchTrips(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const params = new URLSearchParams();
    if (from.trim()) params.set('origin', cityName(from));
    if (to.trim()) params.set('destination', cityName(to));
    if (date) {
      params.set('from', date);
      params.set('to', date);
      params.set('date', date);
    }
    params.set('sort', 'departure');
    navigate(`/browse-travelers?${params.toString()}`);
  }

  const closeMenu = () => setMenuOpen(false);

  return <div className="public-landing">
    <header className="public-landing-header">
      <div className="public-landing-header-inner">
        <Link to="/" className="public-landing-brand" aria-label="HabeshaList Travel home" onClick={closeMenu}>
          <Plane aria-hidden="true" />
          <span>HabeshaList <strong>Travel</strong></span>
        </Link>
        <nav id="public-landing-nav" className={`public-landing-nav${menuOpen ? ' is-open' : ''}`} aria-label="Main navigation">
          <a href="#home" onClick={closeMenu}>Home</a>
          <a href="#how-it-works" onClick={closeMenu}>How it Works</a>
          <a href="#safety" onClick={closeMenu}>Safety</a>
          <a href="#faqs" onClick={closeMenu}>FAQs</a>
          <a href="#contact" onClick={closeMenu}>Contact</a>
        </nav>
        <div className="public-landing-account">
          {user ? <>
            <Link className="public-landing-login" to={user.role === 'ADMIN' ? '/admin' : user.role === 'AGENT' ? '/agent' : '/dashboard'}>My account</Link>
            <button className="public-landing-signup" onClick={() => { logout(); navigate('/'); }}>Log out</button>
          </> : <>
            <Link className="public-landing-login" to="/login">Login</Link>
            <Link className="public-landing-signup" to="/register">Sign Up</Link>
          </>}
        </div>
        <button className="public-landing-menu" type="button" aria-label={menuOpen ? 'Close menu' : 'Open menu'}
          aria-expanded={menuOpen} aria-controls="public-landing-nav" onClick={() => setMenuOpen(open => !open)}>
          {menuOpen ? <X aria-hidden="true" /> : <Menu aria-hidden="true" />}
        </button>
      </div>
    </header>

    <main>
      <section id="home" className={`public-landing-hero${heroLoaded ? '' : ' hero-image-missing'}`} aria-labelledby="public-landing-title">
        {heroLoaded && <img className="public-landing-hero-image" src="/images/hero.jpg"
          alt="Ethiopian Airlines Boeing 787-9 flying against a clear blue sky"
          fetchPriority="high" onError={() => setHeroLoaded(false)} />}
        <div className="public-landing-hero-inner">
          <div className="public-landing-copy">
            <h1 id="public-landing-title">Send Packages<br />with Trusted Travelers</h1>
            <p>Connect with trusted travelers to send your packages from the USA to Ethiopia and beyond.</p>
          </div>
          <form className="public-landing-search" aria-label="Search traveler trips" onSubmit={searchTrips}>
            <label>
              <span>From</span>
              <input aria-label="From" name="from" type="text" value={from} maxLength={100}
                onChange={event => setFrom(event.target.value)} required />
            </label>
            <label>
              <span>To</span>
              <input aria-label="To" name="to" type="text" value={to} maxLength={100}
                onChange={event => setTo(event.target.value)} required />
            </label>
            <label className="public-landing-date">
              <span>Date</span>
              <span className={`public-landing-date-control${date ? ' has-value' : ' is-empty'}`}>
                {!date && <span className="public-landing-date-placeholder" aria-hidden="true">Any date</span>}
                <input aria-label="Date (optional; leave blank for any date)" name="date" type="date" value={date}
                  onChange={event => setDate(event.target.value)} />
              </span>
            </label>
            <button className="public-landing-search-button" type="submit">Search <ArrowRight aria-hidden="true" /></button>
          </form>
        </div>
      </section>

      <section className="public-landing-actions" aria-label="Get started">
        {actions.map(action => <article className="public-landing-action-card" key={action.title}>
          <action.icon className={`public-landing-action-icon icon-${action.role.toLowerCase()}`} aria-hidden="true" />
          <h2>{action.title}</h2>
          <p>{action.description}</p>
          <Link className="public-landing-action-button" to={actionHref(action.role, user?.role)}>
            Get Started <ArrowRight aria-hidden="true" />
          </Link>
        </article>)}
      </section>

      <section className="public-landing-trust" id="safety" aria-label="Community and service features">
        {trustItems.map(item => <div className="public-landing-trust-item" key={item.title}>
          <item.icon aria-hidden="true" /><span>{item.title}</span>
        </div>)}
      </section>

      <section className="public-landing-more" id="how-it-works" aria-label="How it works">
        <h2>How it works</h2>
        <p>Post a package, choose a trusted trip, then track delivery together.</p>
        <div id="faqs" className="public-landing-faq">
          <details><summary>How do I send a package?</summary><p>Create a sender account, add your package and receiver details, then browse trips and request a shipment.</p></details>
          <details><summary>Are payments real?</summary><p>Payments are demo or Stripe test mode only. No real money is collected or transferred.</p></details>
        </div>
      </section>
    </main>

    <footer className="public-landing-footer" id="contact">
      <p>Items must follow airline and customs rules. Agent access requires administrator approval.</p>
      <p className="public-landing-photo-credit">
        Photo: <a href="https://commons.wikimedia.org/wiki/File:Ethiopian_Boeing_787-9_ET-AUP_MD1.jpg" target="_blank" rel="noreferrer">Ethiopian Airlines Boeing 787-9 ET-AUP in flight</a>,
        {' '}Acroterion, Wikimedia Commons, <a href="https://creativecommons.org/licenses/by-sa/4.0/" target="_blank" rel="noreferrer">CC BY-SA 4.0</a>. Resized for display.
      </p>
    </footer>
  </div>;
}
