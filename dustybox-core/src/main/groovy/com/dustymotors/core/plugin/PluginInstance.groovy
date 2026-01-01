package com.dustymotors.core.plugin

import groovy.transform.CompileStatic
import org.springframework.context.ApplicationContext

/**
 * Контейнер для состояния загруженного плагина.
 * Хранит все необходимые ссылки для управления его жизненным циклом.
 */
@CompileStatic
class PluginInstance {
    String id
    PluginDescriptor descriptor
    PluginClassLoader classLoader
    DustyboxPlugin pluginInstance
    ApplicationContext springContext // Дочерний Spring-контекст плагина
    PluginContext pluginContext      // Контекст, передаваемый плагину
    File jarFile                     // Исходный JAR-файл
    boolean started = false

    PluginInstance(String id, PluginDescriptor descriptor, PluginClassLoader classLoader,
                   DustyboxPlugin pluginInstance, ApplicationContext springContext,
                   PluginContext pluginContext, File jarFile) {
        this.id = id
        this.descriptor = descriptor
        this.classLoader = classLoader
        this.pluginInstance = pluginInstance
        this.springContext = springContext
        this.pluginContext = pluginContext
        this.jarFile = jarFile
    }

    void start() {
        if (!started) {
            pluginInstance.start()
            started = true
        }
    }

    void stop() {
        if (started) {
            pluginInstance.stop()
            started = false
        }
    }
}