package com.dustymotors.core.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.*
import org.springframework.web.bind.annotation.*
import com.dustymotors.core.plugin.PluginManager
import groovy.transform.CompileStatic

@RestController
@CompileStatic
class PluginDispatcherController {

    @Autowired
    private PluginManager pluginManager

    /**
     * Простой эндпоинт для проверки доступа к API плагинов
     */
    @GetMapping("/plugins/{pluginId}/api/info")
    ResponseEntity<Map<String, Object>> getPluginApiInfo(@PathVariable String pluginId) {
        def pluginInstance = pluginManager.getPlugin(pluginId)
        if (!pluginInstance) {
            return ResponseEntity.status(404)
                    .body([error: "Plugin not found: ${pluginId}"] as Map<String, Object>)
        }

        return ResponseEntity.ok([
                pluginId: pluginId,
                name: pluginInstance.descriptor.name,
                version: pluginInstance.descriptor.version,
                description: pluginInstance.descriptor.description,
                message: "Plugin API is accessible through direct endpoints",
                timestamp: new Date()
        ] as Map<String, Object>)
    }

    /**
     * Список всех плагинов с их API
     */
    @GetMapping("/plugins/api/list")
    ResponseEntity<Map<String, Object>> listAllPluginApis() {
        Map<String, Object> result = [:] as Map<String, Object>

        pluginManager.loadedPlugins.each { String pluginId, pluginInstance ->
            result[pluginId] = [
                    name: pluginInstance.descriptor.name,
                    version: pluginInstance.descriptor.version,
                    endpoints: getPluginEndpoints(pluginId),
                    started: pluginInstance.started
            ] as Map<String, Object>
        }

        return ResponseEntity.ok([
                count: result.size(),
                plugins: result,
                message: "Plugins are accessible through their direct REST endpoints",
                note: "CDDB plugin API: /api/plugins/cddb/**",
                timestamp: new Date()
        ] as Map<String, Object>)
    }

    private List<String> getPluginEndpoints(String pluginId) {
        // Возвращаем примерные пути к API
        if (pluginId.contains('cddb')) {
            return [
                    "/api/plugins/cddb/disks",
                    "/api/plugins/cddb/disks/{id}",
                    "/api/plugins/cddb/disks/count"
            ] as List<String>
        } else if (pluginId.contains('script')) {
            return [
                    "/api/plugins/script/** (coming soon)"
            ] as List<String>
        } else {
            return ["/api/plugins/${pluginId}/**".toString()] as List<String>
        }
    }
}