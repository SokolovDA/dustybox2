// dustybox-core/src/main/groovy/com/dustymotors/core/web/ApiDocumentationController.groovy
package com.dustymotors.core.web

import org.springframework.web.bind.annotation.*
import com.dustymotors.core.plugin.PluginManager
import org.springframework.beans.factory.annotation.Autowired

@RestController
@RequestMapping("/api/docs")
class ApiDocumentationController {

    @Autowired
    private PluginManager pluginManager

    @GetMapping
    Map<String, Object> getApiDocumentation() {
        def coreEndpoints = [
                "GET    /api/health - Проверка здоровья системы",
                "GET    /api/system/info - Информация о системе",
                "GET    /api/system/endpoints - Все эндпоинты",
                "GET    /api/plugins/management - Управление плагинами",
                "GET    /api/debug/plugins/services - Отладка плагинов",
                "GET    /api/docs - Эта документация",
                "GET    /plugins/ping/{pluginId} - Проверка плагина",
                "GET    /plugins/{pluginId}/api/** - API плагина",
                "GET    /plugins/{pluginId}/web/** - UI плагина"
        ]

        def pluginEndpoints = [] as List<String>
        pluginManager.loadedPlugins.each { id, plugin ->
            pluginEndpoints.add("=== ${plugin.descriptor.name} (${id}) ===")
            pluginEndpoints.addAll(getPluginEndpoints(id, plugin))
        }

        return [
                title: "Dustybox API Documentation",
                version: "1.0.0",
                timestamp: new Date(),
                coreEndpoints: coreEndpoints,
                pluginEndpoints: pluginEndpoints,
                note: "Документация генерируется автоматически"
        ]
    }

    private List<String> getPluginEndpoints(String pluginId, def plugin) {
        def endpoints = []

        if (pluginId.contains("cddb")) {
            endpoints.addAll([
                    "GET    /api/cddb/disks - Все CD диски",
                    "GET    /api/cddb/disks/{id} - Диск по ID",
                    "POST   /api/cddb/disks - Создать диск",
                    "PUT    /api/cddb/disks/{id} - Обновить диск",
                    "DELETE /api/cddb/disks/{id} - Удалить диск",
                    "GET    /api/cddb/disks/search?query={query} - Поиск дисков",
                    "GET    /api/cddb/stats - Статистика",
                    "GET    /api/cddb/artists - Все артисты",
                    "GET    /api/cddb/test - Тест API"
            ])
        }

        if (pluginId.contains("script")) {
            endpoints.addAll([
                    "GET    /api/scripts - Все скрипты",
                    "POST   /api/scripts/execute - Выполнить скрипт"
            ])
        }

        return endpoints
    }
}