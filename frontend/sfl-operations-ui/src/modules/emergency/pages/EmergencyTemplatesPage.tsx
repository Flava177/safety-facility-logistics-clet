import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import type { EmergencyScenario, NotificationTemplate } from 'modules/emergency/api/dto';
import { listChannels } from 'modules/emergency/components/EmergencyFields';
import { useSiteRecords } from 'modules/emergency/components/useSiteRecords';
import {
  CreateScenarioDialog,
  CreateTemplateDialog,
} from 'modules/emergency/dialogs/recordDialogs';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { CellStack, Column } from 'shared/components/DataTable';
import Icon from 'shared/components/Icon';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import Tabs from 'shared/components/Tabs';
import { TextInput } from 'shared/components/fields';
import { formatDateTime } from 'shared/components/format';
import { emergencyPaths } from 'shared/layout/navigation';

/**
 * What gets sent: notification templates and the scenarios that cite them.
 *
 * Two registers on one screen because they answer one question between them and are almost always
 * read together — a scenario is meaningless without the template it defaults to, and a template's
 * break-glass flag only matters against the scenarios that will carry it. Splitting them into two
 * sidebar entries would make an operator cross-reference by hand what belongs side by side.
 *
 * Neither register is filtered by the service beyond the site, and neither has a lifecycle
 * transition — a template cannot be retired through any endpoint. Both are recorded as gaps.
 */
