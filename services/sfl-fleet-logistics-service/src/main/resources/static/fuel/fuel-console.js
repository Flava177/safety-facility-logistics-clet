'use strict';
// Fuel Management and Driver Logbooks console. Plain static SPA; the API is authoritative for
// every anomaly/reconciliation decision — this script only calls it and renders what comes back.

const $ = id => document.getElementById(id);
const el = (t, c, txt) => { const e = document.createElement(t); if (c) e.className = c; if (txt != null) e.textContent = txt; return e; };
const esc = v => { const d = document.createElement('div'); d.textContent = v == null ? '' : String(v); return d.innerHTML; };
const val = v => v && typeof v === 'object' && 'value' in v ? v.value : v;         // unwrap SiteCode { value }
const fmtDt = v => { if (!v) return '—'; const d = new Date(v); return isNaN(d) ? String(v) : d.toLocaleString(); };
const dash = v => (v == null || v === '') ? '—' : v;

// ---- actor context (dev X-SFL-* headers, matching the existing console) --------------------
const site = $('site'), rolesInput = $('roles');
const currentRoles = () => rolesInput.value.split(',').map(r => r.trim().toUpperCase()).filter(Boolean);
function headers(correlationId) {
  return { 'Content-Type': 'application/json', 'X-SFL-User': 'fuel.manager', 'X-SFL-Display-Name': 'Fuel Manager',
    'X-SFL-Roles': currentRoles().join(','), 'X-SFL-Sites': site.value, 'X-SFL-Source-Channel': 'WEB',
    'X-Correlation-ID': correlationId || crypto.randomUUID(), 'Idempotency-Key': crypto.randomUUID() };
}

// ---- role/permission mirror of FuelPermissionMatrix (UI hint only; server enforces) --------
const P = { TXN_READ:'FUEL_TRANSACTION_READ', TXN_CAPTURE:'FUEL_TRANSACTION_CAPTURE', TXN_IMPORT:'FUEL_TRANSACTION_IMPORT',
  TXN_VOID:'FUEL_TRANSACTION_VOID', POLICY_READ:'FUEL_POLICY_READ', POLICY_MANAGE:'FUEL_POLICY_MANAGE',
  LOG_READ:'FUEL_LOGBOOK_READ', LOG_CREATE:'FUEL_LOGBOOK_CREATE', LOG_SUBMIT:'FUEL_LOGBOOK_SUBMIT',
  LOG_REVIEW:'FUEL_LOGBOOK_REVIEW', LOG_REOPEN:'FUEL_LOGBOOK_REOPEN', RECON_RUN:'FUEL_RECONCILIATION_RUN',
  AN_READ:'FUEL_ANOMALY_READ', AN_MANAGE:'FUEL_ANOMALY_MANAGE', AN_APPROVE:'FUEL_ANOMALY_APPROVE',
  AN_ESCALATE:'FUEL_ANOMALY_ESCALATE', REPORT_READ:'FUEL_REPORT_READ', REPORT_EXPORT:'FUEL_REPORT_EXPORT',
  INT_INGEST:'FUEL_INTEGRATION_INGEST', INT_REPLAY:'FUEL_INTEGRATION_REPLAY' };
const ALL = Object.values(P);
const MATRIX = {
  SFL_ADMIN: ALL, FLEET_MANAGER: ALL,
  FLEET_LOGISTICS_OFFICER: [P.TXN_READ,P.TXN_CAPTURE,P.TXN_IMPORT,P.POLICY_READ,P.LOG_READ,P.LOG_CREATE,P.LOG_SUBMIT,P.RECON_RUN,P.AN_READ,P.AN_MANAGE,P.REPORT_READ],
  FLEET_DRIVER: [P.TXN_READ,P.LOG_READ,P.LOG_CREATE,P.LOG_SUBMIT],
  FLEET_REPORTING_VIEWER: [P.TXN_READ,P.LOG_READ,P.AN_READ,P.REPORT_READ],
  COMMAND_ROLE: [P.TXN_READ,P.AN_READ,P.REPORT_READ],
  AUDITOR: [P.TXN_READ,P.LOG_READ,P.AN_READ,P.REPORT_READ,P.REPORT_EXPORT],
  COMPLIANCE_OFFICER: [P.TXN_READ,P.LOG_READ,P.AN_READ,P.AN_APPROVE,P.REPORT_READ,P.REPORT_EXPORT],
  DTI_ADMIN: [P.TXN_READ,P.POLICY_READ,P.REPORT_READ,P.INT_REPLAY],
  INTEGRATION_ENGINEER: [P.TXN_IMPORT,P.INT_INGEST,P.INT_REPLAY],
  SERVICE_INTEGRATION: [P.INT_INGEST]
};
const can = perm => currentRoles().some(r => (MATRIX[r] || []).includes(perm));

