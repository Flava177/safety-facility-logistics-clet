'use strict';
// Mailroom / Courier and Dispatch Tracking console. Static SPA; the API is authoritative for every
// custody / variance / closure decision — this script only calls it and renders what comes back.

const $ = id => document.getElementById(id);
const el = (t, c, txt) => { const e = document.createElement(t); if (c) e.className = c; if (txt != null) e.textContent = txt; return e; };
const val = v => v && typeof v === 'object' && 'value' in v ? v.value : v;
const fmtDt = v => { if (!v) return '—'; const d = new Date(v); return isNaN(d) ? String(v) : d.toLocaleString(); };
const dash = v => (v == null || v === '') ? '—' : v;

const site = $('site'), rolesInput = $('roles');
const currentRoles = () => rolesInput.value.split(',').map(r => r.trim().toUpperCase()).filter(Boolean);
function headers(extra) {
  return { 'Content-Type': 'application/json', 'X-SFL-User': 'dispatch.controller', 'X-SFL-Display-Name': 'Dispatch Controller',
    'X-SFL-Roles': currentRoles().join(','), 'X-SFL-Sites': site.value, 'X-SFL-Source-Channel': 'WEB',
    'X-Correlation-ID': crypto.randomUUID(), 'Idempotency-Key': crypto.randomUUID(), ...(extra || {}) };
}

const message = $('message');
function msg(text, type) { message.textContent = text || ''; message.className = type || ''; }
function errText(e) {
  let t = e.message || 'Request failed';
  if (e.status >= 500) t = 'Server error: ' + t; else if (e.status === 403) t = 'Not permitted: ' + t;
  else if (e.status >= 400) t = 'Validation error: ' + t;
  if (e.code) t += ' [' + e.code + ']';
  if (e.correlationId) t += ' · correlation ' + e.correlationId;
  return t;
}
async function api(path, options = {}) {
  const r = await fetch(path, { ...options, headers: { ...headers(), ...(options.headers || {}) } });
  const b = await r.json().catch(() => ({}));
  if (!r.ok || b.error) { const e = new Error(b.error?.message || `Request failed (${r.status})`); e.code = b.error?.code; e.correlationId = b.error?.correlationId; e.status = r.status; throw e; }
  return b.data;
}

function activate(id) {
  document.querySelectorAll('.view,nav button').forEach(e => e.classList.remove('active'));
  $(id).classList.add('active');
  document.querySelector(`nav button[data-view="${id}"]`)?.classList.add('active');
  window.scrollTo(0, 0);
}
function show(id) { activate(id); load(id); }
document.querySelectorAll('nav button').forEach(b => b.onclick = () => show(b.dataset.view));
document.querySelectorAll('[data-jump]').forEach(b => b.onclick = () => show(b.dataset.jump));
$('refresh').onclick = () => load(document.querySelector('.view.active').id);

// ---- health ------------------------------------------------------------------------------------
async function health() {
  try { const r = await fetch('/actuator/health'); const b = await r.json(); $('health').textContent = 'Service ' + (b.status || 'UP'); }
  catch { $('health').textContent = 'Service unreachable'; }
}

// ---- loaders -----------------------------------------------------------------------------------
function load(view) {
  const s = encodeURIComponent(site.value);
  if (view === 'overview') return loadOverview(s);
  if (view === 'items') return loadItems(s);
  if (view === 'inbound') return loadInbound(s);
  if (view === 'manifests') return loadManifests(s);
  if (view === 'exceptions') return loadExceptions(s);
  if (view === 'integrations') return loadIntegrations();
}

