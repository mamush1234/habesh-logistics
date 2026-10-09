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
import { useAuth } from './auth/AuthContext';

export default function App() {
  const { user } = useAuth();
  return (
    <Layout>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/track" element={<ProtectedRoute><Track /></ProtectedRoute>} />
        <Route path="/track/:trackingNumber" element={<ProtectedRoute><Track /></ProtectedRoute>} />
        <Route path="/send" element={<SendPackage />} />
        <Route path="/dashboard" element={
          user?.role === 'ADMIN' ? <Navigate to="/admin" /> :
          user?.role === 'TRAVELER' ? <Navigate to="/traveler" /> :
          user?.role === 'AGENT' ? <Navigate to="/agent" /> :
          <ProtectedRoute><SenderDashboard /></ProtectedRoute>
        } />
        <Route path="/my-shipments" element={<ProtectedRoute><SenderDashboard /></ProtectedRoute>} />
        <Route path="/traveler" element={<ProtectedRoute roles={['TRAVELER','ADMIN']}><TravelerDashboard /></ProtectedRoute>} />
        <Route path="/agent" element={<ProtectedRoute roles={['AGENT','ADMIN']}><AgentDashboard /></ProtectedRoute>} />
        <Route path="/admin" element={<ProtectedRoute roles={['ADMIN']}><AdminDashboard /></ProtectedRoute>} />
        <Route path="/admin/trips/:id" element={<ProtectedRoute roles={['ADMIN']}><AdminTrip /></ProtectedRoute>} />
        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
    </Layout>
  );
}
