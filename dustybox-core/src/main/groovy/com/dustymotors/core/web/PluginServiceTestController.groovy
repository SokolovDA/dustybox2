// dustybox-core/src/main/groovy/com/dustymotors/core/web/PluginServiceTestController.groovy
package com.dustymotors.core.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*
import com.dustymotors.core.plugin.PluginManager

@RestController
@RequestMapping("/api/debug/plugins")
class PluginServiceTestController {

    @Autowired
    private PluginManager pluginManager

    /**
     * Проверка всех загруженных плагинов и их сервисов
     */
    @GetMapping("/services")
    Map<String, Object> checkAllPluginServices() {
        def results = [:] as Map<String, Object>
        def totalServices = 0

        pluginManager.loadedPlugins.each { pluginId, pluginInstance ->
            try {
                def pluginResult = [:] as Map<String, Object>
                pluginResult["name"] = pluginInstance.descriptor.name
                pluginResult["version"] = pluginInstance.descriptor.version
                pluginResult["started"] = pluginInstance.started
                pluginResult["hasPluginContext"] = pluginInstance.pluginContext != null
                pluginResult["hasSpringContext"] = pluginInstance.springContext != null

                // Пробуем получить доступные сервисы
                if (pluginInstance.pluginContext != null) {
                    // Для CDDB плагина пробуем получить diskService
                    if (pluginId.contains("cddb")) {
                        try {
                            def diskService = pluginInstance.pluginContext.getService("diskService")
                            pluginResult["diskServiceAvailable"] = diskService != null
                            pluginResult["diskServiceClass"] = diskService?.getClass()?.name

                            if (diskService != null) {
                                // Пробуем вызвать простой метод
                                try {
                                    def count = diskService.count()
                                    pluginResult["diskCount"] = count
                                    pluginResult["serviceTest"] = "SUCCESS"
                                    totalServices++
                                } catch (Exception e) {
                                    pluginResult["serviceTest"] = "ERROR: ${e.message}"
                                }
                            }
                        } catch (Exception e) {
                            pluginResult["diskServiceError"] = e.message
                        }
                    }

                    // Для Script плагина
                    if (pluginId.contains("script")) {
                        pluginResult["type"] = "script-manager"
                    }
                }

                results[pluginId] = pluginResult

            } catch (Exception e) {
                results[pluginId] = [error: e.message]
            }
        }

        return [
                timestamp: new Date(),
                totalPlugins: results.size(),
                totalServicesFound: totalServices,
                plugins: results
        ]
    }

    /**
     * Тестирование конкретного плагина
     */
    @GetMapping("/{pluginId}/test")
    Map<String, Object> testPlugin(@PathVariable String pluginId) {
        def plugin = pluginManager.getPlugin(pluginId)
        if (!plugin) {
            return [error: "Plugin not found: ${pluginId}"]
        }

        try {
            def result = [
                    pluginId: pluginId,
                    name: plugin.descriptor.name,
                    version: plugin.descriptor.version,
                    started: plugin.started,
                    pluginContextAvailable: plugin.pluginContext != null,
                    springContextAvailable: plugin.springContext != null,
                    timestamp: new Date()
            ]

            // Тестирование CDDB плагина
            if (pluginId.contains("cddb")) {
                def diskService = plugin.pluginContext?.getService("diskService")
                result["diskServiceAvailable"] = diskService != null

                if (diskService != null) {
                    try {
                        // Тест 1: Получить количество записей
                        def count = diskService.count()
                        result["test1_count"] = count
                        result["test1_status"] = "SUCCESS"

                        // Тест 2: Получить все записи
                        def disks = diskService.findAll()
                        result["test2_findAll"] = disks?.size() ?: 0
                        result["test2_status"] = "SUCCESS"

                        result["overall"] = "CDDB Plugin is working!"

                    } catch (Exception e) {
                        result["testError"] = e.message
                        result["overall"] = "CDDB Plugin has errors"
                    }
                } else {
                    result["overall"] = "DiskService not available"
                }
            }

            // Тестирование Script плагина
            if (pluginId.contains("script")) {
                result["type"] = "Script Manager Plugin"
                result["overall"] = "Script plugin loaded (service testing not implemented)"
            }

            return result

        } catch (Exception e) {
            return [
                    pluginId: pluginId,
                    error: "Test failed: ${e.message}",
                    stackTrace: e.stackTrace.take(5).join("\n")
            ]
        }
    }

