package com.leavesync.leavebalance;

import com.leavesync.entity.LeaveBalance;
import com.leavesync.entity.User;
import com.leavesync.exception.ResourceNotFoundException;
import com.leavesync.repository.LeaveBalanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
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

}
