package com.dustymotors.core.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.*
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.RestTemplate
import com.dustymotors.core.plugin.PluginManager
import groovy.transform.CompileStatic

@RestController
@RequestMapping("/plugins/{pluginId}/api")
@CompileStatic
class PluginProxyController {

    @Autowired
    private PluginManager pluginManager

    private RestTemplate restTemplate = new RestTemplate()

    @RequestMapping("/**")
    ResponseEntity<?> proxyRequest(
            @PathVariable String pluginId,
            @RequestHeader HttpHeaders headers,
            @RequestBody(required = false) String body,
            HttpMethod method,
            HttpServletRequest request
    ) {
        // 1. Получаем базовый URL плагина
        def pluginInstance = pluginManager.getPlugin(pluginId)
        if (!pluginInstance) {
            return ResponseEntity.notFound().build()
        }

        // 2. Формируем целевой URL (пока просто локально)
        String path = extractPath(request.requestURI, pluginId)
        String targetUrl = "http://localhost:8080/internal/plugins/${pluginId}${path}"

        // 3. Проксируем запрос
        try {
            HttpEntity<String> entity = new HttpEntity<>(body, headers)
            return restTemplate.exchange(targetUrl, method, entity, String)
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Proxy error: ${e.message}")
        }
    }

    private String extractPath(String fullUri, String pluginId) {
        String prefix = "/plugins/${pluginId}/api"
        return fullUri.substring(prefix.length())
    }
}