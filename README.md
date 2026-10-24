
# Public home page and registration

The public home page uses a responsive green-and-gold design with a centered
hero, sender/traveler search tabs, upcoming trip cards, How It Works, safety
cards, and a traveler signup call to action. City searches ignore punctuation,
case, and repeated spaces. The sender's date filter limits trip previews by
departure, not guaranteed arrival; full matching checks arrival and other rules.
Signed-in travelers and agents see open sender requests instead of other carriers' trips,
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

Only `GET /api/public/info` and `GET /api/public/travel` are public GET APIs.
The latter returns up to six upcoming traveler marketplace trips with remaining capacity:
ID, route, departure date and available kilograms only. No names, notes, contact
details, operational manifests or packages are public. All package, tracking,
calendar, queue and admin data remain authenticated and role-restricted.

# Private guest sender requests

Admins open **Admin dashboard > Guest sender links** (`/admin/guest-links`),
enter a private reference label, and create a link to share with one sender.
The full link is displayed only at creation; copy it before leaving the page.
Unused links can be revoked. Links expire seven days after creation and accept
exactly one request. Guests do not register, receive a password, or gain account
access. This is separate from the removed account-invitation feature.

The guest form at `/guest-request#token=...` requires full name, email, phone,
pickup/destination cities, availability/deadline, item type/description, weight,
offered USD price, and a product photo. JPEG/PNG inputs are resized in the
browser using the existing photo helper, then validated and re-encoded on the
server (maximum 2 MB, 4096 pixels per side, 16 million pixels). This removes
embedded image metadata. Guests should not include contacts in public package
descriptions or photos. After submission, the page says:
**"Your request has been received. We will contact you once we find a traveler."**
Reloads and duplicate submissions return this receipt, not private contact data;
concurrent retries cannot create duplicate requests.

Guest requests enter the existing marketplace and notify active admins,
travelers, and agents. Travelers/agents can inspect package details and the
product photo, match their own trips, and submit offers as usual. Admins see
the guest's contact details immediately and accept/decline offers on the guest's
behalf. They can also simulate escrow funding and manage delivery status.
Regular account-owned requests still require the owning sender to select an
offer and fund demo escrow. Guest email, phone, and name stay admin-only,
including after matching and simulated funding. Guest coordination is handled
directly by the admin, not through account chat or guest account access.
**Contacting the sender is a manual admin action; no email or SMS is sent.**

API endpoints:
- Admin-only `POST`/`GET /api/admin/guest-request-links` create/list links.
- Admin-only `POST /api/admin/guest-request-links/{id}/revoke` revokes an unused link.
- Anonymous `POST /api/guest-requests/check` accepts `{token}` and returns only
  availability or a receipt.
- Anonymous `POST /api/guest-requests/submit` accepts `{token, name, email, phone,
  photoDataUrl, request}`; `request` uses the normal marketplace request input.
- Authenticated `GET /api/marketplace/requests/{id}/product-photo` requires access
  to that request. Large photo data is excluded from marketplace list DTOs.

Only a SHA-256 hash of the random 256-bit link token is persisted. The frontend
keeps the token in the URL fragment, not in the query string or server path.
Treat the full link as a secret: anyone possessing an unused link can submit
that sender's request. The guest API client is separate from account sessions.
Flyway migration **V14** adds the link table and nullable sender/guest fields,
preserving existing account requests and chat history.

Create links on your deployed HTTPS website before sharing externally.
`localhost`/`127.0.0.1` development links work only on the computer running the app.
Test-profile demo data is in memory, so restarting that demo invalidates its links.

## Converting a guest request into an operational shipment

On a guest request, admins can select **Convert to shipment** before pickup
(`REQUESTED`, `MATCHED`, or `PAID_ESCROW`). The admin supplies the missing
destination country, recipient name/phone/city/address, and delivery method.
Scheduled pickup requires an address and date. The new shipment reuses the
request's destination city, description, weight, and product photo. Documents
map to `DOCUMENT`, medicine to `MEDICINE`, and other item categories to `OTHER`.
Normal configured shipment fees apply, not the marketplace offered price;
operational payment starts `PENDING`. No guest user account is created.

