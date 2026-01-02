package com.dustymotors.core.plugin

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.stereotype.Component
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.orm.jpa.JpaTransactionManager
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

    @Value('${dustybox.plugins.auto-start:true}')
    private boolean autoStartPlugins

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

    // Метрики плагинов
    private Map<String, PluginMetrics> pluginMetrics = [:]

    @PostConstruct
    void loadAllPlugins() {
        logInfo("PluginManager", "Loading plugins from ${pluginsDir}")

        File dir = new File(pluginsDir)
        if (!dir.exists()) {
            logWarn("PluginManager", "Plugins directory doesn't exist: ${pluginsDir}")
            dir.mkdirs()
            logInfo("PluginManager", "Created plugins directory")
        }

        // Сканируем и загружаем плагины
        scanAndLoadPlugins(dir)

        if (loadedPlugins.isEmpty()) {
            logInfo("PluginManager", "No plugins loaded. Place JAR files in ${dir.absolutePath}")
        } else {
            logInfo("PluginManager", "Successfully loaded ${loadedPlugins.size()} plugins")
        }
    }

    @PreDestroy
    void stopAllPlugins() {
        logInfo("PluginManager", "Stopping all plugins")
        for (PluginInstance plugin in loadedPlugins.values()) {
            try {
                plugin.stop()
                logInfo("PluginManager", "Stopped plugin: ${plugin.id}")
            } catch (Exception e) {
                logError("PluginManager", "Error stopping plugin ${plugin.id}: ${e.message}", e)
            }
        }
        loadedPlugins.clear()
        pluginMetrics.clear()
    }

    /**
     * Сканирует директорию и загружает все плагины
     */
    void scanAndLoadPlugins(File pluginsDir = null) {
        File dir = pluginsDir ?: new File(this.pluginsDir)

        if (!dir.exists() || !dir.isDirectory()) {
            throw new PluginLoadingException("Plugins directory not found: ${dir.absolutePath}")
        }

        // Ищем все JAR-файлы
        File[] pluginFiles = dir.listFiles({ File f ->
            f.isFile() && f.name.endsWith('.jar')
        } as FileFilter) ?: [] as File[]

        logInfo("PluginManager", "Found ${pluginFiles.length} plugin files in ${dir.absolutePath}")

        if (pluginFiles.length == 0) {
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

        // Запускаем плагины если auto-start включен
        if (autoStartPlugins) {
            startAllPlugins()
        }
    }

    /**
     * Основной метод загрузки плагина из JAR-файла
     */
    void loadPlugin(File jarFile) {
        String pluginId = extractPluginId(jarFile)

        // Проверяем, не загружен ли уже плагин
        if (loadedPlugins.containsKey(pluginId)) {
            logWarn("PluginManager", "Plugin ${pluginId} is already loaded. Skipping.")
            return
        }

        logInfo("PluginManager", "Loading plugin: ${pluginId}")

        try {
            // 1. Валидация плагина
            validatePluginJar(jarFile)

            // 2. Создаём ClassLoader для плагина
            PluginClassLoader pluginClassLoader = createPluginClassLoader(jarFile)

            // 3. Загружаем дескриптор plugin.yaml
            PluginDescriptor descriptor = loadPluginDescriptor(jarFile, pluginClassLoader)

            // 4. Валидируем дескриптор
            PluginValidator.validatePlugin(descriptor, jarFile)

            // 5. Создаём дочерний Spring-контекст для плагина
            ApplicationContext pluginSpringContext = createPluginSpringContext(pluginClassLoader, descriptor)

            // 6. Создаём PluginContext (API ядра для плагина)
            PluginContext pluginContext = createPluginContext(pluginId, pluginSpringContext)

            registerPluginBeans(pluginContainer)

            registerPluginControllers(pluginSpringContext, descriptor)

            // 7. Загружаем главный класс плагина и создаём экземпляр
            DustyboxPlugin pluginInstance = createPluginInstance(descriptor, pluginClassLoader)

            // 8. Создаём контейнер PluginInstance
            PluginInstance pluginContainer = new PluginInstance(
                    pluginId,
                    descriptor,
                    pluginClassLoader,
                    pluginInstance,
                    pluginSpringContext,
                    pluginContext,
                    jarFile
            )

            // 9. Инициализируем метрики
            pluginMetrics[pluginId] = new PluginMetrics()

            // 10. Регистрируем плагин в менеджере
            loadedPlugins[pluginId] = pluginContainer

            // 11. Инициализируем плагин
            initializePlugin(pluginContainer)

            logInfo("PluginManager", "Successfully loaded plugin: ${descriptor.name} v${descriptor.version}")

        } catch (Exception e) {
            // Откатываем изменения в случае ошибки
            loadedPlugins.remove(pluginId)
            pluginMetrics.remove(pluginId)

            logError("PluginManager", "CRITICAL: Failed to load plugin ${pluginId}: ${e.message}", e)
            throw new PluginLoadingException("Failed to load plugin ${pluginId}: ${e.message}", e)
        }
    }

    private void registerPluginBeans(PluginInstance pluginContainer) {
        try {
            def pluginContext = pluginContainer.springContext
            if (!pluginContext) return

            // Получаем DataSource из основного контекста и регистрируем в контексте плагина
            def dataSource = mainApplicationContext.getBean(javax.sql.DataSource.class)
            pluginContext.beanFactory.registerSingleton("dataSource", dataSource)

            // Регистрируем JdbcTemplate
            def jdbcTemplate = new org.springframework.jdbc.core.JdbcTemplate(dataSource)
            pluginContext.beanFactory.registerSingleton("jdbcTemplate", jdbcTemplate)

            logInfo("PluginManager", "Registered dataSource and jdbcTemplate for plugin: ${pluginContainer.id}")

        } catch (Exception e) {
            logWarn("PluginManager", "Failed to register beans for plugin ${pluginContainer.id}: ${e.message}")
        }
    }

    /**
     * Инициализирует плагин
     */
    private void initializePlugin(PluginInstance pluginContainer) {
        try {
            pluginContainer.pluginInstance.initialize(pluginContainer.pluginContext)
            logInfo("PluginManager", "Initialized plugin: ${pluginContainer.id}")

            // Публикуем событие об успешной инициализации
            eventBus.publish("plugin.initialized", [
                    pluginId: pluginContainer.id,
                    pluginName: pluginContainer.descriptor.name,
                    version: pluginContainer.descriptor.version
            ])

        } catch (Exception e) {
            // Если инициализация не удалась, выгружаем плагин
            loadedPlugins.remove(pluginContainer.id)
            pluginMetrics.remove(pluginContainer.id)

            throw new PluginLoadingException("Plugin initialization failed: ${e.message}", e)
        }
    }

    /**
     * Создаёт PluginClassLoader для плагина
     */
    private PluginClassLoader createPluginClassLoader(File jarFile) {
        try {
            return new PluginClassLoader(
                    [jarFile.toURI().toURL()] as URL[],
                    this.class.classLoader // Родительский загрузчик (ядро)
            )
        } catch (Exception e) {
            throw new PluginLoadingException("Failed to create PluginClassLoader: ${e.message}", e)
        }
    }

    /**
     * Загружает дескриптор плагина
     */
    private PluginDescriptor loadPluginDescriptor(File jarFile, PluginClassLoader classLoader) {
        try {
            // Ищем plugin.yaml внутри JAR
            JarFile jar = new JarFile(jarFile)
            ZipEntry entry = jar.getEntry('META-INF/plugins/plugin.yaml') ?:
                    jar.getEntry('plugin.yaml') ?:
                            jar.getEntry('META-INF/plugin.yaml')

            if (entry == null) {
                jar.close()
                throw new PluginLoadingException("plugin.yaml not found in JAR")
            }

            InputStream yamlStream = jar.getInputStream(entry)
            PluginDescriptor descriptor = PluginDescriptor.load(yamlStream)
            yamlStream.close()
            jar.close()

            logInfo("PluginManager", "Loaded descriptor: ${descriptor.name} v${descriptor.version}")
            return descriptor

        } catch (Exception e) {
            throw new PluginLoadingException("Failed to load plugin descriptor: ${e.message}", e)
        }
    }

    /**
     * Создаёт дочерний Spring ApplicationContext для плагина
     */
// В PluginManager.groovy исправляем метод createPluginSpringContext:
    private ApplicationContext createPluginSpringContext(ClassLoader pluginClassLoader, PluginDescriptor descriptor) {
        logInfo("PluginManager", "Creating Spring context for plugin: ${descriptor.name}")

        try {
            // Используем AnnotationConfigApplicationContext для поддержки конфигурации
            AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()
            context.classLoader = pluginClassLoader
            context.parent = mainApplicationContext // Ключевая строка - делаем дочерним!

            // Регистрируем стандартные бины
            context.beanFactory.registerSingleton("pluginDescriptor", descriptor)

            // 1. ПЕРВЫЙ ПРИОРИТЕТ: Если есть springConfigClass, регистрируем его
            if (descriptor.springConfigClass) {
                try {
                    logInfo("PluginManager", "Loading config class: ${descriptor.springConfigClass}")
                    Class<?> configClass = pluginClassLoader.loadClass(descriptor.springConfigClass)
                    context.register(configClass)
                    logInfo("PluginManager", "Registered config class: ${configClass.name}")
                } catch (ClassNotFoundException e) {
                    logWarn("PluginManager", "Config class not found: ${descriptor.springConfigClass}")
                }
            }

            // 2. ВТОРОЙ ПРИОРИТЕТ: Сканируем пакет плагина
            try {
                String pluginPackage = descriptor.mainClass.substring(0, descriptor.mainClass.lastIndexOf('.'))
                logInfo("PluginManager", "Scanning package: ${pluginPackage}")
                context.scan(pluginPackage)
            } catch (Exception e) {
                logWarn("PluginManager", "Failed to scan package: ${e.message}")
            }

            context.refresh()

            // Логируем все бины в контексте плагина
            logInfo("PluginManager", "Plugin '${descriptor.name}' Spring context created")
            logInfo("PluginManager", "Beans in plugin context (${context.beanDefinitionNames.length}):")
            context.beanDefinitionNames.each { beanName ->
                try {
                    def bean = context.getBean(beanName)
                    logInfo("PluginManager", "  - ${beanName}: ${bean.getClass().name}")
                } catch (Exception e) {
                    logInfo("PluginManager", "  - ${beanName}: [ERROR getting bean]")
                }
            }

            return context
        } catch (Exception e) {
            logError("PluginManager", "Failed to create Spring context: ${e.message}", e)
            throw new PluginLoadingException("Failed to create Spring context: ${e.message}", e)
        }
    }

    private void registerPluginControllers(ApplicationContext pluginContext, PluginDescriptor descriptor) {
        try {
            logInfo("PluginManager", "Registering controllers for plugin: ${descriptor.name}")

            // Получаем все контроллеры из контекста плагина
            def controllerBeans = pluginContext.getBeansWithAnnotation(org.springframework.stereotype.Controller.class)
            controllerBeans.putAll(pluginContext.getBeansWithAnnotation(org.springframework.web.bind.annotation.RestController.class))

            logInfo("PluginManager", "Found ${controllerBeans.size()} controllers in plugin ${descriptor.name}")

            // Для каждого контроллера логируем информацию
            controllerBeans.each { beanName, bean ->
                def methods = bean.class.declaredMethods.findAll { method ->
                    method.annotations.any { ann ->
                        ann.annotationType().simpleName in ['GetMapping', 'PostMapping', 'PutMapping', 'DeleteMapping', 'RequestMapping']
                    }
                }

                logInfo("PluginManager", "  - ${bean.class.simpleName} with ${methods.size()} endpoint methods")
            }

        } catch (Exception e) {
            logWarn("PluginManager", "Failed to register plugin controllers: ${e.message}")
        }
    }

    private void configurePluginJpa(AnnotationConfigApplicationContext context, PluginDescriptor descriptor) {
        // Создаем DataSource для плагина (используем общий)
        def dataSource = mainApplicationContext.getBean(DataSource.class)
        context.beanFactory.registerSingleton("dataSource", dataSource)

        // Создаем EntityManagerFactory для плагина
        def emf = new LocalContainerEntityManagerFactoryBean()
        emf.dataSource = dataSource
        emf.packagesToScan = [descriptor.mainClass.substring(0, descriptor.mainClass.lastIndexOf('.'))]
        emf.persistenceUnitName = "${descriptor.name}-PU"

        def vendorAdapter = new HibernateJpaVendorAdapter()
        emf.jpaVendorAdapter = vendorAdapter

        def properties = new Properties()
        properties.put("hibernate.hbm2ddl.auto", "update")
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
        properties.put("hibernate.format_sql", "true")
        properties.put("hibernate.show_sql", "true")

        emf.setJpaProperties(properties)
        emf.afterPropertiesSet()

        context.beanFactory.registerSingleton("entityManagerFactory", emf.object)

        // Создаем TransactionManager
        def txManager = new org.springframework.orm.jpa.JpaTransactionManager()
        txManager.entityManagerFactory = emf.object
        context.beanFactory.registerSingleton("transactionManager", txManager)
    }

    /**
     * Создаёт PluginContext (API ядра для плагина)
     */
    private PluginContext createPluginContext(String pluginId, ApplicationContext pluginSpringContext) {
        return new PluginContext(
                serviceRegistry: serviceRegistry,
                eventBus: eventBus,
                webUIManager: webUIManager,
                scriptEngine: scriptEngine,
                dataSource: dataSource,
                pluginId: pluginId,
                pluginSpringContext: pluginSpringContext
        )
    }

    /**
     * Создаёт экземпляр плагина
     */
    private DustyboxPlugin createPluginInstance(PluginDescriptor descriptor, PluginClassLoader classLoader) {
        try {
            Class<?> pluginMainClass = classLoader.loadClass(descriptor.mainClass)
            // Создаём экземпляр через рефлексию
            return (DustyboxPlugin) pluginMainClass.newInstance()
        } catch (ClassNotFoundException e) {
            throw new PluginLoadingException("Plugin class not found: ${descriptor.mainClass}", e)
        } catch (InstantiationException | IllegalAccessException e) {
            throw new PluginLoadingException("Failed to instantiate plugin class ${descriptor.mainClass}", e)
        } catch (ClassCastException e) {
            throw new PluginLoadingException("Plugin class must implement DustyboxPlugin interface", e)
        }
    }

    /**
     * Запускает все загруженные плагины
     */
    void startAllPlugins() {
        logInfo("PluginManager", "Starting all plugins")

        for (PluginInstance plugin in loadedPlugins.values()) {
            try {
                if (!plugin.started) {
                    startPlugin(plugin.id)
                }
            } catch (Exception e) {
                logError("PluginManager", "Failed to start plugin ${plugin.id}: ${e.message}", e)
            }
        }
    }

    /**
     * Запускает конкретный плагин
     */
    void startPlugin(String pluginId) {
        PluginInstance plugin = getPlugin(pluginId)
        if (!plugin) {
            throw new PluginLoadingException("Plugin not found: ${pluginId}")
        }

        if (plugin.started) {
            logWarn("PluginManager", "Plugin ${pluginId} is already started")
            return
        }

        try {
            plugin.start()
            pluginMetrics[pluginId].startTime = System.currentTimeMillis()

            logInfo("PluginManager", "Started plugin: ${pluginId}")

            // Публикуем событие
            eventBus.publish("plugin.started", [
                    pluginId: pluginId,
                    pluginName: plugin.descriptor.name
            ])

        } catch (Exception e) {
            logError("PluginManager", "Failed to start plugin ${pluginId}: ${e.message}", e)
            throw new PluginLoadingException("Failed to start plugin ${pluginId}", e)
        }
    }

    /**
     * Останавливает конкретный плагин
     */
    void stopPlugin(String pluginId) {
        PluginInstance plugin = getPlugin(pluginId)
        if (!plugin) {
            throw new PluginLoadingException("Plugin not found: ${pluginId}")
        }

        if (!plugin.started) {
            logWarn("PluginManager", "Plugin ${pluginId} is already stopped")
            return
        }

        try {
            plugin.stop()

            logInfo("PluginManager", "Stopped plugin: ${pluginId}")

            // Публикуем событие
            eventBus.publish("plugin.stopped", [
                    pluginId: pluginId,
                    pluginName: plugin.descriptor.name
            ])

        } catch (Exception e) {
            logError("PluginManager", "Failed to stop plugin ${pluginId}: ${e.message}", e)
            throw new PluginLoadingException("Failed to stop plugin ${pluginId}", e)
        }
    }

    /**
     * Перезагружает плагин
     */
    void reloadPlugin(String pluginId) {
        PluginInstance plugin = getPlugin(pluginId)
        if (!plugin) {
            throw new PluginLoadingException("Plugin not found: ${pluginId}")
        }

        logInfo("PluginManager", "Reloading plugin: ${pluginId}")

        try {
            // 1. Останавливаем плагин
            if (plugin.started) {
                stopPlugin(pluginId)
            }

            // 2. Сохраняем информацию о JAR файле
            File jarFile = plugin.jarFile

            // 3. Удаляем из хранилища
            loadedPlugins.remove(pluginId)
            pluginMetrics.remove(pluginId)

            // 4. Закрываем ClassLoader
            plugin.classLoader.close()

            // 5. Загружаем заново
            loadPlugin(jarFile)

            // 6. Запускаем если был запущен
            if (plugin.started) {
                startPlugin(pluginId)
            }

            logInfo("PluginManager", "Plugin ${pluginId} reloaded successfully")

        } catch (Exception e) {
            logError("PluginManager", "Failed to reload plugin ${pluginId}: ${e.message}", e)
            throw new PluginLoadingException("Failed to reload plugin ${pluginId}", e)
        }
    }

    /**
     * Валидация JAR файла плагина
     */
    private void validatePluginJar(File jarFile) {
        // Проверка существования файла
        if (!jarFile.exists()) {
            throw new PluginLoadingException("Plugin JAR file does not exist: ${jarFile.name}")
        }

        // Проверка размера
        long maxSize = 100 * 1024 * 1024 // 100MB
        if (jarFile.length() > maxSize) {
            throw new PluginLoadingException("Plugin JAR is too large: ${jarFile.length() / 1024 / 1024}MB")
        }

        // Проверка что это действительно JAR
        if (!jarFile.name.endsWith('.jar')) {
            throw new PluginLoadingException("Plugin file must have .jar extension")
        }
    }

    /**
     * Извлекает ID плагина из имени файла
     */
    private String extractPluginId(File jarFile) {
        String fileName = jarFile.name
        // Убираем расширение .jar
        return fileName.replace('.jar', '')
    }

    /**
     * Регистрирует вызов API плагина
     */
    void recordApiCall(String pluginId, String endpoint) {
        PluginMetrics metrics = pluginMetrics[pluginId]
        if (metrics) {
            metrics.recordRequest(endpoint)
        }
    }

    /**
     * Регистрирует ошибку плагина
     */
    void recordPluginError(String pluginId) {
        PluginMetrics metrics = pluginMetrics[pluginId]
        if (metrics) {
            metrics.recordError()
        }
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

    // ==================== Публичные методы для внешнего доступа ====================

    /**
     * Получает экземпляр плагина по ID
     */
    PluginInstance getPlugin(String id) {
        return loadedPlugins[id]
    }

    /**
     * Возвращает все загруженные плагины
     */
    Map<String, PluginInstance> getLoadedPlugins() {
        return Collections.unmodifiableMap(loadedPlugins)
    }

    /**
     * Возвращает все пункты меню от всех плагинов
     */
    List<PluginMenuItem> getAllMenuItems() {
        List<PluginMenuItem> allItems = []
        for (PluginInstance plugin in loadedPlugins.values()) {
            List<PluginMenuItem> pluginItems = plugin.pluginInstance.menuItems
            if (pluginItems) {
                pluginItems.each { it.path = "/plugins/${plugin.id}${it.path}" }
                allItems.addAll(pluginItems)
            }
        }
        allItems.sort { PluginMenuItem item -> item.order }
        return allItems
    }

    /**
     * Возвращает все веб-ресурсы от всех плагинов
     */
    List<WebResource> getAllWebResources() {
        List<WebResource> allResources = []
        for (PluginInstance plugin in loadedPlugins.values()) {
            List<WebResource> pluginResources = plugin.pluginInstance.webResources
            if (pluginResources) {
                pluginResources.each {
                    it.url = it.url?.replace('{pluginId}', plugin.id)
                }
                allResources.addAll(pluginResources)
            }
        }
        return allResources
    }

    /**
     * Получает информацию о всех плагинах
     */
    Map<String, Map<String, Object>> getPluginsInfo() {
        Map<String, Map<String, Object>> result = [:]
        loadedPlugins.each { String id, PluginInstance instance ->
            result[id] = [
                    name: instance.descriptor.name,
                    version: instance.descriptor.version,
                    description: instance.descriptor.description,
                    jarFile: instance.jarFile.name,
                    started: instance.started,
                    springContext: instance.springContext != null,
                    classLoader: instance.classLoader.getClass().simpleName
            ]
        }
        return result
    }

    /**
     * Получает метрики конкретного плагина
     */
    Map<String, Object> getPluginMetrics(String pluginId) {
        PluginMetrics metrics = pluginMetrics[pluginId]
        return metrics ? metrics.toMap() : [:]
    }

    /**
     * Получает статистику по всем плагинам
     */
    Map<String, Object> getPluginStatistics() {
        long totalRequests = 0L
        long totalErrors = 0L
        int startedCount = 0

        loadedPlugins.values().each { plugin ->
            if (plugin.started) startedCount++
        }

        pluginMetrics.values().each { metrics ->
            totalRequests += metrics.requestCount
            totalErrors += metrics.errorCount
        }

        return [
                totalPlugins: loadedPlugins.size(),
                startedPlugins: startedCount,
                stoppedPlugins: loadedPlugins.size() - startedCount,
                totalRequests: totalRequests,
                totalErrors: totalErrors,
                averageErrorRate: totalRequests > 0 ? (totalErrors / totalRequests) * 100 : 0
        ]
    }

    /**
     * Проверяет, существует ли плагин
     */
    boolean pluginExists(String pluginId) {
        return loadedPlugins.containsKey(pluginId)
    }

    /**
     * Проверяет, запущен ли плагин
     */
    boolean isPluginStarted(String pluginId) {
        PluginInstance plugin = getPlugin(pluginId)
        return plugin?.started ?: false
    }

    /**
     * Получает плагины по типу
     */
    List<PluginInstance> getPluginsByType(String type) {
        List<PluginInstance> result = []
        loadedPlugins.values().each { PluginInstance plugin ->
            if (plugin.descriptor.type?.equalsIgnoreCase(type)) {
                result.add(plugin)
            }
        }
        return result
    }

    /**
     * Получает плагины по имени
     */
    List<PluginInstance> getPluginsByName(String name) {
        List<PluginInstance> result = []
        loadedPlugins.values().each { PluginInstance plugin ->
            if (plugin.descriptor.name?.equalsIgnoreCase(name)) {
                result.add(plugin)
            }
        }
        return result
    }
}

/**
 * Метрики плагина
 */
@CompileStatic
class PluginMetrics {
    long startTime = System.currentTimeMillis()
    long requestCount = 0L
    long errorCount = 0L
    Map<String, Long> endpointCalls = [:] as Map<String, Long>

    void recordRequest(String endpoint) {
        requestCount++
        endpointCalls.put(endpoint, endpointCalls.getOrDefault(endpoint, 0L) + 1L)
    }

    void recordError() {
        errorCount++
    }

    Map<String, Object> toMap() {
        long uptime = System.currentTimeMillis() - startTime

        return [
                uptime: uptime,
                uptimeFormatted: formatUptime(uptime),
                requestCount: requestCount,
                errorCount: errorCount,
                errorRate: requestCount > 0 ? (errorCount / requestCount) * 100 : 0,
                requestsPerMinute: uptime > 0 ? (requestCount * 60000 / uptime) : 0,
                endpointCalls: endpointCalls,
                lastUpdated: new Date()
        ] as Map<String, Object>
    }

    private String formatUptime(long millis) {
        long seconds = (long)(millis / 1000L)
        long minutes = (long)(seconds / 60L)
        long hours = (long)(minutes / 60L)
        long days = (long)(hours / 24L)

        if (days > 0) {
            return "${days}d ${hours % 24}h ${minutes % 60}m"
        } else if (hours > 0) {
            return "${hours}h ${minutes % 60}m ${seconds % 60}s"
        } else if (minutes > 0) {
            return "${minutes}m ${seconds % 60}s"
        } else {
            return "${seconds}s"
        }
    }
}

/**
 * Валидатор плагинов
 */
@CompileStatic
class PluginValidator {
    static void validatePlugin(PluginDescriptor descriptor, File jarFile) {
        // Проверка обязательных полей
        requireNonNull(descriptor.name, "Plugin name is required")
        requireNonNull(descriptor.version, "Plugin version is required")
        requireNonNull(descriptor.mainClass, "Plugin main class is required")

        // Проверка формата имени
        if (!descriptor.name.matches(/^[a-zA-Z0-9_-]+$/)) {
            throw new PluginValidationException(
                    "Plugin name can only contain letters, numbers, hyphens and underscores: ${descriptor.name}"
            )
        }

        // Проверка формата версии (SemVer)
        if (!descriptor.version.matches(/^\d+\.\d+\.\d+(-[a-zA-Z0-9.]+)?$/)) {
            throw new PluginValidationException(
                    "Invalid version format. Use Semantic Versioning (e.g., 1.0.0, 2.1.5-beta): ${descriptor.version}"
            )
        }

        // Проверка что mainClass существует
        if (!descriptor.mainClass.matches(/^[a-zA-Z_$][a-zA-Z\d_$]*(\.[a-zA-Z_$][a-zA-Z\d_$]*)*$/)) {
            throw new PluginValidationException(
                    "Invalid main class name: ${descriptor.mainClass}"
            )
        }

        // Проверка описания (не обязательна, но рекомендуется)
        if (!descriptor.description || descriptor.description.trim().isEmpty()) {
            println "[WARN] Plugin ${descriptor.name} has no description"
        }
    }

    private static void requireNonNull(Object obj, String message) {
        if (obj == null) {
            throw new PluginValidationException(message)
        }
    }
}

/**
 * Исключение для ошибок валидации плагинов
 */
@CompileStatic
class PluginValidationException extends PluginLoadingException {
    PluginValidationException(String message, Throwable cause = null) {
        super(message, cause)
    }
}

@CompileStatic
class PluginLoadingException extends RuntimeException {
    PluginLoadingException(String message) {
        super(message)
    }

    PluginLoadingException(String message, Throwable cause) {
        super(message, cause)
    }
}