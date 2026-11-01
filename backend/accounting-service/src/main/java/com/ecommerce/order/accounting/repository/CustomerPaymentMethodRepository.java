package com.ecommerce.order.accounting.repository;

import com.ecommerce.order.accounting.model.CustomerPaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerPaymentMethodRepository extends JpaRepository<CustomerPaymentMethod, Long> {

    List<CustomerPaymentMethod> findByUserId(Long userId);
}
