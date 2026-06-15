package com.leavesync.escalation;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EscalationScheduler {

    private final EscalationService escalationService;

    @Scheduled(cron = "0 0 9 * * *")
    public void triggerDay3Reminders() {
        escalationService.sendDay3Reminders();
    }

    @Scheduled(cron = "0 0 9 * * *")
    public void triggerDay5Escalation() {
        escalationService.escalateDay5Requests();
    }

    @Scheduled(cron = "0 0 17 * * *")
    public void triggerUrgentNotifications() {
        escalationService.sendUrgentNotifications();
    }

}
