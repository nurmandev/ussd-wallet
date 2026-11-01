package com.nurudeen.ussd_wallet.externalbank.enums;

public enum ExternalTransferStatus {
    INITIATED,
    VALIDATING,
    PROCESSING,
    PENDING,
    SUCCESSFUL,
    FAILED,
    UNKNOWN,
    PENDING_RECONCILIATION,
    REVERSAL_PENDING,
    REVERSED
}
