export interface ContentSegment {
  type: 'prose' | 'code';
  text: string;
}

export interface StructuredSection {
  title: string;
  content: string;
  segments: ContentSegment[];
  collapsible: boolean;
}

export interface StructuredMessage {
  isStructured: boolean;
  summary: string | null;
  plainText: string;
  overviewText: string;
  overview: ContentSegment[];
  sections: StructuredSection[];
  plainSegments: ContentSegment[];
}

const SUMMARY_PATTERN = /^\*\*(Proposed Change|Change Summary):\s*(.+?)\*\*$/;

export function splitMessageContent(text: string): ContentSegment[] {
  const segments: ContentSegment[] = [];
  const parts = text.split(/(```[\s\S]*?```)/g);
  for (const part of parts) {
    if (!part) {
      continue;
    }
    if (part.startsWith('```') && part.endsWith('```')) {
      const inner = part.slice(3, -3);
      const firstNewline = inner.indexOf('\n');
      const codeText = firstNewline >= 0 ? inner.slice(firstNewline + 1) : inner;
      segments.push({ type: 'code', text: codeText });
      continue;
    }
    if (part.length > 0) {
      segments.push({ type: 'prose', text: part });
    }
  }
  return segments;
}

export function parseStructuredMessage(text: string): StructuredMessage {
  const normalized = text.replace(/\r\n/g, '\n');
  const lines = normalized.split('\n');
  const firstNonEmptyIndex = lines.findIndex((line) => line.trim().length > 0);

  if (firstNonEmptyIndex < 0) {
    return emptyStructuredMessage();
  }

  const summaryMatch = lines[firstNonEmptyIndex]?.trim().match(SUMMARY_PATTERN);
  if (!summaryMatch) {
    return {
      isStructured: false,
      summary: null,
      plainText: normalized,
      overviewText: '',
      overview: [],
      sections: [],
      plainSegments: splitMessageContent(normalized),
    };
  }

  const chunks: Array<{ title: string | null; lines: string[] }> = [{ title: null, lines: [] }];
  let current = chunks[0]!;
  let insideCodeFence = false;

  for (let i = firstNonEmptyIndex + 1; i < lines.length; i += 1) {
    const line = lines[i] ?? '';
    const trimmed = line.trim();
    if (trimmed.startsWith('```')) {
      insideCodeFence = !insideCodeFence;
    }
    if (!insideCodeFence && trimmed.startsWith('### ')) {
      current = { title: trimmed.slice(4).trim(), lines: [] };
      chunks.push(current);
      continue;
    }
    current.lines.push(line);
  }

  const overview = splitMessageContent(trimChunk(chunks[0]?.lines ?? []));
  const overviewText = trimChunk(chunks[0]?.lines ?? []);
  const sections = chunks
    .slice(1)
    .map((chunk) => {
      const content = trimChunk(chunk.lines);
      return {
        title: chunk.title ?? '',
        content,
        segments: splitMessageContent(content),
        collapsible: chunk.title === 'Brainstorming & Exploration',
      };
    })
    .filter((section) => section.title.length > 0 && section.segments.length > 0);

  return {
    isStructured: true,
    summary: `${summaryMatch[1]}: ${summaryMatch[2]}`,
    plainText: '',
    overviewText,
    overview,
    sections,
    plainSegments: [],
  };
}

function trimChunk(lines: string[]): string {
  return lines.join('\n').replace(/^\s+|\s+$/g, '');
}

function emptyStructuredMessage(): StructuredMessage {
  return {
    isStructured: false,
    summary: null,
    plainText: '',
    overviewText: '',
    overview: [],
    sections: [],
    plainSegments: [],
  };
}
