import { describe, it, expect, vi, beforeEach } from 'vitest';
import { getCaretCoordinates } from '../utils/caretPosition';

function createMockTextarea(value: string, styles: Record<string, string> = {}): HTMLTextAreaElement {
  const textarea = document.createElement('textarea');
  textarea.value = value;
  for (const [key, val] of Object.entries(styles)) {
    textarea.style.setProperty(key, val);
  }
  return textarea;
}

describe('getCaretCoordinates', () => {
  beforeEach(() => {
    const mockComputedStyle: Record<string, string> = {
      fontSize: '14px',
      lineHeight: '20px',
      fontFamily: 'monospace',
      direction: 'ltr',
      boxSizing: 'border-box',
      width: '400px',
      height: '100px',
      overflowX: 'hidden',
      overflowY: 'hidden',
      borderTopWidth: '1px',
      borderRightWidth: '1px',
      borderBottomWidth: '1px',
      borderLeftWidth: '1px',
      borderStyle: 'solid',
      paddingTop: '8px',
      paddingRight: '8px',
      paddingBottom: '8px',
      paddingLeft: '8px',
      fontStyle: 'normal',
      fontVariant: 'normal',
      fontWeight: '400',
      fontStretch: 'normal',
      fontSizeAdjust: 'none',
      textAlign: 'left',
      textTransform: 'none',
      textIndent: '0',
      textDecoration: 'none',
      letterSpacing: '0',
      wordSpacing: '0',
      tabSize: '4',
    };

    vi.spyOn(window, 'getComputedStyle').mockReturnValue({
      getPropertyValue: (prop: string) => mockComputedStyle[prop] ?? '',
      fontSize: mockComputedStyle.fontSize,
      lineHeight: mockComputedStyle.lineHeight,
      fontFamily: mockComputedStyle.fontFamily,
      direction: mockComputedStyle.direction,
      boxSizing: mockComputedStyle.boxSizing,
      width: mockComputedStyle.width,
      height: mockComputedStyle.height,
      overflowX: mockComputedStyle.overflowX,
      overflowY: mockComputedStyle.overflowY,
      borderTopWidth: mockComputedStyle.borderTopWidth,
      borderRightWidth: mockComputedStyle.borderRightWidth,
      borderBottomWidth: mockComputedStyle.borderBottomWidth,
      borderLeftWidth: mockComputedStyle.borderLeftWidth,
      borderStyle: mockComputedStyle.borderStyle,
      paddingTop: mockComputedStyle.paddingTop,
      paddingRight: mockComputedStyle.paddingRight,
      paddingBottom: mockComputedStyle.paddingBottom,
      paddingLeft: mockComputedStyle.paddingLeft,
      fontStyle: mockComputedStyle.fontStyle,
      fontVariant: mockComputedStyle.fontVariant,
      fontWeight: mockComputedStyle.fontWeight,
      fontStretch: mockComputedStyle.fontStretch,
      fontSizeAdjust: mockComputedStyle.fontSizeAdjust,
      textAlign: mockComputedStyle.textAlign,
      textTransform: mockComputedStyle.textTransform,
      textIndent: mockComputedStyle.textIndent,
      textDecoration: mockComputedStyle.textDecoration,
      letterSpacing: mockComputedStyle.letterSpacing,
      wordSpacing: mockComputedStyle.wordSpacing,
      tabSize: mockComputedStyle.tabSize,
    } as unknown as CSSStyleDeclaration);
  });

  it('returns coordinates with top, left, and lineHeight', () => {
    const textarea = createMockTextarea('hello world');
    const coords = getCaretCoordinates(textarea, 5);
    expect(coords).toHaveProperty('top');
    expect(coords).toHaveProperty('left');
    expect(coords).toHaveProperty('lineHeight');
  });

  it('parses lineHeight from computed style', () => {
    const textarea = createMockTextarea('test');
    const coords = getCaretCoordinates(textarea, 0);
    expect(coords.lineHeight).toBe(20);
  });

  it('handles position 0', () => {
    const textarea = createMockTextarea('hello');
    const coords = getCaretCoordinates(textarea, 0);
    expect(typeof coords.top).toBe('number');
    expect(typeof coords.left).toBe('number');
  });

  it('handles position at end of text', () => {
    const textarea = createMockTextarea('hello');
    const coords = getCaretCoordinates(textarea, 5);
    expect(typeof coords.top).toBe('number');
    expect(typeof coords.left).toBe('number');
  });

  it('handles trailing newline', () => {
    const textarea = createMockTextarea('hello\n');
    const coords = getCaretCoordinates(textarea, 6);
    expect(typeof coords.top).toBe('number');
    expect(typeof coords.left).toBe('number');
  });
});
