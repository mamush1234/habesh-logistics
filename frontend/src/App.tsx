import { Routes, Route, Navigate } from 'react-router-dom';
import { Layout } from './components/Layout';
import { ProtectedRoute } from './components/ProtectedRoute';
import { PublicLanding } from './pages/PublicLanding';
import { Login } from './pages/Login';
import { Register } from './pages/Register';
import { Track } from './pages/Track';
import { SendPackage } from './pages/SendPackage';
import { SenderDashboard } from './pages/sender/Dashboard';
import { TravelerDashboard } from './pages/traveler/Dashboard';
import { AgentDashboard } from './pages/agent/Dashboard';
import { Account } from './pages/Account';
import { useAuth } from './auth/AuthContext';
import { PackageQueue } from './pages/PackageQueue';
import { Marketplace, MarketRequestForm } from './pages/Marketplace';
import { MarketplaceRequest } from './pages/MarketplaceRequest';
import { TravelerTripDetails } from './pages/TravelerTripDetails';
import { SendShipmentRequest } from './pages/SendShipmentRequest';
import { ShipmentPayment } from './pages/ShipmentPayment';
import { DeliveryReviewInbox, DeliveryReviewDetails } from './pages/DeliveryReviewInbox';
import { GuestRequest } from './pages/GuestRequest';
import { AgentPortal } from './agent/AgentPortal';
import { AdminPortal } from './admin/AdminPortal';

export default function App() {
  const { user } = useAuth();
  if (user?.mustChangePassword) return <Layout><Routes>
    <Route path="/account" element={<Account />} />
    <Route path="*" element={<Navigate to="/account" replace />} />
  </Routes></Layout>;
  return (
    <Routes>
      <Route path="/" element={<PublicLanding />} />
      <Route path="/agent/legacy" element={<Layout><ProtectedRoute roles={['AGENT', 'ADMIN']}><AgentDashboard /></ProtectedRoute></Layout>} />
      <Route path="/agent/*" element={<ProtectedRoute roles={['AGENT']}><AgentPortal /></ProtectedRoute>} />
      <Route path="/admin/*" element={<ProtectedRoute roles={['ADMIN']}><AdminPortal /></ProtectedRoute>} />
      <Route path="/*" element={<MainSite />} />
    </Routes>
  );
}

function MainSite() {
  const { user } = useAuth();
  return (
    <Layout>
      <Routes>
        <Route path="/browse-travelers" element={<Marketplace browseTravelers />} />
        <Route path="/browse-travelers/:id" element={<TravelerTripDetails />} />
        <Route path="/shipment-request/:tripId" element={<ProtectedRoute roles={['SENDER']}><SendShipmentRequest /></ProtectedRoute>} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/guest-request" element={<GuestRequest />} />
        <Route path="/track" element={<ProtectedRoute><Track /></ProtectedRoute>} />
        <Route path="/track/:trackingNumber" element={<ProtectedRoute><Track /></ProtectedRoute>} />
        <Route path="/send" element={<ProtectedRoute roles={['SENDER']}><MarketRequestForm /></ProtectedRoute>} />
        <Route path="/legacy-send" element={<ProtectedRoute roles={['SENDER', 'ADMIN']}><SendPackage /></ProtectedRoute>} />
        <Route path="/marketplace" element={<ProtectedRoute><Marketplace /></ProtectedRoute>} />
        <Route path="/marketplace/requests/:id" element={<ProtectedRoute><MarketplaceRequest /></ProtectedRoute>} />
        <Route path="/marketplace/requests/:id/payment" element={<ProtectedRoute roles={['SENDER']}><ShipmentPayment /></ProtectedRoute>} />
        <Route path="/dashboard" element={
          user?.role === 'ADMIN' ? <Navigate to="/admin" /> :
          user?.role === 'TRAVELER' ? <Navigate to="/marketplace" /> :
          user?.role === 'AGENT' ? <Navigate to="/agent" /> :
          <ProtectedRoute><Marketplace /></ProtectedRoute>
        } />
        <Route path="/my-shipments" element={<ProtectedRoute roles={['SENDER']}><Marketplace myPackagesOnly /></ProtectedRoute>} />
        <Route path="/my-reviews" element={<ProtectedRoute roles={['SENDER', 'TRAVELER', 'AGENT']}><DeliveryReviewInbox /></ProtectedRoute>} />
        <Route path="/my-reviews/:id" element={<ProtectedRoute roles={['SENDER', 'TRAVELER', 'AGENT', 'ADMIN']}><DeliveryReviewDetails /></ProtectedRoute>} />
        <Route path="/legacy-shipments" element={<ProtectedRoute><SenderDashboard /></ProtectedRoute>} />
        <Route path="/account" element={<ProtectedRoute><Account /></ProtectedRoute>} />
        <Route path="/traveler" element={<ProtectedRoute><Marketplace tripsOnly /></ProtectedRoute>} />
        <Route path="/legacy-trips" element={<ProtectedRoute roles={['TRAVELER','ADMIN']}><TravelerDashboard /></ProtectedRoute>} />
        <Route path="/package-queue" element={<Navigate to="/marketplace" replace />} />
        <Route path="/legacy-packages" element={<ProtectedRoute roles={['ADMIN']}><PackageQueue /></ProtectedRoute>} />
        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
    </Layout>
  );
}
