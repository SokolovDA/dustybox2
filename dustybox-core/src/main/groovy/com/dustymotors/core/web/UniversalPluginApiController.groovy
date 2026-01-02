// dustybox-core/src/main/groovy/com/dustymotors/core/web/UniversalPluginApiController.groovy
package com.dustymotors.core.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*
import com.dustymotors.core.plugin.PluginManager
import com.dustymotors.core.plugin.PluginInstance
import groovy.transform.CompileStatic

@RestController
@RequestMapping("/api/plugins")
@CompileStatic
class UniversalPluginApiController {

    @Autowired
    private PluginManager pluginManager

    @GetMapping("/{pluginId}/info")
    Map<String, Object> getPluginInfo(@PathVariable String pluginId) {
        def plugin = pluginManager.getPlugin(pluginId)
        if (!plugin) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Plugin not found: ${pluginId}"
            )
        }

        return [
                id: pluginId,
                name: plugin.descriptor.name,
                version: plugin.descriptor.version,
                description: plugin.descriptor.description,
                status: plugin.started ? "RUNNING" : "STOPPED",
                hasSpringContext: plugin.springContext != null,
                jarFile: plugin.jarFile.name,
                endpoints: getPluginEndpoints(plugin)
        ]
    }

    @PostMapping("/{pluginId}/commands/{command}")
    Map<String, Object> executeCommand(
            @PathVariable String pluginId,
            @PathVariable String command,
            @RequestBody(required = false) Map<String, Object> data) {

        def plugin = pluginManager.getPlugin(pluginId)
        if (!plugin) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Plugin not found: ${pluginId}"
            )
        }

        try {
            switch (command) {
                case "start":
                    if (plugin.started) {
                        return [status: "already_started", message: "Plugin is already running"]
                    }
                    pluginManager.startPlugin(pluginId)
                    return [status: "started", message: "Plugin started successfully"]

                case "stop":
                    if (!plugin.started) {
                        return [status: "already_stopped", message: "Plugin is already stopped"]
                    }
                    pluginManager.stopPlugin(pluginId)
                    return [status: "stopped", message: "Plugin stopped successfully"]

                case "reload":
                    pluginManager.reloadPlugin(pluginId)
                    return [status: "reloaded", message: "Plugin reloaded successfully"]

                case "call-service":
                    def serviceName = data?.serviceName as String
                    def methodName = data?.methodName as String
                    def params = data?.parameters as List ?: []

                    if (!serviceName || !methodName) {
                        return [error: "serviceName and methodName are required"]
                    }

                    def result = callPluginService(plugin, serviceName, methodName, params)
                    return [status: "success", result: result]

                default:
                    return [error: "Unknown command: ${command}"]
            }
        } catch (Exception e) {
            return [
                    status: "error",
                    error: e.message,
                    stackTrace: e.stackTrace.take(5).collect { it.toString() }
            ]
        }
    }

    private def callPluginService(PluginInstance plugin, String serviceName, String methodName, List params) {
        def springContext = plugin.springContext
        if (!springContext) {
            throw new RuntimeException("Plugin has no Spring context")
        }

        // Ищем сервис по имени
        def service = null
        try {
            service = springContext.getBean(serviceName)
        } catch (Exception e) {
            // Пробуем найти по типу
            try {
                def beanClass = Class.forName(serviceName)
                def beans = springContext.getBeansOfType(beanClass)
                if (beans && !beans.isEmpty()) {
                    service = beans.values().first()
                }
            } catch (Exception e2) {
                throw new RuntimeException("Service not found: ${serviceName}")
            }
        }

        if (!service) {
            throw new RuntimeException("Service not found: ${serviceName}")
        }

        // Вызываем метод через рефлексию
        def method = service.class.methods.find { it.name == methodName }
        if (!method) {
            throw new RuntimeException("Method not found: ${methodName}")
        }

        return method.invoke(service, params as Object[])
    }

    private List<String> getPluginEndpoints(PluginInstance plugin) {
        List<String> endpoints = [] as List<String>

        // Для CDDB плагина
        if (plugin.id.contains("cddb")) {
            def cddbEndpoints = [
                    "GET    /api/plugins/${plugin.id}/api/disks",
                    "GET    /api/plugins/${plugin.id}/api/disks/{id}",
                    "POST   /api/plugins/${plugin.id}/api/disks",
                    "PUT    /api/plugins/${plugin.id}/api/disks/{id}",
                    "DELETE /api/plugins/${plugin.id}/api/disks/{id}",
                    "GET    /api/plugins/${plugin.id}/api/disks/search?query={query}",
                    "GET    /api/plugins/${plugin.id}/api/stats",
                    "GET    /api/plugins/${plugin.id}/api/artists"
            ]
            // Преобразуем GString в String
            cddbEndpoints.each { endpoint ->
                endpoints.add(endpoint.toString())
            }
        }

        // Для Script плагина
        if (plugin.id.contains("script")) {
            def scriptEndpoints = [
                    "GET    /api/plugins/${plugin.id}/api/scripts",
                    "POST   /api/plugins/${plugin.id}/api/scripts/execute"
            ]
            scriptEndpoints.each { endpoint ->
                endpoints.add(endpoint.toString())
            }
        }

        return endpoints
    }

    @GetMapping("/list")
    Map<String, Object> listAllPlugins() {
        def plugins = [] as List<Map<String, Object>>

        pluginManager.loadedPlugins.each { id, plugin ->
            plugins.add([
                    id: id,
                    name: plugin.descriptor.name,
                    version: plugin.descriptor.version,
                    description: plugin.descriptor.description,
                    started: plugin.started,
                    type: plugin.descriptor.type,
                    endpoints: getPluginEndpoints(plugin)
            ])
        }

        return [
                count: plugins.size(),
                plugins: plugins.sort { it.id }
        ]
    }
}