// ---- messaging + fetch envelope handling ---------------------------------------------------
const message = $('message');
function msg(text, type) { message.textContent = text || ''; message.className = type || ''; }
function reportError(e) { msg(errText(e), 'error'); }
function errText(e) {
  let t = e.message || 'Request failed';
  if (e.status >= 500) t = 'Server error: ' + t;
  else if (e.status === 403) t = 'Not permitted: ' + t;
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
function panel(id, state, text) { // state: 'loading'|'empty'|'ready'
  const box = $(id); if (!box) return;
  if (state === 'ready') { box.hidden = true; return; }
  box.hidden = false; box.className = state === 'loading' ? 'empty' : 'empty'; box.textContent = text;
}

// ---- view navigation -----------------------------------------------------------------------
function activate(id) {
  document.querySelectorAll('.view,nav button').forEach(e => e.classList.remove('active'));
  $(id).classList.add('active');
  document.querySelector(`nav button[data-view="${id}"]`)?.classList.add('active');
  window.scrollTo(0, 0);
}
function show(id) { activate(id); load(id); }
document.querySelectorAll('nav button').forEach(b => b.onclick = () => show(b.dataset.view));
document.querySelectorAll('[data-jump]').forEach(b => b.onclick = () => show(b.dataset.jump));

// ---- list rendering ------------------------------------------------------------------------
function statusClass(s) {
  s = String(s || '');
  if (/RECONCILED|APPROVED|CLOSED|MATCHED|ACTIVE|PUBLISHED|ACCEPTED/.test(s)) return 'st-ok';
  if (/EXCEPTION|REJECTED|CANCELLED|VOIDED|DEAD|FAILED/.test(s)) return 'st-bad';
  if (/DETECTED|ESCALATED|AWAITING|HELD|RETURNED|PENDING|WARN|EXPLANATION/.test(s)) return 'st-warn';
  return 'st-neutral';
}
function renderList(id, items, title, meta, onOpen) {
  const root = $(id); root.innerHTML = ''; root.classList.toggle('empty', !items.length);
  if (!items.length) { root.textContent = 'No records found.'; return; }
  items.forEach(x => {
    const a = el('article', onOpen ? 'link' : '');
    const d = el('div'); d.innerHTML = `<strong>${esc(title(x))}</strong><br><small>${esc(meta(x))}</small>`;
    const s = x.status || 'ACTIVE';
    a.append(d, el('span', 'tag ' + statusClass(s), s));
    if (onOpen) { a.tabIndex = 0; a.onclick = () => onOpen(x); a.onkeydown = e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onOpen(x); } }; }
    root.appendChild(a);
  });
}
function defList(target, pairs) {
  const dl = $(target); dl.innerHTML = '';
  pairs.forEach(([k, v]) => { dl.appendChild(el('dt', '', k)); dl.appendChild(el('dd', '', v == null || v === '' ? '—' : String(v))); });
}