async function loadOverview(s) {
  try {
    const d = await api(`/api/v1/dispatch/dashboard?siteCode=${s}`);
    $('c-transit').textContent = d.inTransitCount ?? 0; $('c-exc').textContent = d.openExceptionCount ?? 0;
    $('c-gap').textContent = d.custodyGapCount ?? 0; $('c-var').textContent = d.receiptVarianceCount ?? 0;
    $('c-out').textContent = d.outstandingReturnCount ?? 0; $('c-und').textContent = d.undeliveredCount ?? 0;
    $('c-ovr').textContent = d.overdueReceiptCount ?? 0; $('c-sla').textContent = d.slaBreachCount ?? 0;
    $('freshness').textContent = d.stale ? '⚠ Dashboard data is stale (older than the configured freshness threshold).'
      : 'Dashboard data is current. Source last updated ' + fmtDt(d.sourceUpdatedAt) + '.';
  } catch (e) { msg(errText(e), 'error'); }
}

function statusTag(status) {
  const bad = ['EXCEPTION', 'REJECTED', 'ESCALATED', 'CANCELLED']; const warn = ['DRAFT', 'HELD', 'AWAITING_EXPLANATION', 'DETECTED'];
  const cls = bad.includes(status) ? 'st-bad' : warn.includes(status) ? 'st-warn' : 'st-ok';
  const t = el('span', 'tag ' + cls, status); return t;
}
function listInto(box, rows, render, empty) {
  box.innerHTML = ''; box.classList.toggle('empty', rows.length === 0);
  if (!rows.length) { box.textContent = empty; return; }
  rows.forEach(r => box.appendChild(render(r)));
}

async function loadItems(s) {
  try {
    const rows = await api(`/api/v1/dispatch/items?siteCode=${s}&size=100`);
    listInto($('item-list'), rows, i => {
      const a = el('article'); const left = el('div');
      left.appendChild(el('strong', null, i.itemNumber)); left.appendChild(document.createElement('br'));
      left.appendChild(el('small', null, `${i.direction} · ${i.itemType} · ${val(i.sensitivity) || i.sensitivity}` + (i.chainOfCustodyRequired ? ' · custody' : '')));
      a.appendChild(left); a.appendChild(statusTag(i.status)); return a;
    }, 'No items loaded.');
  } catch (e) { msg(errText(e), 'error'); }
}

$('item-form').onsubmit = async ev => {
  ev.preventDefault(); const f = new FormData(ev.target); const body = Object.fromEntries(f.entries());
  body.siteCode = site.value; Object.keys(body).forEach(k => { if (body[k] === '') delete body[k]; });
  try { await api('/api/v1/dispatch/items', { method: 'POST', body: JSON.stringify(body) }); msg('Item registered.', 'ok'); ev.target.reset(); loadItems(encodeURIComponent(site.value)); }
  catch (e) { msg(errText(e), 'error'); }
};

let selectedInbound = null;
async function loadInbound(s) {
  try {
    const rows = await api(`/api/v1/dispatch/inbound?siteCode=${s}&size=100`);
    listInto($('inbound-list'), rows, i => {
      const a = el('article', 'link'); const left = el('div');
      left.appendChild(el('strong', null, i.itemNumber)); left.appendChild(document.createElement('br'));
      left.appendChild(el('small', null, `${i.itemType} · from ${dash(i.sender)} → ${dash(i.recipient)}`));
      a.appendChild(left); a.appendChild(statusTag(i.status));
      a.onclick = () => { selectedInbound = i.id; $('distribute').hidden = false; msg('Selected inbound item ' + i.itemNumber, 'ok'); };
      return a;
    }, 'No inbound items loaded.');
  } catch (e) { msg(errText(e), 'error'); }
}
$('inbound-form').onsubmit = async ev => {
  ev.preventDefault(); const body = Object.fromEntries(new FormData(ev.target).entries());
  body.siteCode = site.value; Object.keys(body).forEach(k => { if (body[k] === '') delete body[k]; });
  try { await api('/api/v1/dispatch/inbound', { method: 'POST', body: JSON.stringify(body) }); msg('Inbound item registered.', 'ok'); ev.target.reset(); loadInbound(encodeURIComponent(site.value)); }
  catch (e) { msg(errText(e), 'error'); }
};
$('distribute-btn').onclick = async () => {
  if (!selectedInbound) return msg('Select an inbound item first.', 'error');
  const body = { acknowledgedBy: $('ack-by').value, distributionReference: $('ack-ref').value, signatureStorageReference: $('ack-sig').value };
  try { await api(`/api/v1/dispatch/inbound/${selectedInbound}/distribute`, { method: 'POST', body: JSON.stringify(body) }); msg('Distribution acknowledged; item closed.', 'ok'); $('distribute').hidden = true; loadInbound(encodeURIComponent(site.value)); }
  catch (e) { msg(errText(e), 'error'); }
};

