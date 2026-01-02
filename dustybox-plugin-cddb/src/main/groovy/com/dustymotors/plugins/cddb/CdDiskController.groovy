// dustybox-core/src/main/groovy/com/dustymotors/core/web/CddbProxyController.groovy
package com.dustymotors.core.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.*
import groovy.json.JsonOutput

@RestController
@RequestMapping("/api/cddb")
class CddbProxyController {

    @Autowired
    private JdbcTemplate jdbcTemplate

    @GetMapping("/disks")
    List<Map<String, Object>> getAllDisks() {
        return jdbcTemplate.queryForList("""
            SELECT id, title, artist, year, 
                   created_at as "createdAt"
            FROM cd_disks 
            ORDER BY id
        """)
    }

    @GetMapping("/disks/{id}")
    Map<String, Object> getDisk(@PathVariable Long id) {
        def disks = jdbcTemplate.queryForList("""
            SELECT id, title, artist, year, 
                   created_at as "createdAt"
            FROM cd_disks 
            WHERE id = ?
        """, id)

        if (disks.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "CD not found with id: ${id}"
            )
        }

        return disks[0]
    }

    @GetMapping("/disks/count")
    Map<String, Long> getCount() {
        def count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cd_disks",
                Long.class
        )
        return ["count": count]
    }

    @PostMapping("/disks")
    Map<String, Object> createDisk(@RequestBody Map<String, Object> diskData) {
        def title = diskData.get("title")?.toString()
        def artist = diskData.get("artist")?.toString()
        def year = diskData.get("year") as Integer

        if (!title || !artist) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Title and artist are required"
            )
        }

        def id = jdbcTemplate.queryForObject("""
            INSERT INTO cd_disks (title, artist, year) 
            VALUES (?, ?, ?) 
            RETURNING id
        """, Long.class, title, artist, year)

        return getDisk(id)
    }

    @GetMapping("/disks/search")
    List<Map<String, Object>> search(@RequestParam String query) {
        return jdbcTemplate.queryForList("""
            SELECT id, title, artist, year, 
                   created_at as "createdAt"
            FROM cd_disks 
            WHERE title ILIKE ? OR artist ILIKE ?
            ORDER BY title
        """, "%${query}%", "%${query}%")
    }

    @DeleteMapping("/disks/{id}")
    Map<String, Object> deleteDisk(@PathVariable Long id) {
        // Проверяем существование записи
        def existing = jdbcTemplate.queryForList(
                "SELECT id FROM cd_disks WHERE id = ?",
                id
        )

        if (existing.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "CD not found with id: ${id}"
            )
        }

        def rowsAffected = jdbcTemplate.update("DELETE FROM cd_disks WHERE id = ?", id)

        return [
                success: rowsAffected > 0,
                message: rowsAffected > 0 ? "CD deleted successfully" : "Failed to delete CD",
                id: id
        ]
    }

    @PutMapping("/disks/{id}")
    Map<String, Object> updateDisk(@PathVariable Long id, @RequestBody Map<String, Object> diskData) {
        def title = diskData.get("title")?.toString()
        def artist = diskData.get("artist")?.toString()
        def year = diskData.get("year") as Integer

        if (!title || !artist) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Title and artist are required"
            )
        }

        // Проверяем существование записи
        def existing = jdbcTemplate.queryForList(
                "SELECT id FROM cd_disks WHERE id = ?",
                id
        )

        if (existing.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "CD not found with id: ${id}"
            )
        }

        def rowsAffected = jdbcTemplate.update("""
            UPDATE cd_disks 
            SET title = ?, artist = ?, year = ?
            WHERE id = ?
        """, title, artist, year, id)

        if (rowsAffected > 0) {
            return getDisk(id)
        } else {
            return [
                    success: false,
                    message: "Failed to update CD"
            ]
        }
    }

    @GetMapping("/stats")
    Map<String, Object> getStats() {
        def stats = jdbcTemplate.queryForMap("""
            SELECT 
                COUNT(*) as total,
                MIN(year) as earliest_year,
                MAX(year) as latest_year,
                COUNT(DISTINCT artist) as unique_artists
            FROM cd_disks
        """)

        return [
                stats: stats,
                message: "CD database statistics"
        ]
    }

    @GetMapping("/artists")
    List<Map<String, Object>> getArtists() {
        return jdbcTemplate.queryForList("""
            SELECT artist, COUNT(*) as cd_count
            FROM cd_disks
            GROUP BY artist
            ORDER BY cd_count DESC
        """)
    }

    @GetMapping("/test")
    Map<String, Object> test() {
        def tableExists = checkTableExists()
        def recordCount = tableExists ? getCount().count : 0

        return [
                status: "active",
                message: "CDDB Proxy API is working",
                tableExists: tableExists,
                recordCount: recordCount,
                timestamp: new java.util.Date(),
                endpoints: [
                        "GET    /api/cddb/disks",
                        "GET    /api/cddb/disks/{id}",
                        "POST   /api/cddb/disks",
                        "PUT    /api/cddb/disks/{id}",
                        "DELETE /api/cddb/disks/{id}",
                        "GET    /api/cddb/disks/count",
                        "GET    /api/cddb/disks/search?query={query}",
                        "GET    /api/cddb/stats",
                        "GET    /api/cddb/artists",
                        "GET    /api/cddb/test"
                ]
        ]
    }

    @GetMapping("/health")
    Map<String, Object> health() {
        try {
            def tableExists = checkTableExists()
            def isConnected = true

            // Проверяем подключение к БД
            jdbcTemplate.queryForObject("SELECT 1", Integer.class)

            return [
                    status: "UP",
                    database: "CONNECTED",
                    tableExists: tableExists,
                    timestamp: new java.util.Date()
            ]
        } catch (Exception e) {
            return [
                    status: "DOWN",
                    error: e.message,
                    timestamp: new java.util.Date()
            ]
        }
    }

    private boolean checkTableExists() {
        try {
            jdbcTemplate.queryForObject("SELECT 1 FROM cd_disks LIMIT 1", Integer.class)
            return true
        } catch (Exception e) {
            return false
        }
    }
}