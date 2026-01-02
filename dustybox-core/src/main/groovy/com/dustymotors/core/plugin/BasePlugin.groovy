// dustybox-core/src/main/groovy/com/dustymotors/core/plugin/BasePlugin.groovy
package com.dustymotors.core.plugin

import groovy.transform.CompileStatic

@CompileStatic
abstract class BasePlugin implements DustyboxPlugin {

    protected PluginContext context

    @Override
    void initialize(PluginContext context) {
        this.context = context
        println "${getName()} v${getVersion()} initialized"
    }

    @Override
    void start() {
        println "${getName()} started"
    }

    @Override
    void stop() {
        println "${getName()} stopped"
    }

    @Override
    List<PluginMenuItem> getMenuItems() {
        return []
    }

    @Override
    List<WebResource> getWebResources() {
        return []
    }

    protected void logInfo(String message) {
        println "[${getName()}] ${message}"
    }

    protected void logError(String message, Throwable error = null) {
        println "[ERROR ${getName()}] ${message}"
        if (error) {
            error.printStackTrace()
        }
    }
}