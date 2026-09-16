package com.aosorio.ecommerce.observability;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Configuración automática de observabilidad (ADR-0011).
 *
 * <p>Módulo reservado para dependencias y config base; el log format JSON lo
 * aporta el {@code logback-spring.xml} incluido y el tag común {@code application}
 * se configura con {@code management.metrics.tags.application} (mecanismo
 * estándar de Spring Boot, aplica a todo tipo de aplicación, incluida la
 * reactiva del gateway).
 *
 * <p>Se mantiene la clase para dejar un punto de extensión controlado por
 * {@code management.observability.enabled}.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "management.observability.enabled", havingValue = "true", matchIfMissing = true)
public class ObservabilityAutoConfiguration {
}