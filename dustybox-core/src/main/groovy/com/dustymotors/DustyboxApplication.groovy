package com.dustymotors

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.beans.factory.annotation.Autowired
import jakarta.annotation.PostConstruct
import com.dustymotors.core.plugin.PluginManager
import groovy.transform.CompileStatic

@SpringBootApplication
@CompileStatic
class DustyboxApplication {

    @Autowired
    private PluginManager pluginManager

    static void main(String[] args) {
        SpringApplication.run(DustyboxApplication, args)
    }

    @PostConstruct
    void init() {
        println "Dustybox Application starting..."
        pluginManager.loadPlugins()
        println "Dustybox Application started successfully"
    }
}