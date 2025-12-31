package com.dustymotors.core

import org.springframework.stereotype.Component
import groovy.transform.CompileStatic

@Component
@CompileStatic
class EventBus {
    private Map<String, List<Closure>> listeners = [:]

    void subscribe(String event, Closure handler) {
        listeners.computeIfAbsent(event, { [] as List<Closure> }).add(handler)
    }

    void publish(String event, Map<String, Object> data = [:]) {
        listeners.getOrDefault(event, []).each { handler ->
            try {
                handler.call(data)
            } catch (Exception e) {
                println "Error in event handler: ${e.message}"
            }
        }
    }
}