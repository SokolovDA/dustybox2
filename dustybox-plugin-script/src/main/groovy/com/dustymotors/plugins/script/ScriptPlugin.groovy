package com.dustymotors.plugins.script

import com.dustymotors.core.plugin.*
import groovy.transform.CompileStatic

@CompileStatic
class ScriptPlugin extends BasePlugin {

    @Override
    String getName() { "script-manager" }

    @Override
    String getVersion() { "1.0.0" }

    @Override
    String getDescription() { "Управление скриптами" }

    @Override
    void initialize(PluginContext context) {
        super.initialize(context)
        println "Script Manager Plugin initialized"
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
        return []
    }

    @Override
    List<WebResource> getWebResources() {
        return []
    }
}