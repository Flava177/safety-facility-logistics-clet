'use strict';
// Emergency Mass Notification console. Static SPA; the API is authoritative for authorization,
// break-glass eligibility, closure gating and every state decision — this only calls it.

const $ = id => document.getElementById(id);
const el = (t, c, txt) => { const e = document.createElement(t); if (c) e.className = c; if (txt != null) e.textContent = txt; return e; };
const val = v => v && typeof v === 'object' && 'value' in v ? v.value : v;
const dash = v => (v == null || v === '') ? '—' : v;
const csv = s => (s || '').split(',').map(x => x.trim()).filter(Boolean);

const site = $('site'), rolesInput = $('roles');
const roles = () => rolesInput.value.split(',').map(r => r.trim().toUpperCase()).filter(Boolean);
function headers(extra) {
  return { 'Content-Type': 'application/json', 'X-SFL-User': 'emergency.coordinator',
    'X-SFL-Display-Name': 'Emergency Coordinator', 'X-SFL-Roles': roles().join(','), 'X-SFL-Sites': site.value,
    'X-SFL-Source-Channel': 'WEB', 'X-Correlation-ID': crypto.randomUUID(), 'Idempotency-Key': crypto.randomUUID(),
    ...(extra || {}) };
}
const message = $('message');
function msg(t, type) { message.textContent = t || ''; message.className = type || ''; }
function errText(e) { let t = e.message || 'Request failed'; if (e.status >= 500) t = 'Server error: ' + t; else if (e.status === 403) t = 'Not permitted: ' + t; else if (e.status >= 400) t = 'Validation error: ' + t; if (e.code) t += ' [' + e.code + ']'; return t; }
async function api(path, options = {}) {
  const r = await fetch(path, { ...options, headers: { ...headers(), ...(options.headers || {}) } });
  const b = await r.json().catch(() => ({}));
  if (!r.ok || b.error) { const e = new Error(b.error?.message || `Request failed (${r.status})`); e.code = b.error?.code; e.status = r.status; throw e; }
  return b.data;
}
function activate(id) { document.querySelectorAll('.view,nav button').forEach(e => e.classList.remove('active')); $(id).classList.add('active'); document.querySelector(`nav button[data-view="${id}"]`)?.classList.add('active'); window.scrollTo(0, 0); }
function show(id) { activate(id); load(id); }
document.querySelectorAll('nav button').forEach(b => b.onclick = () => show(b.dataset.view));
document.querySelectorAll('[data-jump]').forEach(b => b.onclick = () => show(b.dataset.jump));
$('refresh').onclick = () => load(document.querySelector('.view.active').id);

async function health() { try { const r = await fetch('/actuator/health'); const b = await r.json(); $('health').textContent = 'Service ' + (b.status || 'UP'); } catch { $('health').textContent = 'Service unreachable'; } }

function load(view) {
  const s = encodeURIComponent(site.value);
  if (view === 'overview') return loadOverview(s);
  if (view === 'records') return loadRecords(s);
  if (view === 'activations') return loadActivations(s);
  if (view === 'drills') return loadDrills(s);
  if (view === 'integrations') return loadIntegrations();
}
function listInto(box, rows, render, empty) { box.innerHTML = ''; box.classList.toggle('empty', !rows.length); if (!rows.length) { box.textContent = empty; return; } rows.forEach(r => box.appendChild(render(r))); }
function statusTag(status) { const bad = ['ESCALATED', 'REJECTED', 'CANCELLED', 'FAILED', 'BREAK_GLASS_ACTIVE']; const warn = ['DRAFT', 'PENDING_APPROVAL', 'PARTIALLY_DELIVERED', 'ALL_CLEAR_PENDING']; return el('span', 'tag ' + (bad.includes(status) ? 'st-bad' : warn.includes(status) ? 'st-warn' : 'st-ok'), status); }

async function loadOverview(s) {
  try {
    const d = await api(`/api/v1/emergency/dashboard?siteCode=${s}`);
    $('c-active').textContent = d.activeActivationCount ?? 0; $('c-bg').textContent = d.breakGlassCount ?? 0;
    $('c-failed').textContent = d.failedRecipientCount ?? 0; $('c-ackp').textContent = d.ackPendingCount ?? 0;
    $('c-esc').textContent = d.escalatedCount ?? 0; $('c-acp').textContent = d.allClearPendingCount ?? 0;
    $('c-drill').textContent = d.drillCount ?? 0; $('c-fresh').textContent = d.stale ? 'STALE' : 'OK';
    $('freshness').textContent = d.stale ? '⚠ Dashboard data is stale (older than the configured freshness threshold).' : 'Dashboard data is current.';
  } catch (e) { msg(errText(e), 'error'); }
}

