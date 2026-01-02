package com.dustymotors.core.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf { csrf -> csrf.disable() }
                .authorizeHttpRequests { authz -> authz
                // Основные пути
                        .requestMatchers("/", "/health").permitAll()

                // API ядра
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/api/system/**").permitAll()
                        .requestMatchers("/api/plugins/**").permitAll()

                // Плагины
                        .requestMatchers("/plugins/**").permitAll()

                // CDDB API (через прокси)
                        .requestMatchers("/api/disks/**").permitAll()

                // Swagger
                        .requestMatchers("/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**").permitAll()

                // Остальные запросы требуют аутентификации
                        .anyRequest().authenticated()
                }
                .formLogin { form -> form.disable() }
                .httpBasic { basic -> basic.disable() }

        return http.build()
    }
}