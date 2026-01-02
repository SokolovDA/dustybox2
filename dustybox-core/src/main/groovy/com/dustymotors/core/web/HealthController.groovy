package com.dustymotors.core.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/health")
class HealthController {

    @Autowired
    private JdbcTemplate jdbcTemplate

    @GetMapping
    Map<String, Object> health() {
        def dbStatus = "UNKNOWN"
        def dbDetails = [:]

        try {
            // Проверяем подключение к БД
            def result = jdbcTemplate.queryForObject("SELECT 1", Integer.class)
            dbStatus = result == 1 ? "OK" : "ERROR"
            dbDetails = [
                    connected: true,
                    testQuery: "SELECT 1",
                    result: result
            ]
        } catch (Exception e) {
            dbStatus = "ERROR"
            dbDetails = [
                    connected: false,
                    error: e.message
            ]
        }

        return [
                status: "UP",
                timestamp: new Date(),
                database: [
                        status: dbStatus,
                        details: dbDetails
                ]
        ]
    }
}