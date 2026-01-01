package com.dustymotors.core.plugin

import groovy.transform.CompileStatic
import com.dustymotors.core.ServiceRegistry
import com.dustymotors.core.EventBus
import com.dustymotors.core.WebUIManager
import com.dustymotors.core.ScriptEngine
import org.springframework.context.ApplicationContext
import javax.sql.DataSource

@CompileStatic
interface DustyboxPlugin {
    String getName()
    String getVersion()
    String getDescription()

    void initialize(PluginContext context)
    void start()
    void stop()

    List<PluginMenuItem> getMenuItems()
    List<WebResource> getWebResources()
}

@CompileStatic
class PluginContext {
    // Основные сервисы ядра - явные типы
    ServiceRegistry serviceRegistry
    EventBus eventBus
    WebUIManager webUIManager
    ScriptEngine scriptEngine

    // Доступ к данным
    DataSource dataSource

    // Идентификатор и контекст самого плагина
    String pluginId
    ApplicationContext pluginSpringContext

    // Методы-помощники для удобства плагина
    void registerService(String name, Object service) {
        serviceRegistry.register("${pluginId}.${name}", service)
    }

    Object getService(String name) {
        return serviceRegistry.getService("${pluginId}.${name}")
    }

    void publishEvent(String event, Map<String, Object> data = [:]) {
        Map<String, Object> eventData = new HashMap<>(data)
        eventData.put("pluginId", pluginId)
        eventBus.publish(event, eventData)
    }

    void subscribe(String event, Closure handler) {
        eventBus.subscribe(event, handler)
    }
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
    String type // 'css' или 'js'
    String path // Путь внутри JAR плагина
    String url  // URL для доступа снаружи
}