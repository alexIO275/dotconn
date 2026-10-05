import { Client } from '@stomp/stompjs';
import { api } from './api';
import { currentUser, getToken } from './auth';
import { escape } from './ui';
import './chat.css';

type Navigate = (path: string) => void;
type Page<T> = { content: T[]; last: boolean };
type PrivateMessage = { id: number; senderId: number; recipientId: number; content: string; createdAt: string; readAt: string | null; projectId?: never };
type ProjectMessage = { id: number; projectId: number; senderId: number; senderDisplayName: string | null; content: string; createdAt: string };
type Message = PrivateMessage | ProjectMessage;
type Conversation = { partnerId: number; partnerDisplayName: string | null; lastMessage: PrivateMessage; unreadCount: number };
type ReadReceipt = { readerId: number; readAt: string };

let socket: Client | null = null;
let socketState = 'Conectare…';
const messageListeners = new Set<(message: Message) => void>();
const readListeners = new Set<(receipt: ReadReceipt) => void>();
const stateListeners = new Set<(state: string) => void>();
const reconnectListeners = new Set<() => void>();
const disposers = new Set<() => void>();

function setSocketState(state: string) {
  socketState = state;
  stateListeners.forEach(listener => listener(state));
}
function ensureSocket() {
  if (socket) return;
  const client = new Client({
    brokerURL: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`,
    reconnectDelay: 4000,
    connectionTimeout: 10000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    beforeConnect: async () => {
      const token = getToken();
      if (socket !== client) return;
      if (!token) { setSocketState('Sesiune expirată'); await client.deactivate(); return; }
      client.connectHeaders = { Authorization: `Bearer ${token}` };
    },
    onConnect: () => {
      if (socket !== client) return;
      setSocketState('În timp real');
      client.subscribe('/user/queue/messages', frame => {
        try { const message = JSON.parse(frame.body) as Message; messageListeners.forEach(listener => listener(message)); } catch { /* Invalid frames do not interrupt the chat. */ }
      });
      client.subscribe('/user/queue/read', frame => {
        try { const receipt = JSON.parse(frame.body) as ReadReceipt; readListeners.forEach(listener => listener(receipt)); } catch { /* Ignore malformed read receipts. */ }
      });
      reconnectListeners.forEach(listener => listener());
    },
    onWebSocketClose: () => { if (socket === client) setSocketState('Reconectare… Mesajele se actualizează periodic.'); },
    onStompError: () => { if (socket === client) setSocketState('Conexiune întreruptă. Mesajele se actualizează periodic.'); },
  });
  socket = client;
  client.activate();
}

/** Called by the router; sockets, polling and listeners never survive navigation. */
export function cleanupChat() {
  [...disposers].forEach(dispose => dispose());
  messageListeners.clear(); readListeners.clear(); stateListeners.clear(); reconnectListeners.clear();
  const previous = socket;
  socket = null;
  socketState = 'Conectare…';
  if (previous) { previous.onWebSocketClose = () => {}; void previous.deactivate(); }
}

function lifecycle(root: HTMLElement) {
  let alive = true;
  const localDisposers: (() => void)[] = [];
  const dispose = () => {
    if (!alive) return;
    alive = false;
    localDisposers.forEach(fn => fn());
    disposers.delete(dispose);
  };
  const observer = new MutationObserver(() => { if (!root.isConnected) dispose(); });
  observer.observe(document.body, { childList: true, subtree: true });
  localDisposers.push(() => observer.disconnect());
  disposers.add(dispose);
  return { alive: () => alive && root.isConnected, add: (fn: () => void) => localDisposers.push(fn), dispose };
}
function dateTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleString('ro-RO', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
}
function failure(error: unknown) { return error instanceof Error ? error.message : 'Mesajele nu au putut fi încărcate.'; }
function initials(name: string) { return name.trim().split(/\s+/).slice(0, 2).map(part => part[0]).join('').toUpperCase(); }
function messageHtml(message: Message, me: number, group: boolean) {
  const own = message.senderId === me;
  const sender = 'senderDisplayName' in message ? message.senderDisplayName || `Membru #${message.senderId}` : '';
  return `<article class="chat-message ${own ? 'chat-message-own' : ''}" data-message="${message.id}">${group && !own ? `<strong class="chat-sender">${escape(sender)}</strong>` : ''}<p>${escape(message.content)}</p><span class="chat-message-time">${escape(dateTime(message.createdAt))}${!group && own ? ` · ${'readAt' in message && message.readAt ? 'Citit' : 'Trimis'}` : ''}</span></article>`;
}
function bindStatus(root: HTMLElement, life: ReturnType<typeof lifecycle>) {
  const element = root.querySelector<HTMLElement>('[data-socket-status]');
  const handler = (state: string) => { if (element) { element.textContent = state; element.dataset.online = state === 'În timp real' ? 'true' : 'false'; } };
  stateListeners.add(handler);
  life.add(() => stateListeners.delete(handler));
  handler(socketState);
  ensureSocket();
}

export async function renderChat(root: HTMLElement, partnerId: number | null, navigate: Navigate) {
  const me = currentUser();
  if (!me) { navigate('/login'); return; }
  const life = lifecycle(root);
  let conversations: Conversation[] = [];
  let messages: PrivateMessage[] = [];
  let page = 0;
  let hasOlder = false;
  let loadingOlder = false;
  let sending = false;
  let refreshing = false;
  let partnerName = partnerId ? `Utilizator #${partnerId}` : '';
  root.innerHTML = `<div class="chat-page"><div class="chat-page-heading"><div><p class="eyebrow">Comunicare</p><h1>Mesaje</h1><p class="workspace-muted">Discută direct cu un client sau un programator.</p></div><span class="chat-connection" data-socket-status role="status"></span></div><div class="chat-layout"><aside class="chat-conversations" aria-label="Conversații"><div class="chat-sidebar-title"><h2>Conversații</h2><button type="button" class="workspace-button subtle" data-chat-refresh aria-label="Actualizează conversațiile">↻</button></div><div data-conversations><p class="chat-empty">Se încarcă conversațiile…</p></div><a class="chat-find-people" href="/developers" data-link>Caută programatori →</a></aside><section class="chat-thread" aria-label="Mesaje"><div class="chat-thread-heading"><h2 data-partner-name>${partnerId ? escape(partnerName) : 'Alege o conversație'}</h2>${partnerId ? `<a href="/developers/${partnerId}" data-link class="workspace-link" data-partner-profile hidden>Vezi profilul →</a>` : ''}</div><div class="chat-history" data-chat-history tabindex="0" aria-label="Istoricul mesajelor"><div class="chat-older"><button type="button" class="workspace-button subtle" data-chat-older hidden>Mesaje mai vechi</button></div><div data-messages>${partnerId ? '<p class="chat-empty">Se încarcă mesajele…</p>' : '<div class="chat-welcome"><span aria-hidden="true">↗</span><h3>Începe o discuție</h3><p>Alege o conversație sau deschide profilul unui programator și apasă „Trimite mesaj”.</p></div>'}</div></div>${partnerId ? `<form class="chat-compose" data-chat-compose><label class="sr-only" for="private-message">Mesaj</label><textarea id="private-message" name="content" rows="2" maxlength="2000" placeholder="Scrie un mesaj…" required></textarea><div class="chat-compose-bottom"><span>Enter pentru trimitere · Shift + Enter pentru rând nou</span><button type="submit" class="workspace-button primary">Trimite →</button></div><p class="workspace-feedback" data-chat-error role="status"></p></form>` : ''}</section></div></div>`;
  bindStatus(root, life);
  if (partnerId) void api<{ displayName: string }>(`/api/developers/${partnerId}`).then(developer => {
    if (!life.alive()) return;
    partnerName = developer.displayName;
    root.querySelector<HTMLElement>('[data-partner-name]')!.textContent = partnerName;
    const profileLink = root.querySelector<HTMLAnchorElement>('[data-partner-profile]');
    if (profileLink) profileLink.hidden = false;
  }).catch(() => { /* Clients without developer profiles can still chat. */ });
  const history = root.querySelector<HTMLElement>('[data-chat-history]')!;
  const container = root.querySelector<HTMLElement>('[data-messages]')!;
  const older = root.querySelector<HTMLButtonElement>('[data-chat-older]')!;
  const error = root.querySelector<HTMLElement>('[data-chat-error]');
  const showMessages = (scrollToEnd = false) => {
    if (!life.alive() || !partnerId) return;
    const wasNearBottom = history.scrollHeight - history.scrollTop - history.clientHeight < 100;
    messages.sort((a, b) => a.id - b.id);
    container.innerHTML = messages.length ? messages.map(message => messageHtml(message, me.id, false)).join('') : '<p class="chat-empty">Niciun mesaj încă. Salută și spune cu ce proiect ai nevoie de ajutor.</p>';
    older.hidden = !hasOlder;
    if (scrollToEnd || wasNearBottom) history.scrollTop = history.scrollHeight;
  };
  const merge = (incoming: PrivateMessage[]) => {
    const byId = new Map(messages.map(message => [message.id, message]));
    incoming.forEach(message => byId.set(message.id, message));
    messages = [...byId.values()];
  };
  const showConversations = () => {
    if (!life.alive()) return;
    root.querySelector<HTMLElement>('[data-conversations]')!.innerHTML = conversations.length ? conversations.map(conversation => {
      const name = conversation.partnerDisplayName || `Utilizator #${conversation.partnerId}`;
      return `<button type="button" class="chat-conversation ${conversation.partnerId === partnerId ? 'selected' : ''}" data-partner="${conversation.partnerId}" ${conversation.partnerId === partnerId ? 'aria-current="true"' : ''}><span class="chat-avatar">${escape(initials(name))}</span><span class="chat-conversation-copy"><strong>${escape(name)}</strong><span>${escape(conversation.lastMessage.content)}</span></span>${conversation.unreadCount ? `<span class="chat-unread" aria-label="${conversation.unreadCount} mesaje necitite">${conversation.unreadCount > 99 ? '99+' : conversation.unreadCount}</span>` : ''}</button>`;
    }).join('') : '<p class="chat-empty">Nu ai conversații încă. Poți contacta programatorii de pe profilurile lor.</p>';
    root.querySelectorAll<HTMLButtonElement>('[data-partner]').forEach(button => button.onclick = () => navigate(`/chat/${button.dataset.partner}`));
    const active = conversations.find(conversation => conversation.partnerId === partnerId);
    if (active?.partnerDisplayName) partnerName = active.partnerDisplayName;
    root.querySelector<HTMLElement>('[data-partner-name]')!.textContent = partnerId ? partnerName : 'Alege o conversație';
  };
  const read = async () => {
    if (!partnerId || !life.alive() || document.hidden) return;
    try {
      await api(`/api/chats/${partnerId}/read`, 'POST');
      if (!life.alive()) return;
      const conversation = conversations.find(item => item.partnerId === partnerId);
      if (conversation) { conversation.unreadCount = 0; showConversations(); }
    } catch { /* Read receipts are retried on the next refresh. */ }
  };
  const refresh = async (initial = false) => {
    if (refreshing || !life.alive()) return;
    refreshing = true;
    try {
      const [list, latest] = await Promise.all([api<Conversation[]>('/api/chats'), partnerId ? api<Page<PrivateMessage>>(`/api/chats/${partnerId}/messages?size=50&sort=id,desc`) : Promise.resolve(null)]);
      if (!life.alive()) return;
      conversations = list;
      showConversations();
      if (latest) { merge(latest.content); if (page === 0) hasOlder = !latest.last; showMessages(initial); await read(); }
      if (error) error.textContent = '';
    } catch (cause) { if (life.alive()) { if (error) error.textContent = failure(cause); else root.querySelector<HTMLElement>('[data-conversations]')!.textContent = failure(cause); } }
    finally { refreshing = false; }
  };
  root.querySelector<HTMLButtonElement>('[data-chat-refresh]')!.onclick = () => void refresh();
  older.onclick = async () => {
    if (!partnerId || loadingOlder) return;
    loadingOlder = true; older.disabled = true;
    const before = history.scrollHeight;
    try {
      const previous = await api<Page<PrivateMessage>>(`/api/chats/${partnerId}/messages?page=${page + 1}&size=50&sort=id,desc`);
      if (!life.alive()) return;
      page++; hasOlder = !previous.last; merge(previous.content); showMessages(); history.scrollTop += history.scrollHeight - before;
    } catch (cause) { if (error && life.alive()) error.textContent = failure(cause); }
    finally { loadingOlder = false; older.disabled = false; }
  };
  const receive = (message: Message) => {
    if (!life.alive() || 'projectId' in message) return;
    const other = message.senderId === me.id ? message.recipientId : message.senderId;
    if (other === partnerId) { merge([message]); showMessages(message.senderId === me.id); void read(); }
    const existing = conversations.find(conversation => conversation.partnerId === other);
    if (existing) {
      existing.lastMessage = message;
      if (message.senderId !== me.id && !(other === partnerId && !document.hidden)) existing.unreadCount++;
      conversations = [existing, ...conversations.filter(conversation => conversation !== existing)];
      showConversations();
    } else void refresh();
  };
  const receipt = ({ readerId, readAt }: ReadReceipt) => {
    if (readerId !== partnerId || !life.alive()) return;
    messages.forEach(message => { if (message.senderId === me.id && !message.readAt) message.readAt = readAt; });
    showMessages();
  };
  messageListeners.add(receive); readListeners.add(receipt);
  life.add(() => { messageListeners.delete(receive); readListeners.delete(receipt); });
  const compose = root.querySelector<HTMLFormElement>('[data-chat-compose]');
  if (compose && partnerId) {
    const input = compose.querySelector<HTMLTextAreaElement>('textarea')!;
    const button = compose.querySelector<HTMLButtonElement>('button')!;
    compose.onsubmit = async event => {
      event.preventDefault();
      const content = input.value.trim();
      if (!content || sending) return;
      sending = true; button.disabled = true; input.disabled = true; if (error) error.textContent = '';
      try {
        const saved = await api<PrivateMessage>(`/api/chats/${partnerId}/messages`, 'POST', { content });
        if (!life.alive()) return;
        merge([saved]); showMessages(true); input.value = ''; await refresh();
      } catch (cause) { if (error && life.alive()) error.textContent = failure(cause); }
      finally { sending = false; button.disabled = false; input.disabled = false; if (life.alive()) input.focus(); }
    };
    input.onkeydown = event => { if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) { event.preventDefault(); compose.requestSubmit(); } };
  }
  const onVisible = () => { if (!document.hidden) { void refresh(); } };
  document.addEventListener('visibilitychange', onVisible);
  life.add(() => document.removeEventListener('visibilitychange', onVisible));
  const reconnect = () => void refresh();
  reconnectListeners.add(reconnect);
  life.add(() => reconnectListeners.delete(reconnect));
  const poll = window.setInterval(() => { if (!document.hidden && !socket?.connected) void refresh(); }, 8000);
  life.add(() => clearInterval(poll));
  await refresh(true);
}

export async function renderProjectChat(root: HTMLElement, projectId: number) {
  const me = currentUser();
  if (!me) return;
  const life = lifecycle(root);
  let messages: ProjectMessage[] = [];
  let page = 0;
  let hasOlder = false;
  let sending = false;
  let refreshing = false;
  root.innerHTML = `<div class="project-chat"><div class="workspace-section-heading"><div><h2>Chatul proiectului</h2></div><span class="chat-connection" data-socket-status role="status"></span></div><p class="workspace-muted">Vizibil clientului și membrilor care au acceptat invitația.</p><div class="chat-history" data-project-history tabindex="0" aria-label="Mesajele echipei"><div class="chat-older"><button type="button" class="workspace-button subtle" data-project-older hidden>Mesaje mai vechi</button></div><div data-project-messages><p class="chat-empty">Se încarcă discuția…</p></div></div><form class="chat-compose" data-project-compose><label class="sr-only" for="project-message-${projectId}">Mesaj pentru echipă</label><textarea id="project-message-${projectId}" name="content" maxlength="2000" rows="2" placeholder="Scrie echipei tale…" required></textarea><div class="chat-compose-bottom"><span>Enter pentru trimitere · Shift + Enter pentru rând nou</span><button type="submit" class="workspace-button primary">Trimite →</button></div><p class="workspace-feedback" data-project-error role="status"></p></form></div>`;
  bindStatus(root, life);
  const history = root.querySelector<HTMLElement>('[data-project-history]')!;
  const container = root.querySelector<HTMLElement>('[data-project-messages]')!;
  const older = root.querySelector<HTMLButtonElement>('[data-project-older]')!;
  const error = root.querySelector<HTMLElement>('[data-project-error]')!;
  const merge = (incoming: ProjectMessage[]) => {
    const byId = new Map(messages.map(message => [message.id, message]));
    incoming.forEach(message => byId.set(message.id, message));
    messages = [...byId.values()].sort((a, b) => a.id - b.id);
  };
  const show = (scrollToEnd = false) => {
    if (!life.alive()) return;
    const nearBottom = history.scrollHeight - history.scrollTop - history.clientHeight < 100;
    container.innerHTML = messages.length ? messages.map(message => messageHtml(message, me.id, true)).join('') : '<p class="chat-empty">Începe discuția: stabilește cine face frontendul, backendul și ce endpoint-uri aveți nevoie.</p>';
    older.hidden = !hasOlder;
    if (scrollToEnd || nearBottom) history.scrollTop = history.scrollHeight;
  };
  const refresh = async (initial = false) => {
    if (refreshing || !life.alive()) return;
    refreshing = true;
    try {
      const latest = await api<Page<ProjectMessage>>(`/api/projects/${projectId}/chat/messages?size=50&sort=id,desc`);
      if (!life.alive()) return;
      merge(latest.content); if (page === 0) hasOlder = !latest.last; show(initial); error.textContent = '';
    } catch (cause) { if (life.alive()) error.textContent = failure(cause); }
    finally { refreshing = false; }
  };
  older.onclick = async () => {
    older.disabled = true;
    const before = history.scrollHeight;
    try {
      const previous = await api<Page<ProjectMessage>>(`/api/projects/${projectId}/chat/messages?page=${page + 1}&size=50&sort=id,desc`);
      if (!life.alive()) return;
      page++; hasOlder = !previous.last; merge(previous.content); show(); history.scrollTop += history.scrollHeight - before;
    } catch (cause) { if (life.alive()) error.textContent = failure(cause); }
    finally { older.disabled = false; }
  };
  const receive = (message: Message) => { if ('projectId' in message && message.projectId === projectId && life.alive()) { merge([message as ProjectMessage]); show(message.senderId === me.id); } };
  messageListeners.add(receive);
  life.add(() => messageListeners.delete(receive));
  const form = root.querySelector<HTMLFormElement>('[data-project-compose]')!;
  const input = form.querySelector<HTMLTextAreaElement>('textarea')!;
  const button = form.querySelector<HTMLButtonElement>('button')!;
  form.onsubmit = async event => {
    event.preventDefault();
    const content = input.value.trim();
    if (!content || sending) return;
    sending = true; button.disabled = true; input.disabled = true; error.textContent = '';
    try {
      const message = await api<ProjectMessage>(`/api/projects/${projectId}/chat/messages`, 'POST', { content });
      if (!life.alive()) return;
      merge([message]); show(true); input.value = '';
    } catch (cause) { if (life.alive()) error.textContent = failure(cause); }
    finally { sending = false; button.disabled = false; input.disabled = false; if (life.alive()) input.focus(); }
  };
  input.onkeydown = event => { if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) { event.preventDefault(); form.requestSubmit(); } };
  const reconnect = () => void refresh();
  reconnectListeners.add(reconnect);
  life.add(() => reconnectListeners.delete(reconnect));
  const poll = window.setInterval(() => { if (!document.hidden && !socket?.connected) void refresh(); }, 8000);
  life.add(() => clearInterval(poll));
  const visible = () => { if (!document.hidden) void refresh(); };
  document.addEventListener('visibilitychange', visible);
  life.add(() => document.removeEventListener('visibilitychange', visible));
  await refresh(true);
}
