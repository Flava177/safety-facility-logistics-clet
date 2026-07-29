import { ReactElement, SVGProps } from 'react';

/**
 * The console's icon set.
 *
 * Drawn inline rather than fetched from an icon CDN at runtime: this console runs inside the
 * directorate network, and an icon library that resolves over the public internet is a screen full
 * of empty squares the day that host is unreachable. Every glyph here is a stroked 24x24 outline,
 * so a new one can be added without matching a font or a sprite sheet.
 */

export type IconName =
  | 'dashboard'
  | 'truck'
  | 'driver'
  | 'route'
  | 'workflow'
  | 'shield-check'
  | 'document'
  | 'cloud'
  | 'refresh'
  | 'plus'
  | 'user-plus'
  | 'chevron-right'
  | 'chevron-left'
  | 'chevron-down'
  | 'chevron-up'
  | 'chevrons-left'
  | 'chevrons-right'
  | 'search'
  | 'bell'
  | 'menu'
  | 'close'
  | 'calendar'
  | 'clock'
  | 'alert-circle'
  | 'alert-triangle'
  | 'check-circle'
  | 'info'
  | 'arrow-left'
  | 'download'
  | 'edit'
  | 'more'
  | 'filter'
  | 'map-pin'
  | 'wrench'
  | 'activity'
  | 'gauge'
  | 'play'
  | 'stop'
  | 'flag'
  | 'link'
  | 'lock'
  | 'inbox'
  | 'clipboard'
  | 'user'
  | 'fuel'
  | 'book'
  | 'scale'
  | 'upload'
  | 'coins';

