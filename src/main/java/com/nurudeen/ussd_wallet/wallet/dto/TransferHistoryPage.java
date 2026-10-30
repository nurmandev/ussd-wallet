package com.nurudeen.ussd_wallet.wallet.dto;

import java.util.List;

public record TransferHistoryPage(
        List<TransferHistoryItem> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
