package com.dustymotors.core.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.*
import org.springframework.web.bind.annotation.*
import com.dustymotors.core.plugin.PluginManager
import com.dustymotors.core.plugin.PluginInstance
import java.lang.reflect.Method

@RestController
class PluginDispatcherController {

    @Autowired
    private PluginManager pluginManager

    /**
     * Проксирование API плагинов через единый интерфейс
     */
    @RequestMapping("/plugins/{pluginId}/api/**")
    ResponseEntity<?> proxyPluginApi(
            @PathVariable("pluginId") String pluginId,
            HttpServletRequest request) {

        PluginInstance plugin = pluginManager.getPlugin(pluginId)
        if (!plugin) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body([error: "Plugin not found: ${pluginId}",
                           availablePlugins: pluginManager.loadedPlugins.keySet()])
        }

        try {
            // Получаем Spring контекст плагина
            def springContext = plugin.springContext
            if (!springContext) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body([error: "Plugin ${pluginId} has no Spring context"])
            }

            // Используем рефлексию для доступа к сервису плагина
            return handlePluginRequest(pluginId, springContext, request)

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body([error: "Failed to proxy request to plugin ${pluginId}: ${e.message}"])
        }
    }

    /**
     * Обработка запросов к плагину через рефлексию
     */
    private ResponseEntity<?> handlePluginRequest(String pluginId, def springContext, HttpServletRequest request) {
        try {
            if (pluginId == "cddb") {
                // Получаем CdDiskService через рефлексию
                ClassLoader pluginClassLoader = springContext.getClassLoader()
                Class<?> cdDiskServiceClass = pluginClassLoader.loadClass("com.dustymotors.plugins.cddb.CdDiskService")
                def diskService = springContext.getBean(cdDiskServiceClass)

                def path = request.requestURI
                def method = request.method

                if (path.endsWith("/api/disks") && method == "GET") {
                    // Вызываем метод findAll
                    Method findAllMethod = cdDiskServiceClass.getMethod("findAll")
                    def result = findAllMethod.invoke(diskService)
                    return ResponseEntity.ok(result)

                } else if (path.endsWith("/api/disks/count") && method == "GET") {
                    // Вызываем метод count
                    Method countMethod = cdDiskServiceClass.getMethod("count")
                    def count = countMethod.invoke(diskService)
                    return ResponseEntity.ok([count: count])

                } else if (path.endsWith("/api/disks/health") && method == "GET") {
                    // Вызываем метод count для health check
                    Method countMethod = cdDiskServiceClass.getMethod("count")
                    def count = countMethod.invoke(diskService)
                    return ResponseEntity.ok([
                            status: "UP",
                            service: "CdDiskService",
                            diskCount: count,
                            timestamp: new Date()
                    ])
                }
            }

            // Если не нашли подходящий обработчик
            return ResponseEntity.ok(getPluginApiInfo(pluginId, request.requestURI))

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body([error: "Handler execution failed: ${e.message}"])
        }
    }

    /**
     * Прямой доступ к CDDB API (для обратной совместимости)
     */
    @RequestMapping("/api/disks/**")
    ResponseEntity<?> proxyCddbApi(HttpServletRequest request) {
        return proxyPluginApi("cddb", request)
    }

    /**
     * Проксирование UI плагинов
     */
    @RequestMapping("/plugins/{pluginId}/web/**")
    ResponseEntity<?> proxyPluginWeb(
            @PathVariable("pluginId") String pluginId,
            HttpServletRequest request) {

        PluginInstance plugin = pluginManager.getPlugin(pluginId)
        if (!plugin) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body([error: "Plugin not found: ${pluginId}"])
        }

        return ResponseEntity.ok([
                pluginId: pluginId,
                name: plugin.descriptor.name,
                version: plugin.descriptor.version,
                message: "Plugin UI is available",
                note: "Direct UI access needs to be implemented"
        ])
    }

    /**
     * Информация о доступном API плагина
     */
    private Map<String, Object> getPluginApiInfo(String pluginId, String requestPath) {
        def endpoints = []

        if (pluginId == "cddb") {
            endpoints.addAll([
                    "GET    /plugins/${pluginId}/api/disks - Get all CD disks",
                    "GET    /plugins/${pluginId}/api/disks/{id} - Get disk by ID",
                    "POST   /plugins/${pluginId}/api/disks - Create new disk",
                    "PUT    /plugins/${pluginId}/api/disks/{id} - Update disk",
                    "DELETE /plugins/${pluginId}/api/disks/{id} - Delete disk",
                    "GET    /plugins/${pluginId}/api/disks/count - Count disks",
                    "GET    /plugins/${pluginId}/api/disks/health - Health check"
            ])
        } else if (pluginId == "script") {
            endpoints.addAll([
                    "GET    /plugins/${pluginId}/api/scripts - List scripts",
                    "POST   /plugins/${pluginId}/api/scripts/execute - Execute script"
            ])
        }

        return [
                pluginId: pluginId,
                availableEndpoints: endpoints,
                requestedPath: requestPath,
                message: "Access plugin API through /plugins/${pluginId}/api/**",
                note: "For backward compatibility, CDDB API is also available at /api/disks/**"
        ]
    }

    /**
     * Простой эндпоинт для проверки работы плагинов
     */
    @GetMapping("/plugins/ping/{pluginId}")
    ResponseEntity<Map<String, Object>> pingPlugin(@PathVariable("pluginId") String pluginId) {
        PluginInstance plugin = pluginManager.getPlugin(pluginId)
        if (!plugin) {
            return ResponseEntity.ok([
                    status: "NOT_FOUND",
                    message: "Plugin ${pluginId} not loaded",
                    availablePlugins: pluginManager.loadedPlugins.keySet()
            ])
        }

        return ResponseEntity.ok([
                pluginId: pluginId,
                name: plugin.descriptor.name,
                version: plugin.descriptor.version,
                status: plugin.started ? "RUNNING" : "STOPPED",
                health: "OK",
                timestamp: new Date(),
                apiAccess: "/plugins/${pluginId}/api/**",
                directAccess: pluginId == "cddb" ? "/api/disks/** (legacy)" : "N/A"
        ])
    }
}