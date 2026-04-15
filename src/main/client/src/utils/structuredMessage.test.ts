import { describe, expect, it } from 'vitest';
import { parseStructuredMessage, splitMessageContent } from './structuredMessage';

describe('structuredMessage', () => {
  it('falls back to plain segments for ordinary messages', () => {
    const parsed = parseStructuredMessage('Plain response with ```ts\nconst ok = true;\n```');

    expect(parsed.isStructured).toBe(false);
    expect(parsed.plainSegments).toHaveLength(2);
    expect(parsed.plainSegments[0]).toEqual({ type: 'prose', text: 'Plain response with ' });
    expect(parsed.plainSegments[1]).toEqual({ type: 'code', text: 'const ok = true;\n' });
  });

  it('detects structured proposal summaries and sections', () => {
    const parsed = parseStructuredMessage([
      '**Proposed Change: Add Structured Output**',
      'Short overview line.',
      '',
      '### Brainstorming & Exploration',
      '- Checked existing chat rendering',
      '',
      '### Ready',
      'Run `/opsx-apply` next.',
    ].join('\n'));

    expect(parsed.isStructured).toBe(true);
    expect(parsed.summary).toBe('Proposed Change: Add Structured Output');
    expect(parsed.overview[0]).toEqual({ type: 'prose', text: 'Short overview line.' });
    expect(parsed.sections.map((section) => section.title)).toEqual(['Brainstorming & Exploration', 'Ready']);
    expect(parsed.sections[0]?.collapsible).toBe(true);
    expect(parsed.sections[1]?.collapsible).toBe(false);
  });

  it('preserves fenced code blocks inside structured sections', () => {
    const parsed = parseStructuredMessage([
      '**Change Summary: Structured renderer**',
      'Overview',
      '',
      '### Next Action',
      '```bash',
      'npm test',
      '```',
    ].join('\n'));

    expect(parsed.sections[0]?.segments).toEqual([{ type: 'code', text: 'npm test\n' }]);
  });

  it('splits mixed prose and code content into ordered segments', () => {
    expect(splitMessageContent('Before\n```java\nSystem.out.println("hi");\n```\nAfter')).toEqual([
      { type: 'prose', text: 'Before\n' },
      { type: 'code', text: 'System.out.println("hi");\n' },
      { type: 'prose', text: '\nAfter' },
    ]);
  });
});
