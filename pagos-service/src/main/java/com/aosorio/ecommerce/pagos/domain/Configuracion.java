package com.aosorio.ecommerce.pagos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "configuracion")
public class Configuracion {

    @Id
    @Column(name = "clave", nullable = false, length = 64)
    private String clave;

    @Column(nullable = false, length = 255)
    private String valor;
}