async function loadRecords(s) {
  try {
    listInto($('template-list'), await api(`/api/v1/emergency/templates?siteCode=${s}`), t => row(t.templateCode, `${(t.channels || []).join('/')}${t.breakGlassEligible ? ' · break-glass' : ''} · ${t.id}`, t.lifecycle), 'No templates loaded.');
    listInto($('scenario-list'), await api(`/api/v1/emergency/scenarios?siteCode=${s}`), t => row(t.scenarioCode, `${t.priority}${t.breakGlassEligible ? ' · break-glass' : ''} · ${t.id}`, t.lifecycle), 'No scenarios loaded.');
    listInto($('audience-list'), await api(`/api/v1/emergency/audience-groups?siteCode=${s}`), t => row(t.groupCode, `${t.recipientCount} recipients · ${t.id}`, t.lifecycle), 'No audiences loaded.');
    listInto($('zone-list'), await api(`/api/v1/emergency/recipient-zones?siteCode=${s}`), t => row(t.zoneCode, `${dash(t.locationReference)} · ${t.id}`, t.lifecycle), 'No zones loaded.');
  } catch (e) { msg(errText(e), 'error'); }
}
function row(title, sub, badge) { const a = el('article'); const left = el('div'); left.appendChild(el('strong', null, title)); left.appendChild(document.createElement('br')); left.appendChild(el('small', null, sub)); a.appendChild(left); a.appendChild(el('span', 'badge', badge)); return a; }

async function submitForm(form, path, body, okMsg, reloadView) {
  try { await api(path, { method: 'POST', body: JSON.stringify(body) }); msg(okMsg, 'ok'); if (form) form.reset(); load(reloadView); }
  catch (e) { msg(errText(e), 'error'); }
}
$('template-form').onsubmit = ev => { ev.preventDefault(); const f = new FormData(ev.target); submitForm(ev.target, '/api/v1/emergency/templates', { siteCode: site.value, templateCode: f.get('templateCode') || null, title: f.get('title'), body: f.get('body'), channels: csv(f.get('channels')), breakGlassEligible: f.get('breakGlassEligible') === 'on' }, 'Template created.', 'records'); };
$('scenario-form').onsubmit = ev => { ev.preventDefault(); const f = new FormData(ev.target); submitForm(ev.target, '/api/v1/emergency/scenarios', { siteCode: site.value, scenarioCode: f.get('scenarioCode') || null, name: f.get('name'), priority: f.get('priority'), defaultTemplateId: f.get('defaultTemplateId') || null, breakGlassEligible: f.get('breakGlassEligible') === 'on' }, 'Scenario created.', 'records'); };
$('audience-form').onsubmit = ev => { ev.preventDefault(); const f = new FormData(ev.target); submitForm(ev.target, '/api/v1/emergency/audience-groups', { siteCode: site.value, groupCode: f.get('groupCode') || null, name: f.get('name'), directoryReference: f.get('directoryReference') || null, recipientCount: Number(f.get('recipientCount') || 0) }, 'Audience created.', 'records'); };
$('zone-form').onsubmit = ev => { ev.preventDefault(); const f = new FormData(ev.target); submitForm(ev.target, '/api/v1/emergency/recipient-zones', { siteCode: site.value, zoneCode: f.get('zoneCode') || null, name: f.get('name'), locationReference: f.get('locationReference') || null }, 'Zone created.', 'records'); };

async function loadActivations(s) {
  try {
    const rows = await api(`/api/v1/emergency/activations?siteCode=${s}`);
    listInto($('activation-list'), rows, a => { const art = el('article', 'link'); const left = el('div'); left.appendChild(el('strong', null, a.activationNumber)); left.appendChild(document.createElement('br')); left.appendChild(el('small', null, `${a.mode} · ${(a.channels || []).join('/')}`)); art.appendChild(left); art.appendChild(statusTag(a.status)); art.onclick = () => openActivation(a.id); return art; }, 'No activations loaded.');
  } catch (e) { msg(errText(e), 'error'); }
}
function activationBody(f) { return { siteCode: site.value, scenarioId: f.get('scenarioId') || null, templateId: f.get('templateId') || null, audienceGroupIds: csv(f.get('audienceGroupIds')), recipientZoneIds: csv(f.get('recipientZoneIds')), channels: csv(f.get('channels')), priority: f.get('priority'), incidentReference: f.get('incidentReference') || null }; }
$('activation-form').onsubmit = async ev => { ev.preventDefault(); try { const a = await api('/api/v1/emergency/activations', { method: 'POST', body: JSON.stringify(activationBody(new FormData(ev.target))) }); msg('Draft created.', 'ok'); openActivation(a.id); } catch (e) { msg(errText(e), 'error'); } };
$('break-glass-btn').onclick = async () => { try { const a = await api('/api/v1/emergency/activations/break-glass', { method: 'POST', body: JSON.stringify(activationBody(new FormData($('activation-form')))) }); msg('Break-glass activation sent.', 'ok'); openActivation(a.id); } catch (e) { msg(errText(e), 'error'); } };

