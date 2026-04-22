const mirrorStyleKeys = [
  'direction',
  'boxSizing',
  'width',
  'height',
  'overflowX',
  'overflowY',
  'borderTopWidth',
  'borderRightWidth',
  'borderBottomWidth',
  'borderLeftWidth',
  'borderStyle',
  'paddingTop',
  'paddingRight',
  'paddingBottom',
  'paddingLeft',
  'fontStyle',
  'fontVariant',
  'fontWeight',
  'fontStretch',
  'fontSize',
  'fontSizeAdjust',
  'lineHeight',
  'fontFamily',
  'textAlign',
  'textTransform',
  'textIndent',
  'textDecoration',
  'letterSpacing',
  'wordSpacing',
  'tabSize',
  'MozTabSize',
] as const;

export interface CaretCoordinates {
  top: number;
  left: number;
  lineHeight: number;
}

export function getCaretCoordinates(
  textarea: HTMLTextAreaElement,
  position: number,
): CaretCoordinates {
  const computed = window.getComputedStyle(textarea);
  const mirror = document.createElement('span');
  mirror.setAttribute('aria-hidden', 'true');
  mirror.style.position = 'absolute';
  mirror.style.top = '0';
  mirror.style.left = '-9999px';
  mirror.style.visibility = 'hidden';
  mirror.style.whiteSpace = 'pre-wrap';
  mirror.style.wordWrap = 'break-word';

  for (const key of mirrorStyleKeys) {
    mirror.style.setProperty(key, computed.getPropertyValue(key));
  }

  const textBeforeCursor = textarea.value.substring(0, position);
  mirror.textContent = textBeforeCursor.endsWith('\n') ? textBeforeCursor + ' ' : textBeforeCursor;

  const marker = document.createElement('span');
  marker.textContent = '\u200b';
  mirror.appendChild(marker);

  document.body.appendChild(mirror);

  const markerRect = marker.getBoundingClientRect();
  const mirrorRect = mirror.getBoundingClientRect();

  const coordinates: CaretCoordinates = {
    top: markerRect.top - mirrorRect.top,
    left: markerRect.left - mirrorRect.left,
    lineHeight: parseFloat(computed.lineHeight) || parseInt(computed.fontSize, 10) * 1.2,
  };

  document.body.removeChild(mirror);

  return coordinates;
}
