import SharedWindowNotice from 'shared/components/WindowNotice';

interface WindowNoticeProps {
  truncated: boolean;
  total: number;
  requestedSize: number;
  /** "items", "manifests", "exception cases" — used in the sentence. */
  noun: string;
}

/**
 * What a dispatch register is actually showing — the shared notice, naming the dispatch service.
 *
 * The banner itself moved to `shared/components/WindowNotice` when S174 turned out to need the
 * same warning for the same reason. This wrapper keeps the dispatch screens' call sites unchanged
 * and names the service once, rather than at eleven of them.
 *
 * Recorded as gap 1 in `docs/dispatch/S171_Dispatch_Frontend_Gap_Register.md`. It disappears the
 * day the dispatch endpoints return a paged envelope, exactly as the fuel one did.
 */
const WindowNotice = (props: WindowNoticeProps) => (
  <SharedWindowNotice {...props} service="dispatch service" />
);

export default WindowNotice;
