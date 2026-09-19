package com.aosorio.ecommerce.pagos.config;

import com.aosorio.ecommerce.pagos.domain.Configuracion;
import com.aosorio.ecommerce.pagos.repository.ConfiguracionRepository;
import com.aosorio.ecommerce.pagos.service.ConfiguracionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConfiguracionSeeder implements ApplicationRunner {

    private final ConfiguracionRepository configuracionRepository;

    @Value("${pagos.monto-maximo-aprobado}")
    private BigDecimal montoMaximoPorDefecto;

    @Override
    public void run(ApplicationArguments args) {
        if (!configuracionRepository.existsById(ConfiguracionService.CLAVE_MONTO_MAXIMO_APROBADO)) {
            configuracionRepository.save(Configuracion.builder()
                    .clave(ConfiguracionService.CLAVE_MONTO_MAXIMO_APROBADO)
                    .valor(montoMaximoPorDefecto.toPlainString())
                    .build());
            log.info("Configuracion inicial '{}' = {} creada",
                    ConfiguracionService.CLAVE_MONTO_MAXIMO_APROBADO,
                    montoMaximoPorDefecto.toPlainString());
        }
    }
}