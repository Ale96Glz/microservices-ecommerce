package com.aosorio.ecommerce.catalogo.repository;

import com.aosorio.ecommerce.catalogo.domain.RestockEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestockEventRepository extends JpaRepository<RestockEvent, String> {
}