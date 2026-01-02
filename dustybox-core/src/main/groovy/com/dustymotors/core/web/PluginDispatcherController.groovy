// dustybox-core/src/main/groovy/com/dustymotors/core/web/PluginDispatcherController.groovy
package com.dustymotors.core.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.*
import org.springframework.web.bind.annotation.*
import com.dustymotors.core.plugin.PluginManager
import com.dustymotors.core.plugin.PluginInstance
import org.springframework.web.servlet.HandlerMapping
import groovy.util.logging.Slf4j
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@Slf4j
@RestController
@RequestMapping("/plugins")
class PluginDispatcherController {

    @Autowired
    private PluginManager pluginManager

    /**
     * Диспетчеризация запросов к API плагинов
     * Формат: /plugins/{pluginId}/api/**
     */
    @RequestMapping("/{pluginId}/api/**")
    ResponseEntity<?> dispatchToPluginApi(
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

        // Извлекаем полный путь после /plugins/{pluginId}/api/
        String requestURI = request.requestURI
        String apiPath = extractApiPath(requestURI, pluginId)

        log.info("Dispatching to plugin {}: {}", pluginId, apiPath)

        // Попытка получить контроллер из Spring контекста плагина
        return handlePluginRequest(plugin, apiPath, request)
    }

    /**
     * Диспетчеризация UI запросов к плагинам
     * Формат: /plugins/{pluginId}/web/**
     */
    @RequestMapping("/{pluginId}/web/**")
    ResponseEntity<?> dispatchToPluginWeb(
            @PathVariable("pluginId") String pluginId,
            HttpServletRequest request) {

        PluginInstance plugin = pluginManager.getPlugin(pluginId)
        if (!plugin) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body([error: "Plugin not found: ${pluginId}"])
        }

        String requestURI = request.requestURI
        String webPath = extractWebPath(requestURI, pluginId)

        log.info("Dispatching UI to plugin {}: {}", pluginId, webPath)

        return handlePluginRequest(plugin, webPath, request)
    }

    private ResponseEntity<?> handlePluginRequest(PluginInstance plugin, String path, HttpServletRequest request) {
        try {
            // Пробуем найти контроллер в контексте плагина
            def controller = findControllerForPath(plugin, path, request.method)

            if (controller) {
                return invokeControllerMethod(controller, path, request)
            }

            // Если контроллер не найден, возвращаем информацию о доступных API
            return ResponseEntity.ok([
                    pluginId: plugin.id,
                    pluginName: plugin.descriptor.name,
                    version: plugin.descriptor.version,
                    requestedPath: path,
                    availableEndpoints: getAvailableEndpoints(plugin),
                    message: "Direct plugin API access is being developed",
                    note: "Some plugin endpoints may not be accessible through dispatcher yet"
            ])
        } catch (Exception e) {
            log.error("Failed to dispatch to plugin {}: {}", plugin.id, e.message, e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body([error: "Failed to dispatch to plugin: ${e.message}"])
        }
    }

    private def findControllerForPath(PluginInstance plugin, String path, String method) {
        try {
            // Пока просто возвращаем null - расширенная логика будет добавлена позже
            return null
        } catch (Exception e) {
            log.warn("Error finding controller for path {}: {}", path, e.message)
            return null
        }
    }

    private ResponseEntity<?> invokeControllerMethod(def controller, String path, HttpServletRequest request) {
        // TODO: Реализовать вызов методов контроллера
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body([error: "Controller invocation not implemented yet", path: path])
    }

    private List<String> getAvailableEndpoints(PluginInstance plugin) {
        def endpoints = []

        // Для CDDB плагина
        if (plugin.id.contains("cddb")) {
            endpoints.addAll([
                    "/plugins/${plugin.id}/api/disks - GET: Get all CD disks",
                    "/plugins/${plugin.id}/api/disks/{id} - GET: Get disk by ID",
                    "/plugins/${plugin.id}/api/disks - POST: Create new disk",
                    "/plugins/${plugin.id}/api/disks/search?query={query} - GET: Search disks"
            ])
        }

        // Для Script плагина
        if (plugin.id.contains("script")) {
            endpoints.addAll([
                    "/plugins/${plugin.id}/api/scripts - GET: List scripts",
                    "/plugins/${plugin.id}/api/scripts/execute - POST: Execute script"
            ])
        }

        return endpoints
    }

    /**
     * Извлечение пути API
     */
    private String extractApiPath(String requestURI, String pluginId) {
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
        String prefix = "/plugins/${pluginId}/web/"
        if (requestURI.startsWith(prefix)) {
            return requestURI.substring(prefix.length())
        }
        return requestURI
    }

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
                timestamp: new Date(),
                endpoints: getAvailableEndpoints(plugin)
        ])
    }
}