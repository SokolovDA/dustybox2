package com.dustymotors.core.plugin

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import groovy.transform.CompileStatic
import jakarta.annotation.PostConstruct

@Component
@CompileStatic
class PluginControllerScanner {

    @Autowired
    private PluginManager pluginManager

    @PostConstruct
    void init() {
        println "[INFO] PluginControllerScanner initialized"
        // Отложенная регистрация
        Thread.start {
            try {
                Thread.sleep(3000)
                println "[INFO] Currently plugin controllers are registered directly in plugin JARs"
                println "[INFO] Loaded plugins: ${pluginManager.loadedPlugins.size()}"
                pluginManager.loadedPlugins.each { id, instance ->
                    println "  - ${id}: ${instance.descriptor.name} v${instance.descriptor.version}"
                }
            } catch (Exception e) {
                println "[ERROR] Failed: ${e.message}"
            }
        }
    }
}