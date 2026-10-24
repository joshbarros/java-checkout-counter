package com.checkout.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<OrderEntity, String> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE customer_orders SET status = 'DECLINED' WHERE id = :id AND status = 'PENDING'", nativeQuery = true)
    int markDeclined(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE customer_orders SET status = 'AUTHORIZED' WHERE id = :id AND status = 'PENDING'", nativeQuery = true)
    int markAuthorized(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE customer_orders SET status = 'ALLOCATED' WHERE id = :id AND status IN ('PENDING', 'AUTHORIZED')", nativeQuery = true)
    int markAllocated(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE customer_orders SET status = 'CANCELLED' WHERE id = :id AND status IN ('PENDING', 'AUTHORIZED')", nativeQuery = true)
    int markCancelled(@Param("id") String id);
}
