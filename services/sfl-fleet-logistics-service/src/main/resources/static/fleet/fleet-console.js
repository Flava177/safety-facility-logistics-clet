const state = {
  siteCode: "ACCRA",
  role: "FLEET_MANAGER"
};

const headers = () => ({
  "Accept": "application/json",
  "X-SFL-User": `${state.role.toLowerCase().replaceAll("_", ".")}@clet.edu.gh`,
  "X-SFL-Display-Name": state.role.replaceAll("_", " "),
  "X-SFL-Roles": state.role,
  "X-SFL-Sites": state.siteCode,
  "X-SFL-Source-Channel": "WEB",
  "X-Correlation-ID": `fleet-console-${Date.now()}`
});

const qs = () => new URLSearchParams({ siteCode: state.siteCode }).toString();

async function api(path) {
  const response = await fetch(path, { headers: headers() });
  const envelope = await response.json();
  if (!response.ok || envelope.error) {
    throw new Error(envelope.error?.message || response.statusText);
  }
  return envelope.data;
}

function text(id, value) {
  document.getElementById(id).textContent = value ?? "–";
}

function json(id, value) {
  document.getElementById(id).textContent = JSON.stringify(value, null, 2);
}

function list(id, rows, map) {
  const target = document.getElementById(id);
  target.innerHTML = "";
  rows.forEach(row => {
    const item = document.createElement("li");
    item.textContent = map(row);
    target.appendChild(item);
  });
}

async function loadDashboard() {
  const dashboard = await api(`/api/v1/fleet/dashboards/operations?${qs()}`);
  const indicators = dashboard.indicators;
  text("vehiclesAvailable", indicators.vehiclesAvailable);
  text("expiredCompliance", indicators.expiredCompliance);
  text("serviceDue", indicators.serviceDue);
  text("assignmentConflicts", indicators.assignmentConflicts);
  text("readinessBlockers", indicators.readinessBlockers);
  text("integrationDeadLetters", indicators.integrationDeadLetters);
  text("snapshotMeta", `${dashboard.stale ? "Stale" : "Fresh"} snapshot generated ${dashboard.generatedAt}`);
  list("warnings", dashboard.warnings || [], warning => warning);
  json("reconciliation", dashboard.reconciliation);
  text("lastUpdated", `Last refreshed ${new Date().toLocaleString()}`);
}

async function loadDrilldown() {
  const indicator = document.getElementById("drilldownIndicator").value;
  const rows = await api(`/api/v1/fleet/dashboards/operations/drilldowns/${indicator}?${qs()}`);
  list("drilldownRows", rows, row => `${row.resourceType} ${row.resourceId} · ${row.siteCode} · ${row.summary}`);
}

async function loadIntegrations() {
  json("integrationHealth", await api("/api/v1/fleet/integrations/health"));
}

async function loadReadiness() {
  const report = await api(`/api/v1/fleet/reports/go-live-readiness?${qs()}`);
  const verdict = document.getElementById("goLiveVerdict");
  verdict.textContent = report.ready ? "Ready for PR/release rehearsal." : "Blocked — resolve listed blockers.";
  verdict.className = `verdict ${report.ready ? "ready" : "blocked"}`;
  list("goLiveBlockers", report.blockers || [], blocker => blocker);
}

async function loadWorkflow() {
  const rows = await api(`/api/v1/fleet/workflow-items?siteCode=${encodeURIComponent(state.siteCode)}&size=10`);
  const content = rows.content || [];
  document.getElementById("workflowRows").innerHTML = content.length
    ? content.map(item => `${item.workflowNumber} · ${item.status} · ${item.priority} · ${item.title}`).join("<br>")
    : "No workflow rows returned.";
}

document.getElementById("scopeForm").addEventListener("submit", async event => {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  state.siteCode = String(form.get("siteCode") || "ACCRA").toUpperCase();
  state.role = String(form.get("role") || "FLEET_MANAGER");
  await loadDashboard().catch(showError);
});

document.body.addEventListener("click", event => {
  const action = event.target?.dataset?.action;
  if (!action) return;
  const actions = {
    "load-dashboard": loadDashboard,
    "load-drilldown": loadDrilldown,
    "load-integrations": loadIntegrations,
    "load-readiness": loadReadiness,
    "load-workflow": loadWorkflow
  };
  actions[action]().catch(showError);
});

function showError(error) {
  list("warnings", [error.message], warning => warning);
}

loadDashboard().catch(showError);
