package com.checkout.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HoldRepository extends JpaRepository<HoldEntity, String> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE card_holds SET status = 'RELEASED' WHERE order_id = :id AND status = 'AUTHORIZED'", nativeQuery = true)
    int release(@Param("id") String id);
}
