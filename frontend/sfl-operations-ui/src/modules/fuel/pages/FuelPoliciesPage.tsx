import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { FuelPolicy } from 'modules/fuel/api/dto';
import { fuelPoliciesApi } from 'modules/fuel/api/fuelApi';
import {
  CreatePolicyDialog,
  overlappingActivePolicies,
} from 'modules/fuel/dialogs/policyDialogs';
import { DerivedNote } from 'modules/fuel/components/Provenance';
import { siteOf } from 'modules/fuel/components/fuelFormat';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import { formatDate, formatNumber } from 'shared/components/format';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fuelPaths } from 'shared/layout/navigation';

/** A policy covers `now` when it is ACTIVE and now falls inside its effective period. */
const inForce = (policy: FuelPolicy, at = Date.now()): boolean =>
  policy.status === 'ACTIVE' &&
  new Date(policy.effectiveFrom).getTime() <= at &&
  (!policy.effectiveTo || new Date(policy.effectiveTo).getTime() > at);

/**
 * The fuel policy register.
 *
 * `GET /policies?siteCode=` returns every policy for a site, unpaged and unfiltered — it is the one
 * fuel collection that takes no `size` at all — so this register holds the whole set and the effective
 * period is the thing worth reading. A policy is what reconciliation resolves against a transaction's
 * own timestamp, so "which one is in force right now" is called out rather than left to be worked
 * out from two dates.
 */