const EmergencyTemplatesPage = () => {
  const navigate = useNavigate();
  const { notifySuccess } = useNotifier();

  const [siteCode, setSiteCode] = useState(defaultSite);
  const [tab, setTab] = useState('templates');
  const [search, setSearch] = useState('');
  const [creatingTemplate, setCreatingTemplate] = useState(false);
  const [creatingScenario, setCreatingScenario] = useState(false);

  const records = useSiteRecords(siteCode);

  const needle = search.trim().toLowerCase();

  const templates = useMemo(
    () =>
      records.templates.filter(
        (template) =>
          !needle ||
          template.title.toLowerCase().includes(needle) ||
          template.templateCode.toLowerCase().includes(needle) ||
          template.body.toLowerCase().includes(needle),
      ),
    [records.templates, needle],
  );

  const scenarios = useMemo(
    () =>
      records.scenarios.filter(
        (scenario) =>
          !needle ||
          scenario.name.toLowerCase().includes(needle) ||
          scenario.scenarioCode.toLowerCase().includes(needle),
      ),
    [records.scenarios, needle],
  );

  const breakGlassTemplates = records.templates.filter((template) => template.breakGlassEligible);
  const breakGlassScenarios = records.scenarios.filter((scenario) => scenario.breakGlassEligible);

  const templateColumns = useMemo<Column<NotificationTemplate>[]>(
    () => [
      {
        key: 'template',
        header: 'Template',
        width: 300,
        cell: (row) => (
          <CellStack primary={`${row.templateCode} · ${row.title}`} secondary={row.body} />
        ),
      },
      {
        key: 'channels',
        header: 'Channels',
        width: 220,
        cell: (row) => listChannels(row.channels),
      },
      {
        key: 'breakGlass',
        header: 'Break glass',
        width: 140,
        align: 'center',
        cell: (row) =>
          row.breakGlassEligible ? (
            <StatusChip value="BREAK_GLASS" label="Eligible" tone="blocked" />
          ) : (
            <span className="text-gray-500">—</span>
          ),
      },
      {
        key: 'lifecycle',
        header: 'Lifecycle',
        width: 120,
        hideBelowLg: true,
        cell: (row) => <StatusChip value={row.lifecycle} />,
      },
      {
        key: 'created',
        header: 'Created',
        width: 160,
        align: 'right',
        hideBelowLg: true,
        cell: (row) => formatDateTime(row.metadata.createdAt),
      },
    ],
    [],
  );

  const scenarioColumns = useMemo<Column<EmergencyScenario>[]>(
    () => [
      {
        key: 'scenario',
        header: 'Scenario',
        width: 300,
        cell: (row) => (
          <CellStack
            primary={`${row.scenarioCode} · ${row.name}`}
            secondary={
              row.defaultTemplateId
                ? `Defaults to ${records.templateName(row.defaultTemplateId)}`
                : 'No default template'
            }
          />
        ),
      },
      {
        key: 'priority',
        header: 'Priority',
        width: 120,
        cell: (row) => <StatusChip value={row.priority} />,
      },
      {
        key: 'breakGlass',
        header: 'Break glass',
        width: 140,
        align: 'center',
        cell: (row) =>
          row.breakGlassEligible ? (
            <StatusChip value="BREAK_GLASS" label="Eligible" tone="blocked" />
          ) : (
            <span className="text-gray-500">—</span>
          ),
      },
      {
        key: 'lifecycle',
        header: 'Lifecycle',
        width: 120,
        hideBelowLg: true,
        cell: (row) => <StatusChip value={row.lifecycle} />,
      },
      {
        key: 'created',
        header: 'Created',
        width: 160,
        align: 'right',
        hideBelowLg: true,
        cell: (row) => formatDateTime(row.metadata.createdAt),
      },
    ],
    [records],
  );

  return (
    <div>
      <PageHeader
        title="Templates and scenarios"
        subtitle="What a broadcast says, and the declared situations that cite it."
        crumbs={[
          { label: 'Emergency', to: emergencyPaths.dashboard },
          { label: 'Templates and scenarios' },
        ]}
        actions={
          <>
            <Button variant="primary" startIcon="plus" onClick={() => setCreatingTemplate(true)}>
              Create template
            </Button>
            <Button variant="outline" startIcon="plus" onClick={() => setCreatingScenario(true)}>
              Create scenario
            </Button>
            <Button variant="outline" startIcon="refresh" onClick={records.refetch}>
              Refresh
            </Button>
          </>
        }
      />

      <div className="mb-5">
        <SectionCard>
          <div className="grid gap-4 sm:grid-cols-2 lg:max-w-2xl">
            <SiteSelect value={siteCode} onChange={setSiteCode} required />
            <TextInput
              label="Search"
              value={search}
              onChange={setSearch}
              placeholder="Code, title or message text"
              helperText="Filters the loaded records."
            />
          </div>
        </SectionCard>
      </div>

      {(breakGlassTemplates.length > 0 || breakGlassScenarios.length > 0) && (
        <Alert
          variant="warning"
          title={`${breakGlassTemplates.length} template${breakGlassTemplates.length === 1 ? '' : 's'} and ${breakGlassScenarios.length} scenario${breakGlassScenarios.length === 1 ? '' : 's'} can bypass approval`}
          className="mb-5"
        >
          A break-glass send is allowed when the template <strong>or</strong> the scenario is
          eligible. Every combination that pairs one of these with anything else is a broadcast that
          can go out to this site with nobody approving it.
        </Alert>
      )}

      <SectionCard flush>
        <div className="px-5 pt-4">
          <Tabs
            value={tab}
            onChange={setTab}
            items={[
              { value: 'templates', label: 'Templates', count: records.templates.length },
              { value: 'scenarios', label: 'Scenarios', count: records.scenarios.length },
            ]}
          />
        </div>

        <DataState loading={records.initialising} minHeight={300}>
          {tab === 'templates' ? (
            <DataTable
              rows={templates}
              columns={templateColumns}
              getRowId={(row) => row.id}
              loading={records.loading}
              onRowClick={(row) => navigate(emergencyPaths.templateDetail(row.id))}
              caption="Notification templates at this site, with their channels, break-glass eligibility, lifecycle and creation time."
              emptyMessage="No template matches this search."
            />
          ) : (
            <DataTable
              rows={scenarios}
              columns={scenarioColumns}
              getRowId={(row) => row.id}
              loading={records.loading}
              caption="Emergency scenarios at this site, with their default template, priority, break-glass eligibility, lifecycle and creation time."
              emptyMessage="No scenario matches this search."
            />
          )}
        </DataState>

        <div className="flex items-start gap-1.5 px-5 pt-2 pb-4 text-theme-xs text-gray-600">
          <Icon name="info" size={13} className="mt-0.5 shrink-0 text-teal-700" />
          <span>
            {tab === 'templates'
              ? 'A template cannot be edited or retired: the service exposes creation and reads only. Supersede one by creating its replacement.'
              : 'A scenario cannot be edited or retired either. Both registers are create-and-read, so an obsolete record stays visible and selectable.'}
          </span>
        </div>
      </SectionCard>

      {creatingTemplate && (
        <CreateTemplateDialog
          open
          defaultSiteCode={siteCode}
          onClose={() => setCreatingTemplate(false)}
          onSaved={(template) => {
            notifySuccess(`${template.templateCode} created.`);
            records.refetch();
          }}
        />
      )}

      {creatingScenario && (
        <CreateScenarioDialog
          open
          defaultSiteCode={siteCode}
          templates={records.templates}
          onClose={() => setCreatingScenario(false)}
          onSaved={(scenario) => {
            notifySuccess(`${scenario.scenarioCode} created.`);
            records.refetch();
          }}
        />
      )}
    </div>
  );
};

export default EmergencyTemplatesPage;
