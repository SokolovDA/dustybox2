// dustybox-core/src/main/groovy/com/dustymotors/core/config/OpenApiConfig.groovy
package com.dustymotors.core.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.Contact
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI dustyboxOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Dustybox Platform API")
                        .description("Dustybox Platform - Модульная платформа")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Dustybox Team")
                                .email("support@dustymotors.com")))
    }
}