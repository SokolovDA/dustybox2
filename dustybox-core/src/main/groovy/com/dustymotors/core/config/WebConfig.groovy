package com.dustymotors.core.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig implements WebMvcConfigurer {

    @Override
    void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Для статических ресурсов плагинов
        registry.addResourceHandler("/plugins/static/**")
                .addResourceLocations("classpath:/static/", "file:./plugins/")
                .setCachePeriod(3600)

        // Для UI плагинов
        registry.addResourceHandler("/web/plugins/*/**")
                .addResourceLocations("classpath:/static/", "file:./plugins/")
                .setCachePeriod(0)
    }

    @Override
    void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600)
    }
}