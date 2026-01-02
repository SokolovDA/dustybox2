// dustybox-core/src/main/groovy/com/dustymotors/core/config/WebConfig.groovy
package com.dustymotors.core.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
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

        // Для Swagger UI
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/springdoc-openapi-ui/")
                .resourceChain(false)
    }

    @Override
    void addCorsMappings(CorsRegistry registry) {
        // Разрешаем все для разработки
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600)
    }

    @Override
    void addViewControllers(ViewControllerRegistry registry) {
        // Простые редиректы
        registry.addRedirectViewController("/", "/test-plugins")
        registry.addRedirectViewController("/ui", "/test-plugins")
    }
}