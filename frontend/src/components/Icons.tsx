import type { CSSProperties, ReactNode, SVGProps } from 'react';

type IconProps = SVGProps<SVGSVGElement> & {
  size?: number;
  title?: string;
};

function BaseIcon({
  size = 16,
  title,
  children,
  strokeWidth = 1.3,
  ...props
}: IconProps & { children: ReactNode }) {
  return (
    <svg
      viewBox="0 0 16 16"
      width={size}
      height={size}
      fill="none"
      aria-hidden={title ? undefined : 'true'}
      role={title ? 'img' : undefined}
      {...props}
    >
      {title ? <title>{title}</title> : null}
      <g stroke="currentColor" strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round">
        {children}
      </g>
    </svg>
  );
}

export function IconFrame({ size = 16, style, children }: { size?: number; style?: CSSProperties; children: ReactNode }) {
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: size,
        height: size,
        flexShrink: 0,
        ...style,
      }}
    >
      {children}
    </span>
  );
}

export function FolderIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M1.5 4.5a1 1 0 0 1 1-1h3l1.2 1.5H13.5a1 1 0 0 1 1 1v5.5a1 1 0 0 1-1 1h-11a1 1 0 0 1-1-1z" />
    </BaseIcon>
  );
}

export function PanelFoldIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M2.5 2.5v11" />
      <rect x="4.25" y="2.5" width="9.25" height="11" rx="1.5" />
      <path d="m8.25 8 2-2M8.25 8l2 2" />
    </BaseIcon>
  );
}

export function TerminalIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <rect x="1.75" y="2.5" width="12.5" height="11" rx="2" />
      <path d="m4.5 6 2 2-2 2M8.5 10h3" />
    </BaseIcon>
  );
}

export function FileIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M4 1.75h5.5l2.75 2.75v9A.75.75 0 0 1 11.5 14h-7a.75.75 0 0 1-.75-.75v-10.5A.75.75 0 0 1 4.5 1.75z" />
      <path d="M9.5 1.75V5h2.75" />
    </BaseIcon>
  );
}

function LabeledFileIcon({ label, ...props }: IconProps & { label: string }) {
  const { size = 16, title, ...svgProps } = props;
  return (
    <svg
      viewBox="0 0 16 16"
      width={size}
      height={size}
      fill="none"
      aria-hidden={title ? undefined : 'true'}
      role={title ? 'img' : undefined}
      {...svgProps}
    >
      {title ? <title>{title}</title> : null}
      <path d="M4 1.75h5.5l2.75 2.75v9A.75.75 0 0 1 11.5 14h-7a.75.75 0 0 1-.75-.75v-10.5A.75.75 0 0 1 4.5 1.75z" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M9.5 1.75V5h2.75" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round" />
      <text x="8" y="11.2" textAnchor="middle" fontSize="4.6" fontWeight="700" fill="currentColor" fontFamily="system-ui, sans-serif">{label}</text>
    </svg>
  );
}

export function TypescriptFileIcon(props: IconProps) {
  return <LabeledFileIcon {...props} label="TS" />;
}

export function JavaFileIcon(props: IconProps) {
  return <LabeledFileIcon {...props} label="JV" />;
}

export function MarkdownFileIcon(props: IconProps) {
  return <LabeledFileIcon {...props} label="MD" />;
}

export function JsonFileIcon(props: IconProps) {
  return <LabeledFileIcon {...props} label="{}" />;
}

export function SearchIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <circle cx="7" cy="7" r="3.75" />
      <path d="m10 10 3 3" />
    </BaseIcon>
  );
}

export function SettingsIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <circle cx="8" cy="8" r="2.15" />
      <path d="M8 1.9v1.55M8 12.55v1.55M14.1 8h-1.55M3.45 8H1.9M12.32 3.68l-1.1 1.1M4.78 11.22l-1.1 1.1M12.32 12.32l-1.1-1.1M4.78 4.78l-1.1-1.1" />
      <circle cx="8" cy="8" r="4.1" />
    </BaseIcon>
  );
}

export function ChatBubbleIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M3 3.25h10a1 1 0 0 1 1 1v5.75a1 1 0 0 1-1 1H8l-3.25 2.25v-2.25H3a1 1 0 0 1-1-1V4.25a1 1 0 0 1 1-1z" />
    </BaseIcon>
  );
}

