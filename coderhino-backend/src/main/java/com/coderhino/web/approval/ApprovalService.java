package com.coderhino.web.approval;

import com.coderhino.web.events.SessionEvent;
import com.coderhino.web.events.SessionEventBus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ApprovalService {

    private final ConcurrentHashMap<String, ApprovalRecord> approvals = new ConcurrentHashMap<>();
    private final SessionEventBus eventBus;

    public ApprovalService(SessionEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public ApprovalRecord createRequest(String sessionId, String runId, String action, String summary) {
        var approvalId = UUID.randomUUID().toString();
        var record = ApprovalRecord.pending(approvalId, sessionId, runId, action, summary);
        approvals.put(approvalId, record);

        eventBus.publish(sessionId, SessionEvent.approvalRequested(
                sessionId, approvalId, action, summary));

        return record;
    }

    public List<ApprovalRecord> listBySession(String sessionId) {
        return approvals.values().stream()
                .filter(a -> a.getSessionId().equals(sessionId))
                .collect(Collectors.toList());
    }

    public ApprovalRecord approve(String approvalId) {
        var record = findOrThrow(approvalId);
        if (record.getStatus() != ApprovalRecord.Status.PENDING) {
            throw new ApprovalAlreadyResolvedException(approvalId, record.getStatus().name());
        }
        record.setStatus(ApprovalRecord.Status.APPROVED);
        record.setResolvedAt(Instant.now());

        eventBus.publish(record.getSessionId(),
                SessionEvent.approvalResolved(record.getSessionId(), approvalId, "APPROVED"));

        return record;
    }

    public ApprovalRecord deny(String approvalId) {
        var record = findOrThrow(approvalId);
        if (record.getStatus() != ApprovalRecord.Status.PENDING) {
            throw new ApprovalAlreadyResolvedException(approvalId, record.getStatus().name());
        }
        record.setStatus(ApprovalRecord.Status.DENIED);
        record.setResolvedAt(Instant.now());

        eventBus.publish(record.getSessionId(),
                SessionEvent.approvalResolved(record.getSessionId(), approvalId, "DENIED"));

        return record;
    }

    public ApprovalRecord find(String approvalId) {
        return approvals.get(approvalId);
    }

    private ApprovalRecord findOrThrow(String approvalId) {
        var record = approvals.get(approvalId);
        if (record == null) {
            throw new ApprovalNotFoundException(approvalId);
        }
        return record;
    }
}
