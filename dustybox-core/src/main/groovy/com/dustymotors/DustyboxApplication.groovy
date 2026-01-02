// dustybox-core/src/main/groovy/com/dustymotors/DustyboxApplication.groovy
package com.dustymotors

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.beans.factory.annotation.Autowired
import jakarta.annotation.PostConstruct
import com.dustymotors.core.plugin.PluginManager

/*
Здесь перечислены некоторые требования к Системе.

Ядро и плагины должны предоставлять доступ к своим сервисам перекрестно.
Например, в скриптах, которые запускает плагин dustybox-plugin-script,
должен быть доступ к сервисам плагина dustybox-plugin-cddb.

Также ядро и плагины должны предоставлять доступ к сервисам по протоколу REST.
 */

@SpringBootApplication
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
        println "Plugin API test: http://localhost:8080/api/debug/plugins/services"
        println "CDDB API: http://localhost:8080/api/cddb/disks"
        println "System info: http://localhost:8080/api/system/info"
        println "=" * 50
    }
}