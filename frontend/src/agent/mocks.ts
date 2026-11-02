import type { AgentApiShape } from './api';
import type {
  AgentAuthorization, AgentCommission, AgentCustomer, AgentPackage, AgentTrip, CommissionPage, Page,
} from './types';

// In-memory fixtures used only when USE_AGENT_MOCKS is enabled.
const day = 86_400_000;
const iso = (offsetDays = 0) => new Date(Date.now() + offsetDays * day).toISOString();
const date = (offsetDays = 0) => iso(offsetDays).slice(0, 10);
const wait = <T,>(value: T) => new Promise<T>(resolve => setTimeout(() => resolve(structuredClone(value)), 250));
const id = () => crypto.randomUUID();

function auth(customerId: string, customerName: string): AgentAuthorization {
  return { id: id(), customerId, customerName, status: 'ACTIVE', method: 'IN_PERSON', consentNote: 'Signed consent form',
    grantedAt: iso(-3), expiresAt: iso(87), revokedAt: null };
}
function customer(name: string, type: AgentCustomer['type'], shipments: number, trips: number, days: number): AgentCustomer {
  const cid = id();
  return { id: cid, name, email: `${name.split(' ')[0].toLowerCase()}@example.com`, phone: '+1 (571) 555-1234', type,
    active: true, mustChangePassword: false, shipmentCount: shipments, tripCount: trips,
    authorization: auth(cid, name), createdAt: iso(-days) };
}

const customers: AgentCustomer[] = [
  customer('Mimi Abebe', 'SENDER', 5, 0, 1), customer('Daniel Tesfaye', 'TRAVELER', 0, 3, 2),
  customer('Sofi Mekonnen', 'SENDER', 2, 0, 4), customer('Tigist Alemu', 'TRAVELER', 0, 4, 6),
  customer('Yonas Tadesse', 'SENDER', 1, 0, 8), customer('Selam Tekle', 'TRAVELER', 0, 2, 9),
];
const authorizations: AgentAuthorization[] = customers.map(c => c.authorization!);
const packages: AgentPackage[] = [];
const trips: AgentTrip[] = [];
const commissions: AgentCommission[] = [15, 12, 18, 20, 10, 25, 22].map((amount, i) => ({
  id: id(), requestId: id(), route: 'Washington, DC → Addis Ababa', customerName: customers[i % customers.length].name,
  rate: 0.1, baseAmount: amount * 10, amount, status: i === 6 ? 'CANCELLED' : i % 2 ? 'PAID' : 'PENDING',
  createdAt: iso(-i * 2), paidAt: i % 2 ? iso(-i) : null,
}));

function paginate<T>(items: T[], page = 0, size = 10): Page<T> {
  return { items: items.slice(page * size, page * size + size), page, size, totalItems: items.length,
    totalPages: Math.ceil(items.length / size) };
}
const sum = (list: AgentCommission[], status: AgentCommission['status']) =>
  list.filter(c => c.status === status).reduce((total, c) => total + c.amount, 0);

