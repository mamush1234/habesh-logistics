import { Navigate, Route, Routes } from 'react-router-dom';
import { AdminLayout } from './AdminLayout';
import { AdminDashboard } from '../pages/admin/Dashboard';
import { AdminAccess } from '../pages/admin/Access';
import { AdminGuestLinks } from '../pages/admin/GuestLinks';
import { AdminLuggage } from '../pages/admin/Luggage';
import { AdminTrip } from '../pages/admin/AdminTrip';
import { AdminOverview } from './pages/Overview';
import { AdminUsers } from './pages/Users';
import { AdminTrips } from './pages/Trips';
import { AdminShipmentRequests } from './pages/Requests';
import { AdminPayments } from './pages/Payments';
import { AdminTracking } from './pages/Tracking';
import { AdminDisputes } from './pages/Disputes';
import { AdminSettings } from './pages/Settings';
import { AdminBookings, AdminPackages, AdminReviews } from './pages/RequestViews';
import { AdminCommissions } from './pages/Commissions';
import { AdminReports } from './pages/Reports';

/** Mounted at /admin/* behind ProtectedRoute(['ADMIN']). */
export function AdminPortal() {
  return (
    <AdminLayout>
      <Routes>
        <Route index element={<AdminOverview />} />
        <Route path="operations" element={<AdminDashboard />} />
        <Route path="access" element={<AdminAccess />} />
        <Route path="guest-links" element={<AdminGuestLinks />} />
        <Route path="luggage" element={<AdminLuggage />} />
        <Route path="trips/:id" element={<AdminTrip />} />
        <Route path="users" element={<AdminUsers key="ALL" type="ALL" />} />
        <Route path="travelers" element={<AdminUsers key="TRAVELER" type="TRAVELER" />} />
        <Route path="senders" element={<AdminUsers key="SENDER" type="SENDER" />} />
        <Route path="agents" element={<AdminUsers key="AGENT" type="AGENT" />} />
        <Route path="trips" element={<AdminTrips />} />
        <Route path="requests" element={<AdminShipmentRequests />} />
        <Route path="payments" element={<AdminPayments />} />
        <Route path="tracking" element={<AdminTracking />} />
        <Route path="disputes" element={<AdminDisputes />} />
        <Route path="packages" element={<AdminPackages />} />
        <Route path="bookings" element={<AdminBookings />} />
        <Route path="commissions" element={<AdminCommissions />} />
        <Route path="reviews" element={<AdminReviews />} />
        <Route path="reports" element={<AdminReports />} />
        <Route path="settings" element={<AdminSettings />} />
        <Route path="*" element={<Navigate to="/admin" replace />} />
      </Routes>
    </AdminLayout>
  );
}
