package com.coderhino.web.approval;

public class ApprovalAlreadyResolvedException extends RuntimeException {
    private final String approvalId;
    private final String currentStatus;

    public ApprovalAlreadyResolvedException(String approvalId, String currentStatus) {
        super("Approval " + approvalId + " already resolved: " + currentStatus);
        this.approvalId = approvalId;
        this.currentStatus = currentStatus;
    }

    public String getApprovalId() { return approvalId; }
    public String getCurrentStatus() { return currentStatus; }
}