Conversion generates a normal tracking number and tracking events, then opens
the standard operational path: payment, pickup/drop-off, receiving-agent and
traveler assignment, luggage consolidation, trip assignment, and delivery.
The **Manage shipment** link opens the admin shipment list with its row
highlighted; **View tracking** opens the normal authenticated tracking screen.
The admin shipment row links back to the source request and private contact details.
Only admins may retrieve guest sender contacts. Operational manifests label the
sender as **Guest sender**, rather than exposing guest identity/contact fields.

The conversion transaction closes the source marketplace request as `CANCELLED`
with a conversion timeline entry, declines pending offers, releases any reserved
marketplace kilograms, and refunds any held **demo** escrow. A previously
selected agent/traveler is carried over as the shipment's assigned agent/traveler;
marketplace trips are not operational trips, so luggage and operational trip
assignment remain separate admin actions. The source request displays
**CONVERTED TO SHIPMENT** to admins and retains the shipment/tracking reference.
The request remains an audit record and cannot receive new offers.

`POST /api/admin/guest-requests/{id}/shipment` accepts `{recipient,
destinationCountry, pickupMethod, pickupAddress?, pickupRequestedDate?,
pickupTimeWindow?}`. It is admin-only and supports guest requests only.
Request locking and a unique source-request foreign key prevent duplicate
shipments, including concurrent retries. Failed conversions leave the original
request, escrow, capacity, and offers unchanged. Repeating a successful conversion
returns the existing shipment. Closed, disputed, or already-moving requests cannot
be newly converted. Converted shipments must be cancelled rather than deleted,
preserving the source link and tracking history.

Flyway **V15** adds the unique shipment-to-request relationship and permits a
shipment with a guest source instead of a registered sender; existing shipments
and their normal access/processing rules are preserved.

# Delivery marketplace (demo escrow)

Local Docker development keeps demo user accounts but does not seed operational
sample data. `DEMO_DATA_ENABLED=true` enables the demo accounts;
`DEMO_OPERATIONAL_DATA_ENABLED=false` (default) prevents sample shipments,
recipients, trips, luggage, commissions and marketplace trips from being added.
Existing accounts, passwords and profiles are not reset. These flags do not
delete existing records. Automated tests explicitly enable operational fixtures.

With `DEMO_OPERATIONAL_DATA_ENABLED=true` and
`DEMO_MARKETPLACE_TRIPS_ENABLED=true` in dev/test, startup seeds nine mock marketplace trips
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
proof uploads. Travelers and agents post their own trips from **My trips**: origin,
destination, departure, optional arrival, available weight, allowed and blocked
item categories. Only explicitly allowed categories can match; rules cannot
overlap. Senders use **Send Package** to post pickup/destination cities, an
available-from date, deadline, item category/description, weight and offered USD
price. **My packages** and the sender dashboard show only requests created by the
signed-in sender, with status/search filters and links to their offers and delivery
progress. Traveler trips and marketplace browsing sections are not shown on this
page; they remain available separately under **Marketplace**. Sender marketplace
browsing shows traveler/agent trips, not sender requests. Travelers and agents browse sender
requests and see only their own posted trips, never another traveler's trip
profile in the authenticated trip list. This restriction is enforced server-side,
including trip-match ownership checks. Public landing previews remain anonymous
route/date/capacity summaries without traveler identities. Admins retain full
visibility. Travelers and agents submit offers using only their own compatible
delivery trips, and only the owning sender selects an offer. Agent accounts are
still administrator-created; enabling offers does not enable public agent signup.
Before acceptance, trip listings, suggestions, and pending offers use anonymous
traveler/agent labels instead of real names. Reputation, role, route, capacity,
and pricing remain visible. Participants can see their own identity and admins
retain full visibility. Acceptance reveals the sender's name to the selected
carrier and the selected carrier's name to the sender on that package only;
unselected offer authors stay anonymous. Email/phone fields remain escrow-gated.

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

