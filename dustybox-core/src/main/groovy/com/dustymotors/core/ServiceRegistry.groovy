package com.dustymotors.core

import org.springframework.stereotype.Component
import groovy.transform.CompileStatic

@Component
@CompileStatic
class ServiceRegistry {
    private Map<String, Object> services = [:]

    void register(String name, Object service) {
        services[name] = service
    }

    Object getService(String name) {
        return services[name]
    }

    Map<String, Object> getServicesForScripts() {
        return services.findAll { name, service ->
            // Проверка на наличие аннотации @ScriptAccessible
            service.getClass().annotations.any { it.annotationType().simpleName == 'ScriptAccessible' }
        }
    }
}