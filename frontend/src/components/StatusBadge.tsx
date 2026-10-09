import { clsx } from 'clsx';

const STATUS_META: Record<string, { label: string; cls: string }> = {
  CREATED:                   { label: 'Created',                 cls: 'badge-gray' },
  PAYMENT_PENDING:           { label: 'Payment Pending',         cls: 'badge-amber' },
  READY_FOR_PICKUP:          { label: 'Ready for Pickup',        cls: 'badge-amber' },
  PICKUP_SCHEDULED:          { label: 'Pickup Scheduled',        cls: 'badge-amber' },
  PICKED_UP:                 { label: 'Picked Up',               cls: 'badge-blue' },
  RECEIVED_AT_ALEXANDRIA:    { label: 'Received at Alexandria',  cls: 'badge-blue' },
  READY_FOR_CONSOLIDATION:   { label: 'Ready to Consolidate',    cls: 'badge-blue' },
  ASSIGNED_TO_LUGGAGE:       { label: 'Assigned to Luggage',     cls: 'badge-violet' },
  HANDED_TO_TRAVELER:        { label: 'Handed to Traveler',      cls: 'badge-violet' },
  DEPARTED_US:               { label: 'Departed USA',            cls: 'badge-violet' },
  ARRIVED_ETHIOPIA:          { label: 'Arrived in Ethiopia',     cls: 'badge-blue' },
  RECEIVED_BY_AGENT:         { label: 'Received by Agent',       cls: 'badge-blue' },
  READY_FOR_RECIPIENT:       { label: 'Ready for Recipient',     cls: 'badge-blue' },
  DELIVERED:                 { label: 'Delivered',               cls: 'badge-green' },
  CANCELLED:                 { label: 'Cancelled',               cls: 'badge-red' },
  ON_HOLD:                   { label: 'On Hold',                 cls: 'badge-red' },
  // trip
  OPEN:                      { label: 'Open',                    cls: 'badge-gray' },
  FILLING:                   { label: 'Filling',                 cls: 'badge-amber' },
  READY:                     { label: 'Ready',                   cls: 'badge-blue' },
  DEPARTED:                  { label: 'Departed',                cls: 'badge-violet' },
  ARRIVED:                   { label: 'Arrived',                 cls: 'badge-green' },
  CLOSED:                    { label: 'Closed',                  cls: 'badge-gray' },
  // luggage
  PACKING:                   { label: 'Packing',                 cls: 'badge-amber' },
  SEALED:                    { label: 'Sealed',                  cls: 'badge-blue' },
  IN_TRANSIT:                { label: 'In Transit',              cls: 'badge-violet' },
  // pickup
  REQUESTED:                 { label: 'Requested',               cls: 'badge-amber' },
  SCHEDULED:                 { label: 'Scheduled',               cls: 'badge-amber' },
  ASSIGNED:                  { label: 'Assigned',                cls: 'badge-blue' },
  EN_ROUTE:                  { label: 'En Route',                cls: 'badge-violet' },
  FAILED:                    { label: 'Failed',                  cls: 'badge-red' },
};

export function StatusBadge({ value }: { value: string }) {
  const meta = STATUS_META[value] || { label: value, cls: 'badge-gray' };
  return <span className={clsx('badge', meta.cls)}>{meta.label}</span>;
}
