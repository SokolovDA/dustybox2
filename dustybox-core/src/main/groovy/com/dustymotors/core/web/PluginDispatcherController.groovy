package com.dustymotors.core.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.*
import org.springframework.web.bind.annotation.*
import com.dustymotors.core.plugin.PluginManager
import com.dustymotors.core.plugin.PluginInstance
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.mvc.method.RequestMappingInfo
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

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
                    .body([error: "Plugin not found: ${pluginId}"])
        }

        try {
            // Получаем Spring контекст плагина
            def springContext = plugin.springContext
            if (!springContext) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body([error: "Plugin ${pluginId} has no Spring context"])
            }

            // Ищем обработчик в контексте плагина
            def handlerMapping = springContext.getBean(RequestMappingHandlerMapping.class)
            def handlerMethod = handlerMapping.getHandler(request)

            if (handlerMethod != null) {
                // Исполняем обработчик
                return executeHandlerMethod(springContext, handlerMethod, request)
            } else {
                // Если обработчик не найден, возвращаем информацию о доступных API
                return ResponseEntity.ok(getPluginApiInfo(plugin, request))
            }

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body([error: "Failed to proxy request to plugin ${pluginId}: ${e.message}"])
        }
    }

    /**
     * Прямой доступ к CDDB API (для обратной совместимости)
     */
    @RequestMapping("/api/disks/**")
    ResponseEntity<?> proxyCddbApi(HttpServletRequest request) {
        return proxyPluginApi("cddb-plugin-1.3.0", request)
    }

    @RequestMapping("/api/disks/count")
    ResponseEntity<?> proxyCddbCount() {
        return proxyPluginApi("cddb-plugin-1.3.0", null)
    }

    @RequestMapping("/api/disks/health")
    ResponseEntity<?> proxyCddbHealth() {
        return proxyPluginApi("cddb-plugin-1.3.0", null)
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
     * Исполнение метода обработчика
     */
    private ResponseEntity<?> executeHandlerMethod(def springContext, def handlerMethod, HttpServletRequest request) {
        try {
            // Пока просто возвращаем информацию о найденном обработчике
            // В будущем можно реализовать полное исполнение
            return ResponseEntity.ok([
                    message: "Handler found in plugin",
                    handler: handlerMethod.toString(),
                    requestPath: request.requestURI,
                    note: "Direct handler execution will be implemented in the next version"
            ])
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body([error: "Handler execution failed: ${e.message}"])
        }
    }

    /**
     * Информация о доступном API плагина
     */
    private Map<String, Object> getPluginApiInfo(PluginInstance plugin, HttpServletRequest request) {
        def endpoints = []

        // Для CDDB плагина
        if (plugin.id.contains("cddb")) {
            endpoints.addAll([
                    "GET    /plugins/${plugin.id}/api/disks - Get all CD disks",
                    "GET    /plugins/${plugin.id}/api/disks/{id} - Get disk by ID",
                    "POST   /plugins/${plugin.id}/api/disks - Create new disk",
                    "PUT    /plugins/${plugin.id}/api/disks/{id} - Update disk",
                    "DELETE /plugins/${plugin.id}/api/disks/{id} - Delete disk",
                    "GET    /plugins/${plugin.id}/api/disks/count - Count disks",
                    "GET    /plugins/${plugin.id}/api/disks/health - Health check"
            ])
        }

        return [
                pluginId: plugin.id,
                pluginName: plugin.descriptor.name,
                version: plugin.descriptor.version,
                availableEndpoints: endpoints,
                requestedPath: request?.requestURI,
                message: "Access plugin API through /plugins/${plugin.id}/api/**",
                directAccessNote: "Direct endpoint mapping needs to be configured"
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
                    message: "Plugin ${pluginId} not loaded"
            ])
        }

        return ResponseEntity.ok([
                pluginId: pluginId,
                name: plugin.descriptor.name,
                version: plugin.descriptor.version,
                status: plugin.started ? "RUNNING" : "STOPPED",
                health: "OK",
                timestamp: new Date(),
                apiAccess: "Use /plugins/${pluginId}/api/** to access plugin API"
        ])
    }
}