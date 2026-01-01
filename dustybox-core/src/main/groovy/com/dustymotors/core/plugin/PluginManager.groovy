package com.dustymotors.core.plugin

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import com.dustymotors.core.entity.SystemLog
import com.dustymotors.core.repository.SystemLogRepository
import com.dustymotors.core.ServiceRegistry
import com.dustymotors.core.EventBus
import com.dustymotors.core.ScriptEngine
import com.dustymotors.core.WebUIManager
import groovy.transform.CompileStatic
import javax.sql.DataSource
import java.io.File
import java.io.FileFilter
import java.io.InputStream
import java.util.jar.JarFile
import java.util.zip.ZipEntry

@Component
@CompileStatic
class PluginManager {

    @Value('${dustybox.plugins.dir:./plugins}')
    private String pluginsDir

    @Autowired
    private SystemLogRepository logRepository

    @Autowired
    private ApplicationContext mainApplicationContext

    @Autowired
    private ServiceRegistry serviceRegistry

    @Autowired
    private EventBus eventBus

    @Autowired
    private ScriptEngine scriptEngine

    @Autowired
    private WebUIManager webUIManager

    @Autowired(required = false)
    private DataSource dataSource

    // Хранилище загруженных плагинов: ID -> PluginInstance
    private Map<String, PluginInstance> loadedPlugins = [:]

    @PostConstruct
    void loadAllPlugins() {
        logInfo("PluginManager", "Loading plugins from ${pluginsDir}")

        File dir = new File(pluginsDir)
        if (!dir.exists()) {
            logWarn("PluginManager", "Plugins directory doesn't exist: ${pluginsDir}")
            dir.mkdirs()
            logInfo("PluginManager", "Created plugins directory")
        }

        // Ищем все JAR-файлы
        File[] pluginFiles = dir.listFiles({ File f -> f.name.endsWith('.jar') } as FileFilter) ?: [] as File[]
        logInfo("PluginManager", "Found ${pluginFiles.length} plugin files")

        if (pluginFiles.length == 0) {
            logInfo("PluginManager", "No plugins found. Place JAR files in ${dir.absolutePath}")
            return
        }

        // Загружаем каждый плагин
        for (File jarFile in pluginFiles) {
            try {
                loadPlugin(jarFile)
            } catch (Exception e) {
                logError("PluginManager", "Failed to load plugin from ${jarFile.name}: ${e.message}", e)
            }
        }

        // Запускаем все успешно загруженные плагины
        for (PluginInstance plugin in loadedPlugins.values()) {
            try {
                plugin.start()
                logInfo("PluginManager", "Started plugin: ${plugin.id}")
            } catch (Exception e) {
                logError("PluginManager", "Failed to start plugin ${plugin.id}: ${e.message}", e)
            }
        }
    }

    @PreDestroy
    void stopAllPlugins() {
        logInfo("PluginManager", "Stopping all plugins")
        for (PluginInstance plugin in loadedPlugins.values()) {
            try {
                plugin.stop()
            } catch (Exception e) {
                logError("PluginManager", "Error stopping plugin ${plugin.id}: ${e.message}", e)
            }
        }
        loadedPlugins.clear()
    }

