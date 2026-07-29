import { useNavigate, useParams } from 'react-router';
import { FuelPolicy } from 'modules/fuel/api/dto';
import { fuelPoliciesApi } from 'modules/fuel/api/fuelApi';
import HistoryTimeline from 'modules/fuel/components/HistoryTimeline';
import { siteOf } from 'modules/fuel/components/fuelFormat';
import { humanise } from 'modules/fleet/api/enums';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import KeyValueGrid from 'shared/components/KeyValueGrid';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import StatusChip from 'shared/components/StatusChip';
import { formatDateTime, formatNumber } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fuelPaths } from 'shared/layout/navigation';

const inForce = (policy: FuelPolicy, at = Date.now()): boolean =>
  policy.status === 'ACTIVE' &&
  new Date(policy.effectiveFrom).getTime() <= at &&
  (!policy.effectiveTo || new Date(policy.effectiveTo).getTime() > at);

/**
 * A fuel policy in full.
 *
 * Read by id. That was not possible when this screen was first built — there was no
 * `GET /policies/{id}`, so the page walked the actor's sites listing policies until it found one,
 * and a deep link into a policy at a site the picker had not selected was a dead end. A policy
 * outside the actor's scope now answers with the service's own not-found or authorisation error.
 */
const FuelPolicyDetailPage = () => {
  const { policyId = '' } = useParams();
  const navigate = useNavigate();

  const lookup = useApiQuery(
    (signal) => fuelPoliciesApi.findById(policyId, signal),
    [policyId],
  );

  const history = useApiQuery(
    (signal) => fuelPoliciesApi.history(policyId, signal),
    [policyId],
  );

  const policy = lookup.data;

  return (
    <div>
      <PageHeader
        title={policy ? policy.name : 'Fuel policy'}
        subtitle={policy ? `Version ${policy.policyVersion} · ${siteOf(policy.siteCode)}` : undefined}
        crumbs={[
          { label: 'Fuel', to: fuelPaths.dashboard },
          { label: 'Fuel policies', to: fuelPaths.policies },
          { label: policy?.name ?? '…' },
        ]}
        actions={
          <Button
            variant="outline"
            startIcon="arrow-left"
            onClick={() => navigate(fuelPaths.policies)}
          >
            Register
          </Button>
        }
        meta={
          policy && (
            <div className="flex flex-wrap items-center gap-2">
              <StatusChip value={policy.status} />
              {inForce(policy) ? (
                <StatusChip value="ACTIVE" label="In force now" tone="ready" />
              ) : (
                <StatusChip value="INACTIVE" label="Outside its period" tone="neutral" />
              )}
            </div>
          )
        }
      />

      <DataState
        loading={lookup.initialising}
        error={lookup.error}
        onRetry={lookup.refetch}
        minHeight={300}
      >
        {policy && (
          <div className="space-y-5">
            <div className="grid gap-5 xl:grid-cols-[1.4fr_1fr]">
              <div className="space-y-5">
                <SectionCard title="Effective period" subtitle="What reconciliation resolves against">
                  <KeyValueGrid
                    columns={2}
                    items={[
                      { label: 'Site', value: siteOf(policy.siteCode) },
                      { label: 'Policy version', value: policy.policyVersion },
                      { label: 'Effective from', value: formatDateTime(policy.effectiveFrom) },
                      {
                        label: 'Effective to',
                        value: policy.effectiveTo
                          ? formatDateTime(policy.effectiveTo)
                          : 'Open ended',
                      },
                      { label: 'Status', value: humanise(policy.status) },
                      {
                        label: 'In force now',
                        value: inForce(policy) ? 'Yes' : 'No',
                      },
                    ]}
                  />
                </SectionCard>

                <SectionCard title="Limits" subtitle="What the reconciliation rules read">
                  <KeyValueGrid
                    items={[
                      {
                        label: 'Maximum per transaction',
                        value: formatNumber(policy.maxPerTransaction),
                      },
                      {
                        label: 'Tank capacity',
                        value:
                          policy.tankCapacity === null
                            ? 'Not set — rule skipped'
                            : formatNumber(policy.tankCapacity),
                      },
                      {
                        label: 'Odometer jump tolerance',
                        value: `${formatNumber(policy.odometerJumpTolerance)} km`,
                      },
                      {
                        label: 'Minimum consumption',
                        value:
                          policy.minConsumption === null
                            ? 'Not set'
                            : formatNumber(policy.minConsumption),
                      },
                      {
                        label: 'Maximum consumption',
                        value:
                          policy.maxConsumption === null
                            ? 'Not set'
                            : formatNumber(policy.maxConsumption),
                      },
                      {
                        label: 'Daily limit',
                        value: policy.dailyLimit === null ? 'Not set' : formatNumber(policy.dailyLimit),
                      },
                      {
                        label: 'Monthly limit',
                        value:
                          policy.monthlyLimit === null
                            ? 'Not set'
                            : formatNumber(policy.monthlyLimit),
                      },
                      {
                        label: 'Materiality amount',
                        value: formatNumber(policy.materialityAmount),
                      },
                      { label: 'Anomaly SLA', value: `${policy.anomalySlaHours} hours` },
                    ]}
                  />
                  <p className="mt-3 text-theme-xs text-gray-600">
                    The consumption rule only runs when both bounds are set and a previous
                    transaction exists for the vehicle. The daily and monthly limits are stored but
                    the current reconciliation routine does not read them.
                  </p>
                </SectionCard>

                <SectionCard title="Allowed products and vendors">
                  <div className="grid gap-5 sm:grid-cols-2">
                    <div>
                      <p className="text-theme-xs font-semibold text-gray-600">Fuel products</p>
                      {policy.allowedFuelProducts.length === 0 ? (
                        <p className="mt-1 text-theme-sm text-gray-700">
                          None listed — any product is allowed.
                        </p>
                      ) : (
                        <ul className="mt-1.5 flex flex-wrap gap-1.5">
                          {policy.allowedFuelProducts.map((product) => (
                            <li key={product}>
                              <StatusChip value={product} label={product} tone="neutral" />
                            </li>
                          ))}
                        </ul>
                      )}
                    </div>
                    <div>
                      <p className="text-theme-xs font-semibold text-gray-600">Approved vendors</p>
                      {policy.approvedVendors.length === 0 ? (
                        <p className="mt-1 text-theme-sm text-gray-700">
                          None listed — any vendor is allowed.
                        </p>
                      ) : (
                        <ul className="mt-1.5 flex flex-wrap gap-1.5">
                          {policy.approvedVendors.map((vendor) => (
                            <li key={vendor}>
                              <StatusChip value={vendor} label={vendor} tone="neutral" />
                            </li>
                          ))}
                        </ul>
                      )}
                    </div>
                  </div>
                </SectionCard>
              </div>

              <div className="space-y-5">
                <SectionCard title="Receipts">
                  <KeyValueGrid
                    columns={2}
                    items={[
                      {
                        label: 'Receipt required',
                        value: policy.receiptRequired ? 'Yes' : 'No',
                      },
                      {
                        label: 'Grace period',
                        value: `${policy.receiptGraceHours} hours`,
                      },
                    ]}
                  />
                  <p className="mt-3 text-theme-sm text-gray-700">
                    {policy.receiptRequired
                      ? `A transaction with no receipt passes reconciliation while it is within ${policy.receiptGraceHours} hours of occurring. After that, the scheduled sweep reconciles it again and raises a missing-receipt case.`
                      : 'Reconciliation does not check for a receipt under this policy.'}
                  </p>
                </SectionCard>

                <SectionCard title="History" subtitle="Recorded changes, from the audit log">
                  <DataState
                    loading={history.initialising}
                    error={history.error}
                    onRetry={history.refetch}
                    minHeight={140}
                  >
                    <HistoryTimeline events={history.data} recordNoun="policy" />
                  </DataState>
                </SectionCard>

                <SectionCard title="Editing">
                  <Alert variant="info" title="Policies are not editable">
                    The service exposes create and read only — there is no update or archive
                    endpoint. Superseding a policy means creating one whose period begins where this
                    one ends; an overlapping period is refused, so an open-ended policy has to be
                    given an end date before a successor can be created, which is not possible today.
                  </Alert>
                </SectionCard>
              </div>
            </div>
          </div>
        )}
      </DataState>
    </div>
  );
};

export default FuelPolicyDetailPage;
