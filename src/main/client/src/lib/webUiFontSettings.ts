import type { CSSProperties } from 'react';
import type { WebSettings } from '../types/api';

export const FONT_FAMILY_OPTIONS = [
  { value: 'sans', label: 'Sans' },
  { value: 'mono', label: 'Mono' },
] as const;

export const FONT_SIZE_OPTIONS = [11, 12, 13, 14, 15, 16, 18] as const;

const DEFAULT_FONT_FAMILY = 'sans';
const DEFAULT_FONT_SIZE = 13;
const MONO_FONT_STACK = "ui-monospace, 'SFMono-Regular', Menlo, Monaco, Consolas, 'Liberation Mono', monospace";

function resolveFontFamilyToken(fontFamily: string | null | undefined): string {
  return fontFamily === 'mono' ? MONO_FONT_STACK : 'var(--font-sans)';
}

function resolveFontSize(fontSize: number | null | undefined): number {
  if (typeof fontSize !== 'number' || !Number.isFinite(fontSize)) {
    return DEFAULT_FONT_SIZE;
  }
  return Math.max(11, Math.min(18, Math.round(fontSize)));
}

export function getSidebarFontScopeStyle(settings: WebSettings | null | undefined): CSSProperties {
  const sidebarFontSize = resolveFontSize(settings?.sidebarFontSize);
  return {
    '--sidebar-font-family': resolveFontFamilyToken(settings?.sidebarFontFamily ?? DEFAULT_FONT_FAMILY),
    '--sidebar-font-size': `${sidebarFontSize}px`,
  } as CSSProperties;
}

export function getChatFontScopeStyle(settings: WebSettings | null | undefined): CSSProperties {
  const chatFontSize = resolveFontSize(settings?.chatFontSize);
  return {
    '--chat-font-family': resolveFontFamilyToken(settings?.chatFontFamily ?? DEFAULT_FONT_FAMILY),
    '--chat-font-size': `${chatFontSize}px`,
  } as CSSProperties;
}
