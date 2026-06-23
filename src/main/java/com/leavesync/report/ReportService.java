package com.leavesync.report;

import com.leavesync.common.PageResponse;
import com.leavesync.entity.*;
import com.leavesync.enums.LeaveStatus;
import com.leavesync.exception.ForbiddenException;
import com.leavesync.exception.ResourceNotFoundException;
import com.leavesync.repository.*;
import com.leavesync.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    public PageResponse<WhosOffResponse> getWhosOff(AuthenticatedUser principal, LocalDate date, Pageable pageable) {

        List<LeaveStatus> statuses = List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED);

        Page<LeaveRequest> requests = switch (principal.role()) {
            case EMPLOYEE -> throw new ForbiddenException("Employees are not authorized to view/access this resource");
            case MANAGER -> {
                List<UUID> teamsIds = teamRepository.findByManagerId(principal.userId())
                        .stream()
                        .map(Team::getId)
                        .toList();
                List<UUID> userIds = userRepository.findIdsByTeamIdIn(teamsIds);
                yield leaveRequestRepository
                        .findByStatusInAndDateWithinAndUserIdIn(statuses, date, userIds, pageable);
            }
            case HR, ADMIN -> leaveRequestRepository
                    .findByStatusInAndDateWithin(statuses, date, pageable);
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

        return PageResponse.from(
                requests.map(r -> WhosOffResponse.from(r, userMap.get(r.getUserId()), leaveTypeMap.get(r.getLeaveTypeId())))
        );
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

    public PageResponse<BalanceSummaryResponse> getBalanceSummary(AuthenticatedUser principal, int year, Pageable pageable) {

        Page<LeaveBalance> balances = switch (principal.role()) {
            case EMPLOYEE -> throw new ForbiddenException("Employees are not authorized to view/access this resource");
            case MANAGER -> {
                List<UUID> teamIds = teamRepository.findByManagerId(principal.userId())
                        .stream()
                        .map(Team::getId)
                        .toList();
                List<UUID> userIds = userRepository.findIdsByTeamIdIn(teamIds);
                yield leaveBalanceRepository
                        .findByUserIdInAndYear(userIds, year, pageable);
            }
            case HR, ADMIN -> leaveBalanceRepository.findByYear(year, pageable);
        };

        Map<UUID, User> userMap = userRepository.findAllById(
                balances.stream()
                        .map(LeaveBalance::getUserId).distinct()
                        .toList()
        ).stream().collect(Collectors.toMap(User::getId, Function.identity()));

        return PageResponse.from(
                balances.map(b -> BalanceSummaryResponse.from(b, userMap.get(b.getUserId())))
        );
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

        Map<UUID, List<AuditLog>> auditLogMap = logs.stream()
                .collect(Collectors.groupingBy(AuditLog::getLeaveRequestId));

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

        return requests.stream()
                .map(request -> {
                    User employee = employeeMap.get(request.getUserId());
                    LeaveType leaveType = leaveTypeMap.get(request.getLeaveTypeId());
                    List<AuditLog> auditLogs = auditLogMap.getOrDefault(request.getId(), List.of());
                    return LeaveHistoryResponse.from(
                            request, employee, leaveType, auditLogs, actionedByMap
                    );
                })
                .toList();
    }

    public PageResponse<LeaveHistoryResponse> getLeaveHistory(
            AuthenticatedUser principal, UUID userId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {

        Page<LeaveRequest> requests = switch (principal.role()) {
            case EMPLOYEE, MANAGER -> throw new ForbiddenException("You are not authorized to view/access this resource");
            case HR, ADMIN -> userId != null
                    ? leaveRequestRepository.findByUserId(userId, pageable)
                    : leaveRequestRepository.findAll(pageable);
        };

        List<UUID> requestIds = requests.stream()
                .map(LeaveRequest::getId)
                .toList();

        if (requestIds.isEmpty())
            return new PageResponse<>(List.of(), requests.getNumber(), requests.getSize(), 0L, 0, true, true);

        List<AuditLog> logs = auditLogRepository.findByLeaveRequestIdInAndDateRange(
                requestIds, startDate, endDate
        );

        Map<UUID, List<AuditLog>> auditLogMap = logs.stream()
                .collect(Collectors.groupingBy(AuditLog::getLeaveRequestId));

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

        return PageResponse.from(
                requests
                .map(request -> {
                    User employee = employeeMap.get(request.getUserId());
                    LeaveType leaveType = leaveTypeMap.get(request.getLeaveTypeId());
                    List<AuditLog> auditLogs = auditLogMap.getOrDefault(request.getId(), List.of());
                    return LeaveHistoryResponse.from(
                            request, employee, leaveType, auditLogs, actionedByMap
                    );
                })
        );
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
                .map(user -> AbsencePatternResponse.from(
                        userMap.get(user.getKey()),
                        user.getValue()
                ))
                .toList();
    }

    public PageResponse<AbsencePatternResponse> getAbsencePatterns(
            AuthenticatedUser principal, LocalDate startDate, LocalDate endDate, Pageable pageable) {

        LeaveType sickLeaveType = leaveTypeRepository.findByCode("SICK")
                .orElseThrow(() -> new ResourceNotFoundException("Sick leave type not found"));

        List<LeaveStatus> statuses = List.of(LeaveStatus.APPROVED, LeaveStatus.REJECTED);

        Page<UUID> userIdPage = switch (principal.role()) {
            case EMPLOYEE, MANAGER -> throw new ForbiddenException("You are not authorized to view/access this resource");
            case HR, ADMIN -> leaveRequestRepository.findDistinctUserIdsByLeaveTypeIdAndStatusInAndDateRange(
                    sickLeaveType.getId(), statuses, startDate, endDate, pageable
            );
        };

        List<UUID> userIds = userIdPage.getContent();

        if (userIdPage.isEmpty())
            return new PageResponse<>(List.of(), userIdPage.getNumber(), userIdPage.getSize(), 0L, 0, true, true);

        List<LeaveRequest> sickRequests = leaveRequestRepository.findByLeaveTypeIdAndStatusInAndDateRangeAndUserIdIn(
                sickLeaveType.getId(), statuses, startDate, endDate, userIds
        );

        Map<UUID, List<LeaveRequest>> groupByUser = sickRequests.stream()
                .collect(Collectors.groupingBy(LeaveRequest::getUserId));

        Map<UUID, User> userMap = userRepository.findAllById(userIds)
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));

        List<AbsencePatternResponse> content = userIds.stream()
                .map(userId -> AbsencePatternResponse.from(
                        userMap.get(userId),
                        groupByUser.getOrDefault(userId, List.of())
                ))
                .toList();

        return new PageResponse<>(
                content,
                userIdPage.getNumber(),
                userIdPage.getSize(),
                userIdPage.getTotalElements(),
                userIdPage.getTotalPages(),
                userIdPage.isFirst(),
                userIdPage.isLast()
        );
    }
}