    /**
     * Основной метод загрузки плагина из JAR-файла
     */
    private void loadPlugin(File jarFile) {
        String pluginId = jarFile.name.replace('.jar', '')
        logInfo("PluginManager", "Loading plugin: ${pluginId}")

        // 1. Создаём ClassLoader для плагина
        PluginClassLoader pluginClassLoader = new PluginClassLoader(
                [jarFile.toURI().toURL()] as URL[],
                this.class.classLoader // Родительский загрузчик (ядро)
        )

        // 2. Загружаем дескриптор plugin.yaml
        PluginDescriptor descriptor
        try {
            // Ищем plugin.yaml внутри JAR
            JarFile jar = new JarFile(jarFile)
            ZipEntry entry = jar.getEntry('META-INF/plugins/plugin.yaml') ?: jar.getEntry('plugin.yaml')
            InputStream yamlEntry = null

            if (entry != null) {
                yamlEntry = jar.getInputStream(entry)
            }

            if (yamlEntry == null) {
                jar.close()
                throw new PluginLoadingException("plugin.yaml not found in JAR")
            }

            descriptor = PluginDescriptor.load(yamlEntry)
            yamlEntry.close()
            jar.close()
            logInfo("PluginManager", "Loaded descriptor: ${descriptor.name} v${descriptor.version}")
        } catch (Exception e) {
            throw new PluginLoadingException("Failed to load plugin descriptor: ${e.message}", e)
        }

        try {
            // 3. Создаём дочерний Spring-контекст для плагина
            ApplicationContext pluginSpringContext = createPluginSpringContext(pluginClassLoader, descriptor)

            // 4. Создаём PluginContext (API ядра для плагина)
            PluginContext pluginContext = new PluginContext(
                    serviceRegistry: serviceRegistry,
                    eventBus: eventBus,
                    webUIManager: webUIManager,
                    scriptEngine: scriptEngine,
                    dataSource: dataSource,
                    pluginId: pluginId,
                    pluginSpringContext: pluginSpringContext
            )

            // 5. Загружаем главный класс плагина и создаём экземпляр
            // ВАЖНО: используем класс, загруженный через pluginClassLoader
            DustyboxPlugin pluginInstance
            try {
                Class<?> pluginMainClass = pluginClassLoader.loadClass(descriptor.mainClass)
                // Создаём экземпляр через рефлексию
                pluginInstance = (DustyboxPlugin) pluginMainClass.newInstance()
            } catch (Exception e) {
                throw new PluginLoadingException("Failed to instantiate plugin class ${descriptor.mainClass}: ${e.message}", e)
            }

            // 6. Создаём контейнер PluginInstance
            PluginInstance pluginContainer = new PluginInstance(
                    pluginId,
                    descriptor,
                    pluginClassLoader,
                    pluginInstance,
                    pluginSpringContext,
                    pluginContext,
                    jarFile
            )

            // 7. Регистрируем плагин в менеджере
            loadedPlugins[pluginId] = pluginContainer

            // 8. Инициализируем плагин
            try {
                pluginInstance.initialize(pluginContext)
                logInfo("PluginManager", "Initialized plugin: ${pluginId}")
            } catch (Exception e) {
                loadedPlugins.remove(pluginId)
                throw new PluginLoadingException("Plugin initialization failed: ${e.message}", e)
            }

        } catch (Exception e) {
            // Логируем полный stack trace для диагностики
            logError("PluginManager", "CRITICAL: Failed to load plugin ${pluginId}: ${e.message}", e)
            throw new PluginLoadingException("Failed to load plugin ${pluginId}: ${e.message}", e)
        }
    }

    /**
     * Создаёт дочерний Spring ApplicationContext для плагина
     */
    private ApplicationContext createPluginSpringContext(ClassLoader pluginClassLoader, PluginDescriptor descriptor) {
        logInfo("PluginManager", "Creating Spring context for plugin: ${descriptor.name}")

        // Создаём минимальный контекст
        GenericApplicationContext context = new GenericApplicationContext()
        context.classLoader = pluginClassLoader
        context.parent = mainApplicationContext // Ключевая строка - делаем дочерним!

        // Регистрируем простые бины для тестирования
        // В будущем здесь будет сканирование компонентов плагина
        context.refresh()

        return context
    }

    // Вспомогательные методы логирования
    private void logInfo(String source, String message) {
        println "[INFO] ${source}: ${message}"
        logRepository.save(new SystemLog("INFO", message, source))
    }

    private void logWarn(String source, String message) {
        println "[WARN] ${source}: ${message}"
        logRepository.save(new SystemLog("WARN", message, source))
    }

    private void logError(String source, String message, Throwable exception = null) {
        println "[ERROR] ${source}: ${message}"
        if (exception != null) {
            println "Stack trace:"
            exception.printStackTrace()
        }
        logRepository.save(new SystemLog("ERROR", message, source))
    }

    // Методы для внешнего доступа
    PluginInstance getPlugin(String id) {
        return loadedPlugins[id]
    }

    Map<String, PluginInstance> getLoadedPlugins() {
        return Collections.unmodifiableMap(loadedPlugins)
    }

    List<PluginMenuItem> getAllMenuItems() {
        List<PluginMenuItem> allItems = []
        for (PluginInstance plugin in loadedPlugins.values()) {
            List<PluginMenuItem> pluginItems = plugin.pluginInstance.menuItems
            if (pluginItems) {
                allItems.addAll(pluginItems)
            }
        }
        allItems.sort { PluginMenuItem item -> item.order }
        return allItems
    }
}