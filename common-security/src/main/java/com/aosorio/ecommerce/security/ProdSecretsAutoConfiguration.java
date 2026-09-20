package com.aosorio.ecommerce.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@AutoConfiguration
@Profile("prod")
public class ProdSecretsAutoConfiguration {

    @Bean
    ApplicationRunner productionSecretsGuard(
            @Value("${jwt.secret:}") String jwtSecret,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${spring.datasource.password:}") String datasourcePassword,
            @Value("${spring.data.redis.password:}") String redisPassword,
            @Value("${ecommerce.prod.require-redis-password:false}") boolean requireRedisPassword) {
        return args -> ProductionSecrets.validate(
                jwtSecret, datasourceUrl, datasourcePassword, redisPassword, requireRedisPassword);
    }
}
