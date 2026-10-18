import { useEffect, useRef, useState } from 'react';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { MarketplaceApi } from '../api/marketplace';
import { useAuth } from '../auth/AuthContext';
import { marketError } from '../pages/Marketplace';

export function PackageChat({ requestId, closed }: { requestId: string; closed: boolean }) {
  const { user } = useAuth();
  const qc = useQueryClient();
  const [body, setBody] = useState('');
  const history = useRef<HTMLOListElement>(null);
  const followLatest = useRef(true);
  const key = ['package-chat', requestId, user?.id];
  const query = useInfiniteQuery({
    queryKey: key,
    initialPageParam: null as number | null,
    queryFn: ({ pageParam }) => MarketplaceApi.messages(requestId, pageParam),
    getNextPageParam: page => page.nextBefore ?? undefined,
    refetchInterval: 5000,
    retry: (count, error) => count < 2 && (!isAxiosError(error) || error.response?.status !== 403),
  });
  const send = useMutation({
    mutationFn: () => MarketplaceApi.sendMessage(requestId, body.trim()),
    onSuccess: () => {
      setBody('');
      followLatest.current = true;
      qc.invalidateQueries({ queryKey: key });
    },
    onError: error => { qc.invalidateQueries({ queryKey: key }); marketError(error); },
  });
  const messages = query.data?.pages.flatMap(page => page.messages).slice().reverse() ?? [];
  const latestId = messages.at(-1)?.id;
  useEffect(() => {
    if (followLatest.current && history.current) history.current.scrollTop = history.current.scrollHeight;
  }, [latestId]);
  const writable = !closed && user?.role !== 'ADMIN' && query.data?.pages[0].writable && !query.isError;
  return <section className="card space-y-4" aria-labelledby="package-chat-title">
    <h2 id="package-chat-title" className="text-xl font-bold">Package chat</h2>
    <p className="text-sm text-ink-600">Only this package's sender and selected traveler or agent can message here. Administrators can review the conversation. Chat opens after offer acceptance, before escrow funding; anything you send is visible immediately.</p>
    {user?.role === 'ADMIN' && <p className="text-sm font-semibold">Administrator review &middot; Read-only</p>}
    {closed && <p className="text-sm font-semibold">This delivery is closed. Chat history is read-only.</p>}
    {query.isPending && <p role="status">Loading package chat...</p>}
    {query.isError && <p role="alert">Could not load package chat. You may no longer have access. <button className="underline" onClick={() => query.refetch()}>Retry</button></p>}
    {query.hasNextPage && <button className="btn btn-ghost" disabled={query.isFetching} onClick={() => query.fetchNextPage()}>Load earlier messages</button>}
    {query.isSuccess && messages.length === 0 && <p>No messages yet. Start the conversation about this package.</p>}
    <ol ref={history} aria-label="Package messages" className="max-h-96 overflow-y-auto space-y-3" onScroll={event => {
      const list = event.currentTarget;
      followLatest.current = list.scrollHeight - list.scrollTop - list.clientHeight < 80;
    }}>
      {messages.map(message => <li key={message.id} className={`rounded-xl border p-3 ${message.authorId === user?.id ? 'bg-blue-50 border-blue-200' : 'bg-ink-100 border-ink-200'}`}>
        <div className="flex flex-wrap justify-between gap-1 text-xs text-ink-600">
          <strong className="break-words">{message.authorName} &middot; {message.authorRole}</strong>
          <time dateTime={message.createdAt}>{new Date(message.createdAt).toLocaleString()}</time>
        </div>
        <p className="whitespace-pre-wrap break-words mt-2 text-sm">{message.body}</p>
      </li>)}
    </ol>
    {writable && <form className="space-y-2" onSubmit={event => { event.preventDefault(); if (body.trim()) send.mutate(); }}>
      <label htmlFor="package-message" className="label">Message about this package</label>
      <textarea id="package-message" className="input" required maxLength={2000} rows={3} disabled={send.isPending} value={body} onChange={event => setBody(event.target.value)} />
      <div className="flex flex-wrap justify-between gap-2 items-center">
        <span className="text-xs text-ink-500">{body.length}/2000 &middot; Updates every 5 seconds</span>
        <button className="btn btn-brand" disabled={!body.trim() || send.isPending}>{send.isPending ? 'Sending...' : 'Send message'}</button>
      </div>
      {send.isError && <p role="alert" className="text-sm text-red-700">Message not sent. Your draft has been kept; check the error and try again.</p>}
    </form>}
  </section>;
}
