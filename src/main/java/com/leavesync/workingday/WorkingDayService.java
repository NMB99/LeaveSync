package com.leavesync.workingday;

import com.leavesync.entity.PublicHoliday;
import com.leavesync.repository.PublicHolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkingDayService {

    private final PublicHolidayRepository publicHolidayRepository;

    @Value("${leavesync.default-region}")
    private String defaultRegion;

    public WorkingDayResponse countWorkingDays (LocalDate from, LocalDate to) {

        Set<LocalDate> publicHolidays = publicHolidayRepository
                .findByRegionAndDateBetween(defaultRegion, from, to)
                .stream()
                .map(PublicHoliday::getDate)
                .collect(Collectors.toSet());

        int count = 0;
        LocalDate current = from;

        while (!current.isAfter(to)) {
            DayOfWeek day = current.getDayOfWeek();
            boolean isWeekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
            boolean isPublicHoliday = publicHolidays.contains(current);

            if (!isWeekend && !isPublicHoliday) {
                count++;
            }

            current = current.plusDays(1);
        }

        return new WorkingDayResponse(from, to, count);
    }
}
