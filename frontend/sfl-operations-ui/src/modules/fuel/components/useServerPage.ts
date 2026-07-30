/**
 * The fuel registers' paging helper — now a thin binding over the shared one.
 *
 * Dispatch and emergency needed the same behaviour once their collections started paging, so the
 * implementation moved to `shared/hooks/useServerPage`. This file stays so the fuel screens keep
 * importing from their own module, and so the module's default page size is applied in one place.
 */
import { DEFAULT_PAGE_SIZE } from 'modules/fuel/api/fuelApi';
import {
  useClampPage,
  useServerPage as useSharedServerPage,
  type ServerPage,
} from 'shared/hooks/useServerPage';

export type { ServerPage };
export { useClampPage };

export function useServerPage(filterKey: string, initialSize: number = DEFAULT_PAGE_SIZE): ServerPage {
  return useSharedServerPage(filterKey, initialSize);
}
