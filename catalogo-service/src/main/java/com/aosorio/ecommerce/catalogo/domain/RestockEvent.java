package com.aosorio.ecommerce.catalogo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "restock_event")
public class RestockEvent {

    @Id
    @Column(nullable = false, length = 100)
    private String eventId;

    @Column(nullable = false)
    private Long pedidoId;

    @Column(nullable = false)
    private Long productoId;

    @Column(nullable = false)
    private int cantidad;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    private LocalDateTime aplicadoEn;
}