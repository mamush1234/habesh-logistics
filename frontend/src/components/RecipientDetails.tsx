export function RecipientDetails({ name, phone, recipients, description }: {
  name: string; phone: string; recipients?: { name: string; phone: string }[]; description?: string | null;
}) {
  return (
    <div className="space-y-1 text-sm">
      {(recipients ?? [{ name, phone }]).map((recipient, index) => <div key={index}>
        <div><span className="text-ink-500">Recipient{index > 0 ? ` ${index + 1}` : ''}: </span>{recipient.name}</div>
        <div><span className="text-ink-500">Phone: </span><a className="text-brand-600 underline" href={`tel:${recipient.phone}`}>{recipient.phone}</a></div>
      </div>)}
      <div className="whitespace-pre-wrap break-words"><span className="text-ink-500">Description: </span>{description?.trim() ? description : 'No description provided'}</div>
    </div>
  );
}
