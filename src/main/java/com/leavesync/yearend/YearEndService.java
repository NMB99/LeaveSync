package com.leavesync.yearend;

import com.leavesync.entity.User;
import com.leavesync.leavebalance.LeaveBalanceService;
import com.leavesync.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class YearEndService {

    private final LeaveBalanceService leaveBalanceService;
    private final UserRepository userRepository;

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

}