// ---- section loading -----------------------------------------------------------------------
async function load(view = 'overview') {
  const s = encodeURIComponent(site.value);
  try {
    if (view === 'overview') {
      msg('Loading…');
      const d = await api(`/api/v1/fuel/dashboard?siteCode=${s}`);
      $('spend').textContent = `GHS ${Number(d.fuelSpend || 0).toLocaleString()}`;
      $('volume').textContent = `${Number(d.fuelVolume || 0).toLocaleString()} L`;
      $('count').textContent = d.transactionCount || 0; $('exceptions').textContent = d.exceptionCount || 0;
      $('freshness').textContent = d.stale ? 'Warning: dashboard source data is stale.' : 'Dashboard data is within its freshness threshold.';
      msg('Updated', 'ok');
    } else if (view === 'transactions') {
      msg('Loading…');
      const tx = await api(`/api/v1/fuel/transactions?siteCode=${s}`);
      renderList('transaction-list', tx, x => `${x.fuelProduct} · ${x.quantity} ${x.quantityUnit}`, x => `${x.status} · ${x.vendorReference}`, x => openTransaction(x.id));
      msg('Updated', 'ok');
    } else if (view === 'logbooks') {
      msg('Loading…');
      const lb = await api(`/api/v1/fuel/logbooks?siteCode=${s}`);
      renderList('logbook-list', lb, x => `${x.logbookNumber} · ${x.origin} → ${x.destination}`, x => `${x.status} · ${x.journeyDate}`, x => openLogbook(x.id));
      msg('Updated', 'ok');
    } else if (view === 'anomalies') {
      msg('Loading…');
      const an = await api(`/api/v1/fuel/anomalies?siteCode=${s}`);
      renderList('anomaly-list', an, x => `${x.anomalyNumber} · ${x.type}`, x => `${x.status} · ${x.severity}${x.slaDueAt ? ' · SLA ' + fmtDt(x.slaDueAt) : ''}`, x => openAnomaly(x.id));
      msg('Updated', 'ok');
    } else if (view === 'policies') {
      msg('Loading…');
      const po = await api(`/api/v1/fuel/policies?siteCode=${s}`);
      renderList('policy-list', po, x => `${x.name} · version ${x.policyVersion}`, x => `${x.status} · max ${x.maxPerTransaction}`);
      $('policy-form').hidden = !can(P.POLICY_MANAGE);
      msg('Updated', 'ok');
    } else if (view === 'reports') {
      $('report-download').disabled = !can(P.REPORT_EXPORT);
      $('report-state').textContent = can(P.REPORT_EXPORT) ? '' : 'Your roles do not include FUEL_REPORT_EXPORT.';
      msg('');
    } else if (view === 'imports') {
      $('import-submit').disabled = !can(P.TXN_IMPORT);
      const sc = $('import-form').siteCode; if (!sc.value) sc.value = site.value;
      msg('');
    } else if (view === 'integrations') {
      loadIntegrations();
    }
  } catch (e) { reportError(e); }
}

// ---- transaction detail + reconciliation ---------------------------------------------------
async function openTransaction(id) {
  activate('transaction-detail');
  $('txn-body').hidden = true; panel('txn-state', 'loading', 'Loading transaction…'); msg('Loading…');
  try {
    const t = await api(`/api/v1/fuel/transactions/${id}`);
    renderTransaction(t);
    msg('Loaded', 'ok');
  } catch (e) { panel('txn-state', 'empty', errText(e)); reportError(e); }
}
function renderTransaction(t) {
  $('txn-state').hidden = true; $('txn-body').hidden = false;
  $('txn-title').textContent = `Transaction · ${val(t.siteCode)} · ${t.fuelProduct}`;
  $('txn-corr').textContent = t.metadata?.auditCorrelationId ? 'Audit correlation ID: ' + t.metadata.auditCorrelationId : '';
  const st = $('txn-status'); st.textContent = t.status; st.className = 'tag ' + statusClass(t.status);
  defList('txn-fields', [
    ['Transaction ID', t.id], ['Site', val(t.siteCode)], ['Status', t.status], ['Lifecycle', t.lifecycle],
    ['Source system', t.sourceSystem], ['Provider txn ID', dash(t.providerTransactionId)], ['Vehicle ID', t.vehicleId],
    ['Driver ID', t.driverId], ['Trip ID', dash(t.tripId)], ['Occurred at', fmtDt(t.occurredAt)],
    ['Ingested at', fmtDt(t.ingestionTimestamp)], ['Vendor', t.vendorReference], ['Station', dash(t.stationReference)],
    ['Quantity', `${t.quantity} ${t.quantityUnit}`], ['Unit price', t.unitPrice], ['Total cost', `${t.totalCost} ${t.currency}`],
    ['Masked card', dash(t.maskedCardReference)], ['Odometer', t.odometerReading], ['Comments', dash(t.comments)]
  ]);
  // receipt / evidence indicator (present/absent). Grace is policy-driven and enforced by the API.
  const present = !!t.receiptEvidenceId;
  const rc = $('txn-receipt'); rc.innerHTML = '';
  const chip = el('span', 'ind ' + (present ? 'present' : 'absent'), present ? 'Receipt present' : 'Receipt absent');
  rc.appendChild(chip);
  const note = el('p', 'note', present ? 'Evidence ID ' + t.receiptEvidenceId : 'No receipt evidence attached. A receipt-grace window may still apply per the effective policy; the API evaluates it during reconciliation.');
  rc.appendChild(note);
  // actions
  const acts = $('txn-actions'); acts.innerHTML = '';
  const reconcilable = ['RECEIVED', 'VALIDATING', 'MATCHED', 'EXCEPTION'].includes(t.status);
  if (can(P.RECON_RUN) && reconcilable) acts.appendChild(actionBtn('Reconcile', '', () => reconcile(t.id)));
  const voidable = t.status !== 'VOIDED' && t.lifecycle === 'ACTIVE';
  if (can(P.TXN_VOID) && voidable) {
    $('txn-void').hidden = false;
    acts.appendChild(actionBtn('Void transaction', 'danger', () => voidTxn(t.id)));
  } else { $('txn-void').hidden = true; }
  if (!acts.children.length) acts.appendChild(el('p', 'note', 'No actions available for your roles or this status.'));
  // reconciliation outcome — the reconcile call returns the authoritative status
  $('txn-recon').innerHTML = '';
  const outcome = t.status === 'RECONCILED' ? 'Last outcome: RECONCILED — matched the effective policy.'
    : t.status === 'EXCEPTION' ? 'Last outcome: EXCEPTION — one or more policy rules failed. See linked anomalies below.'
    : 'Not yet reconciled. Run reconciliation to evaluate against the effective policy.';
  $('txn-recon').appendChild(el('p', 'note', outcome));
  loadTxnAnomalies(t.id);
}
async function loadTxnAnomalies(txnId) {
  const box = $('txn-anomalies');
  try {
    const all = await api(`/api/v1/fuel/anomalies?siteCode=${encodeURIComponent(site.value)}`);
    const linked = all.filter(a => a.transactionId === txnId);
    box.innerHTML = ''; box.classList.toggle('empty', !linked.length);
    if (!linked.length) { box.textContent = 'No linked anomalies.'; return; }
    linked.forEach(a => {
      const art = el('article', 'link'); art.tabIndex = 0;
      const d = el('div'); d.innerHTML = `<strong>${esc(a.anomalyNumber + ' · ' + a.type)}</strong><br><small>${esc(a.status + ' · ' + a.severity)}</small>`;
      art.append(d, el('span', 'tag ' + statusClass(a.status), a.status));
      art.onclick = () => openAnomaly(a.id); art.onkeydown = e => { if (e.key === 'Enter') openAnomaly(a.id); };
      box.appendChild(art);
    });
  } catch (e) { box.classList.add('empty'); box.textContent = errText(e); }
}
async function reconcile(id) {
  msg('Reconciling…');
  try { const t = await api(`/api/v1/fuel/transactions/${id}/reconcile`, { method: 'POST' }); renderTransaction(t); msg('Reconciliation complete: ' + t.status, 'ok'); }
  catch (e) { reportError(e); }
}
async function voidTxn(id) {
  const reason = $('txn-void-reason').value.trim();
  if (!reason) { msg('Void reason is required', 'error'); return; }
  msg('Voiding…');
  try { const t = await api(`/api/v1/fuel/transactions/${id}/void`, { method: 'POST', body: JSON.stringify({ reason }) }); renderTransaction(t); msg('Transaction voided', 'ok'); }
  catch (e) { reportError(e); }
}
function actionBtn(label, cls, onClick) { const b = el('button', cls || '', label); b.type = 'button'; b.onclick = onClick; return b; }