const glyphs: Record<IconName, ReactElement> = {
  dashboard: (
    <>
      <rect x="3" y="3" width="7" height="8" rx="1.5" />
      <rect x="14" y="3" width="7" height="5" rx="1.5" />
      <rect x="14" y="11" width="7" height="10" rx="1.5" />
      <rect x="3" y="14" width="7" height="7" rx="1.5" />
    </>
  ),
  truck: (
    <>
      <path d="M3 6.5h10.5v9H3z" />
      <path d="M13.5 9.5h3.7l2.8 3v3h-6.5z" />
      <circle cx="7" cy="17.5" r="1.8" />
      <circle cx="16.5" cy="17.5" r="1.8" />
      <path d="M8.8 17.5h5.9" />
    </>
  ),
  driver: (
    <>
      <rect x="3" y="4.5" width="18" height="15" rx="2.5" />
      <circle cx="9" cy="10.5" r="2.2" />
      <path d="M5.6 16.4c.6-1.7 1.9-2.6 3.4-2.6s2.8.9 3.4 2.6" />
      <path d="M15 9.8h4M15 13h3" />
    </>
  ),
  route: (
    <>
      <circle cx="6" cy="18" r="2.4" />
      <circle cx="18" cy="6" r="2.4" />
      <path d="M15.6 6H10a3.5 3.5 0 0 0 0 7h4a3.5 3.5 0 0 1 0 7H8.4" />
    </>
  ),
  workflow: (
    <>
      <path d="M8 4H6a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2h-2" />
      <rect x="9" y="2.6" width="6" height="3.4" rx="1.2" />
      <path d="M8.5 12.5l2 2 4.5-4.5" />
    </>
  ),
  'shield-check': (
    <>
      <path d="M12 3l7 3v5.5c0 4.3-2.9 8-7 9.5-4.1-1.5-7-5.2-7-9.5V6z" />
      <path d="M9 12l2.2 2.2L15.4 10" />
    </>
  ),
  document: (
    <>
      <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" />
      <path d="M14 3v5h5" />
      <path d="M8.5 13h7M8.5 16.5h5" />
    </>
  ),
  cloud: (
    <>
      <path d="M7 18a4 4 0 0 1-.6-7.95A5.5 5.5 0 0 1 17.2 9.4 3.8 3.8 0 0 1 17 18z" />
    </>
  ),
  refresh: (
    <>
      <path d="M20 11a8 8 0 0 0-13.7-5.2L4 8" />
      <path d="M4 4v4h4" />
      <path d="M4 13a8 8 0 0 0 13.7 5.2L20 16" />
      <path d="M20 20v-4h-4" />
    </>
  ),
  plus: <path d="M12 5v14M5 12h14" />,
  'user-plus': (
    <>
      <circle cx="9.5" cy="8" r="3.2" />
      <path d="M3.5 19.5c.5-3.2 3-5 6-5s5.5 1.8 6 5" />
      <path d="M18 6.5v5M15.5 9h5" />
    </>
  ),
  'chevron-right': <path d="M9.5 5.5l6.5 6.5-6.5 6.5" />,
  'chevron-left': <path d="M14.5 5.5L8 12l6.5 6.5" />,
  'chevron-down': <path d="M5.5 9.5L12 16l6.5-6.5" />,
  'chevron-up': <path d="M5.5 14.5L12 8l6.5 6.5" />,
  'chevrons-left': <path d="M17 6l-6 6 6 6M11 6l-6 6 6 6" />,
  'chevrons-right': <path d="M7 6l6 6-6 6M13 6l6 6-6 6" />,
  search: (
    <>
      <circle cx="10.8" cy="10.8" r="6.3" />
      <path d="M15.5 15.5L20 20" />
    </>
  ),
  bell: (
    <>
      <path d="M18 15.5V10a6 6 0 1 0-12 0v5.5L4.5 18h15z" />
      <path d="M10 20.5a2.2 2.2 0 0 0 4 0" />
    </>
  ),
  menu: <path d="M4 7h16M4 12h16M4 17h16" />,
  close: <path d="M6 6l12 12M18 6L6 18" />,
  calendar: (
    <>
      <rect x="3.5" y="5" width="17" height="15.5" rx="2.5" />
      <path d="M3.5 10h17M8.5 3.2v3.6M15.5 3.2v3.6" />
    </>
  ),
  clock: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.2V12l3.2 2" />
    </>
  ),
  'alert-circle': (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.8v4.8" />
      <circle cx="12" cy="16" r="0.9" fill="currentColor" stroke="none" />
    </>
  ),
  'alert-triangle': (
    <>
      <path d="M12 4.2L21 19.4H3z" />
      <path d="M12 10v3.6" />
      <circle cx="12" cy="16.6" r="0.9" fill="currentColor" stroke="none" />
    </>
  ),
  'check-circle': (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M8.4 12.2l2.5 2.5 4.8-5" />
    </>
  ),
  info: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 11.4V16" />
      <circle cx="12" cy="8.2" r="0.9" fill="currentColor" stroke="none" />
    </>
  ),
  'arrow-left': <path d="M19 12H5M11 6l-6 6 6 6" />,
  download: (
    <>
      <path d="M12 4v10" />
      <path d="M8 10.5l4 4 4-4" />
      <path d="M4.5 19.5h15" />
    </>
  ),
  edit: (
    <>
      <path d="M15.6 4.9l3.5 3.5" />
      <path d="M5 19h3.4L19.1 8.3a1.7 1.7 0 0 0 0-2.4l-1-1a1.7 1.7 0 0 0-2.4 0L5 15.6z" />
    </>
  ),
  more: (
    <>
      <circle cx="5.5" cy="12" r="1.3" fill="currentColor" stroke="none" />
      <circle cx="12" cy="12" r="1.3" fill="currentColor" stroke="none" />
      <circle cx="18.5" cy="12" r="1.3" fill="currentColor" stroke="none" />
    </>
  ),
  filter: <path d="M3.5 5h17l-6.6 7.6v5.6l-3.8 2.2v-7.8z" />,
  'map-pin': (
    <>
      <path d="M12 21s7-5.6 7-11a7 7 0 1 0-14 0c0 5.4 7 11 7 11z" />
      <circle cx="12" cy="10" r="2.6" />
    </>
  ),
  wrench: (
    <path d="M15.8 3.4a5.2 5.2 0 0 0-4.4 8.2L3.9 19a1.8 1.8 0 0 0 2.6 2.5l7.3-7.4a5.2 5.2 0 0 0 6.4-6.8l-3 3-2.6-2.6 3-3a5 5 0 0 0-1.8-1.3z" />
  ),
  activity: <path d="M3 12.5h4l2.8-7 4.2 14 2.6-7H21" />,
  gauge: (
    <>
      <path d="M4 17a8.5 8.5 0 1 1 16 0" />
      <path d="M12 17l4-5.2" />
      <circle cx="12" cy="17" r="1.1" fill="currentColor" stroke="none" />
    </>
  ),
  play: <path d="M8 5.5l10 6.5-10 6.5z" />,
  stop: <rect x="6.5" y="6.5" width="11" height="11" rx="2" />,
  flag: (
    <>
      <path d="M6 21V4" />
      <path d="M6 5h11l-2 3.5 2 3.5H6" />
    </>
  ),
  link: (
    <>
      <path d="M10.5 13.5a3.7 3.7 0 0 0 5.4 0l2.6-2.6a3.8 3.8 0 0 0-5.4-5.4l-1.4 1.4" />
      <path d="M13.5 10.5a3.7 3.7 0 0 0-5.4 0l-2.6 2.6a3.8 3.8 0 0 0 5.4 5.4l1.4-1.4" />
    </>
  ),
  lock: (
    <>
      <rect x="4.5" y="10.5" width="15" height="10" rx="2.5" />
      <path d="M8 10.5V8a4 4 0 0 1 8 0v2.5" />
    </>
  ),
  inbox: (
    <>
      <path d="M3.5 13.5h4l1.5 3h6l1.5-3h4" />
      <path d="M5.6 4.5h12.8l2.1 9v4a2 2 0 0 1-2 2H5.5a2 2 0 0 1-2-2v-4z" />
    </>
  ),
  clipboard: (
    <>
      <path d="M9 4.5H7a2 2 0 0 0-2 2V19a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V6.5a2 2 0 0 0-2-2h-2" />
      <rect x="9" y="2.8" width="6" height="3.4" rx="1.2" />
    </>
  ),
  user: (
    <>
      <circle cx="12" cy="8" r="3.4" />
      <path d="M5 20c.6-3.6 3.4-5.6 7-5.6s6.4 2 7 5.6" />
    </>
  ),
  // A pump with its hose — the fuel module's mark. Drawn at the same 1.7 stroke as the rest.
  fuel: (
    <>
      <path d="M4 20.5V5.5A2 2 0 0 1 6 3.5h5a2 2 0 0 1 2 2v15" />
      <path d="M3 20.5h11" />
      <path d="M6.5 8.5h6" />
      <path d="M13 10h3.5a1.5 1.5 0 0 1 1.5 1.5v5a1.6 1.6 0 0 0 3.2 0V8.2L19 5.8" />
    </>
  ),
  book: (
    <>
      <path d="M5 4.5A1.5 1.5 0 0 1 6.5 3H19v15H6.5A1.5 1.5 0 0 0 5 19.5z" />
      <path d="M5 19.5A1.5 1.5 0 0 1 6.5 18H19v3H6.5A1.5 1.5 0 0 1 5 19.5z" />
      <path d="M9 7.5h6M9 11h4" />
    </>
  ),
  scale: (
    <>
      <path d="M12 4v16M7.5 20h9" />
      <path d="M4 8.5h16" />
      <path d="M4 8.5L1.8 14a2.6 2.6 0 0 0 4.4 0z" />
      <path d="M20 8.5L17.8 14a2.6 2.6 0 0 0 4.4 0z" />
    </>
  ),
  upload: (
    <>
      <path d="M12 15.5V4.5" />
      <path d="M8 8.5l4-4 4 4" />
      <path d="M4.5 19.5h15" />
    </>
  ),
  coins: (
    <>
      <ellipse cx="9" cy="6.8" rx="5.5" ry="2.6" />
      <path d="M3.5 6.8v4.4c0 1.4 2.5 2.6 5.5 2.6s5.5-1.2 5.5-2.6V6.8" />
      <path d="M14.5 11.4c2.7.2 6 1.3 6 2.6v3.2c0 1.4-2.5 2.6-5.5 2.6s-5.5-1.2-5.5-2.6v-3.4" />
    </>
  ),
};

export interface IconProps extends Omit<SVGProps<SVGSVGElement>, 'name'> {
  name: IconName;
  /** Pixel size; the glyph is square. */
  size?: number;
}

const Icon = ({ name, size = 20, className, ...rest }: IconProps) => (
  <svg
    viewBox="0 0 24 24"
    width={size}
    height={size}
    fill="none"
    stroke="currentColor"
    strokeWidth={1.7}
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
    focusable="false"
    className={className}
    {...rest}
  >
    {glyphs[name]}
  </svg>
);

export default Icon;
