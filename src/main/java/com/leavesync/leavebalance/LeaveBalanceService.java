package com.leavesync.leavebalance;

import com.leavesync.common.LeaveBalancePageResponse;
import com.leavesync.entity.LeaveBalance;
import com.leavesync.entity.Team;
import com.leavesync.entity.User;
import com.leavesync.enums.Role;
import com.leavesync.exception.ForbiddenException;
import com.leavesync.exception.ResourceNotFoundException;
import com.leavesync.repository.LeaveBalanceRepository;
import com.leavesync.repository.TeamRepository;
import com.leavesync.repository.UserRepository;
import com.leavesync.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class LeaveBalanceService {

    private final LeaveBalanceRepository leaveBalanceRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;

    @Transactional
    public void createLeaveBalanceForYear(User user, int year) {

        Optional<LeaveBalance> existingLeaveBalance = leaveBalanceRepository.findByUserIdAndYear(user.getId(), year);

        if (existingLeaveBalance.isEmpty()) {
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

            leaveBalanceRepository.save(newLeaveBalance);
        }
    }

    public LeaveBalanceResponse getMyLeaveBalance(AuthenticatedUser principal, int year) {

        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.userId().toString()));

        LeaveBalance balance = leaveBalanceRepository.findByUserIdAndYear(principal.userId(), year)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveBalance", "userId/year", principal.userId() + "/" + year));

        return LeaveBalanceResponse.from(balance, user);
    }

    public LeaveBalanceResponse getLeaveBalanceById(AuthenticatedUser principal, UUID userId, int year) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId.toString()));

        if (principal.role() == Role.MANAGER) {
            if (user.getTeamId() == null) {
                throw new ForbiddenException("You are not authorized to view this resource");
            }
            Team team = teamRepository.findById(user.getTeamId())
                    .orElseThrow(() -> new ResourceNotFoundException("Team", "id", user.getTeamId().toString()));
            if (!team.getManagerId().equals(principal.userId())) {
                throw new ForbiddenException("You are not authorized to view this resource");
            }
        }

        LeaveBalance balance = leaveBalanceRepository.findByUserIdAndYear(userId, year)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveBalance", "userId/year", userId + "/" + year));

        return LeaveBalanceResponse.from(balance, user);
    }

    public LeaveBalancePageResponse getTeamsLeaveBalance(AuthenticatedUser principal, int year, Pageable pageable) {

        List<UUID> teamIds = teamRepository.findByManagerId(principal.userId())
                .stream()
                .map(Team::getId)
                .toList();

        List<UUID> userIds = userRepository.findIdsByTeamIdIn(teamIds);

        Page<LeaveBalance> balances = leaveBalanceRepository.findByUserIdInAndYear(userIds, year, pageable);

        Map<UUID, User> userMap = userRepository.findAllById(balances.map(LeaveBalance::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));

        return LeaveBalancePageResponse.from(balances.map(balance ->
                LeaveBalanceResponse.from(balance, userMap.get(balance.getUserId()))
        ));
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