export function UserIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <circle cx="8" cy="5.3" r="2.1" />
      <path d="M3.5 12.75c.85-2.05 2.55-3.05 4.5-3.05s3.65 1 4.5 3.05" />
    </BaseIcon>
  );
}

export function BotIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <rect x="3" y="4.25" width="10" height="7.5" rx="2" />
      <path d="M8 2.25v2M5.75 8h.01M10.25 8h.01M6 10h4" />
    </BaseIcon>
  );
}

export function CommandIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M5.25 2.5a1.75 1.75 0 1 0 0 3.5h5.5a1.75 1.75 0 1 1 0 3.5h-5.5a1.75 1.75 0 1 0 0 3.5M5.25 2.5v11M10.75 2.5v11M2.5 5.25h11M2.5 10.75h11" />
    </BaseIcon>
  );
}

export function CheckIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="m3.5 8.25 2.5 2.5 6-6" />
    </BaseIcon>
  );
}

export function EditIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M3.25 12.75h2.6l6.15-6.15-2.6-2.6-6.15 6.15z" />
      <path d="m8.95 4.45 2.6 2.6M3.25 12.75l1.15-3.75" />
    </BaseIcon>
  );
}

export function CopyIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <rect x="5.25" y="3.25" width="7" height="8.5" rx="1.2" />
      <path d="M10.75 12.25H4.9A1.15 1.15 0 0 1 3.75 11.1V4.9A1.15 1.15 0 0 1 4.9 3.75" />
    </BaseIcon>
  );
}

export function SpinnerIcon(props: IconProps) {
  return (
    <BaseIcon {...props} style={{ animation: 'spin 1s linear infinite', ...(props.style ?? {}) }}>
      <path d="M13 8a5 5 0 1 1-1.46-3.54" />
    </BaseIcon>
  );
}

export function PackageIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M8 2.25 13 4.8v6.4L8 13.75 3 11.2V4.8z" />
      <path d="M8 2.25v5.5M3 4.8 8 7.75l5-2.95" />
    </BaseIcon>
  );
}

export function InfoIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <circle cx="8" cy="8" r="5.5" />
      <path d="M8 7.1v3.2M8 5.2h.01" />
    </BaseIcon>
  );
}

export function WarningIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M8 2.5 13.5 12H2.5z" />
      <path d="M8 5.5v3.2M8 10.8h.01" />
    </BaseIcon>
  );
}

export function SendIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M2.5 13.5 13.5 8 2.5 2.5l2.25 5.5z" />
      <path d="M4.75 8h8.25" />
    </BaseIcon>
  );
}

export function StopIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <rect x="3.5" y="3.5" width="9" height="9" rx="1.5" />
    </BaseIcon>
  );
}

export function ChevronDownIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="m4.25 6.25 3.75 3.75 3.75-3.75" />
    </BaseIcon>
  );
}

export function ChevronUpIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="m4.25 9.75 3.75-3.75 3.75 3.75" />
    </BaseIcon>
  );
}

export function ChevronRightIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="m6.25 4.25 3.75 3.75-3.75 3.75" />
    </BaseIcon>
  );
}

export function CloseIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="m4.25 4.25 7.5 7.5M11.75 4.25l-7.5 7.5" />
    </BaseIcon>
  );
}

export function MoreHorizontalIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M4 8h.01M8 8h.01M12 8h.01" />
    </BaseIcon>
  );
}

export function PlusIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M8 3v10M3 8h10" />
    </BaseIcon>
  );
}

export function StatusActiveIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <circle cx="8" cy="8" r="2.5" fill="currentColor" stroke="none" />
    </BaseIcon>
  );
}

export function StatusIdleIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <circle cx="8" cy="8" r="2.5" />
    </BaseIcon>
  );
}

export function ServiceStatusIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <rect x="2.25" y="3" width="11.5" height="2.5" rx="1" />
      <rect x="2.25" y="6.75" width="11.5" height="2.5" rx="1" />
      <rect x="2.25" y="10.5" width="11.5" height="2.5" rx="1" />
      <circle cx="4.5" cy="4.25" r="0.6" fill="currentColor" stroke="none" />
      <circle cx="8" cy="8" r="0.6" fill="currentColor" stroke="none" />
      <circle cx="11.5" cy="11.75" r="0.6" fill="currentColor" stroke="none" />
    </BaseIcon>
  );
}
