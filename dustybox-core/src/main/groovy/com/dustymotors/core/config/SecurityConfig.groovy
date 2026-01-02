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
                // 1. Самые специфичные правила — ВВЕРХУ
                        .requestMatchers("/", "/test-plugins").permitAll()
                        .requestMatchers("/api/plugins/**").permitAll() // Правило для API плагинов
                        .requestMatchers("/plugins/**").permitAll()     // Правило для других путей плагинов
                // 2. Более общие правила — ВНИЗУ
                        .requestMatchers("/api/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated()
                }        // 3. Новый синтаксис для отключения formLogin и httpBasic
                .formLogin { form -> form.disable() }
                .httpBasic { basic -> basic.disable() }

        return http.build()
    }
}