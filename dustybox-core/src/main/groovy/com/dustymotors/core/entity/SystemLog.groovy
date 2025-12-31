package com.dustymotors.core.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "system_logs")
class SystemLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id

    @Column(nullable = false)
    String level // INFO, WARN, ERROR

    @Column(nullable = false, length = 1000)
    String message

    @Column
    String source

    @Column(nullable = false)
    LocalDateTime timestamp = LocalDateTime.now()

    // Конструкторы
    SystemLog() {}

    SystemLog(String level, String message, String source = null) {
        this.level = level
        this.message = message
        this.source = source
    }
}