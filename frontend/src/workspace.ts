import { api } from './api';
import { currentUser } from './auth';
import { escape, roles, availability } from './ui';
import { renderProjectChat } from './chat';
import './workspace.css';

type Navigate = (path: string) => void;
type Project = { id: number; ownerId: number; title: string; description: string; summary: string | null; roles: string[]; tasks: string[]; requiredTechnologies: string[]; existingStack: string[]; maxHourlyRate: number | null; repositoryUrl: string | null; apiContract?: string | null; createdAt: string };
type Member = { userId: number; displayName: string | null; role: string | null; availability: string | null; assignedRole?: string | null; owner: boolean; joinedAt: string };
type Invitation = { id: number; projectId: number; projectTitle: string; inviterId: number; inviteeId: number; status: 'pending' | 'accepted' | 'declined'; createdAt: string; respondedAt: string | null };
type TaskStatus = 'todo' | 'in-progress' | 'done';
type Task = { id: number; projectId: number; title: string; description: string | null; status: TaskStatus; assigneeId: number | null; createdById: number; createdAt: string };
type Workspace = { project: Project; members: Member[]; tasks: Task[]; pendingInvitations: Invitation[] };
type TeamMember = { developerId: number; displayName: string; role: string; developerRole: string; technologies: string[]; availability: string | null; hourlyRate: number | null; existingMember: boolean; score: number; reasons: string[] };
type Team = { id: string; score: number; members: TeamMember[] };
type Proposals = { status: 'ready' | 'unavailable' | 'complete'; requiredRoles: string[]; missingRoles: string[]; teams: Team[]; message: string };
type Page<T> = { content: T[]; number: number; totalPages: number; totalElements: number; last: boolean };
const taskLabels: Record<TaskStatus, string> = { todo: 'De făcut', 'in-progress': 'În lucru', done: 'Finalizat' };
const invitationLabels = { pending: 'În așteptare', accepted: 'Acceptată', declined: 'Refuzată' };
const message = (error: unknown) => error instanceof Error ? error.message : 'Operația nu a putut fi finalizată.';
const label = (role: string | null) => role ? roles[role] || role : 'Programator';
const date = (value: string) => { const parsed = new Date(value); return Number.isNaN(parsed.getTime()) ? '' : parsed.toLocaleDateString('ro-RO', { day: 'numeric', month: 'long', year: 'numeric' }); };
const safeRepository = (value: string | null) => { try { const url = new URL(value || ''); return ['https:', 'http:'].includes(url.protocol) ? url.href : null; } catch { return null; } };
const tags = (values: string[], className = '') => values.map(value => `<span class="workspace-tag ${className}">${escape(value)}</span>`).join('');
const memberName = (member: Member) => member.displayName || (member.owner ? 'Clientul proiectului' : `Programator #${member.userId}`);
const nameOf = (members: Member[], id: number | null) => id ? memberName(members.find(member => member.userId === id) || { userId: id, displayName: null, role: null, availability: null, owner: false, joinedAt: '' }) : 'Neatribuit';