// ---- logbook review detail -----------------------------------------------------------------
const LOG_TRANSITIONS = { // status -> [action, label, permission, cssClass]
  DRAFT: [['submit','Submit',P.LOG_SUBMIT,''],['cancel','Cancel',P.LOG_REVIEW,'danger']],
  SUBMITTED: [['review','Start review',P.LOG_REVIEW,''],['cancel','Cancel',P.LOG_REVIEW,'danger']],
  RESUBMITTED: [['review','Start review',P.LOG_REVIEW,'']],
  UNDER_REVIEW: [['approve','Approve',P.LOG_REVIEW,''],['return','Return for correction',P.LOG_REVIEW,'warn']],
  RETURNED: [['submit','Resubmit',P.LOG_SUBMIT,''],['cancel','Cancel',P.LOG_REVIEW,'danger']],
  REOPENED: [['submit','Resubmit',P.LOG_SUBMIT,'']],
  APPROVED: [['reopen','Reopen',P.LOG_REOPEN,'warn']]
};
async function openLogbook(id) {
  activate('logbook-detail'); $('log-body').hidden = true; panel('log-state', 'loading', 'Loading logbook…'); msg('Loading…');
  try { renderLogbook(await api(`/api/v1/fuel/logbooks/${id}`)); msg('Loaded', 'ok'); }
  catch (e) { panel('log-state', 'empty', errText(e)); reportError(e); }
}
function renderLogbook(l) {
  $('log-state').hidden = true; $('log-body').hidden = false;
  $('log-title').textContent = `Logbook · ${l.logbookNumber}`;
  $('log-corr').textContent = l.metadata?.auditCorrelationId ? 'Audit correlation ID: ' + l.metadata.auditCorrelationId : '';
  const st = $('log-status'); st.textContent = l.status; st.className = 'tag ' + statusClass(l.status);
  defList('log-fields', [
    ['Logbook ID', l.id], ['Site', val(l.siteCode)], ['Status', l.status], ['Driver ID', l.driverId], ['Vehicle ID', l.vehicleId],
    ['Trip ID', dash(l.tripId)], ['Journey date', l.journeyDate], ['Start', fmtDt(l.startTime)], ['End', fmtDt(l.endTime)],
    ['Origin → Destination', `${l.origin} → ${l.destination}`], ['Use', l.useClassification], ['Purpose', l.purpose],
    ['Start odometer', l.startOdometer], ['End odometer', dash(l.endOdometer)], ['Declaration accepted', l.declarationAccepted ? 'Yes' : 'No'],
    ['Review comment', dash(l.reviewComment)], ['Submitted at', fmtDt(l.submittedAt)], ['Approved at', fmtDt(l.approvedAt)]
  ]);
  const acts = $('log-actions'); acts.innerHTML = '';
  const options = (LOG_TRANSITIONS[l.status] || []).filter(([, , perm]) => can(perm));
  options.forEach(([action, label, , cls]) => acts.appendChild(actionBtn(label, cls, () => transitionLogbook(l.id, action))));
  const needsComment = options.some(([a]) => ['return', 'approve', 'reopen', 'cancel'].includes(a));
  $('log-comment-wrap').style.display = needsComment ? '' : 'none';
  if (!options.length) acts.appendChild(el('p', 'note', 'No review actions available for your roles or this status.'));
}
async function transitionLogbook(id, action) {
  const comment = $('log-comment').value.trim();
  if (['return', 'reopen', 'cancel'].includes(action) && !comment) { msg(`A comment/reason is required to ${action}`, 'error'); return; }
  msg('Working…');
  try { renderLogbook(await api(`/api/v1/fuel/logbooks/${id}/${action}`, { method: 'POST', body: JSON.stringify({ comment: comment || null }) })); $('log-comment').value = ''; msg('Logbook ' + action + ' done', 'ok'); }
  catch (e) { reportError(e); }
}

