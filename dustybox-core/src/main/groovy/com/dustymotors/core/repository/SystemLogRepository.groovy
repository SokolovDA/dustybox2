package com.dustymotors.core.repository

import com.dustymotors.core.entity.SystemLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SystemLogRepository extends JpaRepository<SystemLog, Long> {
    List<SystemLog> findByLevelOrderByTimestampDesc(String level)
    List<SystemLog> findBySource(String source)
}