Travelers and agents send a priced offer or **Accept asking price** (which sends an offer,
not an automatic match). The sender accepts one; all competing pending offers
are declined. Offers do not reserve capacity. Acceptance locks the request and
trip, rechecks eligibility, and reserves kilograms atomically to prevent double
selection and overbooking. There is no automatic reservation expiry after matching.
Travelers and agents may withdraw pending offers; senders may decline them.

**Escrow is a labeled simulation only. No payment provider is configured, and no
real funds are collected, held, released or refunded.** The owning sender funds
demo escrow for the accepted offer's exact price. It does not charge the old 2%
service fee. Funding unlocks account contact details for the selected participants.
Before that, travelers/agents see route, dates, items and offers they authored, not
sender account contact fields or competing offers. Do not put contacts into public
descriptions or offer messages. Accepted-package chat is an explicit exception:
participants can communicate immediately after acceptance, before escrow funding.

## Package chat

Each marketplace package gets a persistent text conversation once the sender accepts
an offer. Only its owning sender and selected traveler/agent can read and send
messages. Other senders, unselected offer authors, unrelated travelers/agents, and
anonymous visitors have no access, enforced by both chat endpoints. Administrators
can review the full conversation from the package detail page but cannot post.
Chat remains available during disputes; after completion or cancellation its history
is read-only. Requests that never accepted an offer have no chat.

Open **My packages** (sender) or **Marketplace** (traveler/agent/admin), choose the
package, and use **Package chat**. Agents can post routes through **My trips** or
the agent dashboard's **My delivery trips** link. Chat messages identify their author
and role, preserve line breaks, and are rendered as plain text (no HTML or attachments).
Messages must contain 1-2000 characters; whitespace-only messages are rejected.
Failed sends keep the draft and display an error. History polls every 5 seconds
and pages in batches of 50 using **Load earlier messages**. New messages generate
a persistent in-app notification for the other participant without including the
message body. Package locks serialize sends and status changes to keep ordering
stable and prevent writes after closure. No external chat service is used.

Chat content is visible immediately to the other participant and administrators.
Account contact fields still require escrow funding, but chat can reveal any details
participants choose to write before funding.

The enforced marketplace state machine is:

`REQUESTED -> MATCHED -> PAID_ESCROW -> PICKED_UP -> IN_TRANSIT -> DELIVERED -> COMPLETED`

Travelers/agents update pickup/transit/delivery; the sender confirms receipt to complete
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
- `GET /api/marketplace/requests/{id}/matches` (sender/admin, or eligible traveler/agent's own trips)
- `GET /api/marketplace/trips/{id}/matches` (owning traveler/agent/admin)
- `GET /api/marketplace/requests/{id}/messages` (latest 50, optional positive `before` sequence cursor; matched participants/admin)
- `POST /api/marketplace/requests/{id}/messages` (`body`; matched participants only, before closure)
- `POST /api/marketplace/requests/{id}/offers`
- `POST /api/marketplace/requests/{id}/offers/{offerId}/{accept|decline|withdraw}`
- `POST /api/marketplace/requests/{id}/demo-escrow`
- `PUT /api/marketplace/requests/{id}/status` (`status`, optional/required `reason`)
- `POST /api/marketplace/requests/{id}/rating` (`rating`: 1-5)
- `PUT /api/marketplace/travelers/{id}/verification` (admin; `level`)
- `GET /api/package-queue/notifications`, `PUT /api/package-queue/notifications/{id}/read`

V12 adds marketplace tables without rewriting legacy shipments, assignments,
payments or migration history. V13 adds persistent per-package chat without changing
existing delivery records. The old queue scheduler and mutation endpoints
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