export const agentMocks: AgentApiShape = {
  dashboard: () => wait({
    agentName: 'Selam T.', email: 'agent@example.com', referralCode: 'SELAM2026', commissionRate: 0.1,
    activeCustomers: customers.length, activeShipments: 8, pendingRequests: 3,
    totalCommissions: commissions.filter(c => c.status !== 'CANCELLED').reduce((t, c) => t + c.amount, 0),
    recentActivity: [
      { id: id(), action: 'PACKAGE_CREATED', customerName: 'Mimi Abebe', targetType: 'DELIVERY_REQUEST', targetId: null, details: 'Washington, DC to Addis Ababa', createdAt: iso(0) },
      { id: id(), action: 'TRIP_CREATED', customerName: 'Daniel Tesfaye', targetType: 'MARKET_TRIP', targetId: null, details: 'IAD to ADD', createdAt: iso(-0.2) },
      { id: id(), action: 'AUTHORIZATION_GRANTED', customerName: 'Sofi Mekonnen', targetType: 'AUTHORIZATION', targetId: null, details: 'IN_PERSON', createdAt: iso(-1) },
    ],
    recentCustomers: customers.slice(0, 5),
  }),
  customers: ({ type, q, page }) => {
    const query = (q ?? '').toLowerCase();
    return wait(paginate(customers.filter(c => (!type || c.type === type)
      && (!query || c.name.toLowerCase().includes(query) || c.email.includes(query) || (c.phone ?? '').includes(query))), page));
  },
  customer: async customerId => {
    const found = customers.find(c => c.id === customerId);
    if (!found) throw Object.assign(new Error('Forbidden'), { response: { status: 403, data: { message: 'Forbidden' } } });
    return wait(found);
  },
  createCustomer: input => {
    const created = customer(`${input.firstName} ${input.lastName}`, input.customerType, 0, 0, 0);
    created.email = input.email;
    created.phone = input.phone;
    created.mustChangePassword = true;
    customers.unshift(created);
    authorizations.unshift(created.authorization!);
    return wait({ customer: created, credentials: { email: input.email, temporaryPassword: 'mock-temporary-password' },
      authorization: created.authorization! });
  },
  createPackage: (customerId, input) => {
    const c = customers.find(x => x.id === customerId)!;
    const pkg: AgentPackage = { id: id(), customerId, customerName: c.name, ...input.details,
      status: input.draft ? 'DRAFT' : 'REQUESTED', adminApproved: false, hasPhoto: !!input.photoDataUrl,
      createdAt: iso(), updatedAt: iso() };
    packages.unshift(pkg);
    if (!input.draft) c.shipmentCount += 1;
    return wait(pkg);
  },
  submitDraft: (_customerId, packageId) => {
    const pkg = packages.find(p => p.id === packageId)!;
    pkg.status = 'REQUESTED';
    return wait(pkg);
  },
  createTrip: (customerId, input) => {
    const c = customers.find(x => x.id === customerId)!;
    const trip: AgentTrip = { id: id(), customerId, customerName: c.name, ...input, availableKg: input.capacityKg, createdAt: iso() };
    trips.unshift(trip);
    c.tripCount += 1;
    return wait(trip);
  },
  packages: (status, page) => wait(paginate(packages.filter(p => !status || p.status === status), page)),
  trips: page => wait(paginate(trips, page)),
  commissions: ({ from, to, status, page }) => {
    const inRange = commissions.filter(c => (!from || c.createdAt.slice(0, 10) >= from) && (!to || c.createdAt.slice(0, 10) <= to));
    const result: CommissionPage = { ...paginate(inRange.filter(c => !status || c.status === status), page),
      pending: sum(inRange, 'PENDING'), paid: sum(inRange, 'PAID'), total: sum(inRange, 'PENDING') + sum(inRange, 'PAID') };
    return wait(result);
  },
  authorizations: () => wait(authorizations),
  grantAuthorization: input => {
    const c = customers.find(x => x.id === input.customerId)!;
    authorizations.filter(a => a.customerId === c.id && a.status === 'ACTIVE').forEach(a => { a.status = 'REVOKED'; a.revokedAt = iso(); });
    const a = { ...auth(c.id, c.name), method: input.method, consentNote: input.consentNote ?? null, grantedAt: iso(),
      expiresAt: iso(input.validDays ?? 90) };
    authorizations.unshift(a);
    c.authorization = a;
    return wait(a);
  },
  revokeAuthorization: authorizationId => {
    const a = authorizations.find(x => x.id === authorizationId)!;
    a.status = 'REVOKED';
    a.revokedAt = iso();
    const c = customers.find(x => x.id === a.customerId);
    if (c?.authorization?.id === a.id) c.authorization = null;
    return wait(a);
  },
};

export const mockDates = { today: date(), inDays: date };