// ---- anomaly investigation detail ----------------------------------------------------------
const AN_TRANSITIONS = { // status -> actions valid from it (mirrors FuelAnomalyCase state guards)
  DETECTED: ['assign','cancel'],
  ASSIGNED: ['review','reassign','hold','escalate','cancel'],
  UNDER_REVIEW: ['request-explanation','approve','reject','escalate','reassign','hold','cancel'],
  AWAITING_EXPLANATION: ['explain','reassign','hold','escalate','cancel'],
  EXPLANATION_RECEIVED: ['review','reassign','hold','escalate','cancel'],
  APPROVED: ['close','escalate'],
  REJECTED: ['close','escalate'],
  ESCALATED: ['close'],
  HELD: ['resume','reassign'],
  CLOSED: ['reopen'],
  REOPENED: ['assign','cancel']
};
const AN_LABEL = { assign:'Assign', reassign:'Reassign', review:'Start review', 'request-explanation':'Request explanation',
  explain:'Record explanation', approve:'Approve', reject:'Reject', escalate:'Escalate', hold:'Hold', resume:'Resume',
  cancel:'Cancel', close:'Close', reopen:'Reopen' };
const AN_PERM = a => (['approve','reject','close'].includes(a)) ? P.AN_APPROVE : a === 'escalate' ? P.AN_ESCALATE : P.AN_MANAGE;
const AN_CLS = { approve:'', reject:'danger', cancel:'danger', escalate:'warn', hold:'warn', reopen:'warn' };
const AN_NEEDS_VALUE = new Set(['assign','reassign','approve','reject','escalate','hold','cancel','close','reopen','explain']);

