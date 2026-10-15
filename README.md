
# Travel announcements

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
