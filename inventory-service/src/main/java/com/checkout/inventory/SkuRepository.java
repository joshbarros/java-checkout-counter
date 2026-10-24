package com.checkout.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SkuRepository extends JpaRepository<SkuEntity, String> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE skus SET on_hand = on_hand - :qty WHERE sku = :sku AND on_hand >= :qty", nativeQuery = true)
    int decrement(@Param("sku") String sku, @Param("qty") int qty);
}
