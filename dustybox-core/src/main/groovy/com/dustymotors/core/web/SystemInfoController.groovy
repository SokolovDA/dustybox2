package com.dustymotors.core.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*
import com.dustymotors.core.plugin.PluginManager

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

    @GetMapping("/health")
    Map<String, Object> health() {
        return [
                status: "UP",
                timestamp: new Date(),
                system: [
                        javaVersion: System.getProperty("java.version"),
                        os: System.getProperty("os.name"),
                        processors: Runtime.getRuntime().availableProcessors()
                ]
        ]
    }
}