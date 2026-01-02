// dustybox-core/src/main/groovy/com/dustymotors/core/plugin/PluginControllerRegistry.groovy
package com.dustymotors.core.plugin

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.servlet.handler.AbstractHandlerMapping
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerExecutionChain
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.annotation.AnnotationUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.reflect.Method

@Component
class PluginControllerRegistry extends AbstractHandlerMapping {

    private static final Logger log = LoggerFactory.getLogger(PluginControllerRegistry.class)

    @Autowired
    private PluginManager pluginManager

    private final Map<String, Map<String, HandlerMethod>> pluginHandlers = [:]

    @Override
    protected HandlerExecutionChain getHandlerInternal(HttpServletRequest request) throws Exception {
        String requestURI = request.requestURI

        // Проверяем, является ли это запросом к API плагина
        if (requestURI.startsWith("/plugins/") && requestURI.contains("/api/")) {
            // Извлекаем pluginId и путь
            def parts = requestURI.split("/")
            if (parts.length >= 4) {
                String pluginId = parts[2]
                String pluginPath = requestURI.substring("/plugins/${pluginId}/api/".length())

                // Ищем обработчик
                HandlerMethod handler = findHandler(pluginId, request.method, pluginPath)
                if (handler != null) {
                    return new HandlerExecutionChain(handler)
                }
            }
        }

        return null
    }

    private HandlerMethod findHandler(String pluginId, String httpMethod, String path) {
        def handlers = pluginHandlers[pluginId]
        if (!handlers) return null

        // Простой поиск по пути (можно улучшить с использованием паттернов)
        def key = "${httpMethod}:${path}"
        return handlers[key]
    }

    void registerPluginControllers(String pluginId, PluginInstance plugin) {
        try {
            def handlers = [:] as Map<String, HandlerMethod>
            def springContext = plugin.springContext

            if (!springContext) {
                log.warn("No Spring context for plugin: ${pluginId}")
                return
            }

            // Получаем все бины с аннотациями @Controller или @RestController
            def controllerBeans = [:] as Map<String, Object>
            controllerBeans.putAll(springContext.getBeansWithAnnotation(org.springframework.stereotype.Controller.class))
            controllerBeans.putAll(springContext.getBeansWithAnnotation(org.springframework.web.bind.annotation.RestController.class))

            log.info("Found ${controllerBeans.size()} controllers in plugin ${pluginId}")

            controllerBeans.each { beanName, bean ->
                def methods = bean.class.declaredMethods
                methods.each { method ->
                    // Проверяем наличие аннотаций маппинга
                    def mappingAnnotation = getRequestMappingAnnotation(method)
                    if (mappingAnnotation) {
                        def paths = extractPaths(mappingAnnotation)
                        def httpMethods = extractHttpMethods(mappingAnnotation)

                        paths.each { path ->
                            httpMethods.each { methodType ->
                                def fullPath = "/plugins/${pluginId}/api${path}"
                                def key = "${methodType}:${fullPath}"
                                handlers[key] = new HandlerMethod(bean, method)

                                log.info("Registered handler: ${methodType} ${fullPath} -> ${bean.class.simpleName}.${method.name}()")
                            }
                        }
                    }
                }
            }

            pluginHandlers[pluginId] = handlers

        } catch (Exception e) {
            log.error("Failed to register controllers for plugin ${pluginId}: ${e.message}", e)
        }
    }

    void unregisterPluginControllers(String pluginId) {
        pluginHandlers.remove(pluginId)
        log.info("Unregistered controllers for plugin: ${pluginId}")
    }

    private def getRequestMappingAnnotation(Method method) {
        return AnnotationUtils.findAnnotation(method,
                org.springframework.web.bind.annotation.RequestMapping.class) ?:
                AnnotationUtils.findAnnotation(method,
                        org.springframework.web.bind.annotation.GetMapping.class) ?:
                        AnnotationUtils.findAnnotation(method,
                                org.springframework.web.bind.annotation.PostMapping.class) ?:
                                AnnotationUtils.findAnnotation(method,
                                        org.springframework.web.bind.annotation.PutMapping.class) ?:
                                        AnnotationUtils.findAnnotation(method,
                                                org.springframework.web.bind.annotation.DeleteMapping.class) ?:
                                                AnnotationUtils.findAnnotation(method,
                                                        org.springframework.web.bind.annotation.PatchMapping.class)  // Исправлено: Patch -> PatchMapping
    }

    private List<String> extractPaths(def annotation) {
        if (annotation instanceof org.springframework.web.bind.annotation.RequestMapping) {
            def values = annotation.value()
            return values ? Arrays.asList(values) : [""]
        } else if (annotation instanceof org.springframework.web.bind.annotation.GetMapping) {
            def values = annotation.value()
            return values ? Arrays.asList(values) : [""]
        } else if (annotation instanceof org.springframework.web.bind.annotation.PostMapping) {
            def values = annotation.value()
            return values ? Arrays.asList(values) : [""]
        } else if (annotation instanceof org.springframework.web.bind.annotation.PutMapping) {
            def values = annotation.value()
            return values ? Arrays.asList(values) : [""]
        } else if (annotation instanceof org.springframework.web.bind.annotation.DeleteMapping) {
            def values = annotation.value()
            return values ? Arrays.asList(values) : [""]
        } else if (annotation instanceof org.springframework.web.bind.annotation.PatchMapping) {
            def values = annotation.value()
            return values ? Arrays.asList(values) : [""]
        }
        return [""]
    }

    private List<String> extractHttpMethods(def annotation) {
        if (annotation instanceof org.springframework.web.bind.annotation.RequestMapping) {
            def methods = annotation.method()
            if (methods && methods.length > 0) {
                return methods.collect { it.name() }
            }
            return ["GET"]
        } else if (annotation instanceof org.springframework.web.bind.annotation.GetMapping) {
            return ["GET"]
        } else if (annotation instanceof org.springframework.web.bind.annotation.PostMapping) {
            return ["POST"]
        } else if (annotation instanceof org.springframework.web.bind.annotation.PutMapping) {
            return ["PUT"]
        } else if (annotation instanceof org.springframework.web.bind.annotation.DeleteMapping) {
            return ["DELETE"]
        } else if (annotation instanceof org.springframework.web.bind.annotation.PatchMapping) {
            return ["PATCH"]
        }
        return ["GET"] // По умолчанию
    }
}