
# Public home page and registration

The public home page uses a responsive green-and-gold design with a centered
hero, sender/traveler search tabs, Send a Package / Post a Trip / Become an Agent
action cards, upcoming trip cards, How It Works, a trust row, and a traveler signup
call to action. The Secure Payments trust card explicitly states that payments
are currently demo-only. City searches ignore punctuation,
case, and repeated spaces. Signed-in travelers/agents default to sender requests;
senders default to traveler/agent trips. Selecting **I am a traveler** or
**I am a sender** switches the results immediately, including for anonymous visitors.
After normal login, travelers land on the sender-request marketplace and senders
land on the traveler-trip marketplace. **My trips** and **My packages** remain
separate navigation destinations. Explicit login return links, admin/agent
operational dashboards and the required-password-change gate remain unchanged.
Sender requests have a left-side product photo (or an explicit no-photo placeholder)
and a route title such as **DC to Addis Ababa**. Photos that fail to load show a retry.
The traveler's departure-date filter matches each request's inclusive availability window.
Trip lists on the homepage and authenticated marketplace default to newest **posted**
first, not latest departure; customers can choose oldest posted or soonest departure
and inclusive departure-from/through filters. The homepage search also supports an
exact departure date. **Find travelers** now opens the public `/browse-travelers`
view of the existing marketplace with `origin`, `destination`, `from`, `to`, and
`sort` URL filters. A selected date becomes an inclusive one-day range; search
results default to soonest departure. Filters survive refresh and browser history.
**Find packages** keeps the existing inline sender-request search.
Full matching still checks arrival, capacity and item rules.
Public cards show real
capacity and dates, not fabricated identities, ratings, or per-kilogram prices.
Escrow remains explicitly labeled as a simulation. Navigation leads to existing
signup and marketplace flows, preserving selected trips across login/registration.