export async function renderWorkspace(root: HTMLElement, projectId: number | string, navigate: Navigate) {
  const id = Number(projectId);
  const me = currentUser();
  if (!me) { navigate('/login'); return; }
  root.innerHTML = '<div class="workspace-page"><p class="workspace-muted" role="status">Se încarcă workspace-ul…</p></div>';
  const loading = root.firstElementChild!;
  let workspace: Workspace;
  try { workspace = await api<Workspace>(`/api/projects/${id}/workspace`); }
  catch (error) {
    if (loading.isConnected) root.innerHTML = `<div class="workspace-page"><a href="/projects" data-link class="workspace-back">← Proiectele mele</a><h1>Workspace indisponibil</h1><p class="workspace-feedback" role="alert">${escape(message(error))}</p><button type="button" class="workspace-button" data-retry>Încearcă din nou</button></div>`;
    root.querySelector<HTMLButtonElement>('[data-retry]')?.addEventListener('click', () => void renderWorkspace(root, id, navigate));
    return;
  }
  if (!loading.isConnected) return;
  const { project, members, tasks, pendingInvitations } = workspace;
  const owner = project.ownerId === me.id || members.some(member => member.userId === me.id && member.owner);
  const developers = members.filter(member => !member.owner);
  const repo = safeRepository(project.repositoryUrl);
  const completed = tasks.filter(task => task.status === 'done').length;
  root.innerHTML = `<div class="workspace-page"><a href="/projects" data-link class="workspace-back">← Proiectele mele</a><header class="workspace-heading"><div><p class="eyebrow">${owner ? 'Proiectul tău' : 'Workspace-ul echipei'} · ${escape(date(project.createdAt))}</p><h1>${escape(project.title)}</h1><p class="workspace-summary">${escape(project.summary || project.description)}</p><div class="workspace-tags">${tags(project.roles.map(role => label(role)), 'role')}${tags(project.requiredTechnologies || [])}</div></div><button type="button" class="workspace-button" data-refresh>↻ Actualizează</button></header><div class="workspace-metrics"><div><strong>${project.roles.length}</strong><span>roluri necesare</span></div><div><strong>${developers.length}</strong><span>programatori confirmați</span></div><div><strong data-task-count>${completed}<small> / ${tasks.length}</small></strong><span>sarcini finalizate</span></div></div><div class="workspace-grid"><div class="workspace-main">${owner ? `<section class="workspace-section" aria-label="Echipe propuse"><div class="workspace-section-heading"><div><h2>Echipe propuse</h2></div></div><p class="workspace-muted">Un programator distinct pentru fiecare rol. Invitațiile trebuie acceptate înainte ca oamenii să intre în workspace.</p><div data-proposals><p class="workspace-muted" role="status">Căutăm combinații potrivite…</p></div></section>` : ''}<section class="workspace-section"><div class="workspace-section-heading"><div><h2>Sarcinile echipei</h2></div><button type="button" class="workspace-button" data-toggle-task>+ Sarcină nouă</button></div><p class="workspace-muted">Atribuie frontendul și backendul unor membri și urmărește progresul comun.</p><form data-new-task class="workspace-form new-task-form" hidden><h3>Adaugă o sarcină</h3><label>Titlu<input name="title" required maxlength="150" placeholder="De exemplu: endpoint pentru autentificare"></label><label>Descriere<textarea name="description" maxlength="2000" rows="3" placeholder="Ce trebuie livrat și cum verificăm că e gata?"></textarea></label><label>Responsabil<select name="assigneeId">${assigneeOptions(members, null)}</select></label><div class="workspace-form-actions"><button class="workspace-button primary" type="submit">Adaugă sarcina</button><button class="workspace-button subtle" type="button" data-cancel-task>Anulează</button></div><p class="workspace-feedback" role="status" data-task-create-status></p></form><div class="task-board" data-task-board>${(['todo', 'in-progress', 'done'] as TaskStatus[]).map(status => `<div class="task-column"><div class="task-column-heading"><h3>${taskLabels[status]}</h3><span>${tasks.filter(task => task.status === status).length}</span></div>${tasks.filter(task => task.status === status).map(task => taskCard(task, members, owner)).join('') || '<p class="task-column-empty">Nicio sarcină aici.</p>'}</div>`).join('')}</div><p class="workspace-feedback" role="status" data-task-status></p></section><section class="workspace-section"><div class="workspace-section-heading"><div><h2>Repository & contract API</h2></div>${repo ? `<a class="workspace-button" href="${escape(repo)}" target="_blank" rel="noopener noreferrer">Deschide repository ↗</a>` : ''}</div><p class="workspace-muted">Lucrați pe branch-uri separate. Agreați endpoint-urile și payload-urile înainte de integrare.</p>${owner ? `<form class="workspace-form" data-project-settings><label>Repository GitHub / GitLab<input type="url" name="repositoryUrl" maxlength="500" placeholder="https://github.com/echipa/proiect" value="${escape(project.repositoryUrl || '')}" pattern="https?://.*"></label><label>Contract API<textarea name="apiContract" rows="7" maxlength="10000" placeholder="POST /api/auth/login\nRequest: { email, password }\nResponse: { token }">${escape(project.apiContract || '')}</textarea></label><div class="workspace-form-actions"><button type="submit" class="workspace-button primary">Salvează</button><span class="workspace-feedback" data-settings-status role="status"></span></div></form>` : `<dl class="workspace-readonly"><dt>Repository</dt><dd>${repo ? `<a href="${escape(repo)}" target="_blank" rel="noopener noreferrer">${escape(repo)}</a>` : 'Clientul nu a adăugat încă un repository.'}</dd><dt>Contract API</dt><dd><pre class="workspace-contract">${escape(project.apiContract || 'Contractul API nu a fost definit încă. Stabiliți-l împreună în chat.')}</pre></dd></dl>`}</section><section class="workspace-section" data-project-chat></section></div><aside class="workspace-sidebar"><section class="workspace-section"><div class="workspace-section-heading"><h2>Echipa</h2><span class="workspace-counter">${members.length}</span></div><div class="workspace-member-list">${members.map(member => `<article class="workspace-member"><div class="member-avatar">${escape(memberName(member).split(/\s+/).slice(0, 2).map(part => part[0]).join('').toUpperCase())}</div><div><strong>${escape(memberName(member))}${member.userId === me.id ? ' <span class="workspace-you">(tu)</span>' : ''}</strong><span>${member.owner ? 'Client · Proprietar' : escape(label(member.assignedRole || member.role))}</span>${member.availability && !member.owner ? `<small>${escape(availability[member.availability] || member.availability)}</small>` : ''}${member.userId !== me.id ? `<button type="button" class="workspace-link" data-message-user="${member.userId}">Trimite mesaj →</button>` : ''}</div></article>`).join('')}</div></section>${owner ? `<section class="workspace-section"><div class="workspace-section-heading"><h2>Invitații trimise</h2><span class="workspace-counter" data-pending-count>${pendingInvitations.length}</span></div><div data-pending-body>${pendingInvitationsHtml(pendingInvitations)}</div></section>` : ''}<section class="workspace-section"><h2>Brief-ul proiectului</h2><p class="workspace-description">${escape(project.description)}</p>${project.maxHourlyRate != null ? `<p class="workspace-budget">Tarif maxim / persoană / oră: <strong>${escape(project.maxHourlyRate)}</strong></p>` : ''}${project.tasks?.length ? `<h3>Obiective inițiale</h3><ul class="workspace-objectives">${project.tasks.map(task => `<li>${escape(task)}</li>`).join('')}</ul>` : ''}</section></aside></div></div>`;
  const canvas = root.firstElementChild!;
  const alive = () => canvas.isConnected;
  const reload = () => { if (alive()) void renderWorkspace(root, id, navigate); };
  root.querySelector<HTMLButtonElement>('[data-refresh]')!.onclick = reload;
  root.querySelectorAll<HTMLButtonElement>('[data-pending-message]').forEach(button => button.onclick = () => navigate(`/chat/${button.dataset.pendingMessage}`));
  root.querySelectorAll<HTMLButtonElement>('[data-message-user]').forEach(button => button.onclick = () => navigate(`/chat/${button.dataset.messageUser}`));
  root.querySelectorAll<HTMLElement>('[data-invitee-name]').forEach(async element => {
    try { const developer = await api<{ displayName: string }>(`/api/developers/${element.dataset.inviteeName}`); if (alive()) element.textContent = developer.displayName; } catch { /* A deleted profile does not hide an existing invitation. */ }
  });
  const createForm = root.querySelector<HTMLFormElement>('[data-new-task]')!;
  const toggle = root.querySelector<HTMLButtonElement>('[data-toggle-task]')!;
  toggle.onclick = () => { createForm.hidden = !createForm.hidden; if (!createForm.hidden) createForm.querySelector<HTMLInputElement>('input')!.focus(); };
  root.querySelector<HTMLButtonElement>('[data-cancel-task]')!.onclick = () => { createForm.hidden = true; };
  createForm.onsubmit = async event => {
    event.preventDefault();
    const data = new FormData(createForm);
    const submit = createForm.querySelector<HTMLButtonElement>('button[type="submit"]')!;
    const feedback = createForm.querySelector<HTMLElement>('[data-task-create-status]')!;
    submit.disabled = true; feedback.textContent = '';
    try {
      const created = await api<Task>(`/api/projects/${id}/tasks`, 'POST', { title: String(data.get('title')).trim(), description: String(data.get('description')).trim(), assigneeId: data.get('assigneeId') ? Number(data.get('assigneeId')) : null });
      if (alive()) { tasks.push(created); createForm.reset(); createForm.hidden = true; showTasks(); }
    } catch (error) { if (alive()) feedback.textContent = message(error); }
    finally { submit.disabled = false; }
  };
  const showTasks = () => {
    if (!alive()) return;
    root.querySelector<HTMLElement>('[data-task-board]')!.innerHTML = (['todo', 'in-progress', 'done'] as TaskStatus[]).map(status => `<div class="task-column"><div class="task-column-heading"><h3>${taskLabels[status]}</h3><span>${tasks.filter(task => task.status === status).length}</span></div>${tasks.filter(task => task.status === status).map(task => taskCard(task, members, owner)).join('') || '<p class="task-column-empty">Nicio sarcină aici.</p>'}</div>`).join('');
    root.querySelector<HTMLElement>('[data-task-count]')!.innerHTML = `${tasks.filter(task => task.status === 'done').length}<small> / ${tasks.length}</small>`;
    bindTasks();
  };
  const bindTasks = () => {
    root.querySelectorAll<HTMLSelectElement>('[data-task-field]').forEach(select => select.onchange = async () => {
      const field = select.dataset.taskField!;
      const previous = select.dataset.previous!;
      const body = field === 'assigneeId' ? (select.value ? { assigneeId: Number(select.value) } : { clearAssignee: true }) : { status: select.value };
      // Disable both controls on this card while its update is in flight.
      const controls = select.closest('.workspace-task')!.querySelectorAll<HTMLSelectElement>('select');
      controls.forEach(control => control.disabled = true);
      const feedback = root.querySelector<HTMLElement>('[data-task-status]')!;
      feedback.textContent = '';
      try {
        const updated = await api<Task>(`/api/projects/${id}/tasks/${select.dataset.taskId}`, 'PATCH', body);
        if (alive()) { const index = tasks.findIndex(task => task.id === updated.id); if (index >= 0) tasks.splice(index, 1, updated); showTasks(); }
      } catch (error) { if (alive()) { feedback.textContent = message(error); select.value = previous; } }
      finally { controls.forEach(control => control.disabled = false); }
    });
    root.querySelectorAll<HTMLButtonElement>('[data-delete-task]').forEach(button => button.onclick = async () => {
      const task = tasks.find(item => item.id === Number(button.dataset.deleteTask));
      if (!window.confirm(`Ștergi sarcina „${task?.title || ''}”?`)) return;
      button.disabled = true;
      try {
        await api(`/api/projects/${id}/tasks/${button.dataset.deleteTask}`, 'DELETE');
        if (alive()) { const index = tasks.findIndex(task => task.id === Number(button.dataset.deleteTask)); if (index >= 0) tasks.splice(index, 1); showTasks(); }
      } catch (error) { if (alive()) root.querySelector<HTMLElement>('[data-task-status]')!.textContent = message(error); }
      finally { button.disabled = false; }
    });
  };
  bindTasks();
  const settings = root.querySelector<HTMLFormElement>('[data-project-settings]');
  if (settings) settings.onsubmit = async event => {
    event.preventDefault();
    const data = new FormData(settings);
    const submit = settings.querySelector<HTMLButtonElement>('button')!;
    const feedback = settings.querySelector<HTMLElement>('[data-settings-status]')!;
    submit.disabled = true; feedback.textContent = '';
    try {
      await api(`/api/projects/${id}`, 'PATCH', { repositoryUrl: String(data.get('repositoryUrl')).trim(), apiContract: String(data.get('apiContract')).trim() });
      if (alive()) { feedback.textContent = 'Repository-ul și contractul API au fost salvate.'; feedback.dataset.success = 'true'; }
    } catch (error) { if (alive()) { feedback.textContent = message(error); feedback.dataset.success = 'false'; } }
    finally { submit.disabled = false; }
  };
  if (owner) void loadProposals(root, id, alive, navigate);
  void renderProjectChat(root.querySelector<HTMLElement>('[data-project-chat]')!, id);
}

