package com.dustymotors.plugins.cddb

import com.dustymotors.core.plugin.*
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
class CdDatabasePlugin extends BasePlugin {

    private CdDiskService diskService

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

            this.diskService = springContext.getBean(CdDiskService.class)
            log.info("Successfully retrieved CdDiskService: ${diskService?.getClass()?.name}")

            // Регистрируем сервис в реестре ядра
            context.registerService("diskService", diskService)
            log.info("Registered service: diskService")

            // Инициализируем данные
            initializeSampleData(diskService)

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
                ),
                new PluginMenuItem(
                        title: "Поиск CD",
                        icon: "🔍",
                        path: "/web/plugins/cddb/search",
                        order: 2
                )
        ]
    }

    @Override
    List<WebResource> getWebResources() {
        return [
                new WebResource(type: "css", path: "/static/cddb/styles.css", url: "/plugins/cddb/static/styles.css"),
                new WebResource(type: "js", path: "/static/cddb/app.js", url: "/plugins/cddb/static/app.js")
        ]
    }

    /**
     * Инициализация тестовых данных
     */
    private void initializeSampleData(CdDiskService diskService) {
        try {
            def count = diskService.count()
            if (count == 0) {
                log.info("Initializing sample CD data...")
                def sampleDisks = [
                        new CdDisk(title: "The Dark Side of the Moon", artist: "Pink Floyd", year: 1973),
                        new CdDisk(title: "Thriller", artist: "Michael Jackson", year: 1982),
                        new CdDisk(title: "Back in Black", artist: "AC/DC", year: 1980),
                        new CdDisk(title: "The Bodyguard", artist: "Whitney Houston", year: 1992),
                        new CdDisk(title: "Bat Out of Hell", artist: "Meat Loaf", year: 1977)
                ]

                sampleDisks.each { disk ->
                    diskService.save(disk)
                }

                log.info("Initialized ${sampleDisks.size()} sample CDs")
//                pluginContext.publishEvent("cddb.sampleData.initialized", [count: sampleDisks.size()])
            } else {
                log.info("Sample data already exists (${count} records)")
            }
        } catch (Exception e) {
            log.error("Failed to initialize sample data: ${e.message}", e)
        }
    }
}