async function loadManifests(s) {
  try {
    const rows = await api(`/api/v1/dispatch/manifests?siteCode=${s}&size=100`);
    listInto($('manifest-list'), rows, m => {
      const a = el('article', 'link'); const left = el('div');
      left.appendChild(el('strong', null, m.manifestNumber)); left.appendChild(document.createElement('br'));
      left.appendChild(el('small', null, `${m.route} · ${m.itemCount} item(s)`));
      a.appendChild(left); a.appendChild(statusTag(m.status)); a.onclick = () => openManifest(m.id); return a;
    }, 'No manifests loaded.');
  } catch (e) { msg(errText(e), 'error'); }
}
$('manifest-form').onsubmit = async ev => {
  ev.preventDefault(); const body = Object.fromEntries(new FormData(ev.target).entries());
  body.siteCode = site.value; Object.keys(body).forEach(k => { if (body[k] === '') delete body[k]; });
  try { const m = await api('/api/v1/dispatch/manifests', { method: 'POST', body: JSON.stringify(body) }); msg('Manifest created.', 'ok'); ev.target.reset(); openManifest(m.id); }
  catch (e) { msg(errText(e), 'error'); }
};

let currentManifest = null;
async function openManifest(id) {
  currentManifest = id; activate('manifest-detail');
  try {
    const m = await api(`/api/v1/dispatch/manifests/${id}`);
    $('m-title').textContent = 'Manifest ' + m.manifestNumber; $('m-corr').textContent = 'dispatch ' + m.id;
    $('m-status').replaceWith(Object.assign(statusTag(m.status), { id: 'm-status' }));
    const dl = $('m-fields'); dl.innerHTML = '';
    [['Route', m.route], ['Handler', m.assignedHandler], ['Destination', dash(m.destinationCentre)], ['Exam context', dash(m.examinationContext)], ['Items', m.itemCount], ['Seals', (m.sealIds || []).join(', ') || '—'], ['Trip', dash(m.tripId)]]
      .forEach(([k, v]) => { dl.appendChild(el('dt', null, k)); dl.appendChild(el('dd', null, String(v))); });
    const items = await api(`/api/v1/dispatch/manifests/${id}/items`);
    listInto($('m-items'), items, it => { const a = el('article'); a.appendChild(el('small', null, `item ${it.courierItemId} · seal ${dash(it.expectedSealId)} · qty ${it.expectedQuantity}`)); a.appendChild(el('span', 'badge', it.returnStatus)); return a; }, 'No items.');
    const cust = await api(`/api/v1/dispatch/custody?dispatchId=${id}`);
    listInto($('m-custody'), cust, hn => { const a = el('article'); a.appendChild(el('small', null, `#${hn.sequenceNo} ${hn.hop}: ${hn.transferringCustodian} → ${hn.receivingCustodian}`)); a.appendChild(el('span', 'badge', val(hn.sealState) || hn.sealState)); return a; }, 'No handovers.');
    const gaps = await api(`/api/v1/dispatch/custody/${id}/gaps`);
    const gapBox = $('m-gaps'); gapBox.innerHTML = '';
    (gaps.gaps || []).concat((gaps.missingClosureHops || []).map(h => 'MISSING:' + h)).forEach(g => gapBox.appendChild(el('span', 'chip', g)));
    if (gaps.closable) gapBox.appendChild(el('span', 'chip', '✓ custody closable'));
    const receipts = await api(`/api/v1/dispatch/receipts?dispatchId=${id}`);
    listInto($('m-receipts'), receipts, r => { const a = el('article'); a.appendChild(el('small', null, `${dash(r.recipientName)} · count ${r.verifiedCount}/${r.expectedCount} · seal ${val(r.sealState) || r.sealState}`)); a.appendChild(el('span', 'badge', r.outcome)); return a; }, 'No receipts.');
    const returns = await api(`/api/v1/dispatch/returns?dispatchId=${id}`);
    listInto($('m-returns'), returns, r => { const a = el('article'); a.appendChild(el('small', null, `returned ${r.returnedCount}/${r.expectedCount} · short ${r.shortfall} · extra ${r.extras} · broken ${r.brokenSeals}`)); a.appendChild(el('span', 'badge', r.outcome)); return a; }, 'No reconciliations.');
    msg('', '');
  } catch (e) { msg(errText(e), 'error'); }
}
function reopenManifest() { if (currentManifest) openManifest(currentManifest); }
async function post(path, body, okMsg) { try { await api(path, { method: 'POST', body: JSON.stringify(body || {}) }); msg(okMsg, 'ok'); reopenManifest(); } catch (e) { msg(errText(e), 'error'); } }

