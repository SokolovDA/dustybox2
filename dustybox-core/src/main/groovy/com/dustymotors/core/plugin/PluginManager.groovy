package com.dustymotors.core.plugin

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct
import com.dustymotors.core.entity.SystemLog
import com.dustymotors.core.repository.SystemLogRepository
import groovy.transform.CompileStatic

@Component
@CompileStatic
class PluginManager {

    @Value('${dustybox.plugins.dir:./plugins}')
    private String pluginsDir

    @Autowired
    private SystemLogRepository logRepository

    private Map<String, ClassLoader> pluginClassLoaders = [:]
    private Map<String, DustyboxPlugin> loadedPlugins = [:]

    @PostConstruct
    void loadPlugins() {
        logInfo("PluginManager", "Loading plugins from ${pluginsDir}")

        def dir = new File(pluginsDir)
        if (!dir.exists()) {
            logWarn("PluginManager", "Plugins directory doesn't exist: ${pluginsDir}")
            dir.mkdirs()
            logInfo("PluginManager", "Created plugins directory")
        }

        // Проверяем наличие плагинов
        def pluginFiles = dir.listFiles { it.name.endsWith('.jar') }
        logInfo("PluginManager", "Found ${pluginFiles?.size() ?: 0} plugin files")

        // Пока просто добавляем тестовый плагин
        def testPlugin = [
                getName: { "test-plugin" },
                getVersion: { "1.0.0" },
                initialize: { ctx ->
                    logInfo("TestPlugin", "Test plugin initialized")
                },
                getEndpoints: { [] },
                getMenuItems: { [] },
                getServices: { [] },
                getWebResources: { [] }
        ] as DustyboxPlugin

        loadedPlugins["test-plugin"] = testPlugin
        logInfo("PluginManager", "Test plugin added")
    }

    private void logInfo(String source, String message) {
        println "[INFO] ${source}: ${message}"
        logRepository.save(new SystemLog("INFO", message, source))
    }

    private void logWarn(String source, String message) {
        println "[WARN] ${source}: ${message}"
        logRepository.save(new SystemLog("WARN", message, source))
    }

    private void logError(String source, String message) {
        println "[ERROR] ${source}: ${message}"
        logRepository.save(new SystemLog("ERROR", message, source))
    }

    DustyboxPlugin getPlugin(String name) {
        return loadedPlugins[name]
    }

    Map<String, DustyboxPlugin> getLoadedPlugins() {
        return loadedPlugins
    }
}