package com.aosorio.ecommerce.catalogo.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Builder
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductoRequestDTO {

    @NotBlank(message = "El nombre del porducto no puede estar vacio")
    @Size(min = 2, max = 100, message = "El nombre del producto debe tener entre 2 y 100 caracteres")
    private String nombre;

    @Size(max = 500, message = "La descripcion no puede tener mas de 500 caracteres")
    private String descripcion;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.01", message = "El precio debe ser mayor a 0")
    @DecimalMax(value = "9999999999.9", message = "Exediste el limite del precio permitido")
    private BigDecimal precio;

    @Min(value = 1, message = "El producto debe tener como minimo una unidad")
    @Positive(message = "El producto no puede tener una cantidad negativa")
    private int stock;

    @NotNull(message = "La categoria es obligatoria")
    private Long categoriaId;
}
