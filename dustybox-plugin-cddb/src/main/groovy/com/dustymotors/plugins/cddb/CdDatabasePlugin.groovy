package com.dustymotors.plugins.cddb

import com.dustymotors.core.plugin.*
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import javax.sql.DataSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Slf4j
@CompileStatic
@Component
class CdDatabasePlugin implements DustyboxPlugin {

    // Будет инжектирован из дочернего Spring-контекста плагина
    @Autowired
    private CdDiskService cdDiskService

    private PluginContext pluginContext

    // Реализация методов интерфейса DustyboxPlugin
    @Override
    String getName() { "cd-database" }

    @Override
    String getVersion() { "1.3.0" }

    @Override
    String getDescription() { "Управление базой данных CD дисков" }

    @Override
    void initialize(PluginContext context) {
        log.info("CD Database Plugin initializing...")
        this.pluginContext = context

        // Регистрируем сервис в реестре ядра
        context.registerService("diskService", cdDiskService)

        // Подписываемся на события ядра
        context.subscribe("system.start") { Map data ->
            log.info("System started event received")
            initializeSampleData()
        }

        // Публикуем событие о готовности
        context.publishEvent("cddb.ready", [version: version])

        log.info("CD Database Plugin initialized. Registered service: cddb.diskService")
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

    // Метод getEndpoints() больше не нужен - эндпоинты через @RestController
    // Удалите его полностью или оставьте пустую реализацию:
    // List<PluginEndpoint> getEndpoints() { return [] }

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
     * Инициализация тестовых данных (выполняется после старта системы)
     */
    @Transactional
    private void initializeSampleData() {
        try {
            def count = cdDiskService.count()
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
                    cdDiskService.save(disk)
                }

                log.info("Initialized ${sampleDisks.size()} sample CDs")
                pluginContext.publishEvent("cddb.sampleData.initialized", [count: sampleDisks.size()])
            }
        } catch (Exception e) {
            log.error("Failed to initialize sample data: ${e.message}", e)
        }
    }
}