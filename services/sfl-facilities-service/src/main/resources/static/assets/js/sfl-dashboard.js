(function () {
  const state = {
    facilitiesBase: "",
    assetBase: "http://localhost:8094",
    sites: [],
    buildings: [],
    floors: [],
    rooms: [],
    faults: [],
    workOrders: [],
    assets: []
  };

  const $ = (selector) => document.querySelector(selector);
  const $$ = (selector) => Array.from(document.querySelectorAll(selector));
  const byId = (id) => document.getElementById(id);
  const value = (id) => byId(id).value.trim();

  function actorHeaders() {
    return {
      "X-SFL-User": value("actorUser") || "development-user",
      "X-SFL-Display-Name": value("actorDisplay"),
      "X-SFL-Roles": value("actorRoles"),
      "X-SFL-Sites": value("actorSites"),
      "X-Correlation-ID": `sfl-ui-${Date.now()}`
    };
  }

  function jsonHeaders() {
    return {
      "Content-Type": "application/json",
      ...actorHeaders()
    };
  }

  function endpoint(base, path) {
    return `${base.replace(/\/$/, "")}${path}`;
  }

  async function request(base, path, options = {}) {
    const response = await fetch(endpoint(base, path), {
      ...options,
      headers: {
        ...jsonHeaders(),
        ...(options.headers || {})
      }
    });
    const body = await response.text();
    const payload = body ? JSON.parse(body) : null;
    if (!response.ok) {
      const message = payload && payload.message ? payload.message : response.statusText;
      throw new Error(message || `Request failed with ${response.status}`);
    }
    return payload;
  }

  function notify(message, type = "success") {
    const alert = document.createElement("div");
    alert.className = `alert alert-${type} shadow-sm mb-2`;
    alert.role = "alert";
    alert.textContent = message;
    $('[data-region="toasts"]').appendChild(alert);
    window.setTimeout(() => alert.remove(), 5200);
  }

  function bind(name, content) {
    $$(`[data-bind="${name}"]`).forEach((element) => {
      element.innerHTML = content;
    });
  }

  function textBind(name, content) {
    $$(`[data-bind="${name}"]`).forEach((element) => {
      element.textContent = content;
    });
  }

  function shortId(id) {
    return id ? `${String(id).slice(0, 8)}...` : "";
  }

  function formatDate(value) {
    if (!value) {
      return "";
    }
    return new Date(value).toLocaleString();
  }

  function badge(value) {
    const tone = {
      OPEN: "text-bg-warning",
      ASSIGNED: "text-bg-primary",
      CLOSED: "text-bg-success",
      REPORTED: "text-bg-warning",
      ACTIVE: "text-bg-success",
      UNKNOWN: "text-bg-secondary",
      READY: "text-bg-success",
      NOT_READY: "text-bg-danger"
    }[value] || "text-bg-light border";
    return `<span class="badge ${tone}">${value || ""}</span>`;
  }

  function empty(colspan, message) {
    return `<tr><td class="empty-row" colspan="${colspan}">${message}</td></tr>`;
  }

  function renderTable(name, rows, colspan, mapper, emptyMessage) {
    const table = $(`[data-table="${name}"]`);
    table.innerHTML = rows.length ? rows.map(mapper).join("") : empty(colspan, emptyMessage);
  }

  function syncSettings() {
    state.facilitiesBase = value("facilitiesBase");
    state.assetBase = value("assetBase") || "http://localhost:8094";

    textBind("actorSummary", value("actorUser") || "development-user");
    textBind("actorScope", value("actorSites") || "No site scope");
    textBind("snapshotUser", value("actorUser") || "development-user");
    textBind("snapshotRoles", value("actorRoles") || "No roles");
    textBind("snapshotSites", value("actorSites") || "No site scope");
    textBind("snapshotFacilitiesApi", state.facilitiesBase || "same origin");
    textBind("snapshotAssetApi", state.assetBase);
  }

  function setHealth(name, status, ok) {
    const dot = ok ? "status-up" : status === "Checking" ? "status-warn" : "status-down";
    bind(name, `<span class="status-dot ${dot}"></span>${status}`);
  }

  function option(label, value, selected) {
    return `<option value="${value}" ${selected ? "selected" : ""}>${label}</option>`;
  }

  function hydrateSelects() {
    const siteOptions = state.sites.map((site) => option(`${site.siteCode} - ${site.name}`, site.id, false)).join("");
    byId("buildingSite").innerHTML = siteOptions || option("Create a site first", "", true);

    const buildingOptions = state.buildings.map((building) =>
      option(`${building.buildingCode} - ${building.name}`, building.id, false)).join("");
    byId("floorBuilding").innerHTML = buildingOptions || option("Create a building first", "", true);

    const floorOptions = state.floors.map((floor) =>
      option(`${floor.floorCode} - ${floor.name}`, floor.id, false)).join("");
    byId("roomFloor").innerHTML = floorOptions || option("Create a floor first", "", true);
  }

  function selectedSite() {
    const selected = byId("buildingSite").value;
    return state.sites.find((site) => site.id === selected);
  }

  function selectedBuilding() {
    const selected = byId("floorBuilding").value;
    return state.buildings.find((building) => building.id === selected);
  }

  function selectedFloor() {
    const selected = byId("roomFloor").value;
    return state.floors.find((floor) => floor.id === selected);
  }

  function renderRegistry() {
    renderTable("sites", state.sites, 3, (site) => `
      <tr>
        <td><button class="code-link" data-select-site="${site.siteCode}" type="button">${site.siteCode}</button></td>
        <td>${site.name}<div class="record-subtitle">${site.description || ""}</div></td>
        <td>${site.active ? badge("ACTIVE") : badge("INACTIVE")}</td>
      </tr>
    `, "No sites have been created.");

    renderTable("buildings", state.buildings, 3, (building) => `
      <tr>
        <td><button class="code-link" data-select-building="${building.id}" type="button">${building.buildingCode}</button></td>
        <td>${building.name}</td>
        <td>${building.siteCode}</td>
      </tr>
    `, "No buildings have been created.");

    renderTable("rooms", state.rooms, 5, (room) => `
      <tr>
        <td>
          <div class="record-title">${room.roomCode}</div>
          <div class="record-subtitle">${room.siteCode}</div>
        </td>
        <td>${room.name}</td>
        <td>${room.roomType || ""}</td>
        <td>${room.capacity ?? ""}</td>
        <td>${badge(room.readinessStatus)}</td>
      </tr>
    `, "No rooms have been created.");

    hydrateSelects();
    textBind("registryCount", String(state.sites.length + state.buildings.length + state.rooms.length));
  }

  function renderMaintenance() {
    renderTable("faults", state.faults, 5, (fault) => `
      <tr>
        <td>
          <button class="code-link" data-select-fault="${fault.id}" type="button">${fault.faultNumber}</button>
          <div class="record-subtitle">${fault.title}</div>
        </td>
        <td>${fault.priority}</td>
        <td>${badge(fault.status)}</td>
        <td>${fault.siteCode}</td>
        <td>${fault.workOrderId ? shortId(fault.workOrderId) : ""}</td>
      </tr>
    `, "No facility faults reported.");

    renderTable("workOrders", state.workOrders, 6, (workOrder) => `
      <tr>
        <td>
          <button class="code-link" data-select-work-order="${workOrder.id}" type="button">${workOrder.workOrderNumber}</button>
          <div class="record-subtitle">${shortId(workOrder.id)}</div>
        </td>
        <td>${workOrder.title}</td>
        <td>${workOrder.priority}</td>
        <td>${badge(workOrder.status)}</td>
        <td>${workOrder.assignedTo || ""}</td>
        <td>${workOrder.siteCode}</td>
      </tr>
    `, "No work orders visible to this actor.");

    const openCount = state.workOrders.filter((item) => item.status !== "CLOSED").length;
    textBind("openWorkOrders", String(openCount));

    $$("[data-select-fault]").forEach((button) => {
      button.addEventListener("click", () => {
        byId("faultId").value = button.dataset.selectFault;
        notify("Fault selected for work-order creation.");
      });
    });

    $$("[data-select-work-order]").forEach((button) => {
      button.addEventListener("click", () => {
        byId("workOrderId").value = button.dataset.selectWorkOrder;
        notify("Work order selected.");
      });
    });
  }

  function renderAssets() {
    renderTable("assets", state.assets, 5, (asset) => `
      <tr>
        <td>
          <div class="record-title">${asset.assetCode}</div>
          <div class="record-subtitle">${asset.name}</div>
        </td>
        <td>${asset.category}</td>
        <td>${badge(asset.status)}</td>
        <td>${asset.locationType} / ${asset.locationReference}</td>
        <td>${asset.custodianReference || ""}</td>
      </tr>
    `, "No asset references found for this site.");
  }

  function renderActivity() {
    const rows = [
      ...state.workOrders.slice(0, 5).map((item) => ({
        type: "Work Order",
        reference: item.workOrderNumber,
        status: item.status,
        site: item.siteCode,
        updated: item.closedAt || item.assignedAt || item.createdAt
      })),
      ...state.faults.slice(0, 4).map((item) => ({
        type: "Fault",
        reference: item.faultNumber,
        status: item.status,
        site: item.siteCode,
        updated: item.reportedAt
      })),
      ...state.assets.slice(0, 4).map((item) => ({
        type: "Asset",
        reference: item.assetCode,
        status: item.status,
        site: item.siteCode,
        updated: item.updatedAt
      }))
    ];

    renderTable("activity", rows, 5, (row) => `
      <tr>
        <td>${row.type}</td>
        <td>${row.reference}</td>
        <td>${badge(row.status)}</td>
        <td>${row.site}</td>
        <td>${formatDate(row.updated)}</td>
      </tr>
    `, "No records loaded yet.");
  }

  async function refreshHealth() {
    try {
      const health = await request(state.facilitiesBase, "/actuator/health", { headers: actorHeaders() });
      setHealth("facilitiesHealth", health.status || "UP", true);
    } catch (error) {
      setHealth("facilitiesHealth", "Down", false);
    }

    try {
      const health = await request(state.assetBase, "/actuator/health", { headers: actorHeaders() });
      setHealth("assetHealth", health.status || "UP", true);
    } catch (error) {
      setHealth("assetHealth", "Down", false);
    }
  }

  async function refreshRegistry() {
    const siteCode = encodeURIComponent(value("actorSites").split(",")[0] || "MAIN");
    state.sites = await request(state.facilitiesBase, "/api/v1/facilities/sites");
    state.buildings = await request(state.facilitiesBase, `/api/v1/facilities/buildings?siteCode=${siteCode}`);
    const floorResults = await Promise.allSettled(
      state.buildings.map((building) => request(state.facilitiesBase, `/api/v1/facilities/buildings/${building.id}/floors`))
    );
    state.floors = floorResults.flatMap((result) => result.status === "fulfilled" ? result.value : []);
    state.rooms = await request(state.facilitiesBase, `/api/v1/facilities/rooms?siteCode=${siteCode}`);
    renderRegistry();
  }

  async function refreshFaults() {
    state.faults = await request(state.facilitiesBase, "/api/v1/facilities/faults");
    renderMaintenance();
    renderActivity();
  }

  async function refreshWorkOrders() {
    state.workOrders = await request(state.facilitiesBase, "/api/v1/facilities/work-orders");
    renderMaintenance();
    renderActivity();
  }

  async function refreshAssets() {
    const siteCode = encodeURIComponent(value("actorSites").split(",")[0] || "MAIN");
    state.assets = await request(state.assetBase, `/api/v1/assets?siteCode=${siteCode}`);
    renderAssets();
    renderActivity();
  }

  async function refreshAll() {
    syncSettings();
    await refreshHealth();
    const results = await Promise.allSettled([refreshRegistry(), refreshFaults(), refreshWorkOrders(), refreshAssets()]);
    results.filter((result) => result.status === "rejected")
      .forEach((result) => notify(result.reason.message, "warning"));
    renderActivity();
  }

  function postForm(selector, handler) {
    $(selector).addEventListener("submit", async (event) => {
      event.preventDefault();
      try {
        await handler();
      } catch (error) {
        notify(error.message, "danger");
      }
    });
  }

  function wireForms() {
    postForm('[data-form="site"]', async () => {
      await request(state.facilitiesBase, "/api/v1/facilities/sites", {
        method: "POST",
        body: JSON.stringify({
          siteCode: value("siteCode"),
          name: value("siteName"),
          description: value("siteDescription")
        })
      });
      notify("Site created.");
      await refreshRegistry();
    });

    postForm('[data-form="building"]', async () => {
      const site = selectedSite();
      if (!site) {
        throw new Error("Create or select a site first.");
      }
      await request(state.facilitiesBase, "/api/v1/facilities/buildings", {
        method: "POST",
        body: JSON.stringify({
          siteId: site.id,
          siteCode: site.siteCode,
          buildingCode: value("buildingCode"),
          name: value("buildingName"),
          description: ""
        })
      });
      notify("Building created.");
      await refreshRegistry();
    });

    postForm('[data-form="floor"]', async () => {
      const building = selectedBuilding();
      if (!building) {
        throw new Error("Create or select a building first.");
      }
      await request(state.facilitiesBase, "/api/v1/facilities/floors", {
        method: "POST",
        body: JSON.stringify({
          buildingId: building.id,
          siteCode: building.siteCode,
          floorCode: value("floorCode"),
          name: value("floorName"),
          levelNumber: value("floorLevel") ? Number(value("floorLevel")) : null
        })
      });
      notify("Floor created.");
      await refreshRegistry();
    });

    postForm('[data-form="room"]', async () => {
      const floor = selectedFloor();
      if (!floor) {
        throw new Error("Create or select a floor first.");
      }
      await request(state.facilitiesBase, "/api/v1/facilities/rooms", {
        method: "POST",
        body: JSON.stringify({
          floorId: floor.id,
          siteCode: floor.siteCode,
          roomCode: value("roomCode"),
          name: value("roomName"),
          roomType: value("roomType"),
          capacity: value("roomCapacity") ? Number(value("roomCapacity")) : null
        })
      });
      notify("Room created.");
      await refreshRegistry();
    });

    postForm('[data-form="fault"]', async () => {
      const fault = await request(state.facilitiesBase, "/api/v1/facilities/faults", {
        method: "POST",
        body: JSON.stringify({
          siteCode: value("faultSite"),
          locationCode: value("faultLocation"),
          title: value("faultTitle"),
          description: value("faultDescription"),
          category: value("faultCategory"),
          priority: value("faultPriority")
        })
      });
      byId("faultId").value = fault.id;
      notify("Facility fault created.");
      await refreshFaults();
    });

    postForm('[data-form="workOrder"]', async () => {
      const workOrder = await request(state.facilitiesBase, "/api/v1/facilities/work-orders/from-fault", {
        method: "POST",
        body: JSON.stringify({ facilityFaultId: value("faultId") })
      });
      byId("workOrderId").value = workOrder.id;
      notify("Work order created.");
      await Promise.all([refreshFaults(), refreshWorkOrders()]);
    });

    postForm('[data-form="assignment"]', async () => {
      await request(state.facilitiesBase, `/api/v1/facilities/work-orders/${value("workOrderId")}/assignment`, {
        method: "PATCH",
        body: JSON.stringify({ assignedTo: value("assignedTo") })
      });
      notify("Work order assigned.");
      await refreshWorkOrders();
    });

    postForm('[data-form="closure"]', async () => {
      await request(state.facilitiesBase, `/api/v1/facilities/work-orders/${value("workOrderId")}/closure`, {
        method: "PATCH",
        body: JSON.stringify({ closureNotes: value("closureNotes") })
      });
      notify("Work order closed.");
      await refreshWorkOrders();
    });

    postForm('[data-form="asset"]', async () => {
      await request(state.assetBase, "/api/v1/assets", {
        method: "POST",
        body: JSON.stringify({
          assetCode: value("assetCode"),
          name: value("assetName"),
          category: value("assetCategory"),
          siteCode: value("assetSite"),
          locationType: value("assetLocationType"),
          locationReference: value("assetLocation"),
          custodianReference: value("assetCustodian"),
          externalReference: value("assetExternal")
        })
      });
      notify("Asset reference registered.");
      await refreshAssets();
    });
  }

  function wireActions() {
    byId("refresh-all").addEventListener("click", () => refreshAll().catch((error) => notify(error.message, "danger")));
    byId("applySettings").addEventListener("click", () => refreshAll().then(() => notify("Settings applied.")).catch((error) => notify(error.message, "danger")));
    $('[data-refresh="registry"]').addEventListener("click", () => refreshRegistry().catch((error) => notify(error.message, "danger")));
    $('[data-refresh="faults"]').addEventListener("click", () => refreshFaults().catch((error) => notify(error.message, "danger")));
    $('[data-refresh="workOrders"]').addEventListener("click", () => refreshWorkOrders().catch((error) => notify(error.message, "danger")));
    $('[data-refresh="assets"]').addEventListener("click", () => refreshAssets().catch((error) => notify(error.message, "danger")));
  }

  document.addEventListener("DOMContentLoaded", () => {
    syncSettings();
    wireForms();
    wireActions();
    renderRegistry();
    renderMaintenance();
    renderAssets();
    renderActivity();
    refreshAll().catch(() => {});
  });
})();
