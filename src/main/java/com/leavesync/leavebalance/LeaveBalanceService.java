package com.leavesync.leavebalance;

import com.leavesync.entity.LeaveBalance;
import com.leavesync.entity.User;
import com.leavesync.exception.ResourceNotFoundException;
import com.leavesync.repository.LeaveBalanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class LeaveBalanceService {

    private final LeaveBalanceRepository leaveBalanceRepository;

    @Transactional
    public LeaveBalance createLeaveBalanceForYear(User user, int year) {

        return leaveBalanceRepository.findByUserIdAndYear(user.getId(), year)
                .orElseGet(() -> {

                    LeaveBalance previous = leaveBalanceRepository.findByUserIdAndYear(user.getId(), year - 1)
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "LeaveBalance", "userId/year", user.getId().toString() + "/" + (year - 1)));

                    BigDecimal remaining = previous.getTotalEntitlement()
                            .add(previous.getCarriedOver())
                            .subtract(previous.getLeaveUsed())
                            .subtract(previous.getPendingDays());

                    BigDecimal carryOver = remaining.max(BigDecimal.ZERO).min(new BigDecimal("5"));

                    LeaveBalance newLeaveBalance = new LeaveBalance();
                    newLeaveBalance.setUserId(user.getId());
                    newLeaveBalance.setYear(year);
                    newLeaveBalance.setTotalEntitlement(new BigDecimal("25"));
                    newLeaveBalance.setCarriedOver(carryOver);
                    newLeaveBalance.setLeaveUsed(BigDecimal.ZERO);
                    newLeaveBalance.setPendingDays(BigDecimal.ZERO);

                    return leaveBalanceRepository.save(newLeaveBalance);
                });
    }

    public Optional<BigDecimal> getRemainingLeaveBalance(UUID userId, int year) {

        Optional<LeaveBalance> balanceOpt = leaveBalanceRepository.findByUserIdAndYear(userId, year);

        if (balanceOpt.isEmpty()) {
            log.warn("No leave balance found for user {} in year {}", userId, year);
            return Optional.empty();
        }

        LeaveBalance balance = balanceOpt.get();
        return Optional.of(
                balance
                        .getTotalEntitlement()
                        .add(balance.getCarriedOver())
                        .subtract(balance.getLeaveUsed())
                        .subtract(balance.getPendingDays())
        );
    }

}
