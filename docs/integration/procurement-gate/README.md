# Procurement-gate evidence register (SRS 2026/002 §5.2, CORR-07)

> "No vendor system may be reported 'integrated' without the Section 5.2-equivalent procurement-gate
> evidence on file." Phase 1's Go-Live review found every vendor-facing adapter was a simulator while
> status documents implied otherwise (gap G-03). This register is where the evidence lives, and the
> service reads whether it exists rather than anybody asserting it.

## How the service uses this register

`GET /api/v1/facilities/vendor-integrations` (permission `FACILITIES_VENDOR_INTEGRATION_READ`) reports
one row per Phase 2 vendor integration with a `gateStatus` of:

| Status | Meaning |
|---|---|
| `SIMULATED_ADAPTER_ONLY` | No evidence reference is configured. The adapter is a simulator; the integration must not be described as integrated anywhere. **This is the state of every entry today.** |
| `GATE_EVIDENCE_ON_FILE` | An evidence reference is configured (`SFL_S156_PROCUREMENT_GATE_EVIDENCE`, `SFL_S157_PROCUREMENT_GATE_EVIDENCE`). The strongest statement the service will make. |

There is deliberately no `INTEGRATED` value. Whether the evidence is sufficient is a procurement
decision recorded in the evidence pack, not a runtime fact.

Setting the environment variable is the last step, not the first: it should name the approved evidence
pack (document id and version) listed in the system's page below, after every row there is complete.

| System | Delivery | Integration key | Page | Evidence on file |
|---|---|---|---|---|
| S156 BMS / IoT | Buy and Integrate | `bms-iot` | [S156-bms-iot.md](S156-bms-iot.md) | **No** |
| S157 Energy metering / AMI | Hybrid | `energy-metering` | [S157-energy-metering.md](S157-energy-metering.md) | **No** |

S167 telematics and the S168 scan hardware are Phase 2 vendor integrations too, owned by FTLMP/AVAMP
in `sfl-fleet-logistics-service`, and are not built in this pass.