async function openAnomaly(id) {
  activate('anomaly-detail'); $('an-body').hidden = true; panel('an-state', 'loading', 'Loading anomaly…'); msg('Loading…');
  try { renderAnomaly(await api(`/api/v1/fuel/anomalies/${id}`)); msg('Loaded', 'ok'); }
  catch (e) { panel('an-state', 'empty', errText(e)); reportError(e); }
}
function renderAnomaly(a) {
  $('an-state').hidden = true; $('an-body').hidden = false;
  $('an-title').textContent = `${a.anomalyNumber} · ${a.type}`;
  $('an-corr').textContent = a.metadata?.auditCorrelationId ? 'Audit correlation ID: ' + a.metadata.auditCorrelationId : '';
  const st = $('an-status'); st.textContent = a.status; st.className = 'tag ' + statusClass(a.status);
  defList('an-fields', [
    ['Anomaly ID', a.id], ['Number', a.anomalyNumber], ['Site', val(a.siteCode)], ['Type', a.type], ['Severity', a.severity],
    ['Material', a.material ? 'Yes' : 'No'], ['Status', a.status], ['Assignee', dash(a.assignee)], ['SLA due', fmtDt(a.slaDueAt)],
    ['Escalation level', a.escalationLevel], ['Decision', dash(a.decision)], ['Explanation', dash(a.explanation)],
    ['Evidence ID', dash(a.evidenceId)], ['Closure reason', dash(a.closureReason)], ['Transaction ID', dash(a.transactionId)],
    ['Logbook ID', dash(a.logbookId)], ['Vehicle ID', dash(a.vehicleId)], ['Driver ID', dash(a.driverId)], ['Trip ID', dash(a.tripId)]
  ]);
  const rules = $('an-rules'); rules.innerHTML = '';
  (a.detectedRules || []).forEach(r => rules.appendChild(el('span', 'chip', r)));
  if (!(a.detectedRules || []).length) rules.appendChild(el('span', 'note', 'No detected rules recorded.'));
  // manager actions
  const acts = $('an-actions'); acts.innerHTML = '';
  const valid = (AN_TRANSITIONS[a.status] || []).filter(act => can(AN_PERM(act)));
  valid.forEach(act => acts.appendChild(actionBtn(AN_LABEL[act], AN_CLS[act] || 'secondary', () => transitionAnomaly(a.id, act))));
  if (!valid.length) acts.appendChild(el('p', 'note', 'No actions available for your roles or this status.'));
  // source transaction link
  const txn = $('an-txn'); txn.innerHTML = '';
  if (a.transactionId) {
    txn.classList.remove('empty');
    const art = el('article', 'link'); art.tabIndex = 0;
    art.innerHTML = `<div><strong>Open source transaction</strong><br><small>${esc(a.transactionId)}</small></div>`;
    art.onclick = () => openTransaction(a.transactionId); art.onkeydown = e => { if (e.key === 'Enter') openTransaction(a.transactionId); };
    txn.appendChild(art);
  } else { txn.classList.add('empty'); txn.textContent = 'No linked transaction.'; }
}
async function transitionAnomaly(id, action) {
  const value = $('an-value').value.trim(), evidence = $('an-evidence').value.trim();
  if (AN_NEEDS_VALUE.has(action) && !value) { msg(`The "Value" field is required for ${AN_LABEL[action]}`, 'error'); return; }
  const body = { value: value || null, evidenceId: evidence || null };
  msg('Working…');
  try { renderAnomaly(await api(`/api/v1/fuel/anomalies/${id}/${action}`, { method: 'POST', body: JSON.stringify(body) })); $('an-value').value = ''; $('an-evidence').value = ''; msg('Anomaly ' + action + ' done', 'ok'); }
  catch (e) { reportError(e); }
}

// ---- provider import -----------------------------------------------------------------------
$('import-form').onsubmit = async e => {
  e.preventDefault();
  const f = e.target;
  const fd = new FormData();
  fd.append('siteCode', f.siteCode.value); fd.append('sourceSystem', f.sourceSystem.value);
  fd.append('file', f.file.files[0]);
  panel('import-state', 'loading', 'Importing…'); $('import-summary').hidden = true; msg('Importing…');
  const correlationId = crypto.randomUUID();
  try {
    const h = headers(correlationId); delete h['Content-Type']; // let the browser set the multipart boundary
    const r = await fetch(`/api/v1/fuel/imports/csv?siteCode=${encodeURIComponent(f.siteCode.value)}&sourceSystem=${encodeURIComponent(f.sourceSystem.value)}`, { method: 'POST', headers: h, body: fd });
    const b = await r.json().catch(() => ({}));
    if (!r.ok || b.error) { const err = new Error(b.error?.message || `Request failed (${r.status})`); err.code = b.error?.code; err.correlationId = b.error?.correlationId || correlationId; err.status = r.status; throw err; }
    renderImport(b.data, correlationId); msg('Import complete', 'ok');
  } catch (err) { panel('import-state', 'empty', errText(err)); reportError(err); }
};
function renderImport(res, correlationId) {
  $('import-state').hidden = true; $('import-summary').hidden = false;
  $('import-corr').textContent = `Batch ${res.batchId} · correlation ${correlationId}`;
  $('imp-total').textContent = res.totalRows; $('imp-accepted').textContent = res.acceptedRows; $('imp-rejected').textContent = res.rejectedRows;
  const tb = $('import-rows').querySelector('tbody'); tb.innerHTML = '';
  (res.rows || []).forEach(row => {
    const tr = el('tr');
    tr.appendChild(el('td', '', row.rowNumber));
    tr.appendChild(el('td', row.status === 'ACCEPTED' ? 'ok' : 'bad', row.status));
    const result = el('td');
    if (row.status === 'ACCEPTED' && row.transactionId) {
      const link = el('a', 'linkbtn', 'Open transaction'); link.href = '#'; link.onclick = ev => { ev.preventDefault(); openTransaction(row.transactionId); };
      result.appendChild(link);
    } else {
      result.textContent = (row.errorCode ? row.errorCode + ' — ' : '') + (row.errorMessage || '');
    }
    tr.appendChild(result); tb.appendChild(tr);
  });
}

