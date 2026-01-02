package com.dustymotors

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.beans.factory.annotation.Autowired
import jakarta.annotation.PostConstruct
import com.dustymotors.core.plugin.PluginManager

@SpringBootApplication
// Временно удаляем - позже настроим через application.properties
// @EntityScan(basePackages = ["com.dustymotors.core.entity", "com.dustymotors.plugins"])
// @EnableJpaRepositories(basePackages = ["com.dustymotors.core.repository", "com.dustymotors.plugins"])
class DustyboxApplication {

    @Autowired
    private PluginManager pluginManager

    static void main(String[] args) {
        SpringApplication.run(DustyboxApplication, args)
    }

    @PostConstruct
    void init() {
        println "=" * 50
        println "🎛️  Dustybox Platform Started"
        println "=" * 50
        println "Plugins loaded: ${pluginManager.loadedPlugins.size()}"
        pluginManager.loadedPlugins.each { id, plugin ->
            println "  • ${id}: ${plugin.descriptor.name} v${plugin.descriptor.version}"
        }
        println "=" * 50
        println "Health check: http://localhost:8080/api/health"
        println "Plugin API test: http://localhost:8080/plugins/ping/cddb-plugin-1.3.0"
        println "CDDB API: http://localhost:8080/api/plugins/cddb/disks"
        println "=" * 50
    }
}