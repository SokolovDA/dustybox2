package com.dustymotors.plugins.cddb

import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*
import org.springframework.http.ResponseEntity

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
    ResponseEntity<CdDisk> getDisk(@PathVariable Long id) {
        def disk = cdDiskService.findById(id)
        if (disk) {
            return ResponseEntity.ok(disk)
        } else {
            return ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/disks")
    ResponseEntity<CdDisk> createDisk(@RequestBody CdDisk disk) {
        def savedDisk = cdDiskService.save(disk)
        return ResponseEntity.ok(savedDisk)
    }

    @PutMapping("/disks/{id}")
    ResponseEntity<CdDisk> updateDisk(@PathVariable Long id, @RequestBody CdDisk disk) {
        def existingDisk = cdDiskService.findById(id)
        if (!existingDisk) {
            return ResponseEntity.notFound().build()
        }

        // Обновляем поля
        existingDisk.title = disk.title ?: existingDisk.title
        existingDisk.artist = disk.artist ?: existingDisk.artist
        existingDisk.year = disk.year ?: existingDisk.year

        def updatedDisk = cdDiskService.save(existingDisk)
        return ResponseEntity.ok(updatedDisk)
    }

    @DeleteMapping("/disks/{id}")
    ResponseEntity<Void> deleteDisk(@PathVariable Long id) {
        cdDiskService.deleteById(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/disks/search")
    List<CdDisk> search(@RequestParam String query) {
        return cdDiskService.search(query)
    }

    @GetMapping("/disks/count")
    Map<String, Long> getCount() {
        return ["count": cdDiskService.count()]
    }
}