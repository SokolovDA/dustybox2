// dustybox-plugin-cddb/src/main/groovy/com/dustymotors/plugins/cddb/CdDiskService.groovy
package com.dustymotors.plugins.cddb

import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import com.dustymotors.core.ScriptAccessible
import groovy.util.logging.Slf4j
import jakarta.annotation.PostConstruct
import java.sql.ResultSet
import java.sql.SQLException

// Простая модель данных без JPA аннотаций
@CompileStatic
class CdDisk {
    Long id
    String title
    String artist
    Integer year
    java.time.LocalDateTime createdAt

    CdDisk() {}

    CdDisk(String title, String artist, Integer year) {
        this.title = title
        this.artist = artist
        this.year = year
    }
}

// RowMapper для преобразования ResultSet в CdDisk
@CompileStatic
class CdDiskRowMapper implements RowMapper<CdDisk> {
    @Override
    CdDisk mapRow(ResultSet rs, int rowNum) throws SQLException {
        CdDisk disk = new CdDisk()
        disk.id = rs.getLong("id")
        disk.title = rs.getString("title")
        disk.artist = rs.getString("artist")
        disk.year = rs.getObject("year") as Integer
        def timestamp = rs.getTimestamp("created_at")
        if (timestamp != null) {
            disk.createdAt = timestamp.toLocalDateTime()
        }
        return disk
    }
}

@Slf4j
@Service
@ScriptAccessible
@CompileStatic
class CdDiskService {

    @Autowired
    private JdbcTemplate jdbcTemplate

    @PostConstruct
    void init() {
        log.info("CdDiskService initialized with JDBC")
        // Проверяем и создаем таблицу если не существует
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS cd_disks (
                    id BIGSERIAL PRIMARY KEY,
                    title VARCHAR(255) NOT NULL,
                    artist VARCHAR(255) NOT NULL,
                    year INTEGER,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """)
            log.info("CD disks table ready")
        } catch (Exception e) {
            log.error("Failed to create cd_disks table: {}", e.message, e)
        }
    }

    // Основные CRUD операции
    CdDisk save(CdDisk disk) {
        if (disk.id == null) {
            // Insert
            def id = jdbcTemplate.queryForObject("""
                INSERT INTO cd_disks (title, artist, year) 
                VALUES (?, ?, ?) 
                RETURNING id
            """, Long.class, disk.title, disk.artist, disk.year)
            disk.id = id
        } else {
            // Update
            jdbcTemplate.update("""
                UPDATE cd_disks 
                SET title = ?, artist = ?, year = ? 
                WHERE id = ?
            """, disk.title, disk.artist, disk.year, disk.id)
        }
        return disk
    }

    CdDisk findById(Long id) {
        try {
            return jdbcTemplate.queryForObject("""
                SELECT id, title, artist, year, created_at 
                FROM cd_disks 
                WHERE id = ?
            """, new CdDiskRowMapper(), id)
        } catch (Exception e) {
            log.warn("Disk not found with id: {}", id)
            return null
        }
    }

    List<CdDisk> findAll() {
        return jdbcTemplate.query("""
            SELECT id, title, artist, year, created_at 
            FROM cd_disks 
            ORDER BY id
        """, new CdDiskRowMapper())
    }

    void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM cd_disks WHERE id = ?", id)
    }

    // Бизнес-методы
    List<CdDisk> findByArtist(String artist) {
        if (!artist) return []
        return jdbcTemplate.query("""
            SELECT id, title, artist, year, created_at 
            FROM cd_disks 
            WHERE LOWER(artist) LIKE LOWER(?) 
            ORDER BY artist
        """, new CdDiskRowMapper(), "%${artist}%")
    }

    List<CdDisk> findByYearRange(Integer fromYear, Integer toYear) {
        if (fromYear == null || toYear == null) return []
        return jdbcTemplate.query("""
            SELECT id, title, artist, year, created_at 
            FROM cd_disks 
            WHERE year BETWEEN ? AND ? 
            ORDER BY year
        """, new CdDiskRowMapper(), fromYear, toYear)
    }

    List<CdDisk> search(String query) {
        if (!query) return []

        return jdbcTemplate.query("""
            SELECT id, title, artist, year, created_at 
            FROM cd_disks 
            WHERE LOWER(title) LIKE LOWER(?) 
               OR LOWER(artist) LIKE LOWER(?) 
               OR CAST(year AS TEXT) LIKE ?
            ORDER BY title
        """, new CdDiskRowMapper(),
                "%${query}%", "%${query}%", "%${query}%")
    }

    long count() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cd_disks",
                Long.class
        ) ?: 0
    }

    // Метод для работы со скриптами
    CdDisk createDisk(String title, String artist, Integer year) {
        def disk = new CdDisk(title: title, artist: artist, year: year)
        return save(disk)
    }
}