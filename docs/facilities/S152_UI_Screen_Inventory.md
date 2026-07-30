# S152 CAFM/IWMS — dashboard screen inventory

What was built, where it lives, what it calls and who may see it. The permissions column is the
gate on the **navigation item**; every screen is additionally gated by S152 system entitlement
(`SystemRoutes system="S152"`), and every request is authorised again by the service, which is the
only enforcement point that counts.

- Module: `frontend/sfl-operations-ui/src/modules/facilities`
- Route base: `/facilities` (bundled: `/ui/facilities`)
- Service: `sfl-facilities-service` on 8091, `VITE_FACILITIES_API_BASE_URL`
- Programme: SFL.IFIMP · System: S152

## Screens

| # | Screen | Route | File | Reads | Writes | Permission |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Facilities dashboard | `/facilities` | `pages/FacilitiesDashboardPage.tsx` | `GET /dashboard` | — | `FACILITIES_DASHBOARD_READ` |
| 2 | Sites | `/facilities/sites` | `pages/SiteRegisterPage.tsx` | `GET /sites` | `POST /sites` | `FACILITIES_SITE_READ` |
| 3 | Site detail | `/facilities/sites/:siteId` | `pages/SiteDetailPage.tsx` | `GET /sites/{id}`, `GET /buildings` | `PATCH /sites/{id}/operating-mode` | `FACILITIES_SITE_READ` |
| 4 | Spaces | `/facilities/spaces` | `pages/SpaceRegisterPage.tsx` | `GET /rooms/search` | `POST /rooms` | `FACILITIES_SPACE_READ` |
| 5 | Space detail | `/facilities/spaces/:roomId` | `pages/SpaceDetailPage.tsx` | `GET /rooms/{id}`, `GET /readiness/rooms/{id}`, `GET /assets`, `GET /readiness/assessments` | `PATCH /rooms/{id}/readiness`, `PATCH /readiness/blockers/{id}/resolution`, `POST /rooms/{id}/readiness/lock` and `/unlock` | `FACILITIES_SPACE_READ` |
| 6 | Facility assets | `/facilities/assets` | `pages/AssetRegisterPage.tsx` | `GET /assets` | `POST /assets` | `FACILITIES_ASSET_READ` |
| 7 | Asset detail | `/facilities/assets/:assetId` | `pages/AssetDetailPage.tsx` | `GET /assets/{id}` | `PATCH /assets/{id}/status`, `PATCH /assets/{id}/location` | `FACILITIES_ASSET_READ` |
| 8 | Zones | `/facilities/zones` | `pages/ZonesPage.tsx` | `GET /zones`, `GET /zones/{id}/members` | `POST /zones`, `POST`/`DELETE` members | `FACILITIES_ZONE_READ` |
| 9 | Device references | `/facilities/devices` | `pages/DeviceReferencesPage.tsx` | `GET /device-references` | `POST /device-references` | `FACILITIES_DEVICE_REFERENCE_READ` |
| 10 | Readiness assessments | `/facilities/assessments` | `pages/ReadinessAssessmentsPage.tsx` | `GET /readiness/assessments` | `POST /readiness/assessments` | `FACILITIES_READINESS_READ` |
| 11 | Assessment detail | `/facilities/assessments/:assessmentId` | `pages/ReadinessAssessmentDetailPage.tsx` | `GET /readiness/assessments/{id}` | — | `FACILITIES_READINESS_READ` |
| 12 | Readiness checklists | `/facilities/checklists` | `pages/ReadinessChecklistsPage.tsx` | `GET /readiness/checklists` | `POST /readiness/checklists` | `FACILITIES_READINESS_CHECKLIST_MANAGE` |
| 13 | Checklist detail | `/facilities/checklists/:checklistId` | `pages/ReadinessChecklistDetailPage.tsx` | `GET /readiness/checklists/{id}` | `PUT /readiness/checklists/{id}` | `FACILITIES_READINESS_CHECKLIST_MANAGE` |
| 14 | Audit & integrity | `/facilities/audit` | `pages/FacilitiesAuditPage.tsx` | `GET /audit`, `GET /audit/integrity` | — | `FACILITIES_AUDIT_READ` |
| 15 | Configuration | `/facilities/configuration` | `pages/FacilitiesConfigurationPage.tsx` | `GET /configuration` | `PUT /configuration/{key}` | `FACILITIES_CONFIG_READ` |

## Dialogs

| Dialog | Used by | What it guards |
| --- | --- | --- |
| `SubmitAssessmentDialog` | 10 | Every item must be answered — an unanswered item counts as failed, so the dialog says so and disables submit rather than letting a partial answer become a result. |
| `SetReadinessDialog` | 5 | The manual override. READY is disabled with the open critical count before it is submitted; the service refuses it independently. |
| `ResolveBlockerDialog` | 5 | The resolution note is required, because a blocker cleared without one cannot be told from a dismissal at a post-mortem. |
| `AssetStatusDialog` | 7 | Previews the readiness consequence — criticality plus target status — before the change is committed. Mirrors `ReadinessApplicationService.severityFor`. |
| `OperatingModeDialog` | 3 | Declaring or standing down examination mode. Withheld from `FACILITIES_MANAGER` by the matrix; a centre manager, command role or facilities director carries it. |

## Navigation

Three sections, all `programme: 'IFIMP', system: 'S152'`, in `shared/layout/navigation.ts`:

- **Facility operations** — dashboard, readiness assessments
- **Estate registers** — sites, spaces, facility assets, zones, device references
- **Facility assurance** — readiness checklists, audit & integrity, configuration

Each item carries the real service permission, so the sidebar narrows with the actor. A
`FACILITIES_MANAGER` sees ten of the twelve items (no audit, no configuration management); an
`IFIMP_TECHNICIAN` sees nine; a `FLEET_MANAGER` sees none of the three sections at all.

## Tests

`npm run test` — 44 tests, 4 files.

| File | Covers |
| --- | --- |
| `shared/layout/programmeModel.test.ts` | S152 entitlement per role, including the roles that must **not** get it |
| `modules/facilities/api/workflow.test.ts` | The critical-blocker rule, the examination lock, asset-to-blocker severity |
| `modules/facilities/components/facilitiesFormat.test.ts` | Tone mapping and the date/relative-time helpers |
| `modules/facilities/pages/FacilitiesDashboardPage.test.tsx` | Loading, error, stale-data warning, examination mode, restricted drilldown, empty states |
