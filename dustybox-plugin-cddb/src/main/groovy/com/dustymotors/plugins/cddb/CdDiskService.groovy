package com.dustymotors.plugins.cddb

import groovy.transform.CompileStatic

@CompileStatic
class CdDisk {
    Long id
    String title
    String artist
    Integer year

    CdDisk() {}

    CdDisk(String title, String artist, Integer year) {
        this.title = title
        this.artist = artist
        this.year = year
    }
}

@CompileStatic
class CdDiskService {

    private List<CdDisk> disks = []
    private Long nextId = 1L

    List<CdDisk> findAll() {
        // В Groovy используем spread operator для клонирования списка
        // или просто возвращаем новый ArrayList
        return new ArrayList<CdDisk>(disks)
    }

    CdDisk findById(Long id) {
        return disks.find { it.id == id }
    }

    CdDisk save(CdDisk disk) {
        if (disk.id == null) {
            disk.id = nextId++
            disks.add(disk)
            return disk
        } else {
            def existingIndex = disks.findIndexOf { it.id == disk.id }
            if (existingIndex >= 0) {
                // Создаем новый объект с обновленными данными
                CdDisk existing = disks[existingIndex]
                existing.title = disk.title
                existing.artist = disk.artist
                existing.year = disk.year
                return existing
            } else {
                // Диска с таким ID нет, добавляем как новый
                disks.add(disk)
                return disk
            }
        }
    }

    boolean deleteById(Long id) {
        def removed = disks.removeIf { it.id == id }
        return removed
    }

    List<CdDisk> findByArtist(String artist) {
        if (!artist) return []

        String lowerArtist = artist.toLowerCase()
        return disks.findAll { disk ->
            disk.artist?.toLowerCase()?.contains(lowerArtist)
        }
    }

    // Дополнительные полезные методы
    int count() {
        return disks.size()
    }

    List<CdDisk> findByYearRange(Integer fromYear, Integer toYear) {
        return disks.findAll { disk ->
            disk.year != null && disk.year >= fromYear && disk.year <= toYear
        }
    }

    void clearAll() {
        disks.clear()
        nextId = 1L
    }
}