package com.leavesync.workingday;

import com.leavesync.publicholiday.PublicHolidayCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkingDayServiceTest {

    @Mock
    private PublicHolidayCacheService publicHolidayCacheService;

    @InjectMocks
    private WorkingDayService workingDayService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(workingDayService, "defaultRegion", "ENGLAND");
    }

    @Test
    void countWorkingDays_shouldReturnFive_whenGivenFullWeekWithNoHolidays() {
        LocalDate from = LocalDate.of(2026, 6, 22);
        LocalDate to = LocalDate.of(2026, 6, 26);

        when(publicHolidayCacheService.getPublicHolidaysForYear(anyString(), anyInt()))
                .thenReturn(List.of());

        BigDecimal result = workingDayService.countWorkingDays(from, to);

        assertEquals(BigDecimal.valueOf(5), result);
    }

    @Test
    void countWorkingDays_shouldExcludeWeekend_whenRangeSpansFullWeek() {
        LocalDate from = LocalDate.of(2026, 6, 22);
        LocalDate to = LocalDate.of(2026, 6, 28);

        when(publicHolidayCacheService.getPublicHolidaysForYear(anyString(), anyInt()))
                .thenReturn(List.of());

        BigDecimal result = workingDayService.countWorkingDays(from, to);

        assertEquals(BigDecimal.valueOf(5), result);
    }

    @Test
    void countWorkingDays_shouldExcludeBankHolidays_whenHolidayFallsOnWeekday() {
        LocalDate from = LocalDate.of(2026, 6, 22);
        LocalDate to = LocalDate.of(2026, 6, 26);

        when(publicHolidayCacheService.getPublicHolidaysForYear(anyString(), anyInt()))
                .thenReturn(List.of(LocalDate.of(2026, 6, 25)));

        BigDecimal result = workingDayService.countWorkingDays(from, to);

        assertEquals(BigDecimal.valueOf(4), result);
    }

    @Test
    void countWorkingDays_shouldReturnOne_whenFromAndToAreSameWeekday() {
        LocalDate date = LocalDate.of(2026, 6, 22);

        when(publicHolidayCacheService.getPublicHolidaysForYear(anyString(), anyInt()))
                .thenReturn(List.of());

        BigDecimal result = workingDayService.countWorkingDays(date, date);

        assertEquals(BigDecimal.valueOf(1), result);
    }

    @Test
    void countWorkingDays_shouldReturnZero_whenFromAndToAreWeekendOnly() {
        LocalDate from = LocalDate.of(2026, 6, 27);
        LocalDate to = LocalDate.of(2026, 6, 28);

        when(publicHolidayCacheService.getPublicHolidaysForYear(anyString(), anyInt()))
                .thenReturn(List.of());

        BigDecimal result = workingDayService.countWorkingDays(from, to);

        assertEquals(BigDecimal.valueOf(0), result);
    }

    @Test
    void countWorkingDays_shouldHandleCrossYearRange() {
        LocalDate from = LocalDate.of(2026, 12, 30);
        LocalDate to = LocalDate.of(2027, 1, 2);

        when(publicHolidayCacheService.getPublicHolidaysForYear(anyString(), anyInt()))
                .thenReturn(List.of());

        BigDecimal result = workingDayService.countWorkingDays(from, to);

        assertEquals(BigDecimal.valueOf(3), result);
    }

    @Test
    void normaliseToWorkingDay_shouldReturnSameDate_whenDateIsWorkingDay() {
        LocalDate date = LocalDate.of(2026, 6, 22);

        when(publicHolidayCacheService.getPublicHolidaysForYear(anyString(), anyInt()))
                .thenReturn(List.of());

        LocalDate result = workingDayService.normaliseToWorkingDay(date);

        assertEquals(date, result);
    }

    @Test
    void normaliseToWorkingDay_shouldReturnNextWorkingDay_whenDateIsSaturday() {
        LocalDate date = LocalDate.of(2026, 6, 27);

        when(publicHolidayCacheService.getPublicHolidaysForYear(anyString(), anyInt()))
                .thenReturn(List.of());

        LocalDate result = workingDayService.normaliseToWorkingDay(date);

        assertEquals(LocalDate.of(2026, 6, 29), result);
    }

    @Test
    void normaliseToWorkingDay_shouldReturnNextWorkingDay_whenDateIsSunday() {
        LocalDate date = LocalDate.of(2026, 6, 28);

        when(publicHolidayCacheService.getPublicHolidaysForYear(anyString(), anyInt()))
                .thenReturn(List.of());

        LocalDate result = workingDayService.normaliseToWorkingDay(date);

        assertEquals(LocalDate.of(2026, 6, 29), result);
    }

    @Test
    void normaliseToWorkingDay_shouldReturnNextWorkingDay_whenMondayIsBankHoliday() {
        LocalDate monday = LocalDate.of(2026, 6, 29);

        when(publicHolidayCacheService.getPublicHolidaysForYear(anyString(), anyInt()))
                .thenReturn(List.of(monday));

        LocalDate result = workingDayService.normaliseToWorkingDay(monday);

        assertEquals(LocalDate.of(2026, 6, 30), result);
    }

    /**
     * today's date is 2026-06-26 & subtracting 5 days, so the result should be 2026-06-19
     * Note - 2026-06-20 & 2026-06-21 are weekends, so should be skipped
     */
    @Test
    void subtractWorkingDays_shouldReturnCorrectDate_whenNoHolidays() {
        LocalDate date = LocalDate.of(2026, 6, 26);

        when(publicHolidayCacheService.getPublicHolidaysForYear(anyString(), anyInt()))
                .thenReturn(List.of());

        LocalDate result = workingDayService.subtractWorkingDays(date, 5);

        assertEquals(LocalDate.of(2026, 6, 19), result);
    }

    /**
     * today's date is 2026-06-26 & subtracting 5 days, so the result should be 2026-06-17
     * Note - 2026-06-23 & 2026-06-24 are bank holidays, also 2026-06-20 & 2026-06-21 are weekends, so should be skipped
     */
    @Test
    void subtractWorkingDays_shouldReturnCorrectDate_whenHolidaysInRange() {
        LocalDate date = LocalDate.of(2026, 6, 26);

        when(publicHolidayCacheService.getPublicHolidaysForYear(anyString(), anyInt()))
                .thenReturn(List.of(LocalDate.of(2026, 6, 23), LocalDate.of(2026, 6, 24)));

        LocalDate result = workingDayService.subtractWorkingDays(date, 5);

        assertEquals(LocalDate.of(2026, 6, 17), result);
    }

    /**
     * today's date is 2026-06-22 & adding 5 days, so the result should be 2026-06-29
     * Note - 2026-06-27 & 2026-06-28 are weekends, so should be skipped
     */
    @Test
    void addWorkingDays_shouldReturnCorrectDate_whenNoHolidays() {
        LocalDate date = LocalDate.of(2026, 6, 22);

        when(publicHolidayCacheService.getPublicHolidaysForYear(anyString(), anyInt()))
                .thenReturn(List.of());

        LocalDate result = workingDayService.addWorkingDays(date, 5);

        assertEquals(LocalDate.of(2026, 6, 29), result);
    }

    /**
     * today's date is 2026-06-22 & adding 5 days, so the result should be 2026-06-30
     * Note - 2026-06-29 is a bank holiday & 2026-06-27 & 2026-06-28 are weekends, so should be skipped
     */
    @Test
    void addWorkingDays_shouldReturnCorrectDate_whenHolidaysInRange() {
        LocalDate date = LocalDate.of(2026, 6, 22);

        when(publicHolidayCacheService.getPublicHolidaysForYear(anyString(), anyInt()))
                .thenReturn(List.of(LocalDate.of(2026, 6, 29)));

        LocalDate result = workingDayService.addWorkingDays(date, 5);

        assertEquals(LocalDate.of(2026, 6, 30), result);
    }

}