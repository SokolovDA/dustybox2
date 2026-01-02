package com.dustymotors.plugins.cddb

import com.dustymotors.core.plugin.*
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
class CdDatabasePlugin extends BasePlugin {

    @Override
    String getName() { "cd-database" }

    @Override
    String getVersion() { "1.3.0" }

    @Override
    String getDescription() { "Управление базой данных CD дисков" }

    @Override
    void initialize(PluginContext context) {
        super.initialize(context)
        log.info("CD Database Plugin initializing...")

        try {
            // Получаем сервис из Spring контекста
            def springContext = context.pluginSpringContext
            if (springContext == null) {
                throw new IllegalStateException("Plugin Spring Context is null")
            }

            // Просто логируем, что сервис доступен
            def diskService = springContext.getBean(CdDiskService.class)
            log.info("Successfully retrieved CdDiskService: ${diskService?.getClass()?.name}")

            //TODO Это для отладки
            try {
                def count = diskService.count()
                log.info("CDDB INF: Disks: $count")
            } catch (Exception e) {
                log.error("CDDB INF: Error: ${e.message}", e)
            }

        } catch (Exception e) {
            log.error("Failed to initialize CD Database Plugin: ${e.message}", e)
            throw e
        }
    }

    @Override
    void start() {
        log.info("CD Database Plugin starting...")
        println "CD Database Plugin started"
    }

    @Override
    void stop() {
        log.info("CD Database Plugin stopping...")
        println "CD Database Plugin stopped"
    }

    @Override
    List<PluginMenuItem> getMenuItems() {
        return [
                new PluginMenuItem(
                        title: "CD Диски",
                        icon: "💿",
                        path: "/web/plugins/cddb/disks",
                        order: 1
                )
        ]
    }

    @Override
    List<WebResource> getWebResources() {
        return []
    }
}