$('m-add-btn').onclick = () => post(`/api/v1/dispatch/manifests/${currentManifest}/items`, { courierItemId: $('m-add-item').value, expectedSealId: $('m-add-seal').value, expectedQuantity: 1 }, 'Item added.');
$('m-seal-btn').onclick = () => post(`/api/v1/dispatch/manifests/${currentManifest}/seal`, { sealIds: $('m-seals').value.split(',').map(x => x.trim()).filter(Boolean) }, 'Manifest sealed.');
$('m-dispatch-btn').onclick = () => post(`/api/v1/dispatch/manifests/${currentManifest}/dispatch`, {}, 'Dispatched.');
$('m-intransit-btn').onclick = () => post(`/api/v1/dispatch/manifests/${currentManifest}/in-transit`, {}, 'Marked in transit.');
$('cu-btn').onclick = () => post('/api/v1/dispatch/custody', { dispatchId: currentManifest, hop: $('cu-hop').value, sealState: $('cu-seal').value, transferringCustodian: $('cu-from').value, receivingCustodian: $('cu-to').value, verifiedCount: $('cu-count').value ? Number($('cu-count').value) : null }, 'Handover recorded.');
$('r-btn').onclick = () => post('/api/v1/dispatch/receipts', { dispatchId: currentManifest, sealState: $('r-seal').value, sealVerified: $('r-seal-verified').checked, verifiedCount: Number($('r-count').value || 0), recipientName: $('r-recipient').value, signatureStorageReference: $('r-sig').value }, 'Receipt confirmed.');
$('rr-btn').onclick = () => post('/api/v1/dispatch/returns/reconcile', { dispatchId: currentManifest, returnedCount: Number($('rr-count').value || 0), brokenSeals: Number($('rr-broken').value || 0) }, 'Return reconciled.');
$('m-close-btn').onclick = () => post(`/api/v1/dispatch/manifests/${currentManifest}/close`, { reason: $('m-close-reason').value }, 'Dispatch closed.');

