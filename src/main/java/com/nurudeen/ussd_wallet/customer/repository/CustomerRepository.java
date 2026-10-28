package com.nurudeen.ussd_wallet.customer.repository;

import com.nurudeen.ussd_wallet.customer.entity.Customer;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    boolean existsByPhoneNumber(String phoneNumber);
}
