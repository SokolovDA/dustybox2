package com.dustymotors.plugins.cddb

import groovy.transform.CompileStatic
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import com.dustymotors.core.ScriptAccessible
import groovy.util.logging.Slf4j
import jakarta.persistence.*

// Сущность JPA
@Entity
@Table(name = "cd_disks")
@CompileStatic
class CdDisk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id

    @Column(nullable = false)
    String title

    @Column(nullable = false)
    String artist

    @Column
    Integer year

    @Column(name = "created_at")
    java.time.LocalDateTime createdAt = java.time.LocalDateTime.now()

    CdDisk() {}

    CdDisk(String title, String artist, Integer year) {
        this.title = title
        this.artist = artist
        this.year = year
    }
}

// Spring Data JPA репозиторий
interface CdDiskRepository extends org.springframework.data.jpa.repository.JpaRepository<CdDisk, Long> {
    List<CdDisk> findByArtistContainingIgnoreCase(String artist)
    List<CdDisk> findByYearBetween(Integer startYear, Integer endYear)
    List<CdDisk> findByTitleContainingIgnoreCase(String title)
    long count()
}

// Сервис с транзакциями
@Slf4j
@Service
@Transactional
@ScriptAccessible
@CompileStatic
class CdDiskService {

    @Autowired
    private CdDiskRepository repository

    @PostConstruct
    void init() {
        log.info("CdDiskService initialized")
    }

    // Основные CRUD операции
    CdDisk save(CdDisk disk) {
        return repository.save(disk)
    }

    CdDisk findById(Long id) {
        return repository.findById(id).orElse(null)
    }

    List<CdDisk> findAll() {
        return repository.findAll()
    }

    void deleteById(Long id) {
        repository.deleteById(id)
    }

    // Бизнес-методы
    List<CdDisk> findByArtist(String artist) {
        if (!artist) return []
        return repository.findByArtistContainingIgnoreCase(artist)
    }

    List<CdDisk> findByYearRange(Integer fromYear, Integer toYear) {
        if (fromYear == null || toYear == null) return []
        return repository.findByYearBetween(fromYear, toYear)
    }

    List<CdDisk> search(String query) {
        if (!query) return []

        def results = [] as Set<CdDisk>
        results.addAll(repository.findByTitleContainingIgnoreCase(query))
        results.addAll(repository.findByArtistContainingIgnoreCase(query))

        try {
            def year = Integer.parseInt(query)
            results.addAll(repository.findByYearBetween(year, year))
        } catch (NumberFormatException e) {
            // Игнорируем, если query не число
        }

        return results.toList()
    }

    long count() {
        return repository.count()
    }

    // Метод для работы со скриптами
    CdDisk createDisk(String title, String artist, Integer year) {
        def disk = new CdDisk(title: title, artist: artist, year: year)
        return save(disk)
    }
}