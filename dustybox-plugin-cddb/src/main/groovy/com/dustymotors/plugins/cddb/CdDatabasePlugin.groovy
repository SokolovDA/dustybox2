package com.dustymotors.plugins.cddb

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

// @ScriptAccessible - временно убрано, пока аннотация не будет доступна
@Slf4j
@CompileStatic
class CdDatabasePlugin {

    private CdDiskService cdDiskService

    String getName() { "cd-database" }

    String getVersion() { "1.0.0" }

    String getDescription() { "Управление базой данных CD дисков" }

    void initialize(def context) {
        log.info("CD Database Plugin initializing...")

        // Инициализируем сервис
        cdDiskService = new CdDiskService()

        // Добавляем тестовые данные
        initializeTestData()

        println "CD Database Plugin initialized with ${cdDiskService.count()} disks"
    }

    void start() {
        log.info("CD Database Plugin starting...")
        println "CD Database Plugin started"
    }

    void stop() {
        log.info("CD Database Plugin stopping...")
        println "CD Database Plugin stopped"
    }

    List getEndpoints() {
        return [
                [
                        path: "/api/cddisks",
                        method: "GET",
                        handler: { Map params ->
                            return [
                                    disks: cdDiskService.findAll(),
                                    count: cdDiskService.count(),
                                    status: "ok"
                            ]
                        }
                ],
                [
                        path: "/api/cddisks",
                        method: "POST",
                        handler: { Map params ->
                            def disk = params.disk as Map
                            if (disk) {
                                def newDisk = new CdDisk(
                                        disk.title as String,
                                        disk.artist as String,
                                        disk.year as Integer
                                )
                                def saved = cdDiskService.save(newDisk)
                                return [savedDisk: saved, status: "created"]
                            }
                            return [error: "No disk data provided", status: "error"]
                        }
                ]
        ]
    }

    List getMenuItems() {
        return [
                [title: "CD Диски", icon: "💿", path: "/web/cddisks", order: 1]
        ]
    }

    List getServices() {
        return [CdDiskService.class] // Возвращаем класс сервиса
    }

    List getWebResources() {
        return [] // Пока возвращаем пустой список
    }

    private void initializeTestData() {
        // Добавляем тестовые данные
        cdDiskService.save(new CdDisk("The Dark Side of the Moon", "Pink Floyd", 1973))
        cdDiskService.save(new CdDisk("Thriller", "Michael Jackson", 1982))
        cdDiskService.save(new CdDisk("Back in Black", "AC/DC", 1980))
        cdDiskService.save(new CdDisk("The Bodyguard", "Whitney Houston", 1992))
        cdDiskService.save(new CdDisk("Bat Out of Hell", "Meat Loaf", 1977))
    }
}