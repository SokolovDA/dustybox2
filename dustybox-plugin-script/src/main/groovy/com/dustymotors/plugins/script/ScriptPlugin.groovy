package com.dustymotors.plugins.script

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
class ScriptPlugin {

    String getName() { "script-manager" }

    String getVersion() { "1.0.0" }

    String getDescription() { "Управление и редактирование Groovy скриптов" }

    void initialize(def context) {
        log.info("Script Manager Plugin initializing...")
        println "Script Manager Plugin initialized"
    }

    void start() {
        log.info("Script Manager Plugin starting...")
        println "Script Manager Plugin started"
    }

    void stop() {
        log.info("Script Manager Plugin stopping...")
        println "Script Manager Plugin stopped"
    }

    List getEndpoints() {
        return [
                [path: "/api/scripts", method: "GET", handler: { params ->
                    return [message: "Scripts endpoint", status: "ok"]
                }]
        ]
    }

    List getMenuItems() {
        return [
                [title: "Скрипты", icon: "📝", path: "/web/scripts", order: 1]
        ]
    }

    List getServices() {
        return []
    }

    List getWebResources() {
        return []
    }
}