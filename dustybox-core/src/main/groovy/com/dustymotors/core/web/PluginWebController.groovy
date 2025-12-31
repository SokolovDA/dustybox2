package com.dustymotors.core.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import com.dustymotors.core.plugin.PluginManager
import groovy.transform.CompileStatic

@Controller
@RequestMapping("/plugins")
@CompileStatic
class PluginWebController {

    @Autowired
    private PluginManager pluginManager

    @GetMapping("/")
    @ResponseBody
    Map<String, Object> listPlugins() {
        return [
                plugins: pluginManager.loadedPlugins.collect { name, plugin ->
                    [
                            name: plugin.name,
                            version: plugin.version,
                            description: plugin.description
                    ]
                }
        ]
    }

    @GetMapping("/ui")
    String pluginUI(Model model) {
        model.addAttribute("menuItems", [])
        model.addAttribute("resources", [])
        return "plugin-ui"
    }
}