package com.leavesync.workingday;

import com.leavesync.entity.PublicHoliday;
import com.leavesync.repository.PublicHolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

    public WorkingDayResponse totalWorkingDaysResponse (LocalDate from, LocalDate to) {
        return new WorkingDayResponse(from, to, countWorkingDays(from, to));
    }

    public BigDecimal countWorkingDays (LocalDate from, LocalDate to) {

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

        return BigDecimal.valueOf(count);
    }

    public LocalDate subtractWorkingDays (LocalDate from, int days) {

        Set<LocalDate> publicHolidays = publicHolidayRepository
                .findByRegionAndDateBetween(defaultRegion, from.minusDays(days * 3L), from)
                .stream()
                .map(PublicHoliday::getDate)
                .collect(Collectors.toSet());

        int counted = 0;
        LocalDate current = from.minusDays(1);

        while (counted < days) {
            DayOfWeek day = current.getDayOfWeek();
            boolean isWeekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
            boolean isPublicHoliday = publicHolidays.contains(current);

            if (!isWeekend && !isPublicHoliday) {
                counted++;
            }

            if (counted < days) {
                current = current.minusDays(1);
            }
        }

        return current;
    }

    public LocalDate addWorkingDays (LocalDate from, int days) {

        Set<LocalDate> publicHolidays = publicHolidayRepository
                .findByRegionAndDateBetween(defaultRegion, from, from.plusDays(days * 3L))
                .stream()
                .map(PublicHoliday::getDate)
                .collect(Collectors.toSet());

        int counted = 0;
        LocalDate current = from.plusDays(1);

        while (counted < days) {
            DayOfWeek day = current.getDayOfWeek();
            boolean isWeekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
            boolean isPublicHoliday = publicHolidays.contains(current);

            if (!isWeekend && !isPublicHoliday) {
                counted++;
            }

            if (counted < days) {
                current = current.plusDays(1);
            }
        }
        return current;
    }

    public LocalDate normaliseToWorkingDay (LocalDate date) {

        Set<LocalDate> publicHolidays = publicHolidayRepository
                .findByRegionAndDateBetween(defaultRegion, date, date.plusDays(7L))
                .stream()
                .map(PublicHoliday::getDate)
                .collect(Collectors.toSet());

        LocalDate current = date;

        while (true) {
            DayOfWeek day = current.getDayOfWeek();
            boolean isWeekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
            boolean isPublicHoliday = publicHolidays.contains(current);

            if (!isWeekend && !isPublicHoliday) {
                return current;
            }

            current = current.plusDays(1);
        }
    }
}
