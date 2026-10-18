
# Public home page and registration

The public home page uses a responsive green-and-gold design with a centered
hero, sender/traveler search tabs, upcoming trip cards, How It Works, safety
cards, and a traveler signup call to action. City searches ignore punctuation,
case, and repeated spaces. The sender's date filter limits trip previews by
departure, not guaranteed arrival; full matching checks arrival and other rules.
Signed-in travelers see open sender requests instead of other travelers' trips,
with a departure-date filter inside each request's drop-off/deadline window.
Request data is never fetched for anonymous visitors. Public cards show real
capacity and dates, not fabricated identities, ratings, or per-kilogram prices.
Escrow remains explicitly labeled as a simulation. Navigation leads to existing
signup and marketplace flows, preserving selected trips across login/registration.

Senders and travelers sign up directly at `/register`; invitations are no longer
required or supported. Registration accepts a `SENDER` or `TRAVELER` role and
creates the corresponding profile. Agent accounts are created by administrators
from the existing admin dashboard. Public signup cannot create agents or admins.
Passwords require at least 12 characters and at most 72 UTF-8 bytes.
Admins can still disable/re-enable accounts under **Account access**.

The invitation UI, API endpoints, token redemption, email delivery service, and
SMTP configuration have been removed. The historical V8 Flyway migration/table is
retained to preserve existing database migration history; old invitation links no
longer grant access. Existing accounts and passwords are unchanged.

Only `GET /api/public/info` and `GET /api/public/travel` are public read APIs.
The latter returns up to six upcoming traveler marketplace trips with remaining capacity:
ID, route, departure date and available kilograms only. No names, notes, contact
details, operational manifests or packages are public. All package, tracking,
calendar, queue and admin data remain authenticated and role-restricted.

# Delivery marketplace (demo escrow)

With demo data enabled in dev/test, startup seeds nine mock marketplace trips
for `traveler@example.com` to Addis Ababa from `Washington, DC`, `Alexandria, VA`,
`Arlington, VA`, `Baltimore, MD`, and `Silver Spring, MD`. Departures are 3-21 days
after startup, arrivals one day later, with 4-20 kg capacities and varied item
rules. Medicine is blocked on all sample trips; food is allowed only from Silver
Spring. DC, Maryland, and Northern Virginia share one pickup region. For example,
Washington, DC, clothing, 4 kg, available today, and a deadline 15 days away
matches six trips across the region.
Same-day restarts do not duplicate these trips. Existing demo accounts are also
backfilled, without changing their trips or reservations. Set
`app.demo-marketplace-trips-enabled=false` to disable this optional seed (disabled
in automated tests). Production never seeds demo trips.

The marketplace **replaces** the exclusive 10-minute queue and new service-fee
proof uploads. Travelers post their own trips from **My trips**: origin,
destination, departure, optional arrival, available weight, allowed and blocked
item categories. Only explicitly allowed categories can match; rules cannot
overlap. Senders use **Send Package** to post pickup/destination cities, an
available-from date, deadline, item category/description, weight and offered USD
price. **My packages** and the sender dashboard show only requests created by the
signed-in sender, with status/search filters and links to their offers and delivery
progress. Traveler trips and marketplace browsing sections are not shown on this
page; they remain available separately under **Marketplace**. Sender marketplace
browsing shows traveler trips, not sender requests. Travelers browse sender
requests and see only their own posted trips, never another traveler's trip
profile in the authenticated trip list. This restriction is enforced server-side,
including trip-match ownership checks. Public landing previews remain anonymous
route/date/capacity summaries without traveler identities. Admins retain full
visibility. Agents can browse;
only travelers submit offers, and only the owning sender selects an offer.

Suggestions accept pickup locations within the same DC/Maryland/Northern Virginia
region, or matching pickup city names elsewhere (case and repeated whitespace ignored).
The region recognizes DC/Washington, Maryland/MD and Maryland-qualified cities,
VA/Virginia as regional shorthand, and Northern Virginia localities such as
Alexandria, Arlington, Fairfax, Reston, Herndon, and Dulles. Explicit cities elsewhere
in Virginia, such as Richmond, are not included. Destination city names must still match.
Suggestions also require
departure on/after today and available-from, arrival by the deadline, enough
remaining kilograms, an active traveler, and allowed item rules. If arrival is
omitted, only the departure date is checked against the deadline; this is not an
arrival guarantee. Locations are not geocoded; the pickup region uses named aliases,
not distance calculations. Nearby airports are not otherwise inferred.
Date boundaries are inclusive: departure on the sender's drop-off (available-from)
date and arrival on the delivery deadline both qualify, including same-day trips.
The traveler offer form lists only their own compatible trips, including arrival
dates. If none qualify, it explains the requirements and links to posting a trip.
Offer submission and sender acceptance recheck these same rules and report the
specific mismatch (including remaining capacity), rather than a generic error.

Ranking is deterministic: price up to 50 points, completed-delivery ratings up
to 30, and verification up to 20. For senders, price points are
`50 * lowest candidate price / candidate price`; for travelers, they are
`50 * offered price / highest compatible offered price`. Prices for suggested
trips use the traveler's pending offer, or the sender's asking price if no offer
exists. Rating points are `30 * average rating / 5`; unrated travelers get zero,
not a fabricated score. Admin-managed verification levels are UNVERIFIED (0),
ID_VERIFIED (10), FULLY_VERIFIED (20). Verification is an administrator attestation,
not an automated identity check. Admin controls are on marketplace trip cards.

