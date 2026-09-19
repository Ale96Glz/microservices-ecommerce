package com.aosorio.ecommerce.pagos.repository;

import com.aosorio.ecommerce.pagos.domain.Configuracion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracionRepository extends JpaRepository<Configuracion, String> {
}