package com.coderhino.web.approval;

public class ApprovalNotFoundException extends RuntimeException {
    private final String approvalId;

    public ApprovalNotFoundException(String approvalId) {
        super("Approval not found: " + approvalId);
        this.approvalId = approvalId;
    }

    public String getApprovalId() {
        return approvalId;
    }
}
