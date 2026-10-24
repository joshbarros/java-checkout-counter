package com.checkout.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AllocationRepository extends JpaRepository<AllocationEntity, String> {
}
