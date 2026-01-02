// dustybox-plugin-cddb/src/main/groovy/com/dustymotors/plugins/cddb/CdDiskController.groovy
package com.dustymotors.plugins.cddb

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*
import org.springframework.http.ResponseEntity
import groovy.transform.CompileStatic

@RestController
@RequestMapping("/api/disks")
@CompileStatic
class CdDiskController {

    @Autowired
    private CdDiskService diskService

    @GetMapping
    List<CdDisk> getAllDisks() {
        return diskService.findAll()
    }

    @GetMapping("/{id}")
    CdDisk getDisk(@PathVariable Long id) {
        def disk = diskService.findById(id)
        if (!disk) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "CD not found with id: ${id}"
            )
        }
        return disk
    }

    @PostMapping
    CdDisk createDisk(@RequestBody CdDisk disk) {
        if (!disk.title || !disk.artist) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Title and artist are required"
            )
        }
        return diskService.save(disk)
    }

    @PutMapping("/{id}")
    CdDisk updateDisk(@PathVariable Long id, @RequestBody CdDisk disk) {
        disk.id = id
        return diskService.save(disk)
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> deleteDisk(@PathVariable Long id) {
        diskService.deleteById(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/search")
    List<CdDisk> search(@RequestParam String query) {
        return diskService.search(query)
    }

    @GetMapping("/artist/{artist}")
    List<CdDisk> findByArtist(@PathVariable String artist) {
        return diskService.findByArtist(artist)
    }

    @GetMapping("/count")
    Map<String, Long> count() {
        return [count: diskService.count()]
    }

    @GetMapping("/stats")
    Map<String, Object> getStats() {
        def count = diskService.count()
        def allDisks = diskService.findAll()

        def earliestYear = allDisks*.year?.min() ?: 0
        def latestYear = allDisks*.year?.max() ?: 0
        def uniqueArtists = allDisks*.artist?.unique()?.size() ?: 0

        return [
                total: count,
                earliestYear: earliestYear,
                latestYear: latestYear,
                uniqueArtists: uniqueArtists,
                yearsRange: latestYear - earliestYear
        ]
    }

    @GetMapping("/health")
    Map<String, Object> health() {
        return [
                status: "UP",
                service: "CdDiskService",
                diskCount: diskService.count(),
                timestamp: new Date()
        ]
    }
}