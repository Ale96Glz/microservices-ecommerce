package com.aosorio.ecommerce.pagos.service;

import com.aosorio.ecommerce.pagos.repository.ConfiguracionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ConfiguracionService {

    public static final String CLAVE_MONTO_MAXIMO_APROBADO = "monto_maximo_aprobado";

    private final ConfiguracionRepository configuracionRepository;

    @Value("${pagos.monto-maximo-aprobado}")
    private BigDecimal montoMaximoPorDefecto;

    public BigDecimal montoMaximoAprobado() {
        return configuracionRepository.findById(CLAVE_MONTO_MAXIMO_APROBADO)
                .map(c -> new BigDecimal(c.getValor()))
                .orElse(montoMaximoPorDefecto);
    }
}