function pendingInvitationsHtml(invitations: Invitation[], names = new Map<number, string>()) {
  return invitations.length ? `<ul class="workspace-pending">${invitations.map(invitation => `<li><span data-invitee-name="${invitation.inviteeId}">${escape(names.get(invitation.inviteeId) || `Programator #${invitation.inviteeId}`)}</span><small>În așteptare</small><button class="workspace-link" type="button" data-pending-message="${invitation.inviteeId}">Trimite mesaj</button></li>`).join('')}</ul>` : '<p class="workspace-muted">Nicio invitație în așteptare.</p>';
}

function assigneeOptions(members: Member[], selected: number | null) {
  return `<option value="" ${selected == null ? 'selected' : ''}>Neatribuit</option>${members.map(member => `<option value="${member.userId}" ${member.userId === selected ? 'selected' : ''}>${escape(memberName(member))}${member.owner ? ' (client)' : ''}</option>`).join('')}`;
}
function taskCard(task: Task, members: Member[], owner: boolean) {
  return `<article class="workspace-task"><h4>${escape(task.title)}</h4>${task.description ? `<p>${escape(task.description)}</p>` : ''}<label>Responsabil<select aria-label="Responsabil pentru ${escape(task.title)}" data-task-field="assigneeId" data-task-id="${task.id}" data-previous="${task.assigneeId || ''}">${assigneeOptions(members, task.assigneeId)}</select></label><label>Status<select aria-label="Status pentru ${escape(task.title)}" data-task-field="status" data-task-id="${task.id}" data-previous="${task.status}">${Object.entries(taskLabels).map(([status, title]) => `<option value="${status}" ${task.status === status ? 'selected' : ''}>${title}</option>`).join('')}</select></label>${owner ? `<button type="button" class="task-delete" data-delete-task="${task.id}" aria-label="Șterge sarcina ${escape(task.title)}">Șterge</button>` : ''}</article>`;
}

