package com.aosorio.ecommerce.notificaciones.repository;

import com.aosorio.ecommerce.notificaciones.domain.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    List<Notificacion> findByUsuarioId(Long usuarioId);

    List<Notificacion> findByUsuarioIdAndLeida(Long usuarioId, boolean leida);
}
