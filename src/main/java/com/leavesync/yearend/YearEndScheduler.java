package com.leavesync.yearend;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class YearEndScheduler {

    private final YearEndService yearEndService;

    @Scheduled(cron = "0 0 1 1 1 *")
    public void triggerAnnualRollover() {
        yearEndService.processAnnualRollover();
    }

    @Scheduled(cron = "0 0 9 1 12 *")
    public void triggerYearEndWarnings() {
        yearEndService.sendYearEndWarnings();
    }
}