    /**
     * Быстрая проверка CDDB API (если доступен)
     */
    @GetMapping("/cddb/quick-test")
    Map<String, Object> quickCddbTest() {
        def cddbPlugin = pluginManager.getPlugin("cddb-plugin-1.3.0")
        if (!cddbPlugin) {
            return [error: "CDDB plugin not found"]
        }

        try {
            def diskService = cddbPlugin.pluginContext?.getService("diskService")
            if (!diskService) {
                return [
                        plugin: "cddb-plugin-1.3.0",
                        status: "LOADED",
                        service: "NOT_AVAILABLE",
                        message: "DiskService not found in plugin context"
                ]
            }

            // Пробуем выполнить несколько операций
            def count = diskService.count()
            def disks = diskService.findAll()

            return [
                    plugin: "cddb-plugin-1.3.0",
                    status: "WORKING",
                    service: "CdDiskService",
                    operations: [
                            count: count,
                            findAll: disks?.size() ?: 0,
                            sampleTitles: disks?.take(3)?.collect { it.title } ?: []
                    ],
                    message: "CDDB plugin services are working correctly!"
            ]

        } catch (Exception e) {
            return [
                    plugin: "cddb-plugin-1.3.0",
                    status: "ERROR",
                    error: e.message,
                    stackTrace: e.stackTrace.take(3).join("\n")
            ]
        }
    }

    /**
     * Информация о состоянии всех плагинов
     */
    @GetMapping("/status")
    Map<String, Object> pluginsStatus() {
        def plugins = [] as List<Map<String, Object>>
        def startedCount = 0

        pluginManager.loadedPlugins.each { pluginId, pluginInstance ->
            def pluginInfo = [
                    id: pluginId,
                    name: pluginInstance.descriptor.name,
                    version: pluginInstance.descriptor.version,
                    description: pluginInstance.descriptor.description,
                    started: pluginInstance.started,
                    jarFile: pluginInstance.jarFile.name,
                    jarSize: "${pluginInstance.jarFile.length() / 1024} KB"
            ]

            if (pluginInstance.started) startedCount++

            // Проверяем наличие сервисов
            if (pluginInstance.pluginContext != null) {
                if (pluginId.contains("cddb")) {
                    pluginInfo["services"] = ["CdDiskService"]
                } else if (pluginId.contains("script")) {
                    pluginInfo["services"] = ["ScriptManager"]
                }
            }

            plugins.add(pluginInfo)
        }

        return [
                timestamp: new Date(),
                totalPlugins: plugins.size(),
                startedPlugins: startedCount,
                stoppedPlugins: plugins.size() - startedCount,
                plugins: plugins.sort { it.id }
        ]
    }

    @GetMapping("/{pluginId}/services/detailed")
    Map<String, Object> getPluginServicesDetailed(@PathVariable String pluginId) {
        def plugin = pluginManager.getPlugin(pluginId)
        if (!plugin) {
            return [error: "Plugin not found: ${pluginId}"]
        }

        def result = [
                pluginId: pluginId,
                name: plugin.descriptor.name,
                version: plugin.descriptor.version,
                started: plugin.started,
                timestamp: new Date()
        ]

        // Проверяем Spring контекст
        if (plugin.springContext) {
            def beanNames = plugin.springContext.beanDefinitionNames
            result["springContextBeans"] = beanNames.length
            result["springBeanNames"] = beanNames.take(10) // Первые 10 бинов

            // Ищем сервисы
            def services = beanNames.findAll { it.contains("Service") }
            result["serviceBeans"] = services
        }

        // Проверяем реестр сервисов
        if (plugin.pluginContext) {
            // Пытаемся получить сервисы через контекст
            try {
                def diskService = plugin.pluginContext.getService("diskService")
                result["diskServiceAvailable"] = diskService != null
                if (diskService) {
                    result["diskServiceMethods"] = diskService.class.declaredMethods.collect { it.name }
                }
            } catch (Exception e) {
                result["serviceError"] = e.message
            }
        }

        return result
    }
}