const FuelPoliciesPage = () => {
  const navigate = useNavigate();
  const { notifySuccess } = useNotifier();

  const [siteCode, setSiteCode] = useState(defaultSite);
  const [activeOnly, setActiveOnly] = useState(false);
  const [creating, setCreating] = useState(false);

  const query = useApiQuery((signal) => fuelPoliciesApi.list(siteCode, signal), [siteCode]);

  const policies = useMemo(() => query.data ?? [], [query.data]);

  const rows = useMemo(
    () =>
      [...(activeOnly ? policies.filter((policy) => policy.status === 'ACTIVE') : policies)].sort(
        (left, right) => right.effectiveFrom.localeCompare(left.effectiveFrom),
      ),
    [policies, activeOnly],
  );

  const currentlyInForce = useMemo(() => policies.filter((policy) => inForce(policy)), [policies]);

  /**
   * Active policies whose effective periods overlap one another.
   *
   * The domain document states this cannot happen; the service does not enforce it (gap 11). Where
   * two active policies cover one instant, `findApplicablePolicy` returns whichever the query
   * surfaces, so the rules a transaction is judged against stop being reproducible — which is worth
   * saying loudly on the register that owns them.
   */
  const overlaps = useMemo(() => {
    const clashing = new Set<string>();
    policies
      .filter((policy) => policy.status === 'ACTIVE')
      .forEach((policy) => {
        const others = policies.filter((other) => other.id !== policy.id);
        overlappingActivePolicies(
          others,
          policy.siteCode.value,
          policy.effectiveFrom,
          policy.effectiveTo,
        ).forEach((other) => {
          clashing.add(policy.id);
          clashing.add(other.id);
        });
      });
    return clashing;
  }, [policies]);

  const columns = useMemo<Column<FuelPolicy>[]>(
    () => [
      {
        key: 'policy',
        header: 'Policy',
        width: 260,
        cell: (row) => (
          <CellStack
            primary={`${row.name} · version ${row.policyVersion}`}
            secondary={
              inForce(row)
                ? 'In force now'
                : row.status === 'ACTIVE'
                  ? 'Active, outside its period'
                  : `Status ${row.status.toLowerCase()}`
            }
          />
        ),
      },
      {
        key: 'period',
        header: 'Effective period',
        width: 220,
        cell: (row) => (
          <CellStack
            primary={formatDate(row.effectiveFrom)}
            secondary={row.effectiveTo ? `to ${formatDate(row.effectiveTo)}` : 'no end date'}
          />
        ),
      },
      {
        key: 'max',
        header: 'Max per transaction',
        width: 150,
        align: 'right',
        cell: (row) => formatNumber(row.maxPerTransaction),
      },
      {
        key: 'sla',
        header: 'Anomaly SLA',
        width: 120,
        align: 'right',
        hideBelowLg: true,
        cell: (row) => `${row.anomalySlaHours} hrs`,
      },
      {
        key: 'receipt',
        header: 'Receipt',
        width: 140,
        hideBelowLg: true,
        cell: (row) =>
          row.receiptRequired ? (
            <StatusChip
              value="ACTIVE"
              label={`Required · ${row.receiptGraceHours}h grace`}
              tone="active"
            />
          ) : (
            <StatusChip value="INACTIVE" label="Not required" tone="neutral" />
          ),
      },
      {
        key: 'status',
        header: 'Status',
        width: 150,
        align: 'right',
        cell: (row) => (
          <div className="flex items-center justify-end gap-1.5">
            {overlaps.has(row.id) && (
              <StatusChip value="WARNING" label="Overlapping" tone="caution" />
            )}
            <StatusChip value={row.status} />
          </div>
        ),
      },
    ],
    [overlaps],
  );

  return (
    <div>
      <PageHeader
        title="Fuel policies"
        subtitle="The effective-dated limits every reconciliation is read from."
        crumbs={[{ label: 'Fuel', to: fuelPaths.dashboard }, { label: 'Fuel policies' }]}
        actions={
          <Button variant="primary" startIcon="plus" onClick={() => setCreating(true)}>
            Create policy
          </Button>
        }
      />

      <SectionCard flush>
        <FilterBar onReset={() => setActiveOnly(false)} resetDisabled={!activeOnly}>
          <SiteSelect value={siteCode} onChange={setSiteCode} required />
          <div className="flex items-end pb-1">
            <Button
              variant={activeOnly ? 'primary' : 'outline'}
              startIcon="filter"
              onClick={() => setActiveOnly((current) => !current)}
            >
              Active only
            </Button>
          </div>
        </FilterBar>
      </SectionCard>

      <div className="mt-5 space-y-5">
        {query.data && currentlyInForce.length === 0 && (
          <Alert variant="warning" title="No policy is in force at this site right now">
            Reconciliation resolves the policy covering a transaction’s own timestamp and refuses the
            run when it finds none. Transactions can still be captured; they cannot be reconciled.
          </Alert>
        )}

        {overlaps.size > 0 && (
          <Alert
            variant="warning"
            title={`${overlaps.size} active policies have overlapping effective periods`}
          >
            Where two active policies cover the same instant, which one reconciliation reads is not
            reproducible. Close the earlier policy’s period, or archive it.
            <DerivedNote>
              A check this console makes over the policies it loaded. The service does not enforce
              it, despite the domain model documenting it as an invariant.
            </DerivedNote>
          </Alert>
        )}

        <SectionCard flush>
          <DataState
            loading={query.initialising}
            error={query.error}
            empty={rows.length === 0}
            emptyTitle="No fuel policy at this site"
            emptyHint="Create one before capturing transactions, or reconciliation will have nothing to judge them against."
            onRetry={query.refetch}
            minHeight={280}
          >
            <DataTable
              rows={rows}
              columns={columns}
              getRowId={(row) => row.id}
              loading={query.loading}
              onRowClick={(row) => navigate(fuelPaths.policyDetail(row.id))}
              caption="Fuel policies at this site, with their effective period, per-transaction limit, anomaly SLA, receipt rule and status."
            />
          </DataState>
        </SectionCard>

        {currentlyInForce.length > 0 && (
          <SectionCard
            title="In force right now"
            subtitle="What a reconciliation run today would read"
          >
            <ul className="space-y-2.5">
              {currentlyInForce.map((policy) => (
                <li key={policy.id} className="flex flex-wrap items-baseline justify-between gap-2">
                  <span className="text-theme-sm text-gray-900">
                    <span className="font-semibold">{policy.name}</span>
                    <span className="text-gray-600">
                      {' '}
                      · version {policy.policyVersion} · {siteOf(policy.siteCode)}
                    </span>
                  </span>
                  <Button
                    size="sm"
                    variant="ghost"
                    endIcon="chevron-right"
                    onClick={() => navigate(fuelPaths.policyDetail(policy.id))}
                  >
                    Open
                  </Button>
                </li>
              ))}
            </ul>
          </SectionCard>
        )}
      </div>

      {creating && (
        <CreatePolicyDialog
          open
          defaultSiteCode={siteCode}
          existingPolicies={policies}
          onClose={() => setCreating(false)}
          onSaved={(policy) => {
            notifySuccess(
              `${policy.name} created as an active policy.`,
              `Version ${policy.policyVersion}, effective from ${formatDate(policy.effectiveFrom)}.`,
            );
            query.refetch();
          }}
        />
      )}
    </div>
  );
};

export default FuelPoliciesPage;
