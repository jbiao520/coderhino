import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import SessionContextPanel from './SessionContextPanel';
import type { SessionContextDto } from '../types/api';

describe('SessionContextPanel', () => {
  it('renders numeric zero usage metrics instead of unavailable', () => {
    const context: SessionContextDto = {
      summary: {
        sessionId: 'ses-ctx-1',
        name: 'Context Session',
        model: 'MiniMax-M2.7',
        providerId: 'provider-1',
        permissionMode: 'BYPASS',
        status: 'IDLE',
        createdAt: '2026-04-12T10:00:00Z',
        messageCount: 0,
        currentUsage: {
          inputTokens: 0,
          outputTokens: 0,
          cacheReadTokens: 0,
          cacheWriteTokens: 0,
          toolUses: 0,
          contextLength: 0,
        },
        sessionTotals: {
          inputTokens: 0,
          outputTokens: 0,
          cacheReadTokens: 0,
          cacheWriteTokens: 0,
          toolUses: 0,
          contextLength: 0,
        },
      },
      rawAiHistory: [
        {
          direction: 'request',
          content: '{"model":"MiniMax-M2.7"}',
          timestamp: '2026-04-12T10:01:00Z',
        },
        {
          direction: 'response',
          content: '{"id":"msg_123"}',
          timestamp: '2026-04-12T10:01:05Z',
        },
        {
          direction: 'request',
          content: '{"model":"MiniMax-M2.7","messages":[]}',
          timestamp: '2026-04-12T10:02:00Z',
        },
      ],
    };

    render(
      <SessionContextPanel
        context={context}
        loading={false}
        error={null}
        sessionLabel="Context"
      />,
    );

    expect(screen.getByText('Session Information')).toBeTruthy();
    expect(screen.getByText('Session Usage')).toBeTruthy();
    expect(screen.getByText('AI History')).toBeTruthy();
    expect(screen.getByText('Request + Response')).toBeTruthy();
    expect(screen.getByText('Request')).toBeTruthy();
    expect(screen.queryByText('Show raw message')).toBeNull();
    expect(screen.queryByText('Hide raw message')).toBeNull();
    expect(screen.queryByText('Folded by default')).toBeNull();
    expect(screen.queryByText('Current Usage')).toBeNull();
    expect(screen.queryByText('Session Totals')).toBeNull();
    expect(screen.queryByText('Context Length')).toBeNull();
    expect(screen.queryByText('Unavailable')).toBeNull();
    expect(screen.getAllByText('0').length).toBeGreaterThanOrEqual(5);

    expect(screen.queryByText('{"model":"MiniMax-M2.7"}')).toBeNull();
    fireEvent.click(screen.getByLabelText('Expand AI history entry 1'));
    expect(screen.queryByText('{"model":"MiniMax-M2.7"}')).toBeNull();
    expect(screen.queryByText('{"id":"msg_123"}')).toBeNull();
    fireEvent.click(screen.getByLabelText('Expand request for AI history entry 1'));
    expect(screen.getByText('{"model":"MiniMax-M2.7"}')).toBeTruthy();
    fireEvent.click(screen.getByLabelText('Expand response for AI history entry 1'));
    expect(screen.getByText('{"id":"msg_123"}')).toBeTruthy();
    expect(screen.getAllByText('Request').length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText('Response')).toBeTruthy();

    fireEvent.click(screen.getByLabelText('Expand AI history entry 2'));
    expect(screen.queryByText('{"model":"MiniMax-M2.7","messages":[]}')).toBeNull();
    fireEvent.click(screen.getByLabelText('Expand request for AI history entry 2'));
    expect(screen.getByText('{"model":"MiniMax-M2.7","messages":[]}')).toBeTruthy();
  });
});
