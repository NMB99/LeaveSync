package com.leavesync.report;

import com.leavesync.entity.*;
import com.leavesync.enums.LeaveStatus;
import com.leavesync.exception.ForbiddenException;
import com.leavesync.exception.ResourceNotFoundException;
import com.leavesync.repository.*;
import com.leavesync.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final AuditLogRepository auditLogRepository;

    public List<WhosOffResponse> getWhosOff(AuthenticatedUser principal, LocalDate date) {

        List<LeaveStatus> statuses = List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED);

        List<LeaveRequest> requests = switch (principal.role()) {
            case EMPLOYEE -> throw new ForbiddenException("Employees are not authorized to view/access this resource");
            case MANAGER -> {
                List<UUID> teamsIds = teamRepository.findByManagerId(principal.userId())
                        .stream()
                        .map(Team::getId)
                        .toList();
                List<UUID> userIds = userRepository.findIdsByTeamIdIn(teamsIds);
                yield leaveRequestRepository
                        .findByStatusInAndDateWithinAndUserIdIn(statuses, date, userIds);
            }
            case HR, ADMIN -> leaveRequestRepository
                        .findByStatusInAndDateWithin(statuses, date);
        };

        Map<UUID, User> userMap = userRepository.findAllById(
                requests.stream()
                        .map(LeaveRequest::getUserId).distinct()
                        .toList()
        ).stream().collect(Collectors.toMap(User::getId, Function.identity()));

        Map<UUID, LeaveType> leaveTypeMap = leaveTypeRepository.findAllById(
                requests.stream()
                        .map(LeaveRequest::getLeaveTypeId).distinct()
                        .toList()
        ).stream().collect(Collectors.toMap(LeaveType::getId, Function.identity()));

        return requests.stream()
                .map(rq -> WhosOffResponse.from(rq, userMap.get(rq.getUserId()), leaveTypeMap.get(rq.getLeaveTypeId())))
                .toList();
    }

    public List<BalanceSummaryResponse> getBalanceSummary(AuthenticatedUser principal, int year) {

        List<LeaveBalance> balances = switch (principal.role()) {
            case EMPLOYEE -> throw new ForbiddenException("Employees are not authorized to view/access this resource");
            case MANAGER -> {
                List<UUID> teamIds = teamRepository.findByManagerId(principal.userId())
                        .stream()
                        .map(Team::getId)
                        .toList();
                List<UUID> userIds = userRepository.findIdsByTeamIdIn(teamIds);
                yield leaveBalanceRepository
                        .findByUserIdInAndYear(userIds, year);
            }
            case HR, ADMIN -> leaveBalanceRepository.findByYear(year);
        };

        Map<UUID, User> userMap = userRepository.findAllById(
                balances.stream()
                        .map(LeaveBalance::getUserId).distinct()
                        .toList()
        ).stream().collect(Collectors.toMap(User::getId, Function.identity()));

        return balances.stream()
                .map(b -> BalanceSummaryResponse.from(b, userMap.get(b.getUserId())))
                .toList();
    }

    public List<LeaveHistoryResponse> getLeaveHistory(
            AuthenticatedUser principal, UUID userId, LocalDateTime startDate, LocalDateTime endDate) {

        List<LeaveRequest> requests = switch (principal.role()) {
            case EMPLOYEE, MANAGER -> throw new ForbiddenException("You are not authorized to view/access this resource");
            case HR, ADMIN -> userId != null
                    ? leaveRequestRepository.findByUserId(userId)
                    : leaveRequestRepository.findAll();
        };

        List<UUID> requestIds = requests.stream()
                .map(LeaveRequest::getId)
                .toList();

        if (requestIds.isEmpty())
            return List.of();

        List<AuditLog> logs = auditLogRepository.findByLeaveRequestIdInAndDateRange(
                requestIds, startDate, endDate
        );

        Map<UUID, LeaveRequest> requestMap = requests.stream()
                .collect(Collectors.toMap(LeaveRequest::getId, Function.identity()));

        Map<UUID, User> employeeMap = userRepository.findAllById(
                requests.stream()
                        .map(LeaveRequest::getUserId).distinct()
                        .toList()
        ).stream().collect(Collectors.toMap(User::getId, Function.identity()));

        Map<UUID, LeaveType> leaveTypeMap = leaveTypeRepository.findAllById(
                requests.stream()
                        .map(LeaveRequest::getLeaveTypeId).distinct()
                        .toList()
        ).stream().collect(Collectors.toMap(LeaveType::getId, Function.identity()));

        List<UUID> actionedByIds = logs.stream()
                .map(AuditLog::getActionedBy)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, User> actionedByMap = userRepository.findAllById(actionedByIds)
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));

        return logs.stream()
                .filter(log -> requestMap.containsKey(log.getLeaveRequestId()))
                .map(log -> {
                    LeaveRequest request = requestMap.get(log.getLeaveRequestId());
                    return LeaveHistoryResponse.from(
                            log,
                            request,
                            employeeMap.get(request.getUserId()),
                            leaveTypeMap.get(request.getLeaveTypeId()),
                            actionedByMap.get(log.getActionedBy())
                    );
                })
                .toList();
    }

    public List<AbsencePatternResponse> getAbsencePatterns(
            AuthenticatedUser principal, LocalDate startDate, LocalDate endDate) {

        LeaveType sickLeaveType = leaveTypeRepository.findByCode("SICK")
                .orElseThrow(() -> new ResourceNotFoundException("Sick leave type not found"));

        List<LeaveStatus> statuses = List.of(LeaveStatus.APPROVED, LeaveStatus.REJECTED);

        List<LeaveRequest> sickRequests = switch (principal.role()) {
            case EMPLOYEE, MANAGER -> throw new ForbiddenException("You are not authorized to view/access this resource");
            case HR, ADMIN -> leaveRequestRepository.findByLeaveTypeIdAndStatusInAndDateRange(
                    sickLeaveType.getId(), statuses, startDate, endDate
            );
        };

        Map<UUID, List<LeaveRequest>> groupUserRequests = sickRequests.stream()
                .collect(Collectors.groupingBy(LeaveRequest::getUserId));

        Map<UUID, User> userMap = userRepository.findAllById(groupUserRequests.keySet())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));

        return groupUserRequests.entrySet().stream()
                .map(entry -> {
                    UUID userId = entry.getKey();
                    List<LeaveRequest> requests = entry.getValue();
                    User user = userMap.get(userId);

                    List<AbsencePatternResponse.SickLeaveInstance> instances = requests.stream()
                            .map(rq -> new AbsencePatternResponse.SickLeaveInstance(
                                    rq.getId(), rq.getStartDate(), rq.getEndDate(),
                                    rq.getTotalWorkingDays(), rq.getStatus()
                            ))
                            .toList();
                    return new AbsencePatternResponse(
                            userId,
                            user.getFirstName() + " " + user.getLastName(),
                            instances.size(),
                            instances
                    );
                })
                .toList();
    }
}
