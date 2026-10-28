import type { ReactNode } from "react";

function Svg({ children, size = 16 }: { children: ReactNode; size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {children}
    </svg>
  );
}

export const IconOverview = () => (
  <Svg>
    <rect x="3" y="3" width="7" height="9" rx="1.5" />
    <rect x="14" y="3" width="7" height="5" rx="1.5" />
    <rect x="14" y="12" width="7" height="9" rx="1.5" />
    <rect x="3" y="16" width="7" height="5" rx="1.5" />
  </Svg>
);

export const IconFlask = () => (
  <Svg>
    <path d="M9 3h6" />
    <path d="M10 3v6.2L4.6 18a2 2 0 0 0 1.7 3h11.4a2 2 0 0 0 1.7-3L14 9.2V3" />
    <path d="M7.5 14h9" />
  </Svg>
);

export const IconSliders = () => (
  <Svg>
    <path d="M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0" />
    <circle cx="16" cy="6" r="2" />
    <circle cx="10" cy="12" r="2" />
    <circle cx="18" cy="18" r="2" />
  </Svg>
);

export const IconList = () => (
  <Svg>
    <path d="M8 6h13M8 12h13M8 18h13" />
    <path d="M3 6h.01M3 12h.01M3 18h.01" />
  </Svg>
);

export const IconTrend = () => (
  <Svg>
    <path d="m3 17 6-6 4 4 8-8" />
    <path d="M15 7h6v6" />
  </Svg>
);

export const IconGithub = () => (
  <Svg>
    <path d="M9 19c-4.3 1.4-4.3-2.5-6-3m12 5v-3.5c0-1 .1-1.4-.5-2 2.8-.3 5.5-1.4 5.5-6a4.6 4.6 0 0 0-1.3-3.2 4.2 4.2 0 0 0-.1-3.2s-1.1-.3-3.5 1.3a12.3 12.3 0 0 0-6.2 0C6.5 2.8 5.4 3.1 5.4 3.1a4.2 4.2 0 0 0-.1 3.2A4.6 4.6 0 0 0 4 9.5c0 4.6 2.7 5.7 5.5 6-.6.6-.6 1.2-.5 2V21" />
  </Svg>
);

export const IconArrowRight = () => (
  <Svg size={14}>
    <path d="M5 12h14M13 6l6 6-6 6" />
  </Svg>
);

/** Before and after: the tall bar is the query as it was, the short one is what the index made of it. */
export const IconMark = () => (
  <svg width="22" height="22" viewBox="0 0 22 22" aria-hidden="true">
    <defs>
      <linearGradient id="mark-g" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0" stopColor="#0070f3" />
        <stop offset="1" stopColor="#7928ca" />
      </linearGradient>
    </defs>
    <rect x="3" y="3" width="6" height="16" rx="2" fill="url(#mark-g)" />
    <rect x="13" y="10" width="6" height="9" rx="2" fill="#17b26a" />
  </svg>
);

export const IconSearch = () => (
  <Svg size={14}>
    <circle cx="11" cy="11" r="7" />
    <path d="m20 20-3.5-3.5" />
  </Svg>
);

export const IconDownload = () => (
  <Svg size={14}>
    <path d="M12 4v11M7 10l5 5 5-5M5 20h14" />
  </Svg>
);