async function loadExceptions(s) {
  try {
    const rows = await api(`/api/v1/dispatch/exceptions?siteCode=${s}&size=100`);
    listInto($('exc-list'), rows, x => {
      const a = el('article', 'link'); const left = el('div');
      left.appendChild(el('strong', null, x.exceptionNumber)); left.appendChild(document.createElement('br'));
      left.appendChild(el('small', null, `${x.type} · ${x.severity}` + (x.securityRelevant ? ' · SSEMP' : '')));
      a.appendChild(left); a.appendChild(statusTag(x.status)); a.onclick = () => openException(x.id); return a;
    }, 'No exceptions loaded.');
  } catch (e) { msg(errText(e), 'error'); }
}
const ACTIONS = ['assign', 'reassign', 'review', 'request-explanation', 'explain', 'approve', 'reject', 'escalate', 'hold', 'resume', 'cancel', 'close', 'reopen'];
let currentException = null;
async function openException(id) {
  currentException = id; activate('exception-detail');
  try {
    const x = await api(`/api/v1/dispatch/exceptions/${id}`);
    $('e-title').textContent = 'Exception ' + x.exceptionNumber; $('e-corr').textContent = 'id ' + x.id;
    $('e-status').replaceWith(Object.assign(statusTag(x.status), { id: 'e-status' }));
    const dl = $('e-fields'); dl.innerHTML = '';
    [['Type', x.type], ['Severity', x.severity], ['Security relevant', x.securityRelevant], ['Assignee', dash(x.assignee)], ['SLA due', fmtDt(x.slaDueAt)], ['Dispatch', dash(x.dispatchId)], ['Item', dash(x.courierItemId)], ['Decision', dash(x.decision)], ['Escalation level', x.escalationLevel]]
      .forEach(([k, v]) => { dl.appendChild(el('dt', null, k)); dl.appendChild(el('dd', null, String(v))); });
    const rules = $('e-rules'); rules.innerHTML = ''; (x.detectedRules || []).forEach(r => rules.appendChild(el('span', 'chip', r)));
    const box = $('e-actions'); box.innerHTML = '';
    ACTIONS.forEach(act => { const b = el('button', act === 'reject' || act === 'cancel' ? 'danger' : act === 'escalate' ? 'warn' : 'secondary', act); b.onclick = () => exceptionAction(act); box.appendChild(b); });
    msg('', '');
  } catch (e) { msg(errText(e), 'error'); }
}
async function exceptionAction(action) {
  const body = { value: $('e-value').value || null, evidenceId: $('e-evidence').value || null };
  try { await api(`/api/v1/dispatch/exceptions/${currentException}/${action}`, { method: 'POST', body: JSON.stringify(body) }); msg('Applied ' + action + '.', 'ok'); openException(currentException); }
  catch (e) { msg(errText(e), 'error'); }
}

async function loadIntegrations() {
  try {
    const h = await api('/api/v1/dispatch/integrations/health');
    $('int-state').hidden = true; $('int-body').hidden = false;
    $('i-proc').textContent = h.inbox?.processedMessages ?? 0; $('i-rej').textContent = h.inbox?.rejectedMessages ?? 0;
    $('o-pub').textContent = h.outbox?.published ?? 0; $('o-dead').textContent = h.outbox?.deadLettered ?? 0;
  } catch (e) { $('int-state').hidden = false; $('int-state').textContent = errText(e); }
}
async function download(path, name) {
  try {
    const r = await fetch(path, { headers: headers() });
    if (!r.ok) throw new Error('Export failed (' + r.status + ')');
    const blob = await r.blob(); const url = URL.createObjectURL(blob); const a = el('a'); a.href = url; a.download = name; a.click(); URL.revokeObjectURL(url);
    $('rep-state').textContent = 'Downloaded ' + name;
  } catch (e) { $('rep-state').textContent = e.message; }
}
$('rep-items').onclick = () => download(`/api/v1/dispatch/reports/items.csv?siteCode=${encodeURIComponent(site.value)}`, 'dispatch-items.csv');
$('rep-exc').onclick = () => download(`/api/v1/dispatch/reports/exceptions.csv?siteCode=${encodeURIComponent(site.value)}`, 'dispatch-exceptions.csv');

health();
load('overview');
