package com.dustymotors.core.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.*
import org.springframework.web.bind.annotation.*
import com.dustymotors.core.plugin.PluginManager
import groovy.transform.CompileStatic

@RestController
@RequestMapping("/api/plugins/management")
@CompileStatic
class PluginManagementController {

    @Autowired
    private PluginManager pluginManager

    /**
     * Получить список всех плагинов
     */
    @GetMapping
    ResponseEntity<Map<String, Object>> listPlugins() {
        List<Map<String, Object>> plugins = [] as List<Map<String, Object>>
        pluginManager.loadedPlugins.each { String id, instance ->
            plugins.add([
                    id: id,
                    name: instance.descriptor.name,
                    version: instance.descriptor.version,
                    description: instance.descriptor.description,
                    started: instance.started,
                    jarFile: instance.jarFile.name,
                    menuItems: instance.pluginInstance.menuItems?.size() ?: 0,
                    webResources: instance.pluginInstance.webResources?.size() ?: 0
            ] as Map<String, Object>)
        }

        return ResponseEntity.ok([
                count: plugins.size(),
                plugins: plugins
        ] as Map<String, Object>)
    }

    /**
     * Получить детальную информацию о плагине
     */
    @GetMapping("/{pluginId}")
    ResponseEntity<?> getPluginDetails(@PathVariable String pluginId) {
        def plugin = pluginManager.getPlugin(pluginId)
        if (!plugin) {
            return ResponseEntity.notFound().build()
        }

        return ResponseEntity.ok([
                id: pluginId,
                descriptor: [
                        name: plugin.descriptor.name,
                        version: plugin.descriptor.version,
                        description: plugin.descriptor.description,
                        mainClass: plugin.descriptor.mainClass,
                        type: plugin.descriptor.type
                ],
                status: [
                        started: plugin.started,
                        jarFile: plugin.jarFile.absolutePath,
                        jarSize: "${plugin.jarFile.length() / 1024} KB",
                        lastModified: new Date(plugin.jarFile.lastModified())
                ],
                capabilities: [
                        menuItems: plugin.pluginInstance.menuItems,
                        webResources: plugin.pluginInstance.webResources
                ]
        ])
    }

    /**
     * Запустить плагин
     */
    @PostMapping("/{pluginId}/start")
    ResponseEntity<Map<String, Object>> startPlugin(@PathVariable String pluginId) {
        def plugin = pluginManager.getPlugin(pluginId)
        if (!plugin) {
            return ResponseEntity.notFound().build()
        }

        if (plugin.started) {
            return ResponseEntity.badRequest().body([
                    error: "Plugin already started"
            ] as Map<String, Object>)
        }

        pluginManager.startPlugin(pluginId)

        return ResponseEntity.ok([
                message: "Plugin ${pluginId} started successfully",
                started: true
        ] as Map<String, Object>)
    }

    /**
     * Остановить плагин
     */
    @PostMapping("/{pluginId}/stop")
    ResponseEntity<Map<String, Object>> stopPlugin(@PathVariable String pluginId) {
        def plugin = pluginManager.getPlugin(pluginId)
        if (!plugin) {
            return ResponseEntity.notFound().build()
        }

        if (!plugin.started) {
            return ResponseEntity.badRequest().body([
                    error: "Plugin already stopped"
            ] as Map<String, Object>)
        }

        pluginManager.stopPlugin(pluginId)

        return ResponseEntity.ok([
                message: "Plugin ${pluginId} stopped successfully",
                started: false
        ] as Map<String, Object>)
    }

    /**
     * Перезагрузить плагин
     */
    @PostMapping("/{pluginId}/reload")
    ResponseEntity<Map<String, Object>> reloadPlugin(@PathVariable String pluginId) {
        try {
            pluginManager.reloadPlugin(pluginId)
            return ResponseEntity.ok([
                    message: "Plugin ${pluginId} reloaded successfully"
            ] as Map<String, Object>)
        } catch (Exception e) {
            return ResponseEntity.status(500).body([
                    error: "Failed to reload plugin: ${e.message}"
            ] as Map<String, Object>)
        }
    }

    /**
     * Сканировать директорию плагинов
     */
    @PostMapping("/scan")
    ResponseEntity<Map<String, Object>> scanPlugins() {
        try {
            // Метод loadAllPlugins перезагружает плагины из директории
            pluginManager.loadAllPlugins()
            return ResponseEntity.ok([
                    message: "Plugins directory scanned successfully",
                    pluginCount: pluginManager.loadedPlugins.size()
            ] as Map<String, Object>)
        } catch (Exception e) {
            return ResponseEntity.status(500).body([
                    error: "Scan failed: ${e.message}"
            ] as Map<String, Object>)
        }
    }
}