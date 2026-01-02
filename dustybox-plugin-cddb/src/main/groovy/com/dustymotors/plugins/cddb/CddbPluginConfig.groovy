// dustybox-plugin-cddb/src/main/groovy/com/dustymotors/plugins/cddb/CddbPluginConfig.groovy
package com.dustymotors.plugins.cddb

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@ComponentScan(basePackages = "com.dustymotors.plugins.cddb")
@EnableJpaRepositories(basePackages = "com.dustymotors.plugins.cddb")
class CddbPluginConfig {
    // Простая конфигурация - Spring Boot сделает остальное
}