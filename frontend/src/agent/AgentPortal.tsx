import { Navigate, Route, Routes } from 'react-router-dom';
import { AgentLayout } from './AgentLayout';
import { AgentDashboardPage } from './pages/Dashboard';
import { CustomersPage } from './pages/Customers';
import { CustomerDetailPage } from './pages/CustomerDetail';
import { RegisterCustomerPage } from './pages/RegisterCustomer';
import { CreatePackagePage } from './pages/CreatePackage';
import { PostTripPage } from './pages/PostTrip';
import { ShipmentRequestsPage, TrackingPage } from './pages/Requests';
import { CommissionsPage } from './pages/Commissions';
import { AuthorizationsPage } from './pages/Authorizations';
import { MessagesPage, SettingsPage } from './pages/Other';

/** Mounted at /agent/* behind ProtectedRoute(['AGENT']). */
export function AgentPortal() {
  return (
    <AgentLayout>
      <Routes>
        <Route index element={<AgentDashboardPage />} />
        <Route path="customers" element={<CustomersPage />} />
        <Route path="customers/new" element={<RegisterCustomerPage />} />
        <Route path="customers/:id" element={<CustomerDetailPage />} />
        <Route path="packages/new" element={<CreatePackagePage />} />
        <Route path="trips/new" element={<PostTripPage />} />
        <Route path="requests" element={<ShipmentRequestsPage />} />
        <Route path="tracking" element={<TrackingPage />} />
        <Route path="commissions" element={<CommissionsPage />} />
        <Route path="authorizations" element={<AuthorizationsPage />} />
        <Route path="messages" element={<MessagesPage />} />
        <Route path="settings" element={<SettingsPage />} />
        <Route path="*" element={<Navigate to="/agent" replace />} />
      </Routes>
    </AgentLayout>
  );
}
