// dustybox-core/src/main/groovy/com/dustymotors/core/config/DatabaseInitializer.groovy
package com.dustymotors.core.config

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct
import javax.sql.DataSource

@Component
class DatabaseInitializer {

    @Autowired
    private DataSource dataSource

    @Autowired
    private JdbcTemplate jdbcTemplate

    @PostConstruct
    void initialize() {
        println "[INFO] Initializing database tables..."

        // Создаем таблицу system_logs (если еще не создана Spring Boot)
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS system_logs (
                    id BIGSERIAL PRIMARY KEY,
                    level VARCHAR(20) NOT NULL,
                    message VARCHAR(1000) NOT NULL,
                    source VARCHAR(255),
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """)
            println "[SUCCESS] system_logs table ready"
        } catch (Exception e) {
            println "[WARN] system_logs table may already exist: ${e.message}"
        }

        // Создаем таблицу для CDDB плагина
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
            println "[SUCCESS] cd_disks table created"

            // Вставляем тестовые данные
            try {
                def count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM cd_disks WHERE title = 'The Dark Side of the Moon'",
                        Integer.class
                )

                if (count == 0) {
                    jdbcTemplate.update("""
                        INSERT INTO cd_disks (title, artist, year) VALUES 
                        ('The Dark Side of the Moon', 'Pink Floyd', 1973),
                        ('Thriller', 'Michael Jackson', 1982),
                        ('Back in Black', 'AC/DC', 1980)
                    """)
                    println "[SUCCESS] Test data inserted into cd_disks"
                } else {
                    println "[INFO] Test data already exists in cd_disks"
                }
            } catch (Exception e) {
                println "[INFO] Could not insert test data (may already exist): ${e.message}"
            }

        } catch (Exception e) {
            println "[ERROR] Failed to create cd_disks table: ${e.message}"
        }

        // Показываем список таблиц
        try {
            def tables = jdbcTemplate.queryForList(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name"
            )

            println "\n" + "="*50
            println "DATABASE TABLES (${tables.size()}):"
            println "="*50
            tables.each { table ->
                println "  • ${table.table_name}"
            }
            println "="*50

        } catch (Exception e) {
            println "[ERROR] Failed to list tables: ${e.message}"
        }
    }
}