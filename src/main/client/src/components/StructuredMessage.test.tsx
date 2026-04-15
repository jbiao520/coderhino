import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import StructuredMessage from './StructuredMessage';

describe('StructuredMessage', () => {
  it('renders ordinary messages as formatted markdown without structured wrappers', () => {
    render(<StructuredMessage text={'Hello\n```ts\nconst x = 1;\n```'} />);

    expect(screen.queryByTestId('structured-summary')).toBeNull();
    expect(screen.getByText('Hello')).toBeTruthy();
    expect(screen.getByText('const x = 1;')).toBeTruthy();
  });

  it('renders markdown lists and inline code', () => {
    render(<StructuredMessage text={'- first\n- second\n\nUse `npm test`.'} />);

    expect(screen.getByRole('list')).toBeTruthy();
    expect(screen.getByText('first')).toBeTruthy();
    expect(screen.getByText('npm test').tagName).toBe('CODE');
  });

  it('renders safe html and strips unsafe html attributes and tags', () => {
    const { container } = render(
      <StructuredMessage text={'<p><strong>Safe</strong> <a href="https://example.com">link</a></p><p onclick="alert(1)">No handler</p><script>alert(1)</script>'} />,
    );

    expect(screen.getByText('Safe').tagName).toBe('STRONG');
    expect(screen.getByRole('link', { name: 'link' })).toHaveAttribute('href', 'https://example.com');
    expect(container.querySelector('script')).toBeNull();
    expect(screen.getByText('No handler')).not.toHaveAttribute('onclick');
  });

  it('renders structured summary and collapses brainstorming details by default', () => {
    render(
      <StructuredMessage
        text={[
          '**Proposed Change: Add Structured Output**',
          'Overview text',
          '',
          '### Brainstorming & Exploration',
          '- Detail line',
          '',
          '### Next Action',
          'Run `/opsx-apply`.',
        ].join('\n')}
      />,
    );

    expect(screen.getByTestId('structured-summary').textContent).toContain('Proposed Change: Add Structured Output');
    expect(screen.getByText('Show Details')).toBeTruthy();
    expect(screen.queryByTestId('brainstorming-content')).toBeNull();
    expect(screen.getByTestId('structured-section-next-action')).toBeTruthy();
  });

  it('expands brainstorming details on toggle', () => {
    render(
      <StructuredMessage
        text={[
          '**Proposed Change: Add Structured Output**',
          'Overview text',
          '',
          '### Brainstorming & Exploration',
          '- Detail line',
        ].join('\n')}
      />,
    );

    fireEvent.click(screen.getByTestId('brainstorming-toggle'));

    expect(screen.getByTestId('brainstorming-content')).toBeTruthy();
    expect(screen.getByText('Detail line')).toBeTruthy();
  });

  it('shows a cursor for live structured output', () => {
    render(
      <StructuredMessage
        text={[
          '**Change Summary: Streaming Layout**',
          'Overview',
          '',
          '### Ready',
          'Apply the change.',
        ].join('\n')}
        showCursor
      />,
    );

    expect(screen.getByTestId('structured-message-cursor')).toBeTruthy();
  });

  it('keeps partial streamed formatting readable while showing the cursor', () => {
    render(<StructuredMessage text={'<em>Almost there\n\n```ts\nconst value ='} showCursor />);

    expect(screen.getByText(/Almost there/)).toBeTruthy();
    expect(screen.getByTestId('structured-message-cursor')).toBeTruthy();
  });
});
