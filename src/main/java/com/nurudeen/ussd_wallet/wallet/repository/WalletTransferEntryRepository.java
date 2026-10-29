package com.nurudeen.ussd_wallet.wallet.repository;

import com.nurudeen.ussd_wallet.wallet.entity.WalletTransferEntry;
import com.nurudeen.ussd_wallet.ussd.dto.UssdTransactionHistoryItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletTransferEntryRepository extends JpaRepository<WalletTransferEntry, UUID> {

    @Query("""
            select new com.nurudeen.ussd_wallet.ussd.dto.UssdTransactionHistoryItem(
                case when entry.entryType = com.nurudeen.ussd_wallet.wallet.entity.WalletTransferEntry.EntryType.DEBIT
                    then 'Sent' else 'Received' end,
                entry.amount,
                entry.currency,
                entry.transfer.reference,
                entry.createdAt
            )
            from WalletTransferEntry entry
            where entry.wallet.customer.id = :customerId
            order by entry.createdAt desc
            """)
    List<UssdTransactionHistoryItem> findRecentForCustomer(
            @Param("customerId") UUID customerId, Pageable pageable);
}