Travelers send a priced offer or **Accept asking price** (which sends an offer,
not an automatic match). The sender accepts one; all competing pending offers
are declined. Offers do not reserve capacity. Acceptance locks the request and
trip, rechecks eligibility, and reserves kilograms atomically to prevent double
selection and overbooking. There is no automatic reservation expiry after matching.
Travelers may withdraw pending offers; senders may decline them.

**Escrow is a labeled simulation only. No payment provider is configured, and no
real funds are collected, held, released or refunded.** The owning sender funds
demo escrow for the accepted offer's exact price. It does not charge the old 2%
service fee. Funding unlocks account contact details for the selected participants.
Before that, travelers see route, dates, items and offers they authored, not sender
contact details or competing offers. Do not put contacts into descriptions/messages.

The enforced marketplace state machine is:

`REQUESTED -> MATCHED -> PAID_ESCROW -> PICKED_UP -> IN_TRANSIT -> DELIVERED -> COMPLETED`

Travelers update pickup/transit/delivery; the sender confirms receipt to complete
and simulate escrow release. Admins can perform delivery steps but cannot choose
offers or fund escrow on a sender's behalf. A sender may submit one 1-5 rating
after completion. Sender/selected traveler/admin may cancel before pickup;
cancellation restores weight and simulates refund if funded. After funding and
before completion, participants can open a dispute with a reason. DISPUTED freezes
demo escrow; only an admin can resolve to COMPLETED (release) or CANCELLED (refund)
with a reason. Completed/cancelled requests are terminal. Delivery events persist
in sequence order, including funding, disputes and resolutions.

Admins see every request with status filtering and route/description search.
New requests notify active travelers, agents and admins; offers and delivery
updates notify the participants, and disputes notify admins. In-app notifications
are persistent and poll every 15 seconds; no email/push is implied.

Marketplace APIs (authenticated; ownership and role checks are server-side):

- `GET/POST /api/marketplace/trips`
- `GET/POST /api/marketplace/requests`
- `GET /api/marketplace/requests/{id}`
- `GET /api/marketplace/requests/{id}/matches` (sender/admin)
- `GET /api/marketplace/trips/{id}/matches` (owning traveler/admin)
- `POST /api/marketplace/requests/{id}/offers`
- `POST /api/marketplace/requests/{id}/offers/{offerId}/{accept|decline|withdraw}`
- `POST /api/marketplace/requests/{id}/demo-escrow`
- `PUT /api/marketplace/requests/{id}/status` (`status`, optional/required `reason`)
- `POST /api/marketplace/requests/{id}/rating` (`rating`: 1-5)
- `PUT /api/marketplace/travelers/{id}/verification` (admin; `level`)
- `GET /api/package-queue/notifications`, `PUT /api/package-queue/notifications/{id}/read`

V12 adds marketplace tables without rewriting legacy shipments, assignments,
payments or migration history. The old queue scheduler and mutation endpoints
are removed. `/package-queue` redirects to the marketplace. Admins retain a
read-only **Legacy package archive** (including old payment confirmations);
existing paid-claimant shipment privacy checks remain intact. Legacy operational
shipments, travel announcements, transport trips, luggage and tracking remain
separate under legacy/admin links; they are not escrow-funded marketplace deliveries.
New operational shipments created through legacy tooling do not enter the retired queue.

# Legacy travel announcements

Agents and administrators can use **Post travel** on their dashboard to publish a
route, departure date, optional expected arrival date, and announcement details.
No traveler assignment is required. These are planning announcements, not
operational trips or confirmed shipment bookings; existing trip, luggage, and
shipment workflows are unchanged.

Agents enter total capacity in kilograms and may enter a luggage count for
information (luggage pieces are not reserved). Each post shows total, approved,
and remaining kilograms. Posting agents and administrators can edit capacity,
but cannot reduce it below the already approved weight. Older posts require
capacity to be set with **Edit capacity** before accepting requests.

Senders select **Request space on this travel** in the calendar, or choose travel
in the shipment form. The form shows the shipment's kilograms and the projected
remaining space if approved. Selecting travel is optional for existing shipment
workflows. Requests may exceed current space, but cannot be approved until they
fit. Pending requests do not reserve any kilograms.

The posting agent reviews **Travel space requests** and approves or rejects each
request; administrators may also decide. Only approval deducts declared kilograms.
Approvals and capacity edits lock the travel record to prevent concurrent
overbooking. Repeated identical decisions do not deduct twice. Cancellation or
deletion of an approved shipment releases its space. Cancelled/rejected requests
are not automatically re-approved if shipment status changes.
Senders see their request status in **My shipments**, refreshed every 30 seconds.
Approval reserves space only; it does not assign operational luggage or a traveler.

After signing in, senders can see announcements on the home page and their
shipment dashboard. The monthly calendar marks departure dates; selecting a day
shows its announcements, author, and dates. Previous/Next switches months.
The calendar refreshes every minute and when the browser window regains focus.
Announcement details are visible to every signed-in user, so do not include
private shipment or passenger information.

`GET /api/travel-announcements` requires authentication.
`POST /api/travel-announcements` is restricted to agents and administrators;
the server records the signed-in author. Departure dates cannot be in the past
and expected arrival cannot precede departure. Flyway migration V9 creates the
persistent announcement table on backend startup.
Migration V10 adds travel capacity and shipment approval fields without removing
existing announcements or shipments.

`GET /api/travel-announcements/requests` is restricted to agents (their own travel
posts only) and administrators. `PUT /api/travel-announcements/{id}/capacity` and
`PUT /api/travel-announcements/{id}/requests/{shipmentId}` require the posting
agent or an administrator. The latter accepts `APPROVED` or `REJECTED`.
