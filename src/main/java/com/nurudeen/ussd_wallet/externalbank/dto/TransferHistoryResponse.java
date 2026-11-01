package com.nurudeen.ussd_wallet.externalbank.dto;

import java.util.List;

public record TransferHistoryResponse(List<TransferStatusResponse> items) {
}
