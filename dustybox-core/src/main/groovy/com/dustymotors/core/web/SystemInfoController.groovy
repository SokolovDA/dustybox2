// dustybox-core/src/main/groovy/com/dustymotors/core/web/SystemInfoController.groovy
package com.dustymotors.core.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*
import com.dustymotors.core.plugin.PluginManager
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.servlet.HandlerMapping

@RestController
@RequestMapping("/api/system")
class SystemInfoController {

    @Autowired
    private PluginManager pluginManager

    @GetMapping("/info")
    Map<String, Object> getSystemInfo() {
        return [
                status: "running",
                timestamp: new Date(),
                springBootVersion: "4.0.1",
                javaVersion: System.getProperty("java.version"),
                loadedPlugins: pluginManager.loadedPlugins.size(),
                plugins: pluginManager.loadedPlugins.collect { id, plugin ->
                    [
                            id: id,
                            name: plugin.descriptor.name,
                            version: plugin.descriptor.version,
                            started: plugin.started,
                            hasSpringContext: plugin.springContext != null
                    ]
                }
        ]
    }

    @GetMapping("/endpoints")
    Map<String, Object> listAllEndpoints(HttpServletRequest request) {
        def endpoints = [] as List<String>

        // Основные эндпоинты ядра
        def coreEndpoints = [
                "/api/health",
                "/api/plugins/management",
                "/api/system/info",
                "/api/system/endpoints",
                "/plugins/ping/{pluginId}",
                "/plugins/{pluginId}/api/info",
                "/plugins/{pluginId}/api/**",
                "/plugins/{pluginId}/web/**"
        ]

        endpoints.addAll(coreEndpoints)

        // Динамические эндпоинты плагинов (если зарегистрированы)
        pluginManager.loadedPlugins.each { id, plugin ->
            endpoints.add("/plugins/${id}/api/**")
            endpoints.add("/plugins/${id}/web/**")
        }

        return [
                totalEndpoints: endpoints.size(),
                endpoints: endpoints.sort(),
                note: "Плагины должны регистрировать свои API через PluginDispatcherController"
        ]
    }
}