async function loadProposals(root: HTMLElement, projectId: number, alive: () => boolean, navigate: Navigate) {
  const element = root.querySelector<HTMLElement>('[data-proposals]')!;
  const retry = () => void loadProposals(root, projectId, alive, navigate);
  try {
    const proposals = await api<Proposals>(`/api/projects/${projectId}/team-proposals`);
    if (!alive()) return;
    if (proposals.status === 'complete') {
      element.innerHTML = `<div class="workspace-notice success"><strong>Echipa este completă</strong><p>${escape(proposals.message)}</p></div>`;
      return;
    }
    if (proposals.status === 'unavailable' || !proposals.teams.length) {
      element.innerHTML = `<div class="workspace-notice"><strong>Nu avem încă o echipă completă</strong><p>${escape(proposals.message)}</p>${proposals.missingRoles.length ? `<div class="workspace-tags">${tags(proposals.missingRoles.map(role => label(role)), 'missing')}</div>` : ''}<p>Completează profilurile programatorilor sau ajustează cerințele proiectului.</p><button type="button" class="workspace-button" data-retry-proposals>Caută din nou</button></div>`;
      element.querySelector<HTMLButtonElement>('[data-retry-proposals]')!.onclick = retry;
      return;
    }
    element.innerHTML = `<div class="team-proposals">${proposals.teams.map((team, index) => `<article class="team-package"><div class="team-package-heading"><h3>${index === 0 ? 'Echipa recomandată' : `Alternativa ${index}`}</h3><span class="team-package-count">${team.members.length} roluri acoperite</span></div><div class="team-package-members">${team.members.map(member => `<div class="team-package-member"><span class="team-assigned-role">${escape(label(member.role))}</span><a href="/developers/${member.developerId}" data-link class="team-person-name">${escape(member.displayName)}</a>${member.developerRole !== member.role ? `<small>Profil ${escape(label(member.developerRole))}</small>` : ''}<span class="team-member-availability">${member.existingMember ? '✓ Deja în echipă' : escape(availability[member.availability || ''] || 'Disponibilitate nespecificată')}</span><div class="workspace-tags">${tags(member.technologies.slice(0, 4))}</div><span class="team-rate">${member.hourlyRate == null ? 'Tarif nespecificat' : `${escape(member.hourlyRate)} / oră`}</span>${member.reasons?.length ? `<ul class="team-reasons">${member.reasons.slice(0, 3).map(reason => `<li>${escape(reason)}</li>`).join('')}</ul>` : ''}</div>`).join('')}</div><div class="team-package-bottom"><span>Fiecare programator confirmă separat.</span><button type="button" class="workspace-button primary" data-invite-team="${index}">Invită echipa</button></div><p class="workspace-feedback" data-team-feedback="${index}" role="status"></p></article>`).join('')}</div>`;
    let inFlight = false;
    element.querySelectorAll<HTMLButtonElement>('[data-invite-team]').forEach(button => button.onclick = async () => {
      if (inFlight) return;
      inFlight = true;
      const index = Number(button.dataset.inviteTeam);
      const feedback = element.querySelector<HTMLElement>(`[data-team-feedback="${index}"]`)!;
      element.querySelectorAll<HTMLButtonElement>('[data-invite-team]').forEach(item => item.disabled = true);
      feedback.textContent = '';
      try {
        const result = await api<{ invitations: Invitation[]; alreadyMembers: number }>(`/api/projects/${projectId}/team-invitations`, 'POST', { assignments: proposals.teams[index].members.map(member => ({ role: member.role, developerId: member.developerId })) });
        if (!alive()) return;
        const pending = result.invitations.filter(invitation => invitation.status === 'pending').length;
        feedback.textContent = pending ? `${pending} invitații sunt în așteptare. Membrii vor apărea după acceptare.` : 'Toți programatorii sunt deja membri ai proiectului.';
        feedback.dataset.success = 'true';
        button.textContent = 'Invitații trimise ✓';
        // Refresh the sidebar immediately while keeping the proposal acknowledgement visible.
        const workspace = await api<Workspace>(`/api/projects/${projectId}/workspace`);
        if (!alive()) return;
        const names = new Map(proposals.teams[index].members.map(member => [member.developerId, member.displayName]));
        root.querySelector<HTMLElement>('[data-pending-body]')!.innerHTML = pendingInvitationsHtml(workspace.pendingInvitations, names);
        root.querySelector<HTMLElement>('[data-pending-count]')!.textContent = String(workspace.pendingInvitations.length);
        root.querySelectorAll<HTMLButtonElement>('[data-pending-message]').forEach(item => item.onclick = () => navigate(`/chat/${item.dataset.pendingMessage}`));
      } catch (error) {
        if (alive()) { feedback.textContent = message(error); feedback.dataset.success = 'false'; element.querySelectorAll<HTMLButtonElement>('[data-invite-team]').forEach(item => item.disabled = false); }
      } finally { inFlight = false; }
    });
  } catch (error) {
    if (!alive()) return;
    element.innerHTML = `<div class="workspace-notice"><p>${escape(message(error))}</p><button type="button" class="workspace-button" data-retry-proposals>Încearcă din nou</button></div>`;
    element.querySelector<HTMLButtonElement>('[data-retry-proposals]')!.onclick = retry;
  }
}

