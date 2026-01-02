package com.dustymotors.core.plugin

import groovy.transform.CompileStatic
import org.yaml.snakeyaml.Yaml
import java.util.Map as JMap

@CompileStatic
class PluginDescriptor {
    String name
    String version
    String description
    String mainClass
    String type
    String springConfigClass

    static PluginDescriptor load(InputStream input) {
        try {
            Yaml yaml = new Yaml()
            JMap<String, Object> yamlMap = yaml.load(input)

            if (yamlMap == null) {
                throw new PluginLoadingException("plugin.yaml is empty or invalid")
            }

            return new PluginDescriptor(
                    name: getString(yamlMap, 'name'),
                    version: getString(yamlMap, 'version'),
                    description: getString(yamlMap, 'description'),
                    mainClass: getString(yamlMap, 'mainClass'),
                    type: getString(yamlMap, 'type'),
                    springConfigClass: getString(yamlMap, 'springConfigClass')
            )
        } catch (Exception e) {
            throw new PluginLoadingException("Failed to parse plugin.yaml: ${e.message}", e)
        }
    }

    private static String getString(JMap<String, Object> map, String key) {
        Object value = map.get(key)
        return value != null ? value.toString() : null
    }
}