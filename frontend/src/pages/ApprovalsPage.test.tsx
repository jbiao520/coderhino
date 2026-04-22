import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import ApprovalsPage from '../pages/ApprovalsPage';

const pendingApproval = {
  approvalId: 'apr-001',
  sessionId: 'ses-abc',
  runId: 'run-001',
  action: 'bash',
  summary: 'Run ls command',
  status: 'PENDING',
  createdAt: '2026-04-07T10:00:00Z',
  resolvedAt: null,
};

function renderPage() {
  return render(
    <MemoryRouter>
      <ApprovalsPage />
    </MemoryRouter>,
  );
}

async function renderWithApprovals(approvals: typeof pendingApproval[]) {
  (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
    ok: true,
    json: async () => approvals,
  });

  renderPage();

  const input = screen.getByTestId('session-id-lookup') as HTMLInputElement;
  const form = screen.getByTestId('approvals-dev-panel').querySelector('form')!;

  vi.spyOn(window, 'FormData').mockReturnValueOnce({
    get: () => 'ses-abc',
  } as unknown as FormData);

  fireEvent.change(input, { target: { value: 'ses-abc' } });
  form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

  await waitFor(() => {
    expect(screen.getByTestId(`approval-item-${approvals[0]!.approvalId}`)).toBeTruthy();
  });
}

describe('ApprovalsPage', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn() as typeof fetch;
  });

  it('renders structural info and link to sessions', () => {
    renderPage();
    expect(screen.getByText('Approvals')).toBeTruthy();
    expect(screen.getByText('Go to Sessions')).toBeTruthy();
    expect(screen.getByTestId('approvals-dev-panel')).toBeTruthy();
  });

  it('fetches approvals for a session id when form is submitted', async () => {
    const mockApprovals = [pendingApproval];
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockApprovals,
    });

    renderPage();

    const input = screen.getByTestId('session-id-lookup') as HTMLInputElement;
    const form = screen.getByTestId('approvals-dev-panel').querySelector('form')!;

    Object.defineProperty(input, 'value', { writable: true, value: 'ses-abc' });
    input.dispatchEvent(new Event('input', { bubbles: true }));

    const formData = new FormData(form);
    formData.set('sessionId', 'ses-abc');

    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockApprovals,
    });

    vi.spyOn(window, 'FormData').mockReturnValueOnce({
      get: () => 'ses-abc',
    } as unknown as FormData);

    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalled();
    });
  });

  it('session-scoped info card is visible', () => {
    renderPage();
    expect(screen.getByText('Session-scoped approvals')).toBeTruthy();
  });

  it('approve button calls POST approve endpoint', async () => {
    const approvedApproval = { ...pendingApproval, status: 'APPROVED', resolvedAt: '2026-04-07T10:01:00Z' };
    (globalThis.fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ ok: true, json: async () => [pendingApproval] })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ approval: approvedApproval, run: { runId: 'run-001', status: 'COMPLETED' } }) });

    await renderWithApprovals([pendingApproval]);

    const approveBtn = screen.getByTestId('approve-btn-apr-001');
    fireEvent.click(approveBtn);

    await waitFor(() => {
      const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls as unknown[][];
      const approveCall = calls.find(
        (c) =>
          Array.isArray(c) &&
          typeof c[0] === 'string' &&
          (c[0] as string).includes('/approvals/apr-001/approve') &&
          c[1] !== undefined &&
          (c[1] as RequestInit).method === 'POST',
      );
      expect(approveCall).toBeTruthy();
    });
  });

  it('deny button calls POST deny endpoint', async () => {
    const deniedApproval = { ...pendingApproval, status: 'DENIED', resolvedAt: '2026-04-07T10:01:00Z' };
    (globalThis.fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ ok: true, json: async () => [pendingApproval] })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ approval: deniedApproval, run: { runId: 'run-001', status: 'CANCELLED' } }) });

    await renderWithApprovals([pendingApproval]);

    const denyBtn = screen.getByTestId('deny-btn-apr-001');
    fireEvent.click(denyBtn);

    await waitFor(() => {
      const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls as unknown[][];
      const denyCall = calls.find(
        (c) =>
          Array.isArray(c) &&
          typeof c[0] === 'string' &&
          (c[0] as string).includes('/approvals/apr-001/deny') &&
          c[1] !== undefined &&
          (c[1] as RequestInit).method === 'POST',
      );
      expect(denyCall).toBeTruthy();
    });
  });

  it('status changes to APPROVED after approve resolves', async () => {
    const approvedApproval = { ...pendingApproval, status: 'APPROVED', resolvedAt: '2026-04-07T10:01:00Z' };
    (globalThis.fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ ok: true, json: async () => [pendingApproval] })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ approval: approvedApproval, run: { runId: 'run-001', status: 'COMPLETED' } }) });

    await renderWithApprovals([pendingApproval]);

    const approveBtn = screen.getByTestId('approve-btn-apr-001');
    fireEvent.click(approveBtn);

    await waitFor(() => {
      const statusBadge = screen.getByTestId('approval-status-apr-001');
      expect(statusBadge.textContent).toBe('APPROVED');
    });

    expect(screen.queryByTestId('approve-btn-apr-001')).toBeNull();
    expect(screen.queryByTestId('deny-btn-apr-001')).toBeNull();
  });

  it('status changes to DENIED after deny resolves', async () => {
    const deniedApproval = { ...pendingApproval, status: 'DENIED', resolvedAt: '2026-04-07T10:01:00Z' };
    (globalThis.fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ ok: true, json: async () => [pendingApproval] })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ approval: deniedApproval, run: { runId: 'run-001', status: 'CANCELLED' } }) });

    await renderWithApprovals([pendingApproval]);

    const denyBtn = screen.getByTestId('deny-btn-apr-001');
    fireEvent.click(denyBtn);

    await waitFor(() => {
      const statusBadge = screen.getByTestId('approval-status-apr-001');
      expect(statusBadge.textContent).toBe('DENIED');
    });

    expect(screen.queryByTestId('approve-btn-apr-001')).toBeNull();
    expect(screen.queryByTestId('deny-btn-apr-001')).toBeNull();
  });

  it('approve and deny buttons not shown for already-resolved approvals', async () => {
    const resolvedApprovals = [
      { ...pendingApproval, approvalId: 'apr-002', status: 'APPROVED', resolvedAt: '2026-04-07T10:00:30Z' },
      { ...pendingApproval, approvalId: 'apr-003', status: 'DENIED', resolvedAt: '2026-04-07T10:00:45Z' },
    ];

    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => resolvedApprovals,
    });

    renderPage();

    vi.spyOn(window, 'FormData').mockReturnValueOnce({
      get: () => 'ses-abc',
    } as unknown as FormData);

    const form = screen.getByTestId('approvals-dev-panel').querySelector('form')!;
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    await waitFor(() => {
      expect(screen.getByTestId('approval-item-apr-002')).toBeTruthy();
    });

    expect(screen.queryByTestId('approve-btn-apr-002')).toBeNull();
    expect(screen.queryByTestId('deny-btn-apr-002')).toBeNull();
    expect(screen.queryByTestId('approve-btn-apr-003')).toBeNull();
    expect(screen.queryByTestId('deny-btn-apr-003')).toBeNull();
  });
});
