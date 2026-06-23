package com.leavesync.workingday;

import com.leavesync.publicholiday.PublicHolidayCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class WorkingDayService {

    private final PublicHolidayCacheService publicHolidayCacheService;

    @Value("${leavesync.default-region}")
    private String defaultRegion;

    public WorkingDayResponse totalWorkingDaysResponse (LocalDate from, LocalDate to) {
        return new WorkingDayResponse(from, to, countWorkingDays(from, to));
    }

    public BigDecimal countWorkingDays (LocalDate from, LocalDate to) {

        Set<Integer> years = Stream.of(from.getYear(), to.getYear())
                .collect(Collectors.toSet());

        Set<LocalDate> publicHolidays = years.stream()
                .flatMap(year -> publicHolidayCacheService
                        .getPublicHolidaysForYear(defaultRegion, year)
                        .stream()
                )
                .collect(Collectors.toUnmodifiableSet());

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

        Set<Integer> years = Stream.of(from.minusDays(days * 3L).getYear(), from.getYear())
                .collect(Collectors.toSet());

        Set<LocalDate> publicHolidays = years.stream()
                .flatMap(year -> publicHolidayCacheService
                        .getPublicHolidaysForYear(defaultRegion, year)
                        .stream()
                )
                .collect(Collectors.toUnmodifiableSet());

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

        Set<Integer> years = Stream.of(from.getYear(), from.plusDays(days * 3L).getYear())
                .collect(Collectors.toSet());

        Set<LocalDate> publicHolidays = years.stream()
                .flatMap(year -> publicHolidayCacheService
                        .getPublicHolidaysForYear(defaultRegion, year)
                        .stream()
                )
                .collect(Collectors.toUnmodifiableSet());

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

        Set<Integer> years = Stream.of(date.getYear(), date.plusDays(7L).getYear())
                .collect(Collectors.toSet());

        Set<LocalDate> publicHolidays = years.stream()
                .flatMap(year -> publicHolidayCacheService
                        .getPublicHolidaysForYear(defaultRegion, year)
                        .stream()
                )
                .collect(Collectors.toUnmodifiableSet());

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
