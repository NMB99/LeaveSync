package com.leavesync.publicholiday;

import com.leavesync.entity.PublicHoliday;
import com.leavesync.repository.PublicHolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PublicHolidayCacheService {

    private final PublicHolidayRepository publicHolidayRepository;

    @Cacheable(value = "holidays", key = "#region + '_' + #year")
    public Set<LocalDate> getPublicHolidaysForYear(String region, int year) {

        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);

        List<PublicHoliday> holidays =  publicHolidayRepository.findByRegionAndDateBetween(region, from, to);

        return holidays.stream()
                .map(PublicHoliday::getDate)
                .collect(Collectors.toUnmodifiableSet());
    }
}
