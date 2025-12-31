package com.dustymotors.core

import org.springframework.stereotype.Component
import groovy.transform.CompileStatic
import groovy.lang.Binding
import groovy.lang.GroovyShell

@Component
@CompileStatic
class ScriptEngine {

    Object executeScript(String scriptContent, Map<String, Object> bindingVars = [:]) {
        Binding binding = new Binding()

        // Добавляем стандартные переменные
        binding.setVariable('println', { Object msg ->
            System.out.println("[Script] ${msg}")
        } as Closure)

        binding.setVariable('print', { Object msg ->
            System.out.print("[Script] ${msg}")
        } as Closure)

        // Добавляем пользовательские переменные
        bindingVars.each { key, value ->
            binding.setVariable(key, value)
        }

        try {
            GroovyShell shell = new GroovyShell(binding)
            return shell.evaluate(scriptContent)
        } catch (Exception e) {
            throw new ScriptExecutionException("Error executing script: ${e.message}", e)
        }
    }

    void addToBinding(String name, Object value) {
        // Этот метод будет использоваться для регистрации сервисов
        println "Added to binding: ${name}"
    }

    static class ScriptExecutionException extends RuntimeException {
        ScriptExecutionException(String message, Throwable cause) {
            super(message, cause)
        }
    }
}