// ---- reports -------------------------------------------------------------------------------
$('report-download').onclick = async () => {
  const s = site.value; const state = $('report-state'); state.textContent = 'Preparing CSV…'; msg('Preparing report…');
  try {
    const r = await fetch(`/api/v1/fuel/reports/transactions.csv?siteCode=${encodeURIComponent(s)}`, { headers: headers() });
    const ct = r.headers.get('content-type') || '';
    if (!r.ok || ct.includes('application/json')) {
      const b = await r.json().catch(() => ({})); const err = new Error(b.error?.message || `Request failed (${r.status})`);
      err.code = b.error?.code; err.correlationId = b.error?.correlationId; err.status = r.status; throw err;
    }
    const blob = await r.blob(); const url = URL.createObjectURL(blob);
    const link = el('a'); link.href = url; link.download = `fuel-transactions-${s}.csv`; document.body.appendChild(link); link.click(); link.remove(); URL.revokeObjectURL(url);
    state.textContent = 'Downloaded.'; msg('Report downloaded', 'ok');
  } catch (e) { state.textContent = errText(e); reportError(e); }
};

// ---- integration health (inbound inbox + outbound outbox) ----------------------------------
async function loadIntegrations() {
  const gated = can(P.INT_REPLAY);
  $('inbox-body').hidden = true; $('outbox-body').hidden = true;
  panel('inbox-state', 'loading', 'Loading inbox health…'); panel('outbox-state', 'loading', 'Loading outbox health…');
  msg('Loading…');
  // inbound
  try {
    const h = await api('/api/v1/fuel/integrations/health');
    $('inbox-state').hidden = true; $('inbox-body').hidden = false;
    $('inbox-processed').textContent = h.processedMessages ?? 0; $('inbox-rejected').textContent = h.rejectedMessages ?? 0; $('inbox-dead').textContent = h.deadLetterMessages ?? 0;
    $('inbox-checked').textContent = 'Checked at ' + fmtDt(h.checkedAt);
    const tb = $('inbox-rows').querySelector('tbody'); tb.innerHTML = '';
    (h.recentMessages || []).forEach(m => {
      const tr = el('tr');
      tr.appendChild(el('td', '', m.sourceSystem)); tr.appendChild(el('td', '', m.eventType));
      const stcell = el('td'); stcell.appendChild(el('span', 'tag ' + statusClass(m.status), m.status)); tr.appendChild(stcell);
      tr.appendChild(el('td', '', m.attempts)); tb.appendChild(tr);
    });
    if (!(h.recentMessages || []).length) tb.appendChild(rowSpan(4, 'No recent inbound messages.'));
  } catch (e) { panel('inbox-state', 'empty', errText(e)); }
  // outbound
  try {
    const o = await api('/api/v1/fuel/integrations/outbox/health');
    $('outbox-state').hidden = true; $('outbox-body').hidden = false;
    $('outbox-pending').textContent = o.pending ?? 0; $('outbox-published').textContent = o.published ?? 0; $('outbox-dead').textContent = o.deadLettered ?? 0;
    const tb = $('outbox-rows').querySelector('tbody'); tb.innerHTML = '';
    (o.recentDeadLetters || []).forEach(m => {
      const tr = el('tr');
      tr.appendChild(el('td', '', m.eventType)); tr.appendChild(el('td', '', `${m.aggregateType} ${m.aggregateId || ''}`));
      tr.appendChild(el('td', '', m.attemptCount)); tr.appendChild(el('td', '', m.failureReason || '—'));
      const act = el('td');
      if (gated) { act.appendChild(actionBtn('Replay', 'secondary', () => replay(m.id))); }
      else { act.appendChild(el('span', 'note', '—')); }
      tr.appendChild(act); tb.appendChild(tr);
    });
    if (!(o.recentDeadLetters || []).length) tb.appendChild(rowSpan(5, 'No dead-lettered messages.'));
    msg('Updated', 'ok');
  } catch (e) { panel('outbox-state', 'empty', errText(e)); reportError(e); }
}
function rowSpan(cols, text) { const tr = el('tr'); const td = el('td', 'note', text); td.colSpan = cols; tr.appendChild(td); return tr; }
async function replay(messageId) {
  msg('Replaying…');
  try { const r = await api(`/api/v1/fuel/integrations/outbox/${messageId}/replay`, { method: 'POST' }); msg(r.requeued ? 'Message requeued for delivery' : 'Message was not dead-lettered (no requeue)', r.requeued ? 'ok' : 'error'); loadIntegrations(); }
  catch (e) { reportError(e); }
}

