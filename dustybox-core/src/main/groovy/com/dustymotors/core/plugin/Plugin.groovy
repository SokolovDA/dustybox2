package com.dustymotors.core.plugin

import groovy.transform.CompileStatic
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
    // Доступ к данным
    DataSource dataSource

    // Идентификатор и контекст самого плагина
    String pluginId
    ApplicationContext pluginSpringContext

    // Методы-помощники для удобства плагина
    Object getService(String name) {
        if (pluginSpringContext != null) {
            try {
                return pluginSpringContext.getBean(name)
            } catch (Exception e) {
                return null
            }
        }
        return null
    }

    // Получить бин по имени и классу
    <T> T getBean(String name, Class<T> requiredType) {
        if (pluginSpringContext != null) {
            try {
                return pluginSpringContext.getBean(name, requiredType)
            } catch (Exception e) {
                return null
            }
        }
        return null
    }

    // Получить бин по классу
    <T> T getBean(Class<T> requiredType) {
        if (pluginSpringContext != null) {
            try {
                return pluginSpringContext.getBean(requiredType)
            } catch (Exception e) {
                return null
            }
        }
        return null
    }

    // Метод для регистрации бинов (если нужно)
    void registerBean(String name, Object bean) {
        // Эта функциональность может быть добавлена позже
        println "Bean registration not implemented yet: ${name}"
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