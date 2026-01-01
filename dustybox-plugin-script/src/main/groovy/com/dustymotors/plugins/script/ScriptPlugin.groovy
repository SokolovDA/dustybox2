package com.dustymotors.plugins.script

import com.dustymotors.core.plugin.*
import groovy.transform.CompileStatic

@CompileStatic
class ScriptPlugin implements DustyboxPlugin {  // Убедитесь, что implements есть

    @Override
    String getName() { "script-manager" }

    @Override
    String getVersion() { "1.0.0" }

    @Override
    String getDescription() { "Управление и редактирование Groovy скриптов" }

    @Override
    void initialize(PluginContext context) {
        println "Script Manager Plugin initializing..."
    }

    @Override
    void start() {
        println "Script Manager Plugin started"
    }

    @Override
    void stop() {
        println "Script Manager Plugin stopped"
    }

    @Override
    List<PluginMenuItem> getMenuItems() {
        return [
                new PluginMenuItem(
                        title: "Скрипты",
                        icon: "📝",
                        path: "/web/plugins/script",
                        order: 1
                )
        ]
    }

    @Override
    List<WebResource> getWebResources() {
        return []
    }
}