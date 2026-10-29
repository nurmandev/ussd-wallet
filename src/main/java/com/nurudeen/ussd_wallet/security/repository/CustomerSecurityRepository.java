package com.nurudeen.ussd_wallet.security.repository;

import com.nurudeen.ussd_wallet.security.entity.CustomerSecurity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerSecurityRepository extends JpaRepository<CustomerSecurity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select security from CustomerSecurity security where security.customerId = :customerId")
    Optional<CustomerSecurity> findByCustomerIdForUpdate(@Param("customerId") UUID customerId);
}
