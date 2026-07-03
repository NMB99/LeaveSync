package com.leavesync.repository;

import com.leavesync.entity.PublicHoliday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, UUID> {

    List<PublicHoliday> findByRegion(String region);

    List<PublicHoliday> findByRegionAndDateBetween(String region, LocalDate from, LocalDate to);

    List<PublicHoliday> findByDateBetween(LocalDate from, LocalDate to);

    boolean existsByDateAndRegion(LocalDate date, String region);

    @Query("SELECT DISTINCT p.region FROM PublicHoliday p")
    List<String> findDistinctRegions();
}
