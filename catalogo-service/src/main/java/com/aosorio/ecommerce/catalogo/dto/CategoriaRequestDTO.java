package com.aosorio.ecommerce.catalogo.dto;


import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Builder
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class CategoriaRequestDTO {

    @NotBlank(message = "El nombre de la categoria no puede estar en blanco")
    @Size(min = 3, max = 50, message = "El nombre de la categoria debe tener entre 3 y 50 caracteres ")
    private String nombre;

    @Size(max = 500, message = "La descripcion no puede tener mas de 500 caracteres")
    private String descripcion;
}