`/register` presents Sender, Traveler and Agent account-selection cards.
Sender and Traveler selections open their respective onboarding wizards.
Direct `?role=SENDER` / `?role=TRAVELER` links still work. Account selection
preserves the `next` return URL and supports browser back/forward navigation.
Sender signup now uses **Account → Package Details → Review**, with required name,
email, phone (at least seven digits), and password. Package entry includes route,
availability/deadline, category chips (Clothing, Shoes, Laptop, Electronics,
Cosmetics, Others), description and an offered USD price. Review does not
display the password. `POST /api/auth/register/sender` creates the sender profile,
initial delivery request, timeline and notifications in one transaction; a failed
package or duplicate email does not leave a partial signup. The response contains
the existing auth response and new request ID. After signup, default and `/send`
return links open the posted request instead of prompting for another package;
other explicit return links remain honored.
The initial package awaits admin approval under the existing traveler read-only
policy. No payment is collected.
The new categories extend the existing string enums without removing old values;
the existing PostgreSQL item-type columns are varchar, so no schema migration is
required for this step.
Sender signup, regular package posting, and agent package creation now share an
item editor: add/remove up to 50 items, choose each category, enter its description
and weight (minimum 0.1 kg, at most two decimal places), and see the calculated
total. Receiver name, phone, and address in Ethiopia are required; receiver notes
are optional. Review and private package details display the item list and receiver.
Agent drafts retain these details and the existing authorization and submission rules.
Migration V20 adds `package_items`, backfills existing packages as one item, and
links requests to the existing `recipients` table. New structured requests include
both `items` and `receiver`; legacy requests omitting both remain supported.
The backend calculates weight from items (ignoring the legacy scalar weight for
structured requests) and checks every category against trip rules. Receiver details
are visible only to the sender, managing agent in its scoped portal, admin, or the
accepted carrier after funding under the existing contact-unlock rules; public
previews do not include them.
Traveler signup now uses **Account → Trip Details → Review**, reusing the account
fields and existing marketplace trip form. It requires name, email, phone, password,
route, departure/arrival dates, available capacity, at least one accepted category,
and acceptance of the displayed traveler community terms. The existing signed-in
trip form still allows an optional arrival date and retains selected item rules
after publishing.
`POST /api/auth/register/traveler` atomically creates a TRAVELER account, its
profile, and a marketplace trip. Invalid trips, missing consent, and duplicate
emails do not leave partial accounts. Migration V21 adds nullable consent timestamp
and version fields to `traveler_profiles`; older users retain their existing
verification and have no fabricated consent record. New onboarding records
acceptance of `traveler-community-v1`. Default signup redirects to the traveler's
existing trip page; explicit safe return links remain honored.
The wizard displays identity-document upload, selfie, and phone verification as
**Pending setup**, not completed. It collects no identity documents or selfies,
sends no SMS, and does not mark the phone verified. Accounts start UNVERIFIED,
although the posted trip can appear in existing traveler search. Administrator
verification remains unchanged, and travelers still cannot manage sender requests.
Traveler signup, signed-in trip posting, and agent on-behalf trip posting now share
category pricing and optional preferred handoff location (500 characters) and notes
(1000 characters). Choosing **Will carry** shows a required USD price and **Per kg**
or **Per item** unit. A per-item price applies to each listed package item, not an
entire shipment. Prices must be $0.01–$99,999,999.99 with at most two decimal places.
The signed-in trip form preserves selected category prices after publishing.
Migration V22 adds `trip_item_prices` and nullable handoff/notes fields to
`market_trips`. Existing trips stay unchanged with no invented prices. Legacy API
clients may omit `itemPrices`; if provided, it must contain exactly one price per
allowed category and no blocked or unaccepted categories. New forms always send it.
Prices, units, handoff, and notes appear in signup review, authenticated trip cards,
and agent trip views. Public previews exclude handoff details and notes.
Do not enter contact information
or private addresses in trip notes or handoff text.
These are advertised transport prices before platform fees. Existing offer prices,
matching, demo payments, and capacity reservation remain unchanged; automatic
shipment quotes and fee calculations are part of the later shipment-request step.
**Browse Travelers** now shows privacy-preserving cards with generic avatars,
arrival/departure dates, available kg, administrator-assigned verification badges,
existing completed-delivery rating averages/counts, and accepted categories with
their advertised USD price/unit. Unrated travelers show "Not yet rated"; legacy
categories without prices show "Price not provided". No names, photos, user IDs,
contact details, handoff locations, or notes are added to the public API.
The existing `/api/public/travel` projection now includes `expectedArrivalDate`,
`rating`, `ratingCount`, `verification`, `allowedItems`, and `itemPrices`. Both
ID_VERIFIED and FULLY_VERIFIED qualify for **Verified travelers only**; UNVERIFIED
does not. This filter uses `verifiedOnly=true` alongside the existing route/date/sort
URL filters and survives refresh and browser history. The authenticated sender
marketplace trip list also supports the verified-only filter.
**View trip** opens `/browse-travelers/:id`, a dedicated, refreshable details screen
with the same private profile summary, prices, dates, and capacity. The public
`GET /api/public/travel/{id}` uses exactly the browse projection and returns 404
for missing, past, full, or inactive-carrier trips. Signed-in senders/admins load
handoff text, trip notes, and blocked categories through
`GET /api/marketplace/trips/{id}`; this endpoint follows the existing authenticated
trip-list visibility rules (admin, owner, or sender viewing an upcoming active
carrier's trip). Free text is never added to public responses. No biography is
fabricated. Its package-posting action retains the selected trip through login,
but does not reserve space. Loading, unavailable, error/retry, empty,
result-count, and mobile states use existing components and styles.

**Request shipment** opens `/shipment-request/:tripId` (sender only), with
Package, Price & Confirm, and Request Sent steps. Select an existing open,
account-owned package, or post one first. The server validates ownership, route,
dates, every category, carrier activity, and capacity using the existing matching
rules. Every category must have an advertised price; unpriced legacy trips use the
existing offer flow, not an invented price. PER_KG multiplies each listed item's
weight by its rate; PER_ITEM charges once per listed item. Line amounts round
HALF_UP to cents before summing. The platform fee comes from `platform_settings`,
not a hardcoded 10%. SENDER adds the fee to the sender total; TRAVELER deducts it
from the carrier payout; SPLIT assigns half (HALF_UP, including an odd cent) to the
carrier and the remainder to the sender. Review shows both shares and the total.

`GET /api/marketplace/shipment-requests/quote?packageId=&tripId=` returns a
server-computed quote. `POST /api/marketplace/shipment-requests` accepts package ID,
trip ID, the reviewed quote key, and an optional 500-character message. Changed
prices/settings/package revisions require a new review. V23 persists an immutable
quote snapshot and a PENDING request, expiring exactly one hour after submission.
There is at most one pending request per package, including concurrent submissions;
identical pending retries return the same record without duplicate notifications.
Submission neither reserves kg nor changes existing offer acceptance, escrow,
chat, or payment behavior. No payment is enabled by submitting a request.

Travelers remain read-only by explicit policy. Requests for traveler trips notify
admins, not the traveler; agent trips notify their owner and admins. Sender,
targeted agent, and admin can inspect saved requests on the package details page
through `GET /api/marketplace/shipment-requests?packageId=`. Other senders and
travelers cannot access this private history; unrelated agents get no rows.
The expiry job runs every minute (after a
one-minute startup delay), and history reads also reconcile expiry. Pending
requests also expire when their package leaves REQUESTED or their trip is past
or its carrier is inactive; the existing marketplace offer flow is not blocked.

**Request management:** the existing agent/admin Shipment Requests pages now
include a targeted inbox with Pending, Accepted, Declined, and Expired filters,
counts, countdowns, and package-details links, without replacing their existing
customer-package or approval views. `GET /api/marketplace/shipment-requests/inbox`
returns only the sender's own requests, requests for an agent's own trips, or all
requests for administrators. Travelers cannot use the inbox or decision API.

`POST /api/marketplace/shipment-requests/{id}/decision` accepts an `action` and
the reviewed `quoteKey`. ADMIN or the owning AGENT can ACCEPT, DECLINE, or
PROPOSE_PRICE (with `proposedPrice`, a package subtotal in USD before fees).
A proposal keeps PENDING, uses the original fee percentage/payer snapshot, and
does not reset the original one-hour expiry or reserve capacity. The original
itemized quote is immutable; V24 separately stores the latest proposed quote.
Only the package's SENDER can CONFIRM_PRICE or REJECT_PRICE, using the latest
proposal's key. Stale approvals fail explicitly. Rejecting a proposal declines
the targeted request, allowing the sender to send another request.

Acceptance revalidates compatibility under package/trip/request locks, reserves
kg once, records an accepted delivery offer, and moves the existing package to
MATCHED. This reuses downstream package tracking/cancellation behavior; cancelling
before pickup releases reserved weight. Administrators may accept for travelers
without re-enabling any traveler mutations or revealing unpaid contact data.
Decline/expiry reserve nothing and clear the pending-package slot. Acceptances and declines
are recorded with a timestamp and generate notifications. A deadline reached
during review returns EXPIRED without accepting or reserving. The existing
legacy offer workflow stays available, but targeted accepted bookings cannot
use demo escrow funding; they use the separate Stripe test payment flow below.

**Stripe payments (test mode only):** after an accepted targeted shipment request,
the owning sender can open **Open Stripe test payment** from their package.
The responsive payment page shows the agreed itemized/revised price and original
fee percentage/payer snapshot, then mounts Stripe's hosted Card Element. Card
numbers/CVC go directly to Stripe; they are never sent to or stored by our API.
3-D Secure is handled by Stripe's confirmation dialog. Apple Pay/Express Checkout,
live charges, Stripe Connect, actual escrow, and real carrier payouts are not enabled.
Receiver-code confirmation releases the test/demo booking state only, as described below.

Backend environment variables (leave blank to disable payment):

```text
STRIPE_TEST_SECRET_KEY=       # Stripe sandbox/test sk_test_ key
STRIPE_TEST_PUBLISHABLE_KEY=  # Matching pk_test_ key
STRIPE_TEST_WEBHOOK_SECRET=  # whsec_ signing secret for this endpoint
```

Set these privately in the backend environment (or local ignored `.env` for
Compose). Never commit keys or paste them into chat. Live keys are rejected.
No frontend Stripe key variable is needed; the authenticated API supplies only
the publishable test key. The frontend production CSP permits the Stripe-hosted
script, frames and API connections while retaining existing restrictions.

For local verification with your own sandbox:

1. Configure the matching test API keys in the backend environment.
2. Run `stripe listen --forward-to localhost:8080/api/payments/stripe/webhook`.
3. Configure the listener's signing secret as `STRIPE_TEST_WEBHOOK_SECRET`
   and restart the backend. In hosted environments, register an HTTPS test-mode
   webhook at `/api/payments/stripe/webhook` on the frontend domain; its existing
   `/api/` proxy reaches the private backend. Subscribe to
   `payment_intent.succeeded`, `payment_intent.processing`,
   `payment_intent.payment_failed`, and `payment_intent.canceled`.
4. As a sender, request a priced trip; have its agent/admin accept (or approve
   the latest proposed price). Open payment from the package.
5. Use Stripe's test card `4242 4242 4242 4242`, a future expiry and any 3-digit
   CVC. Use `4000 0025 0000 3155` to test authentication and `4000 0000 0000 9995`
   to test insufficient funds. Never use real card details.
6. Confirm the page waits for the signed webhook before displaying SUCCEEDED.
   Reload/retry to verify the same durable attempt and PaymentIntent are reused.

API: `GET /api/marketplace/requests/{id}/payment` returns the owning sender's
accepted quote, configuration and saved status; `POST .../payment/intent` creates
or reuses the test PaymentIntent and returns its client secret with `no-store`.
The server ignores client-supplied amounts/currency and charges the saved USD
sender total, including the agreed fee share. Stripe USD limits are enforced
($0.50 minimum, $999,999.99 maximum). Setup attempts are committed before Stripe
is called; deterministic idempotency keys protect timeout/retry/concurrent flows.
An unresolved attempt with no saved intent ID is blocked after 23 hours, before
Stripe's 24-hour idempotency retention boundary; ask an administrator to reconcile
it in Stripe, rather than automatically creating a potentially duplicate charge.

The public webhook verifies the raw body/signature with a five-minute tolerance,
rejects live events, retrieves the PaymentIntent server-side, checks its metadata,
currency and exact expected/received amounts, and locks the package before
recording payment. Duplicate/out-of-order notifications cannot double-fund or
restore cancelled bookings. Browser confirmation alone never marks a booking paid.
Verified success sets PAID_ESCROW with distinct STRIPE_TEST_HELD status and unlocks
participant contacts, but represents test funds only. Failed cards reuse the same
intent for correction; processing/successful intents cannot be paid a second time.
Delayed webhooks leave the page pending with explicit instructions, not false success.

Cancelling a booking through the app first cancels its pending test intent or
confirms a full Stripe test refund (including when success raced its webhook),
then releases capacity. If Stripe/refund confirmation fails, cancellation fails
explicitly and preserves the booking/capacity for a safe retry. Perform refunds
through this app, not directly in Stripe's dashboard; external/partial-refund and
dispute reconciliation is not implemented. Receiver-code confirmation finishes
new bookings as STRIPE_TEST_RELEASED, explicitly without any payout or commission
creation. Legacy confirmation and reasoned admin dispute resolution remain supported.

Migration V25 creates `stripe_payments` linked to the existing package/request.
The admin Payments page shows a separate paginated Stripe test-attempt table
(`GET /api/admin/stripe-test-payments?page=`); these amounts are excluded from the
legacy demo escrow/revenue totals. With test keys unset, the rest of the app
continues to work and payment shows a clear not-configured state.

The Agent selection shows approval guidance and an agent login link; it does not
submit an application or create a sender account by mistake.
Invitations are no longer required or supported. Registration accepts a `SENDER` or `TRAVELER` role and
creates the corresponding profile. Agent accounts are created by administrators
from the existing admin dashboard. The Become an Agent card opens registration
with an explicit administrator-approval notice; it does not grant agent access.
Signed-in agents go directly to their portal. Public signup cannot create agents or admins.
Passwords require at least 12 characters and at most 72 UTF-8 bytes.
Admins can still disable/re-enable accounts under **Account access**.

The invitation UI, API endpoints, token redemption, email delivery service, and
SMTP configuration have been removed. The historical V8 Flyway migration/table is
retained to preserve existing database migration history; old invitation links no
longer grant access. Existing accounts and passwords are unchanged.

Public GET APIs include `/api/public/info`, `/api/public/travel`,
`/api/public/requests`, and `/api/public/requests/{id}/product-photo`.
Travel returns all upcoming active-carrier trips with remaining capacity, newest
posted first: ID, route, departure date, available kilograms and posting timestamp.
Request previews contain only ID, route, availability/deadline, item type, weight,
offered price, photo availability and posting timestamp. Only open, unexpired
requests from active senders (or admin-managed guest requests) qualify. Public photo
access uses the same availability checks and `Cache-Control: no-store`; matched,
closed and expired request photos return 404. No names, account IDs, descriptions,
offers, history, contacts, manifests or tracking data are public. Full request
details still require login and existing role permissions; only agents can
currently make offers. Public request previews and photos additionally require
explicit admin approval.

## Temporary traveler read-only policy and admin request approval

**Post-delivery review exception (V26):** a matched traveler may submit one
review of their sender after confirmed completion. No other traveler mutation
permission changes. The scoped `/my-reviews` page/API exposes only that
participant's review records and route, even if the package is unapproved; it
does not expose package contents, receiver/contact details, photos or chat.
Full package/photo/chat and notification access still requires admin approval.

All sender requests (registered and guest) require explicit approval before
travelers can see them. **V17** adds `admin_approved` with a default of false:
all existing requests remain in the database but start unapproved, as do new
requests. This approval is separate from offer acceptance and shipment status;
it does not reserve capacity, accept an offer, fund escrow, or convert a shipment.

Admins open **Marketplace**, filter **Traveler approval > Awaiting approval**,
open a request, and select **Approve for traveler viewing**. **Remove traveler
approval** hides it again. The admin-only
`PUT /api/marketplace/requests/{id}/approval` accepts `{approved: true|false}`.
Changes are recorded in the request timeline with the admin actor; repeated
identical approvals do not duplicate events or notifications.

Travelers may view only approved requests, including previously matched requests.
Direct detail/photo URLs, match suggestions, public previews/photos, package chat
and old notification rows cannot expose unapproved requests. Approval notifies
active travelers; submission notifies admins and agents only. Revoking approval
also hides existing request notifications. Public previews still require an open,
unexpired request; authenticated traveler lists may show approved requests at
other statuses.

Traveler accounts cannot send offers, accept/decline/withdraw offers, fund escrow,
change request delivery status, use the old sender-only rating endpoint, or send package chat messages. These rules
are enforced on the server as well as in the UI. Existing traveler offers/history
are retained, but pending traveler offers cannot be accepted while this policy is
active. Matched travelers can read existing chat only while the request is approved.
Admins can continue delivery updates for previously matched travelers. Travelers
can still post their own trips; separate operational shipment/trip workflows are
unchanged.

**Agents retain their existing request visibility and offer/chat/delivery
permissions**, regardless of approval. Senders still see their own requests and
can manage normal account requests; guest-account requests remain read-only.
This temporary policy supersedes traveler mutation capabilities described in the
general marketplace lifecycle below.

# Private guest sender requests

Admins open **Admin dashboard > Guest sender links** (`/admin/guest-links`),
enter a private reference label, and create a link to share with one sender.
The full link is displayed only at creation; copy it before leaving the page.
Unused links can be revoked. Links expire seven days after creation and accept
exactly one request. No sign-in is needed to submit. For a previously unregistered
email, submission creates an active sender account and sender profile with the
provided name, normalized email and required phone number. This is separate from
the removed account-invitation feature.

After submission, a newly created sender is automatically signed in using the
one-time login email and unique, cryptographically random temporary password.
The customer is taken directly to **Change password**, with their temporary
password filled in and a request-received confirmation. The password is kept only
in React memory for this session; only the normal session token goes into browser
storage. Refreshing discards the prefilled password. If automatic sign-in fails,
the receipt explicitly reports the error, displays the credentials once for the
customer to save, and offers a sign-in retry without resubmitting the package.
The password is stored server-side only as a BCrypt hash; it is not available in link checks, retries, admin
lists, logs, or browser storage. Credential-bearing responses use `Cache-Control:
no-store`. If the response is lost, contact the team; retrying cannot recover or
reset credentials. No automatic password recovery/delivery is provided.

New accounts must set a different password at first login (at least 12 characters,
at most 72 UTF-8 bytes). Until then, authenticated APIs allow only viewing their
own account and changing their password. After changing it, all previous sessions
are revoked and the customer signs in again. Existing accounts are not forced to
change passwords.

If the email is already registered (regardless of role or active state), submission
does not change that account or password and does not attach the request to it.
The receipt asks the customer to sign in with their existing credentials; the
request remains exclusively admin-managed. Supplying an email does not prove
ownership of an existing account. For newly created accounts only, the original
request appears in **My packages** as read-only, including its photo and timeline.
The customer cannot accept/decline offers, fund escrow, change status, rate,
use chat, or convert that request. Future normal requests retain the regular workflow.

The guest form at `/guest-request#token=...` requires full name, email, phone,
pickup/destination cities, availability/deadline, item type/description, weight,
offered USD price, and a product photo. JPEG/PNG inputs are resized in the
browser using the existing photo helper, then validated and re-encoded on the
server (maximum 2 MB, 4096 pixels per side, 16 million pixels). This removes
embedded image metadata. Guests should not include contacts in public package
descriptions or photos. The form explains that open request route/photo previews
become public only after admin approval, while contact details stay private.
After submission, the page says:
**"Your request has been received. We will contact you once we find a traveler."**
Reloads and duplicate submissions return this receipt without credentials or contact data;
concurrent retries cannot create duplicate requests.

Guest requests enter the existing marketplace and notify active admins and agents.
Travelers are notified after admin approval and then have read-only access.
Agents can inspect package details and the product photo, match their own trips,
and submit offers as usual. Admins see
the guest's contact details immediately and accept/decline offers on the guest's
behalf. They can also simulate escrow funding and manage delivery status.
Regular account-owned requests still require the owning sender to select an
offer and fund demo escrow. Guest email, phone, and name remain hidden from
travelers/agents, including after matching and simulated funding. Admins and
the newly created owner account can see the guest's own contact details.
Guest coordination is handled directly by the admin, not through account chat.
**Contacting the sender is a manual admin action; no email or SMS is sent.**

API endpoints:
- Admin-only `POST`/`GET /api/admin/guest-request-links` create/list links.
- Admin-only `POST /api/admin/guest-request-links/{id}/revoke` revokes an unused link.
- Anonymous `POST /api/guest-requests/check` accepts `{token}` and returns only
  availability or a receipt.
- Anonymous `POST /api/guest-requests/submit` accepts `{token, name, email, phone,
  photoDataUrl, request}`; `request` uses the normal marketplace request input.
  It returns `{status, message, credentials, accountMessage}`; `credentials` is
  `{email, temporaryPassword}` only on the first successful new-account submission
  and is `null` for existing-email submissions and retries.
- Authenticated `GET /api/marketplace/requests/{id}/product-photo` requires access
  to that request. Large photo data is excluded from marketplace list DTOs.

Only a SHA-256 hash of the random 256-bit link token is persisted. The frontend
keeps the token in the URL fragment, not in the query string or server path.
Treat the full link as a secret: anyone possessing an unused link can submit
that sender's request. The guest API client is separate from account sessions.
Flyway migration **V14** adds the link table and nullable sender/guest fields,
preserving existing account requests and chat history.
Migration **V16** adds a separate guest account owner and a required-password-change
flag, defaulting to false for existing users. Existing guest requests are not
retroactively assigned or provisioned accounts.

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
operational payment starts `PENDING`. Conversion creates no additional account;
the source request remains read-only for its newly provisioned sender, if any.

Conversion generates a normal tracking number and tracking events, then opens
the standard operational path: payment, pickup/drop-off, receiving-agent and
traveler assignment, luggage consolidation, trip assignment, and delivery.
The **Manage shipment** link opens the admin shipment list with its row
highlighted; **View tracking** opens the normal authenticated tracking screen.
The admin shipment row links back to the source request and private contact details.
Operational shipment access remains unchanged and admin-managed; the new sender
can view the original marketplace request, not the converted shipment. Only
admins retrieve guest contacts through operational shipment APIs. Manifests label the
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

The landing page follows the travel-first design: a locally hosted photograph
of an Ethiopian Airlines Boeing 787-9 in flight against a plain sky, route/date
search, sender/traveler search modes, three signup/action cards, a trust strip,
and FAQs. Sender search forwards route/date filters to Browse Travelers; traveler
search keeps the approved-package preview flow. Action cards preserve role-aware
destinations and administrator-approved agent access. The header includes Login
and Sign up on desktop and mobile. Public identities stay private, and payment
copy explicitly describes demo/test mode.

Hero photograph: **Ethiopian Airlines Boeing 787-9 ET-AUP on approach to
Washington Dulles International Airport over Maryland, USA**, Acroterion.
[Wikimedia Commons source](https://commons.wikimedia.org/wiki/File:Ethiopian_Boeing_787-9_ET-AUP_MD1.jpg),
licensed under [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
The resized display image is `frontend/public/images/hero.jpg`; the page credits
the author and links to the source and license. No airline affiliation or
endorsement is claimed.

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
visibility. Agents submit offers using only their own compatible
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

**Legacy demo escrow is a labeled simulation only; targeted bookings use the
separate Stripe test flow above. No real funds are collected, held, released or
refunded in either flow.** The owning sender funds
demo escrow for the accepted offer's exact price. It does not charge the old 2%
service fee. Funding unlocks account contact details for the selected participants.
Before that, travelers/agents see route, dates, items and offers they authored, not
sender account contact fields or competing offers. Do not put contacts into public
descriptions or offer messages. Package chat also requires confirmed payment.

## Package chat

Each non-guest marketplace package gets a persistent text conversation after offer
acceptance **and confirmed payment**. Targeted bookings require the verified Stripe
test webhook; creating an intent, submitting a card form, or provider success before
the webhook does not unlock chat. Existing demo bookings require simulated escrow
funding instead. Neither mode moves real money.

Only the owning sender and selected agent can send messages. The selected traveler
can read chat only while the request is admin-approved; the temporary traveler
read-only policy remains unchanged. Other senders, unselected offer authors, unrelated travelers/agents, and
anonymous visitors have no access, enforced by both chat endpoints. Administrators
can review the full conversation from the package detail page but cannot post.
Chat remains available during disputes; after completion or cancellation its history
is read-only when payment was confirmed (including refunded bookings). Unfunded
bookings, including old pre-funding conversations and bookings cancelled before
payment, remain inaccessible; no existing messages are deleted.

Open **My packages** (sender) or **Marketplace** (traveler/agent/admin), choose the
package, and use **Booking workspace > Chat**. The responsive workspace also has
keyboard-accessible **Tracking**, **Package Details**, and **Payment** side tabs.
Tracking reuses stored delivery events; recent activities show the five latest
events with timestamps. The Payment tab shows the existing payment state and links
the sender to the private Stripe test fee/payment summary (admins use their own
payment records). It never exposes a sender-only payment intent to the carrier.
Agents can post routes through **My trips** or
the agent dashboard's **My delivery trips** link. Chat messages identify their author
and role, preserve line breaks, and are rendered as plain text (no HTML or attachments).
Messages must contain 1-2000 characters; whitespace-only messages are rejected.
Failed sends keep the draft and display an error. History polls every 5 seconds
and pages in batches of 50 using **Load earlier messages**. New messages generate
a persistent in-app notification for the other participant without including the
message body. Package locks serialize sends and status changes to keep ordering
stable and prevent writes after closure. No external chat service is used.

Chat content is visible immediately to the other participant and administrators.
Account contact fields and chat require confirmed payment. Chat can reveal any
details participants choose to write; avoid unnecessary personal information.
Switching workspace tabs preserves the chat draft and existing polling. Loading or
refetch errors are visible; cached messages are hidden when the chat request fails,
including after access is revoked.

The enforced marketplace state machine is:

`REQUESTED -> MATCHED -> PAID_ESCROW -> PICKED_UP -> IN_TRANSIT -> ARRIVED -> DELIVERED -> COMPLETED`

### Shipment tracking

The package's **Booking workspace > Tracking** tab shows five milestones:
**Booking Confirmed** (confirmed payment), **Package Received** (carrier pickup),
**In Transit**, **Arrived**, and **Delivered**. Each timestamp comes from the
existing persisted delivery event, displayed in the viewer's local timezone.
Acceptance alone is shown as awaiting payment, not a confirmed paid booking.
The history panel retains all events, including approval changes and disputes.
This is periodic status tracking (the package refreshes every 10 seconds), not GPS.

The selected agent or an administrator records **Mark arrived** after transit,
then verifies the receiver code for new bookings (legacy bookings still use
**Mark delivered**). A sender cannot mark arrival, and travelers
remain read-only. Arrival remains an active shipment in agent/admin counts and
tracking filters; disputes can be opened at arrival, but cancellations still
require an admin dispute resolution after pickup. Arrival does not release funds
or permit reviews. New bookings complete when the receiver code is verified;
legacy bookings retain separate sender/admin receipt confirmation.

Existing delivered/completed bookings are unchanged. Missing historical milestones
say **Not recorded**, with no fabricated timestamps; admin dispute resolutions do
not fabricate delivery events either. Cancelled and disputed timelines display
explicit notices and preserve their recorded history. No schema migration is
needed: marketplace statuses are existing `varchar(32)` fields, not database enums.
The separate legacy HL-number shipment tracking flow is unchanged.

Agents/admins update pickup/transit/arrival; travelers remain read-only.
New bookings use receiver-code confirmation to complete and simulate escrow release.
Admins can perform delivery steps but cannot choose
offers or fund escrow on a sender's behalf. A sender may submit one 1-5 rating
after completion. Sender/selected traveler/admin may cancel before pickup;
cancellation restores weight and simulates refund if funded. After funding and
before completion, participants can open a dispute with a reason. DISPUTED freezes
demo escrow; only an admin can resolve to COMPLETED (release) or CANCELLED (refund)
with a reason. Completed/cancelled requests are terminal. Delivery events persist
in sequence order, including funding, disputes and resolutions.

### Receiver delivery-code confirmation

For new, non-guest bookings, open **Delivery confirmation** on the package after
the funded booking reaches **Arrived**. The sender selects **Reveal receiver code**
and manually shares the six-digit code privately with the receiver. **No SMS or
email is sent**, and no receiver identity/phone verification is implied. The receiver
should share the code with the selected agent/admin only after receiving the package.
The carrier cannot retrieve or generate the code; even admins cannot reveal it.
It is returned only to the sender on issuance, not through GETs, chat, tracking,
notifications, logs or public DTOs. The browser does not persist the revealed code;
after hide/reload, wait for the allowed window and generate a replacement if needed.

The selected agent or admin enters the receiver's code. **Travelers remain fully
read-only**, including selected travelers: their admin performs code verification.
A valid code atomically records `DELIVERED` and `COMPLETED`, consumes the code,
closes chat for writing, unlocks post-delivery reviews and sets `DEMO_RELEASED` or
`STRIPE_TEST_RELEASED`. No separate sender receipt action is required for these
bookings. **No real payout, transfer or Stripe Connect operation occurs.** Stripe
test bookings do not generate commission records; existing demo commission behavior
is retained. Direct status updates cannot bypass code verification on new bookings.
Reasoned admin dispute resolution remains an explicit audited alternative.

Codes are cryptographically generated, stored only as salted BCrypt hashes, expire
after 15 minutes, and lock after five incorrect attempts. Failed attempts persist
even when the API rejects the code. Sender reissuance invalidates the previous code:
at least 60 seconds between issues, at most five issues per hour per package, and
locked codes cannot be replaced until expiry. Package locks serialize issuance,
verification and delivery/dispute changes to prevent duplicate completion/release.
Expired, incorrect, locked, closed, disputed and unauthorized operations fail explicitly.

Authenticated endpoints under `/api/marketplace/requests/{id}/delivery-confirmation`:

- `GET`: scoped status, expiry, attempt count and permitted actions; never the code/hash.
- `POST /code`: owning sender only; returns `{code, expiresAt}` once, with `Cache-Control: no-store`.
- `POST /verify` with `{code: "123456"}`: selected agent or admin only; no code returned.

Migration V27 adds hashed confirmation records and a `delivery_code_required` flag.
Existing bookings are explicitly legacy (`false`); new bookings default to `true`.
It does not fabricate confirmations, delete history or block completion of existing
delivered bookings. Admin-managed guest bookings and the separate legacy HL-number
shipment flow retain their existing handling. This manual-sharing test workflow is
not a substitute for production receiver authentication, SMS delivery or real payouts.

### Post-delivery reviews

The existing public trip/profile view (`/browse-travelers/{tripId}`) includes a
**Traveler / agent reviews** summary: average overall score, proportional stars,
and sender review count across the carrier's deliveries (not just the displayed
trip). Browse cards reuse the same compact rating display. Unrated carriers show
**Not yet rated**, not a zero-star score. The existing public API supplies only
aggregate rating/count; no new public review endpoint or identity is exposed.
Legacy overall-only ratings count once, and reciprocal carrier-to-sender reviews
do not increase the carrier's score or review count. Comments and category scores
remain private. The summary follows the trip page's existing refresh/error handling.

Participants open **My reviews** (`/my-reviews`, newest completed bookings first,
20 per page) or the review section on a package they can already access. Agents
also have a My Reviews portal link. Sender and selected carrier (traveler or
agent) may each review the other exactly once after delivery is `COMPLETED` via
receiver-code verification, legacy receipt confirmation, or admin dispute resolution.
`DELIVERED` alone does not permit a review: legacy bookings still await receipt
confirmation. Cancelled, disputed, incomplete and
admin-managed guest bookings cannot be reviewed; admins can read but cannot
submit on a participant's behalf.

Each detailed review includes an overall 1-5 score, four required whole-number
1-5 category scores, and an optional text comment (up to 2,000 characters):

- Sender reviewing carrier: communication, on-time delivery, package handling,
  reliability.
- Carrier reviewing sender: communication, accurate description, handoff
  timeliness, packaging quality.

Scores must match the review direction; recipient/author IDs are selected by
the server, never the form. Package locking and unique database constraints
prevent repeated/concurrent submission, including attempts through the legacy
rating endpoint. Submissions are immutable; editing/deletion and moderation
workflows are not added in this checkpoint.

`GET /api/marketplace/reviews?page=0` is the current sender/carrier's scoped inbox.
`GET /api/marketplace/requests/{id}/reviews` returns reviews and eligibility to
the two participants or an admin; `POST` to the same URL accepts
`{rating, scores: {CRITERION: score, ...}, comment}` for an eligible participant.
Anonymous users, outsiders, and non-participating agents/travelers cannot read
or write reviews even when a package is approved. Both participants see feedback
immediately; reviews are not double-blind. UI forms have validation, explicit
loading/empty/error/retry states, labeled keyboard controls and mobile layouts.

Review DTOs use generic sender/carrier labels rather than author names, emails,
phone numbers or user IDs. Comments/category scores remain private to the
participants/admins and are rendered as text; keep personal details out of
comments. Existing public traveler cards/details/rankings continue to show only
the carrier's overall rating average/count. New sender-to-carrier reviews set
the established package rating once; reciprocal feedback never changes a
carrier's rating or double-counts a delivery. Sender reputation summaries are
not public in this checkpoint.

V26 adds `delivery_reviews` and `delivery_review_scores`. Existing ratings are
backfilled as overall-only legacy reviews, without fabricated category scores
or comments. Existing `POST .../rating` clients remain supported and now create
the same overall-only record; a prior legacy rating cannot be replaced with
a second detailed review. Existing carrier averages/counts and scores remain
unchanged. The admin Reviews view includes bookings with feedback in either
direction (even if no carrier overall rating yet); **View** opens both reviews
and their details. Its existing search/pagination and the unrelated pending
package-approval KPI remain unchanged.

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

# Agent portal

Users with the `AGENT` role get a dedicated portal at `/agent`. It has a dark green sidebar, a dashboard, customer management, package and trip wizards, shipment requests, tracking, commissions, authorizations, messages and settings. The previous agent dashboard moved to `/agent/legacy`; admins still reach it there. Everyone else who visits `/agent` is redirected.

- **Customers.** Agents register their own `SENDER` or `TRAVELER` customers. The agent must confirm the customer's consent; the server enforces this with `authorization.confirmed=true`. Each new account gets a random temporary password, shown to the agent once, and the customer must change it at first sign-in.
- **Authorization rule.** Every on-behalf action needs an `ACTIVE`, unexpired authorization for that customer. On-behalf actions are creating a package (optionally as a `DRAFT`), submitting a draft, and posting a trip. Each one writes an `agent_audit_log` row. Requests outside an agent's own customers, without an authorization, or from non-agents return `403`.
- **Commissions.** When a delivery created by an agent reaches `COMPLETED`, a `PENDING` commission is recorded. The amount is the agent's commission rate × the accepted offer price.
- **Admin tools.** `POST /api/admin/agents/{userId}/promote` (optional `commissionRate`, 0–1) turns an existing user into an agent. `POST /api/admin/agents/commissions/{id}/pay` marks a commission paid. Agents can also still be created with `POST /api/agents`.

Endpoints (all `ROLE_AGENT`, scoped to the signed-in agent, page size 10):

- `GET /api/agent/dashboard`
- `GET /api/agent/customers?type=&q=&page=`, `POST /api/agent/customers`, `GET /api/agent/customers/{id}`
- `POST /api/agent/customers/{id}/packages` (`details`, optional `photoDataUrl`, `draft`)
- `POST /api/agent/customers/{id}/packages/{packageId}/submit`
- `POST /api/agent/customers/{id}/trips`
- `GET /api/agent/packages?status=&page=`, `GET /api/agent/trips?page=`
- `GET /api/agent/commissions?from=&to=&status=&page=`
- `GET/POST /api/agent/authorizations`, `DELETE /api/agent/authorizations/{id}`

Migration V18 adds `agent_customers`, `agent_authorizations`, `agent_commissions` and `agent_audit_log`. It also adds a nullable `created_by_agent_id` to delivery requests and marketplace trips; the existing `agent_profiles` table serves as the agents table.

The frontend lives in `frontend/src/agent/`. To develop the portal before the backend is available, set `VITE_AGENT_MOCKS=true`, which swaps in in-memory mock data. The flag is off by default, and mocks are excluded from production builds.

# Admin console

Admins get a purple console at `/admin`. Its sidebar covers Dashboard, Users, Travelers, Senders, Agents, Trips, Packages, Shipment Requests, Bookings, Payments, Commissions, Tracking, Reviews, Disputes, Reports and Settings. The top bar has a date range (7/30/90 days, year to date, or custom); it drives the Dashboard, Payments and Reports. The previous admin dashboard now lives at `/admin/operations`. Account access, guest links, luggage and trip detail keep their old URLs under `/admin`. Non-admins are redirected.

- **Dashboard.** Shows Total Revenue (demo escrow funded in the range), Platform Fees, Active Shipments, and Pending Reviews (requests awaiting admin approval). It also has a Revenue & Bookings chart (daily buckets for ranges up to 31 days, weekly beyond that) and recent activity.
- **Users.** Tabs for All, Travelers, Senders and Agents, with search. Each user has a status:
  - Suspended: the account is inactive.
  - Pending: the account still has a temporary password, or it is an unverified traveler or an inactive agent.
  - Verified: everyone else.

  Admins can suspend or reactivate an account, verify a traveler's ID, and activate an agent.
- **Shipment Requests, Packages, Bookings, Tracking, Disputes, Reviews.** These are filtered views of delivery requests. Admins can approve or reject new requests, advance tracking (picked up → in transit → delivered), and resolve disputes as completed or cancelled with a reason.
- **Payments and Commissions.** Payments shows escrow held, released and refunded, plus the platform fee and traveler payout per request. Commissions lists agent commissions, and pending ones can be marked paid.
- **Settings.** Sets the platform fee %, the default agent commission % (used when an agent has no individual rate), and who pays the fee (sender, traveler, or split 50/50).

Endpoints live under `/api/admin/console/**`, which, like all `/api/admin/**` routes, is restricted to `ROLE_ADMIN`:

- `overview`, `users`, `trips`, `requests` (`view=all|bookings|tracking|disputes|reviews`), `payments`, `commissions`, `reports`, `settings`
- `POST disputes/{id}/resolve`, `PUT settings`

Migration V19 adds the single-row `platform_settings` table, seeded with a 10% fee, 10% agent commission, and the sender as fee payer.

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
