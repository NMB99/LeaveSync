package com.leavesync.yearend;

import com.leavesync.email.EmailService;
import com.leavesync.entity.User;
import com.leavesync.enums.Role;
import com.leavesync.leavebalance.LeaveBalanceService;
import com.leavesync.repository.PublicHolidayRepository;
import com.leavesync.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class YearEndService {

    private final LeaveBalanceService leaveBalanceService;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PublicHolidayRepository publicHolidayRepository;

    public void processAnnualRollover() {

        int newYear = LocalDate.now().getYear();
        List<User> users = userRepository.findByIsActiveTrue();

        int succeeded = 0;
        int failed = 0;

        for (User user : users) {
            try {
                leaveBalanceService.createLeaveBalanceForYear(user, newYear);
                succeeded++;
            }
            catch (Exception e) {
                failed++;
                log.error("Rollover failed for userId = {} year = {} : {}", user.getId(), newYear, e.getMessage());
            }
        }

        log.info("Annual rollover processed for year {}. {} succeeded, {} failed.", newYear, succeeded, failed);
    }

    public void sendYearEndWarnings() {

        int currentYear = LocalDate.now().getYear();
        List<User> activeUsers = userRepository.findByIsActiveTrue();
        List<YearEndWarningEntry> entries = new ArrayList<>();
        List<UUID> failedUserIds = new ArrayList<>();

        for (User user : activeUsers) {
            try {
                Optional<BigDecimal> remainingOpt = leaveBalanceService.getRemainingLeaveBalance(user.getId(), currentYear);

                if (remainingOpt.isEmpty()) {
                    continue;
                }

                BigDecimal remainingBalance = remainingOpt.get();
                if (remainingBalance.compareTo(new BigDecimal("5")) > 0) {
                    BigDecimal daysAtRisk = remainingBalance.subtract(new BigDecimal("5"));
                    entries.add(new YearEndWarningEntry(
                            user.getFirstName() + " " + user.getLastName(),
                            remainingBalance,
                            daysAtRisk
                    ));
                    emailService.sendYearEndWarningEmail(
                            user.getEmail(),
                            user.getFirstName(),
                            remainingBalance,
                            daysAtRisk
                    );
                }
            }
            catch (Exception e) {
                failedUserIds.add(user.getId());
                log.error("Year-end warnings: failed for userId = {} year = {} : {}", user.getId(), currentYear, e.getMessage());
            }
        }

        if (!failedUserIds.isEmpty()) {
            log.error("Year-end warnings: emails failed for {} users: {}", failedUserIds.size(), failedUserIds);
        }

        if (entries.isEmpty()) {
            log.info("Year-end warnings: No employees with remaining balance above carry-over threshold.");
            return;
        }

        List<YearEndWarningEntry> sortedEntries = entries.stream()
                .sorted(Comparator.comparing(YearEndWarningEntry::daysAtRisk).reversed())
                .toList();

        List<User> hrUsers = userRepository.findAllByRoleAndIsActiveTrue(Role.HR);

        for (User hr : hrUsers) {
            emailService.sendYearEndSummaryEmail(
                    hr.getEmail(),
                    hr.getFirstName(),
                    sortedEntries
            );
        }

        log.info("Year-end warnings: {} employees flagged, summary sent to {} HR users.", entries.size(), hrUsers.size());
    }

    public void checkNewYearPublicHolidays() {

        int newYear = LocalDate.now().getYear() + 1;

        List<String> regions = publicHolidayRepository.findDistinctRegions();

        List<String> missingRegions = regions.stream()
                .filter(region -> {
                    LocalDate from = LocalDate.of(newYear, 1, 1);
                    LocalDate to = LocalDate.of(newYear, 12, 31);
                    return publicHolidayRepository.findByRegionAndDateBetween(region, from, to).isEmpty();
                })
                .toList();

        if (missingRegions.isEmpty()) {
            log.info("Public holidays check: All regions have data for {}", newYear);
            return;
        }

        List<User> recipients = new ArrayList<>();
        recipients.addAll(userRepository.findAllByRoleAndIsActiveTrue(Role.HR));
        recipients.addAll(userRepository.findAllByRoleAndIsActiveTrue(Role.ADMIN));

        List<UUID> failedUserIds = new ArrayList<>();

        for (User recipient : recipients) {
            try {
                emailService.sendMissingPublicHolidaysEmail(
                        recipient.getEmail(),
                        recipient.getFirstName(),
                        newYear,
                        missingRegions
                );
            }
            catch (Exception e) {
                failedUserIds.add(recipient.getId());
                log.error("Public holidays check: email failed for userId = {} : {}", recipient.getId(), e.getMessage());
            }
        }

        if (!failedUserIds.isEmpty()) {
            log.error("Public holidays check: emails failed for {} recipients: {}", failedUserIds.size(), failedUserIds);
        }

        log.info("Public holidays check: missing holidays data for {} in regions {}", newYear, missingRegions);
    }

}
