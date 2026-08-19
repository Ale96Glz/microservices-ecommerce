package com.aosorio.ecommerce.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequestDTO {

    @NotBlank(message = "El email no puede estar vacio")
    @Email(message = "El email no es valido")
    private String email;

    @NotBlank(message = "La contraseña no puede estar vacia")
    private String password;
}