let currentActivation = null;
async function openActivation(id) {
  currentActivation = id; activate('activation-detail');
  try {
    const v = await api(`/api/v1/emergency/activations/${id}/status`);
    const a = v.activation;
    $('a-title').textContent = 'Activation ' + a.activationNumber; $('a-corr').textContent = 'id ' + a.id;
    $('a-status').replaceWith(Object.assign(statusTag(a.status), { id: 'a-status' }));
    const dl = $('a-fields'); dl.innerHTML = '';
    [['Mode', a.mode], ['Priority', a.priority], ['Channels', (a.channels || []).join(', ')], ['Approved by', dash(a.approvedBy)], ['After-action by', dash(a.afterActionApprovedBy)], ['Escalation level', a.escalationLevel], ['Fast-lane ms', dash(a.fastLaneMillis)], ['Acknowledgements', v.acknowledgements]].forEach(([k, val2]) => { dl.appendChild(el('dt', null, k)); dl.appendChild(el('dd', null, String(val2))); });
    listInto($('a-channels'), v.channels, c => row(c.channelType, `sent ${c.sentCount} · delivered ${c.deliveredCount} · failed ${c.failedCount} · ack ${c.acknowledgedCount}`, c.status), 'No channels.');
    const box = $('a-actions'); box.innerHTML = '';
    const act = (label, path, cls, bodyFn) => { const b = el('button', cls, label); b.onclick = () => actAction(path, bodyFn); box.appendChild(b); };
    act('Submit', 'submit', 'ghost', () => ({})); act('Approve', 'approve', 'secondary', () => ({})); act('Reject', 'reject', 'ghost', () => ({ reason: reason() }));
    act('Activate', 'activate', '', () => ({})); act('All-clear', 'all-clear', 'secondary', () => ({}));
    act('After-action approve', 'after-action-approval', 'secondary', () => ({ justification: reason() || 'Reviewed after action' }));
    act('Close', 'close', 'warn', () => ({ reason: reason() || 'Resolved', evidenceStorageReference: $('a-evidence').value || 'evidence://closure/' + id, retentionClass: 'INCIDENT_10_YEARS' }));
    msg('', '');
  } catch (e) { msg(errText(e), 'error'); }
}
function reason() { return $('a-reason').value; }
async function actAction(path, bodyFn) { try { await api(`/api/v1/emergency/activations/${currentActivation}/${path}`, { method: 'POST', body: JSON.stringify(bodyFn()) }); msg('Applied ' + path + '.', 'ok'); openActivation(currentActivation); } catch (e) { msg(errText(e), 'error'); } }

let currentDrill = null;
async function loadDrills(s) {
  try {
    const rows = await api(`/api/v1/emergency/drills?siteCode=${s}`);
    listInto($('drill-list'), rows, d => { const art = el('article', 'link'); const left = el('div'); left.appendChild(el('strong', null, d.drillNumber)); left.appendChild(document.createElement('br')); left.appendChild(el('small', null, `target ${d.targetRecipients} · ack ${d.acknowledgedRecipients}`)); art.appendChild(left); art.appendChild(el('span', 'badge', d.status)); art.onclick = () => { currentDrill = d.id; $('drill-complete').hidden = d.status !== 'RUNNING'; msg('Selected drill ' + d.drillNumber, 'ok'); }; return art; }, 'No drills loaded.');
  } catch (e) { msg(errText(e), 'error'); }
}
$('drill-form').onsubmit = ev => { ev.preventDefault(); const f = new FormData(ev.target); submitForm(ev.target, '/api/v1/emergency/drills', { siteCode: site.value, scenarioId: f.get('scenarioId') || null, targetRecipients: Number(f.get('targetRecipients') || 0), notes: f.get('notes') || null }, 'Drill started.', 'drills'); };
$('drill-complete-btn').onclick = async () => { if (!currentDrill) return msg('Select a running drill first.', 'error'); try { await api(`/api/v1/emergency/drills/${currentDrill}/complete`, { method: 'POST', body: JSON.stringify({ reachedRecipients: Number($('d-reached').value || 0), acknowledgedRecipients: Number($('d-ack').value || 0), activationMillis: Number($('d-millis').value || 0), notes: 'Drill completed via console' }) }); msg('Drill completed.', 'ok'); $('drill-complete').hidden = true; loadDrills(encodeURIComponent(site.value)); } catch (e) { msg(errText(e), 'error'); } };

async function loadIntegrations() {
  try { const health = await api('/api/v1/emergency/integrations/health'); $('int-state').hidden = true; $('int-body').hidden = false; $('o-pending').textContent = health.pending ?? 0; $('o-pub').textContent = health.published ?? 0; $('o-dead').textContent = health.deadLettered ?? 0; }
  catch (e) { $('int-state').hidden = false; $('int-state').textContent = errText(e); }
}
$('report-btn').onclick = async () => {
  try { const r = await fetch(`/api/v1/emergency/reports/activations.csv?siteCode=${encodeURIComponent(site.value)}`, { headers: headers() }); if (!r.ok) throw new Error('Export failed (' + r.status + ')'); const blob = await r.blob(); const url = URL.createObjectURL(blob); const a = el('a'); a.href = url; a.download = 'emergency-activations.csv'; a.click(); URL.revokeObjectURL(url); $('report-state').textContent = 'Downloaded emergency-activations.csv'; }
  catch (e) { $('report-state').textContent = e.message; }
};

health();
load('overview');