export async function renderInvitations(root: HTMLElement, navigate: Navigate) {
  const me = currentUser();
  if (!me) { navigate('/login'); return; }
  root.innerHTML = `<div class="workspace-page invitations-page"><header class="workspace-heading"><div><p class="eyebrow">Lucrează cu o echipă</p><h1>Invitațiile mele</h1><p class="workspace-summary">Acceptă un proiect ca să intri în workspace-ul comun cu clientul și ceilalți programatori.</p></div></header><div class="invitation-filters" role="group" aria-label="Filtrează invitațiile"><button type="button" class="workspace-button selected" data-invitation-filter="pending">În așteptare</button><button type="button" class="workspace-button" data-invitation-filter="accepted">Acceptate</button><button type="button" class="workspace-button" data-invitation-filter="declined">Refuzate</button><button type="button" class="workspace-button" data-invitation-filter="">Toate</button></div><p class="workspace-feedback" data-invitation-status role="status"></p><div data-invitation-list></div><div class="invitation-pagination" data-invitation-pagination></div></div>`;
  const canvas = root.firstElementChild!;
  let filter = 'pending';
  let page = 0;
  let version = 0;
  const list = root.querySelector<HTMLElement>('[data-invitation-list]')!;
  const feedback = root.querySelector<HTMLElement>('[data-invitation-status]')!;
  const load = async () => {
    const request = ++version;
    list.innerHTML = '<p class="workspace-muted" role="status">Se încarcă invitațiile…</p>';
    root.querySelector<HTMLElement>('[data-invitation-pagination]')!.innerHTML = '';
    try {
      const result = await api<Page<Invitation>>(`/api/me/invitations?size=12&page=${page}&sort=id,desc${filter ? `&status=${filter}` : ''}`);
      if (!canvas.isConnected || request !== version) return;
      list.innerHTML = result.content.length ? `<div class="invitation-list">${result.content.map(invitation => `<article class="invitation-card"><div><span class="invitation-state ${invitation.status}">${invitationLabels[invitation.status] || invitation.status}</span><h2>${escape(invitation.projectTitle)}</h2><p class="workspace-muted">Invitație primită pe ${escape(date(invitation.createdAt))}</p></div><div class="invitation-actions">${invitation.status === 'pending' ? `<button type="button" class="workspace-button primary" data-invitation-action="accept" data-invitation-id="${invitation.id}">Acceptă proiectul</button><button type="button" class="workspace-button" data-invitation-action="decline" data-invitation-id="${invitation.id}">Refuză</button>` : invitation.status === 'accepted' ? `<button type="button" class="workspace-button primary" data-open-project="${invitation.projectId}">Deschide workspace →</button>` : ''}<button type="button" class="workspace-link" data-client-message="${invitation.inviterId}">Discută cu clientul →</button></div></article>`).join('')}</div>` : `<div class="workspace-empty"><h2>${filter === 'pending' ? 'Nicio invitație în așteptare' : 'Nicio invitație în această categorie'}</h2><p>Completează-ți profilul și disponibilitatea pentru a putea apărea în echipele propuse.</p><a href="/profile" data-link class="workspace-button">Completează profilul →</a></div>`;
      root.querySelectorAll<HTMLButtonElement>('[data-open-project]').forEach(button => button.onclick = () => navigate(`/projects/${button.dataset.openProject}`));
      root.querySelectorAll<HTMLButtonElement>('[data-client-message]').forEach(button => button.onclick = () => navigate(`/chat/${button.dataset.clientMessage}`));
      root.querySelectorAll<HTMLButtonElement>('[data-invitation-action]').forEach(button => button.onclick = async () => {
        const card = button.closest('.invitation-card')!;
        const buttons = card.querySelectorAll<HTMLButtonElement>('button');
        buttons.forEach(item => item.disabled = true);
        feedback.textContent = '';
        try {
          const updated = await api<Invitation>(`/api/invitations/${button.dataset.invitationId}`, 'PATCH', { action: button.dataset.invitationAction });
          if (!canvas.isConnected) return;
          if (updated.status === 'accepted') navigate(`/projects/${updated.projectId}`);
          else { feedback.textContent = 'Invitația a fost refuzată.'; feedback.dataset.success = 'true'; await load(); }
        } catch (error) { if (canvas.isConnected) { feedback.textContent = message(error); feedback.dataset.success = 'false'; buttons.forEach(item => item.disabled = false); } }
      });
      if (result.totalPages > 1) {
        const pagination = root.querySelector<HTMLElement>('[data-invitation-pagination]')!;
        pagination.innerHTML = `<button type="button" class="workspace-button" data-prev ${page === 0 ? 'disabled' : ''}>← Înapoi</button><span>Pagina ${page + 1} din ${result.totalPages}</span><button type="button" class="workspace-button" data-next ${result.last ? 'disabled' : ''}>Înainte →</button>`;
        pagination.querySelector<HTMLButtonElement>('[data-prev]')!.onclick = () => { page--; void load(); };
        pagination.querySelector<HTMLButtonElement>('[data-next]')!.onclick = () => { page++; void load(); };
      }
    } catch (error) { if (canvas.isConnected && request === version) list.innerHTML = `<p class="workspace-feedback" role="alert">${escape(message(error))}</p><button type="button" class="workspace-button" data-retry-invitations>Încearcă din nou</button>`; list.querySelector<HTMLButtonElement>('[data-retry-invitations]')?.addEventListener('click', () => void load()); }
  };
  root.querySelectorAll<HTMLButtonElement>('[data-invitation-filter]').forEach(button => button.onclick = () => {
    filter = button.dataset.invitationFilter || ''; page = 0;
    root.querySelectorAll<HTMLButtonElement>('[data-invitation-filter]').forEach(item => item.classList.toggle('selected', item === button));
    feedback.textContent = ''; void load();
  });
  await load();
}
