package com.nurudeen.ussd_wallet.security.repository;

import com.nurudeen.ussd_wallet.security.entity.CustomerSecurity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerSecurityRepository extends JpaRepository<CustomerSecurity, UUID> {
}
