// dustybox-core/src/main/groovy/com/dustymotors/core/web/PluginTestController.groovy
package com.dustymotors.core.web

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class PluginTestController {

    @GetMapping("/test-plugins")
    String testPlugins(Model model) {
        return "test-plugins"
    }
}