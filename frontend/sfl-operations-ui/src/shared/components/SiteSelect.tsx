import { sflActor } from 'shared/api/config';
import { SelectInput } from './fields';

/**
 * The actor's site scope, parsed once.
 *
 * `sflActor.sites` is the comma-separated value the console sends as `X-SFL-Sites` on every
 * request, so it is by definition the complete set of sites this operator may read or write. Both
 * the filters and the request fields derive from it, which is why it lives beside the control
 * rather than being re-parsed in each of the nine screens that need it.
 */
export const sflSites: string[] = sflActor.sites
  .split(',')
  .map((site) => site.trim())
  .filter(Boolean);

/** The site a form opens on. */
export const defaultSite: string = sflSites[0] ?? '';

interface SiteSelectProps {
  label?: string;
  value: string;
  onChange: (value: string) => void;
  required?: boolean;
  error?: boolean;
  helperText?: string;
  onBlur?: () => void;
  disabled?: boolean;
  className?: string;
  /** Adds an "all sites" choice — use for a filter, never for a request field. */
  allowEmpty?: boolean;
  emptyLabel?: string;
}

/**
 * Site scope as a choice rather than free text.
 *
 * Every write carries a site code, and the service refuses one outside the actor's scope with
 * `FLEET_UNAUTHORIZED_SCOPE` — a rule the operator only discovered by typing a neighbouring site
 * and having the submission bounced. The options are the actor's own sites, so the console can no
 * longer offer a site it cannot write to. Its props mirror `TextInput` so it drops into the same
 * grid without any other change.
 */
const SiteSelect = ({
  label = 'Site code',
  emptyLabel = 'All sites',
  ...rest
}: SiteSelectProps) => (
  <SelectInput
    {...rest}
    label={label}
    emptyLabel={emptyLabel}
    options={sflSites.map((site) => ({ value: site, label: site }))}
  />
);

export default SiteSelect;
