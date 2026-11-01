package com.ecommerce.order.accounting.repository;

import com.ecommerce.order.accounting.model.CustomerAccountingPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerAccountingPreferencesRepository extends JpaRepository<CustomerAccountingPreferences, Long> {
}
