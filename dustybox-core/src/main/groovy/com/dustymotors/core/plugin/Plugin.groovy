package com.dustymotors.core.plugin

import groovy.transform.CompileStatic

@CompileStatic
interface DustyboxPlugin {
    String getName()
    String getVersion()
    String getDescription()

    void initialize(PluginContext context)
    void start()
    void stop()

    List<PluginEndpoint> getEndpoints()
    List<PluginMenuItem> getMenuItems()
    List<Class<?>> getServices()
    List<WebResource> getWebResources()
}

@CompileStatic
class PluginContext {
    def serviceRegistry
    def eventBus
    def webUIManager
    def scriptEngine
}

@CompileStatic
class PluginEndpoint {
    String path
    String method
    Closure handler
}

@CompileStatic
class PluginMenuItem {
    String title
    String icon
    String path
    int order
}

@CompileStatic
class WebResource {
    String type
    String path
    String url
}