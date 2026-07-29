import { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { FuelPolicy } from 'modules/fuel/api/dto';
import { fuelPoliciesApi } from 'modules/fuel/api/fuelApi';
import { overlappingActivePolicies } from 'modules/fuel/dialogs/policyDialogs';
import RecordProvenance, { DerivedNote } from 'modules/fuel/components/Provenance';
import { siteOf } from 'modules/fuel/components/fuelFormat';
import { humanise } from 'modules/fleet/api/enums';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import KeyValueGrid from 'shared/components/KeyValueGrid';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite, sflSites } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import { formatDate, formatDateTime, formatNumber } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fuelPaths } from 'shared/layout/navigation';

const inForce = (policy: FuelPolicy, at = Date.now()): boolean =>
  policy.status === 'ACTIVE' &&
  new Date(policy.effectiveFrom).getTime() <= at &&
  (!policy.effectiveTo || new Date(policy.effectiveTo).getTime() > at);

/**
 * A fuel policy in full.
 *
 * There is no `GET /policies/{id}` (gap 3), so the policy is selected out of the site's list. That
 * costs nothing in completeness — the list returns whole `FuelPolicy` records — but it does mean the
 * page has to know which site to ask. It opens on the actor's default site and, if the policy is not
 * there, tries the actor's other sites before giving up; a site picker is offered either way so a
 * deep link into a policy at a second site is recoverable rather than a dead end.
 */
const FuelPolicyDetailPage = () => {
  const { policyId = '' } = useParams();
  const navigate = useNavigate();
  const [siteCode, setSiteCode] = useState(defaultSite);

  /**
   * Searches the actor's sites for the policy, starting with the selected one.
   *
   * Sequential rather than parallel: the first site nearly always holds it, and firing a request at
   * every site the actor can read to find one record is a lot of load for a rare case.
   */
  const lookup = useApiQuery(
    async (signal) => {
      const ordered = [siteCode, ...sflSites.filter((site) => site !== siteCode)];
      for (const site of ordered) {
        const policies = await fuelPoliciesApi.list(site, signal);
        const match = policies.find((policy) => policy.id === policyId);
        if (match) {
          return { policy: match, siblings: policies, site };
        }
      }
      return { policy: undefined, siblings: [], site: siteCode };
    },
    [policyId, siteCode],
  );

  const policy = lookup.data?.policy;

  const overlaps = useMemo(() => {
    if (!policy || policy.status !== 'ACTIVE') {
      return [];
    }
    return overlappingActivePolicies(
      (lookup.data?.siblings ?? []).filter((other) => other.id !== policy.id),
      policy.siteCode.value,
      policy.effectiveFrom,
      policy.effectiveTo,
    );
  }, [policy, lookup.data]);

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
        {!policy ? (
          <div className="space-y-5">
            <Alert variant="warning" title="This policy was not found in your site scope">
              Policies are read through the site register — the service has no per-policy endpoint —
              so it can only be found at a site you can read. Choose a different site if you expect
              it elsewhere.
            </Alert>
            <SectionCard title="Look at another site">
              <div className="max-w-xs">
                <SiteSelect value={siteCode} onChange={setSiteCode} required />
              </div>
            </SectionCard>
          </div>
        ) : (
          <div className="space-y-5">
            {overlaps.length > 0 && (
              <Alert
                variant="warning"
                title={`Overlaps ${overlaps.length} other active ${overlaps.length === 1 ? 'policy' : 'policies'}`}
              >
                <ul className="mt-1 list-disc space-y-1 pl-4">
                  {overlaps.map((other) => (
                    <li key={other.id}>
                      {other.name} (version {other.policyVersion}) · from{' '}
                      {formatDate(other.effectiveFrom)}
                      {other.effectiveTo ? ` to ${formatDate(other.effectiveTo)}` : ' with no end date'}
                    </li>
                  ))}
                </ul>
                <DerivedNote>
                  A check this console makes over the site’s policies. The service does not enforce
                  non-overlap, so which policy a transaction is judged against is not reproducible
                  while this stands.
                </DerivedNote>
              </Alert>
            )}

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
                  <DerivedNote>
                    The consumption rule only runs when both bounds are set and a previous
                    transaction exists for the vehicle. The daily and monthly limits are stored but
                    the current reconciliation routine does not read them.
                  </DerivedNote>
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

                <SectionCard title="History">
                  <RecordProvenance metadata={policy.metadata} recordNoun="policy" />
                </SectionCard>

                <SectionCard title="Editing">
                  <Alert variant="info" title="Policies are not editable">
                    The service exposes create and read only — there is no update or archive
                    endpoint. Superseding a policy means creating a new version and, if the old one
                    should stop applying, giving it an end date, which is itself not possible today.
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
