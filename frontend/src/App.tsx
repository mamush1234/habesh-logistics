import { Routes, Route, Navigate } from 'react-router-dom';
import { Layout } from './components/Layout';
import { ProtectedRoute } from './components/ProtectedRoute';
import { Home } from './pages/Home';
import { Login } from './pages/Login';
import { Register } from './pages/Register';
import { Track } from './pages/Track';
import { SendPackage } from './pages/SendPackage';
import { SenderDashboard } from './pages/sender/Dashboard';
import { TravelerDashboard } from './pages/traveler/Dashboard';
import { AgentDashboard } from './pages/agent/Dashboard';
import { AdminDashboard } from './pages/admin/Dashboard';
import { AdminTrip } from './pages/admin/AdminTrip';
import { AdminLuggage } from './pages/admin/Luggage';
import { AdminAccess } from './pages/admin/Access';
import { Account } from './pages/Account';
import { useAuth } from './auth/AuthContext';
import { PackageQueue } from './pages/PackageQueue';
import { Marketplace, MarketRequestForm } from './pages/Marketplace';
import { MarketplaceRequest } from './pages/MarketplaceRequest';
import { GuestRequest } from './pages/GuestRequest';
import { AdminGuestLinks } from './pages/admin/GuestLinks';

export default function App() {
  const { user } = useAuth();
  if (user?.mustChangePassword) return <Layout><Routes>
    <Route path="/account" element={<Account />} />
    <Route path="*" element={<Navigate to="/account" replace />} />
  </Routes></Layout>;
  return (
    <Layout>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/guest-request" element={<GuestRequest />} />
        <Route path="/track" element={<ProtectedRoute><Track /></ProtectedRoute>} />
        <Route path="/track/:trackingNumber" element={<ProtectedRoute><Track /></ProtectedRoute>} />
        <Route path="/send" element={<ProtectedRoute roles={['SENDER']}><MarketRequestForm /></ProtectedRoute>} />
        <Route path="/legacy-send" element={<ProtectedRoute roles={['SENDER', 'ADMIN']}><SendPackage /></ProtectedRoute>} />
        <Route path="/marketplace" element={<ProtectedRoute><Marketplace /></ProtectedRoute>} />
        <Route path="/marketplace/requests/:id" element={<ProtectedRoute><MarketplaceRequest /></ProtectedRoute>} />
        <Route path="/dashboard" element={
          user?.role === 'ADMIN' ? <Navigate to="/admin" /> :
          user?.role === 'TRAVELER' ? <Navigate to="/marketplace" /> :
          user?.role === 'AGENT' ? <Navigate to="/agent" /> :
          <ProtectedRoute><Marketplace /></ProtectedRoute>
        } />
        <Route path="/my-shipments" element={<ProtectedRoute roles={['SENDER']}><Marketplace myPackagesOnly /></ProtectedRoute>} />
        <Route path="/legacy-shipments" element={<ProtectedRoute><SenderDashboard /></ProtectedRoute>} />
        <Route path="/account" element={<ProtectedRoute><Account /></ProtectedRoute>} />
        <Route path="/traveler" element={<ProtectedRoute><Marketplace tripsOnly /></ProtectedRoute>} />
        <Route path="/legacy-trips" element={<ProtectedRoute roles={['TRAVELER','ADMIN']}><TravelerDashboard /></ProtectedRoute>} />
        <Route path="/agent" element={<ProtectedRoute roles={['AGENT','ADMIN']}><AgentDashboard /></ProtectedRoute>} />
        <Route path="/package-queue" element={<Navigate to="/marketplace" replace />} />
        <Route path="/legacy-packages" element={<ProtectedRoute roles={['ADMIN']}><PackageQueue /></ProtectedRoute>} />
        <Route path="/admin" element={<ProtectedRoute roles={['ADMIN']}><AdminDashboard /></ProtectedRoute>} />
        <Route path="/admin/access" element={<ProtectedRoute roles={['ADMIN']}><AdminAccess /></ProtectedRoute>} />
        <Route path="/admin/guest-links" element={<ProtectedRoute roles={['ADMIN']}><AdminGuestLinks /></ProtectedRoute>} />
        <Route path="/admin/luggage" element={<ProtectedRoute roles={['ADMIN']}><AdminLuggage /></ProtectedRoute>} />
        <Route path="/admin/trips/:id" element={<ProtectedRoute roles={['ADMIN']}><AdminTrip /></ProtectedRoute>} />
        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
    </Layout>
  );
}