// ---- create forms (existing + policy) ------------------------------------------------------
$('transaction-form').onsubmit = async e => {
  e.preventDefault(); const f = Object.fromEntries(new FormData(e.target));
  const body = { ...f, siteCode: site.value, sourceSystem: 'MANUAL', providerTransactionId: null,
    occurredAt: new Date(f.occurredAt).toISOString(), quantity: Number(f.quantity), unitPrice: Number(f.unitPrice),
    totalCost: null, odometerReading: Number(f.odometerReading), tripId: f.tripId || null,
    cardReference: f.cardReference || null, receiptEvidenceId: f.receiptEvidenceId || null, quantityUnit: 'LITRE' };
  try { await api('/api/v1/fuel/transactions', { method: 'POST', body: JSON.stringify(body) }); e.target.reset(); msg('Transaction captured', 'ok'); load('transactions'); }
  catch (x) { reportError(x); }
};
$('logbook-form').onsubmit = async e => {
  e.preventDefault(); const f = Object.fromEntries(new FormData(e.target));
  const body = { ...f, siteCode: site.value, startTime: new Date(f.startTime).toISOString(),
    endTime: f.endTime ? new Date(f.endTime).toISOString() : null, startOdometer: Number(f.startOdometer),
    endOdometer: f.endOdometer ? Number(f.endOdometer) : null, tripId: f.tripId || null,
    declarationAccepted: f.declarationAccepted === 'on', routeNotes: null, passengerLoadNotes: null, evidenceId: null };
  try { await api('/api/v1/fuel/logbooks', { method: 'POST', body: JSON.stringify(body) }); e.target.reset(); msg('Logbook draft saved', 'ok'); load('logbooks'); }
  catch (x) { reportError(x); }
};
$('policy-form').onsubmit = async e => {
  e.preventDefault(); const f = Object.fromEntries(new FormData(e.target));
  const set = v => v ? v.split(',').map(x => x.trim()).filter(Boolean) : [];
  const num = v => v === '' || v == null ? null : Number(v);
  const body = { siteCode: site.value, name: f.name, policyVersion: Number(f.policyVersion),
    effectiveFrom: new Date(f.effectiveFrom).toISOString(), effectiveTo: f.effectiveTo ? new Date(f.effectiveTo).toISOString() : null,
    maxPerTransaction: Number(f.maxPerTransaction), dailyLimit: num(f.dailyLimit), monthlyLimit: num(f.monthlyLimit),
    tankCapacity: num(f.tankCapacity), minConsumption: num(f.minConsumption), maxConsumption: num(f.maxConsumption),
    odometerJumpTolerance: Number(f.odometerJumpTolerance), receiptRequired: f.receiptRequired === 'on',
    receiptGraceHours: Number(f.receiptGraceHours), materialityAmount: Number(f.materialityAmount),
    anomalySlaHours: Number(f.anomalySlaHours), allowedFuelProducts: set(f.allowedFuelProducts), approvedVendors: set(f.approvedVendors) };
  try { await api('/api/v1/fuel/policies', { method: 'POST', body: JSON.stringify(body) }); e.target.reset(); msg('Policy created', 'ok'); load('policies'); }
  catch (x) { reportError(x); }
};

// ---- boot ----------------------------------------------------------------------------------
$('refresh').onclick = () => load(document.querySelector('.view.active').id);
rolesInput.onchange = () => { const id = document.querySelector('.view.active').id; if (['policies','reports','imports','integrations'].includes(id)) load(id); };
fetch('/actuator/health').then(r => r.json()).then(x => $('health').textContent = `Service ${x.status}`).catch(() => $('health').textContent = 'Service unavailable');
load();
