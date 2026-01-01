package com.dustymotors.plugins.cddb

import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/plugins/cddb")
@CompileStatic
class CdDiskController {

    @Autowired
    private CdDiskService cdDiskService

    @GetMapping("/disks")
    List<CdDisk> getAllDisks() {
        return cdDiskService.findAll()
    }

    @GetMapping("/disks/{id}")
    CdDisk getDisk(@PathVariable Long id) {
        return cdDiskService.findById(id)
    }

    @PostMapping("/disks")
    CdDisk createDisk(@RequestBody CdDisk disk) {
        return cdDiskService.save(disk)
    }

    @GetMapping("/disks/count")
    Map<String, Long> getCount() {
        return ["count": cdDiskService.count()]
    }

    @GetMapping("/disks/search")
    List<CdDisk> search(@RequestParam String query) {
        return cdDiskService.search(query)
    }
}