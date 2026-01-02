// dustybox-core/src/main/groovy/com/dustymotors/core/web/PluginDispatcherController.groovy
package com.dustymotors.core.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.*
import org.springframework.web.bind.annotation.*
import com.dustymotors.core.plugin.PluginManager
import com.dustymotors.core.plugin.PluginInstance

@RestController
@RequestMapping("/plugins")
class PluginDispatcherController {

    @Autowired
    private PluginManager pluginManager

    /**
     * Простой эндпоинт для проверки работы диспетчера
     */
    @GetMapping("/ping/{pluginId}")
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
                timestamp: new Date()
        ])
    }

    /**
     * Эндпоинт для тестирования доступности плагинов
     */
    @GetMapping("/test/{pluginId}")
    ResponseEntity<Map<String, Object>> testPlugin(@PathVariable("pluginId") String pluginId) {
        PluginInstance plugin = pluginManager.getPlugin(pluginId)
        if (!plugin) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body([error: "Plugin not found: ${pluginId}"])
        }

        // Пробуем получить CD диски через сервис плагина
        try {
            def diskService = plugin.pluginContext?.getService("diskService")
            def diskCount = diskService ? "Service available" : "Service not found"

            return ResponseEntity.ok([
                    pluginId: pluginId,
                    name: plugin.descriptor.name,
                    version: plugin.descriptor.version,
                    status: plugin.started ? "STARTED" : "STOPPED",
                    diskService: diskCount,
                    message: "Plugin is loaded successfully",
                    note: "Plugins API is not fully implemented yet"
            ])
        } catch (Exception e) {
            return ResponseEntity.ok([
                    pluginId: pluginId,
                    name: plugin.descriptor.name,
                    error: "Plugin loaded but services may not be available: ${e.message}",
                    suggestion: "Plugin dispatcher is in development"
            ])
        }
    }

    /**
     * Диспетчеризация запросов к API плагинов
     * Формат: /plugins/{pluginId}/api/{остальной путь}
     */
    @RequestMapping("/{pluginId}/api/**")
    ResponseEntity<?> dispatchToPlugin(
            @PathVariable("pluginId") String pluginId,
            HttpServletRequest request) {

        PluginInstance plugin = pluginManager.getPlugin(pluginId)
        if (!plugin) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body([error: "Plugin not found: ${pluginId}"])
        }

        if (!plugin.started) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body([error: "Plugin ${pluginId} is not started"])
        }

        // Извлекаем путь API из оригинального запроса
        String requestURI = request.requestURI
        String apiPath = extractApiPath(requestURI, pluginId)

        // Проксируем запрос к Spring контексту плагина
        return proxyRequestToPlugin(plugin, apiPath, request)
    }

    /**
     * Диспетчеризация UI запросов к плагинам
     * Формат: /plugins/{pluginId}/web/**
     */
    @RequestMapping("/{pluginId}/web/**")
    ResponseEntity<?> dispatchToPluginUI(
            @PathVariable("pluginId") String pluginId,
            HttpServletRequest request) {

        PluginInstance plugin = pluginManager.getPlugin(pluginId)
        if (!plugin) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body([error: "Plugin not found: ${pluginId}"])
        }

        String requestURI = request.requestURI
        String webPath = extractWebPath(requestURI, pluginId)

        // Проксируем UI запрос
        return proxyRequestToPlugin(plugin, webPath, request)
    }

    /**
     * Проксирование запроса к плагину
     */
    private ResponseEntity<?> proxyRequestToPlugin(
            PluginInstance plugin,
            String path,
            HttpServletRequest originalRequest) {

        try {
            // Для демонстрации возвращаем информацию о плагине
            return ResponseEntity.ok([
                    pluginId: plugin.id,
                    pluginName: plugin.descriptor.name,
                    requestedPath: path,
                    message: "Plugin dispatcher is in development",
                    note: "Direct plugin API access is not yet implemented",
                    workaround: "Try accessing plugin services through core API endpoints",
                    endpoints: [
                            "/api/plugins/management - Manage plugins",
                            "/plugins/ping/{pluginId} - Plugin status",
                            "/plugins/test/{pluginId} - Plugin test"
                    ]
            ])
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body([error: "Failed to dispatch to plugin: ${e.message}"])
        }
    }

    /**
     * Извлечение пути API
     */
    private String extractApiPath(String requestURI, String pluginId) {
        // Убираем префикс /plugins/{pluginId}/api/
        String prefix = "/plugins/${pluginId}/api/"
        if (requestURI.startsWith(prefix)) {
            return requestURI.substring(prefix.length())
        }
        return requestURI
    }

    /**
     * Извлечение пути UI
     */
    private String extractWebPath(String requestURI, String pluginId) {
        // Убираем префикс /plugins/{pluginId}/web/
        String prefix = "/plugins/${pluginId}/web/"
        if (requestURI.startsWith(prefix)) {
            return requestURI.substring(prefix.length())
        }
        return requestURI
    }
}