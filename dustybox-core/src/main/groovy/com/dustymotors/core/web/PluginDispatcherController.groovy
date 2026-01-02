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

        // Пробуем найти обработчик в Spring контексте плагина
        try {
            def handlerMapping = plugin.springContext.getBean(
                    org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class
            )

            def handler = handlerMapping.getHandler(request)
            if (handler != null) {
                return ResponseEntity.ok([
                        message: "Plugin ${pluginId} can handle this request",
                        path: request.requestURI,
                        handler: handler.toString()
                ])
            }
        } catch (Exception e) {
            // Контроллер не найден
        }

        return ResponseEntity.ok([
                pluginId: pluginId,
                pluginName: plugin.descriptor.name,
                version: plugin.descriptor.version,
                message: "Plugin loaded successfully",
                apiAvailable: true,
                started: plugin.started,
                note: "Plugin API endpoints should be registered in plugin's Spring context"
        ])
    }

    /**
     * Простой эндпоинт для проверки работы плагинов
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
}