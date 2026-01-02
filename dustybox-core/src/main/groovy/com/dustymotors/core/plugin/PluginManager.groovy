package com.dustymotors.core.plugin

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
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

    private Map<String, PluginInstance> loadedPlugins = [:] // Key: stable plugin ID (without version)

    @Autowired
    private ApplicationContext mainApplicationContext

    @Autowired(required = false)
    private DataSource dataSource

    @PostConstruct
    void loadAllPlugins() {
        println "[INFO] Loading plugins from ${pluginsDir}"

        File dir = new File(pluginsDir)
        if (!dir.exists()) {
            println "[INFO] Plugins directory doesn't exist, creating: ${pluginsDir}"
            dir.mkdirs()
            return
        }

        // Ищем все JAR-файлы
        File[] pluginFiles = dir.listFiles({ File f ->
            f.isFile() && f.name.endsWith('.jar')
        } as FileFilter) ?: [] as File[]

        if (pluginFiles.length == 0) {
            println "[INFO] No plugins found in ${dir.absolutePath}"
            return
        }

        // Загружаем каждый плагин
        for (File jarFile in pluginFiles) {
            try {
                loadPlugin(jarFile)
            } catch (Exception e) {
                println "[ERROR] Failed to load plugin from ${jarFile.name}: ${e.message}"
                e.printStackTrace()
            }
        }

        println "[INFO] Successfully loaded ${loadedPlugins.size()} plugins"
    }

    @PreDestroy
    void stopAllPlugins() {
        println "[INFO] Stopping all plugins"
        for (PluginInstance plugin in loadedPlugins.values()) {
            try {
                plugin.stop()
                println "[INFO] Stopped plugin: ${plugin.id}"
            } catch (Exception e) {
                println "[ERROR] Error stopping plugin ${plugin.id}: ${e.message}"
            }
        }
        loadedPlugins.clear()
    }

    void loadPlugin(File jarFile) {
        try {
            // Создаём ClassLoader для плагина
            URL[] urls = [jarFile.toURI().toURL()] as URL[]
            PluginClassLoader pluginClassLoader = new PluginClassLoader(urls, Thread.currentThread().contextClassLoader)

            // Загружаем дескриптор plugin.yaml
            PluginDescriptor descriptor = loadPluginDescriptor(jarFile)

            // Используем стабильный ID плагина (без версии)
            String stablePluginId = createStablePluginId(descriptor.name)

            if (loadedPlugins.containsKey(stablePluginId)) {
                println "[WARN] Plugin with stable ID ${stablePluginId} is already loaded. Skipping."
                return
            }

            println "[INFO] Loading plugin: ${stablePluginId} (${descriptor.name} v${descriptor.version})"

            // Создаём Spring-контекст для плагина
            ApplicationContext pluginSpringContext = createPluginSpringContext(pluginClassLoader, descriptor)

            // Загружаем главный класс плагина
            Class<?> pluginClass = pluginClassLoader.loadClass(descriptor.mainClass)
            DustyboxPlugin pluginInstance = (DustyboxPlugin) pluginClass.newInstance()

            // Создаём PluginContext
            PluginContext pluginContext = new PluginContext(
                    dataSource: dataSource,
                    pluginId: stablePluginId,
                    pluginSpringContext: pluginSpringContext
            )

            // Создаём контейнер PluginInstance
            PluginInstance pluginContainer = new PluginInstance(
                    stablePluginId,
                    descriptor,
                    pluginClassLoader,
                    pluginInstance,
                    pluginSpringContext,
                    pluginContext,
                    jarFile
            )

            // Инициализируем плагин
            println "[INFO] Initializing plugin: ${descriptor.name}"
            pluginInstance.initialize(pluginContext)

            // Регистрируем плагин
            loadedPlugins[stablePluginId] = pluginContainer

            // Запускаем плагин
            pluginContainer.start()

            println "[SUCCESS] Loaded plugin: ${descriptor.name} v${descriptor.version} (stable ID: ${stablePluginId})"

        } catch (Exception e) {
            println "[ERROR] Failed to load plugin ${jarFile.name}: ${e.message}"
            e.printStackTrace()
            throw e
        }
    }

    void reloadPlugin(String pluginId) {
        PluginInstance plugin = getPlugin(pluginId)
        if (!plugin) {
            throw new PluginLoadingException("Plugin not found: ${pluginId}")
        }

        println "[INFO] Reloading plugin: ${pluginId}"

        try {
            // Останавливаем плагин
            if (plugin.started) {
                stopPlugin(pluginId)
            }

            // Сохраняем информацию о JAR файле
            File jarFile = plugin.jarFile

            // Удаляем из хранилища
            loadedPlugins.remove(pluginId)

            // Закрываем ClassLoader
            plugin.classLoader.close()

            // Загружаем заново
            loadPlugin(jarFile)

            println "[SUCCESS] Plugin ${pluginId} reloaded"

        } catch (Exception e) {
            println "[ERROR] Failed to reload plugin ${pluginId}: ${e.message}"
            throw new PluginLoadingException("Failed to reload plugin ${pluginId}", e)
        }
    }

    private PluginDescriptor loadPluginDescriptor(File jarFile) {
        JarFile jar = null
        try {
            jar = new JarFile(jarFile)
            def entry = jar.getEntry('plugin.yaml') ?:
                    jar.getEntry('META-INF/plugin.yaml') ?:
                            jar.getEntry('META-INF/plugins/plugin.yaml')

            if (entry == null) {
                throw new PluginLoadingException("plugin.yaml not found in JAR")
            }

            InputStream yamlStream = jar.getInputStream(entry)
            PluginDescriptor descriptor = PluginDescriptor.load(yamlStream)
            yamlStream.close()
            return descriptor

        } catch (Exception e) {
            throw new PluginLoadingException("Failed to load plugin descriptor: ${e.message}", e)
        } finally {
            jar?.close()
        }
    }

    private String createStablePluginId(String pluginName) {
        // Преобразуем имя плагина в стабильный ID
        // Например: "cd-database" -> "cddb", "script-manager" -> "script"
        String stableId = pluginName.toLowerCase()
                .replace('-database', 'db')
                .replace('-manager', '')
                .replace('-plugin', '')
                .replace('-', '')

        return stableId
    }

    private ApplicationContext createPluginSpringContext(ClassLoader pluginClassLoader, PluginDescriptor descriptor) {
        println "[INFO] Creating Spring context for plugin: ${descriptor.name}"

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()
        context.classLoader = pluginClassLoader
        context.parent = mainApplicationContext

        // Если есть конфигурационный класс, регистрируем его
        if (descriptor.springConfigClass) {
            try {
                Class<?> configClass = pluginClassLoader.loadClass(descriptor.springConfigClass)
                context.register(configClass)
                println "[INFO] Registered config class: ${descriptor.springConfigClass}"
            } catch (ClassNotFoundException e) {
                println "[WARN] Config class not found: ${descriptor.springConfigClass}"
            }
        }

        // Сканируем пакет плагина
        try {
            String pluginPackage = descriptor.mainClass.substring(0, descriptor.mainClass.lastIndexOf('.'))
            println "[INFO] Scanning package: ${pluginPackage}"
            context.scan(pluginPackage)
        } catch (Exception e) {
            println "[WARN] Failed to scan plugin package: ${e.message}"
        }

        context.refresh()
        println "[SUCCESS] Spring context created for plugin: ${descriptor.name}"
        return context
    }

    // ==================== Публичные методы ====================

    PluginInstance getPlugin(String id) {
        return loadedPlugins[id]
    }

    Map<String, PluginInstance> getLoadedPlugins() {
        return Collections.unmodifiableMap(loadedPlugins)
    }

    void startPlugin(String pluginId) {
        PluginInstance plugin = getPlugin(pluginId)
        if (!plugin) {
            throw new PluginLoadingException("Plugin not found: ${pluginId}")
        }

        if (!plugin.started) {
            plugin.start()
        }
    }

    void stopPlugin(String pluginId) {
        PluginInstance plugin = getPlugin(pluginId)
        if (!plugin) {
            throw new PluginLoadingException("Plugin not found: ${pluginId}")
        }

        if (plugin.started) {
            plugin.stop()
        }
    }
}

class PluginLoadingException extends RuntimeException {
    PluginLoadingException(String message) {
        super(message)
    }

    PluginLoadingException(String message, Throwable cause) {
        super(message, cause)
    }
}