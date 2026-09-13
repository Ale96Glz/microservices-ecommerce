package com.aosorio.ecommerce.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration
@ConditionalOnProperty(name = "jwt.filter.enabled", havingValue = "true", matchIfMissing = true)
public class JwtSecurityAutoConfiguration {

    @Bean
    public JwtValidator jwtValidator(@Value("${jwt.secret}") String secret) {
        return new JwtValidator(secret);
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtSecurityFilterRegistration(
            JwtValidator jwtValidator,
            @Value("${jwt.filter.public-paths:}") String publicPaths,
            @Value("${jwt.filter.public-get-prefixes:}") String publicGetPrefixes) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new JwtAuthFilter(
                jwtValidator,
                JwtAuthFilter.asList(publicPaths),
                JwtAuthFilter.asList(publicGetPrefixes)
        ));
        registration.addUrlPatterns("/*");
        registration.setName("jwtAuthFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}