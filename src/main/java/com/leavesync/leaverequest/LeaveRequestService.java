package com.leavesync.leaverequest;

import com.leavesync.common.PageResponse;
import com.leavesync.email.EmailService;
import com.leavesync.entity.*;
import com.leavesync.enums.LeaveStatus;
import com.leavesync.enums.Role;
import com.leavesync.exception.ForbiddenException;
import com.leavesync.exception.InvalidLeaveRequestException;
import com.leavesync.exception.LeaveRequestStateException;
import com.leavesync.exception.ResourceNotFoundException;
import com.leavesync.leavebalance.LeaveBalanceService;
import com.leavesync.repository.*;
import com.leavesync.security.AuthenticatedUser;
import com.leavesync.workingday.WorkingDayService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final WorkingDayService workingDayService;
    private final EmailService emailService;
    private final LeaveBalanceService leaveBalanceService;

    @Transactional
    public LeaveRequestResponse submit(UUID userId, SubmitLeaveRequest request) {

        User submitter = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id:", userId.toString()));

        if (submitter.getRole() == Role.ADMIN) {
            throw new ForbiddenException("Admin accounts cannot submit leave requests");
        }

        LeaveType leaveType = leaveTypeRepository.findById(request.leaveTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("LeaveType", "id:", request.leaveTypeId().toString()));

        LocalDate today = LocalDate.now();

        boolean sickLeave = leaveType.getCode().equals("SICK");
        if (!sickLeave && request.startDate().isBefore(today)) {
            throw new InvalidLeaveRequestException("Start date cannot be in the past");
        }

        if (sickLeave && request.startDate().isAfter(today)) {
            throw new InvalidLeaveRequestException("Sick leave cannot be submitted for a future date");
        }

        if (leaveType.isRequiresReason()
                && (request.reason() == null || request.reason().isBlank())) {
            throw new InvalidLeaveRequestException("A reason is required for " + leaveType.getName() + " leave");
        }

        if (request.endDate().isBefore(request.startDate())) {
            throw new InvalidLeaveRequestException("End date must be after start date");
        }

        BigDecimal requestedDays = workingDayService
                .countWorkingDays(request.startDate(), request.endDate());

        if (request.isHalfDay()) {
            if (!request.startDate().isEqual(request.endDate())) {
                throw new InvalidLeaveRequestException("Half-day leave must be a single day");
            }
            requestedDays = new BigDecimal("0.5");
        }

        if (requestedDays.compareTo(BigDecimal.ZERO) == 0) {
            throw new InvalidLeaveRequestException("Leave request must span at least one working day");
        }

        List<LeaveRequest> overlappingRequests = leaveRequestRepository.findOverlappingRequests(
                userId,
                List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED),
                request.startDate(),
                request.endDate()
        );
        boolean overlapWarning = !overlappingRequests.isEmpty();

        boolean noticePeriodWarning = false;
        if (leaveType.getCode().equals("ANNUAL")) {
            BigDecimal noticeDays = workingDayService.countWorkingDays(today.plusDays(1), request.startDate());
            noticePeriodWarning = noticeDays.compareTo(new BigDecimal("5")) < 0;
        }
        else if (leaveType.getCode().equals("UNPAID")) {
            BigDecimal noticeDays = workingDayService.countWorkingDays(today.plusDays(1), request.startDate());
            noticePeriodWarning = noticeDays.compareTo(new BigDecimal("10")) < 0;
        }

        boolean balanceWarning = false;
        LeaveBalance balance = null;

        if (leaveType.isRequiresBalanceTracking()) {
            int year = request.startDate().getYear();
            int currentYear = LocalDate.now().getYear();

            if (leaveBalanceRepository.findByUserIdAndYear(userId, year).isEmpty() && year == currentYear + 1) {
                leaveBalanceService.createLeaveBalanceForYear(submitter, year);
            }

            balance = leaveBalanceRepository.findByUserIdAndYear(userId, year)
                    .orElseThrow(() -> new ResourceNotFoundException("No leave balance found for user in " + year));

            BigDecimal remaining = balance.getTotalEntitlement()
                    .add(balance.getCarriedOver())
                    .subtract(balance.getLeaveUsed())
                    .subtract(balance.getPendingDays());

            balanceWarning = requestedDays.compareTo(remaining) > 0;
        }

        ApproverResult approverResult = findLeaveRequestApprover(submitter, leaveType);

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setUserId(userId);
        leaveRequest.setLeaveTypeId(request.leaveTypeId());
        leaveRequest.setStartDate(request.startDate());
        leaveRequest.setEndDate(request.endDate());
        leaveRequest.setHalfDay(request.isHalfDay());
        leaveRequest.setReason(request.reason());
        leaveRequest.setTotalWorkingDays(requestedDays);
        leaveRequest.setStatus(
                approverResult.rerouted() ? LeaveStatus.REROUTED_TO_HR : LeaveStatus.PENDING
        );
        leaveRequest.setAssignedTo(approverResult.assignedTo());
        leaveRequest.setNoticePeriodWarning(noticePeriodWarning);
        leaveRequest.setOverlapWarning(overlapWarning);
        leaveRequest.setEscalationStartDate(workingDayService.normaliseToWorkingDay(today));

        leaveRequestRepository.save(leaveRequest);

        AuditLog newLog = new AuditLog();
        newLog.setLeaveRequestId(leaveRequest.getId());
        newLog.setPreviousStatus(null);
        newLog.setNewStatus(
                approverResult.rerouted() ? LeaveStatus.REROUTED_TO_HR : LeaveStatus.PENDING
        );
        newLog.setAssignedTo(approverResult.assignedTo());
        newLog.setActionedBy(null);
        newLog.setNotes(
                approverResult.rerouted()
                        ? "Leave request automatically rerouted to HR - no active manager found"
                        : "Leave request submitted by " + submitter.getFirstName() + " " + submitter.getLastName()
        );
        auditLogRepository.save(newLog);

        if (leaveType.isRequiresBalanceTracking() && balance != null) {
            balance.setPendingDays(balance.getPendingDays().add(requestedDays));
            leaveBalanceRepository.save(balance);
        }

        final BigDecimal finalRequestedDays = requestedDays;

        for (User approver : approverResult.approvers()){
            emailService.sendLeaveRequestEmailToApprover(
                    approver.getEmail(),
                    approver.getFirstName(),
                    submitter.getFirstName() + " " + submitter.getLastName(),
                    leaveType.getName(),
                    request.startDate().toString(),
                    request.endDate().toString(),
                    finalRequestedDays
            );
        }

        return LeaveRequestResponse.from(leaveRequest, submitter, leaveType, balanceWarning);
    }

    public PageResponse<LeaveRequestResponse> getMyLeaveRequests(UUID userId, Pageable pageable) {
        Page<LeaveRequest> requests = leaveRequestRepository.findByUserId(userId, pageable);

        Map<UUID, User> userMap = userRepository
                .findAllById(requests.map(LeaveRequest::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        Map<UUID, LeaveType> leaveTypeMap = leaveTypeRepository
                .findAllById(requests.map(LeaveRequest::getLeaveTypeId).stream().toList())
                .stream()
                .collect(Collectors.toMap(LeaveType::getId, Function.identity()));

        return PageResponse.from(requests.map(leaveRequest -> {
            User requestOwner = userMap.get(leaveRequest.getUserId());
            LeaveType leaveType = leaveTypeMap.get(leaveRequest.getLeaveTypeId());
            return LeaveRequestResponse.from(leaveRequest, requestOwner, leaveType);
        }));
    }

    public PageResponse<LeaveRequestResponse> getLeaveRequests(AuthenticatedUser principal, Pageable pageable) {

        Page<LeaveRequest> requests = switch (principal.role()) {
            case EMPLOYEE -> leaveRequestRepository.findByUserId(principal.userId(), pageable);
            case MANAGER -> {
                List<UUID> teamIds = teamRepository.findByManagerId(principal.userId())
                        .stream()
                        .map(Team::getId)
                        .toList();
                List<UUID> teamMemberIds = userRepository.findIdsByTeamIdIn(teamIds);
                yield  leaveRequestRepository.findByAssignedToAndStatusInAndUserIdIn(
                        Role.MANAGER,
                        List.of(LeaveStatus.PENDING),
                        teamMemberIds,
                        pageable
                );
            }
            case HR -> leaveRequestRepository.findByAssignedToAndStatusIn(
                    Role.HR,
                    List.of(LeaveStatus.PENDING, LeaveStatus.REROUTED_TO_HR, LeaveStatus.ESCALATED),
                    pageable
            );
            case ADMIN -> leaveRequestRepository.findByAssignedToAndStatusIn(
                    Role.ADMIN,
                    List.of(LeaveStatus.PENDING, LeaveStatus.REROUTED_TO_HR, LeaveStatus.ESCALATED),
                    pageable
            );
        };

        Map<UUID, User> userMap = userRepository
                .findAllById(requests.map(LeaveRequest::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        Map<UUID, LeaveType> leaveTypeMap = leaveTypeRepository
                .findAllById(requests.map(LeaveRequest::getLeaveTypeId).stream().toList())
                .stream()
                .collect(Collectors.toMap(LeaveType::getId, Function.identity()));

        return PageResponse.from(requests.map(leaveRequest -> {
            User requestOwner = userMap.get(leaveRequest.getUserId());
            LeaveType leaveType = leaveTypeMap.get(leaveRequest.getLeaveTypeId());
            return LeaveRequestResponse.from(leaveRequest, requestOwner, leaveType);
        }));
    }

    public LeaveRequestResponse getLeaveRequestById(AuthenticatedUser principal, UUID requestId) {

        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", "id:", requestId.toString()));

        User requestOwner = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id:", request.getUserId().toString()));

        switch (principal.role()) {
            case EMPLOYEE -> {
                if (!request.getUserId().equals(principal.userId())) {
                    throw new ForbiddenException("You are not authorized to view this leave request");
                }
            }
            case MANAGER -> {
                if (request.getUserId().equals(principal.userId())) {
                    break;
                }
                List<UUID> teamIds = teamRepository.findByManagerId(principal.userId())
                        .stream()
                        .map(Team::getId)
                        .toList();
                if (requestOwner.getTeamId() == null || !teamIds.contains(requestOwner.getTeamId())) {
                    throw new ForbiddenException("You are not authorized to view this leave request outside your team");
                }
            }
            case HR, ADMIN -> {}
        }

        LeaveType leaveType = leaveTypeRepository.findById(request.getLeaveTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("LeaveType", "id:", request.getLeaveTypeId().toString()));

        return LeaveRequestResponse.from(request, requestOwner, leaveType);
    }

    @Transactional
    public LeaveRequestResponse cancelLeaveRequest(AuthenticatedUser principal, UUID requestId) {

        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", "id:", requestId.toString()));

        User requestOwner = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id:", request.getUserId().toString()));

        if (!request.getUserId().equals(principal.userId())) {
            throw new ForbiddenException("You can only cancel your own leave request");
        }

        if (request.getStatus() != LeaveStatus.PENDING
                && request.getStatus() != LeaveStatus.ESCALATED
                && request.getStatus() != LeaveStatus.REROUTED_TO_HR) {
            throw new LeaveRequestStateException("Only PENDING, ESCALATED OR REROUTED_TO_HR leave requests can be cancelled");
        }

        LeaveStatus previousStatus = request.getStatus();

        request.setStatus(LeaveStatus.CANCELLED);
        request.setActionedBy(principal.userId());
        leaveRequestRepository.save(request);

        LeaveType leaveType = leaveTypeRepository.findById(request.getLeaveTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("LeaveType", "id:", request.getLeaveTypeId().toString()));

        if (leaveType.isRequiresBalanceTracking()) {
            int year = request.getStartDate().getYear();
            LeaveBalance balance = leaveBalanceRepository.findByUserIdAndYear(request.getUserId(), year)
                    .orElseThrow(() -> new ResourceNotFoundException("Leave balance not found for user in " + year));
            balance.setPendingDays(balance.getPendingDays().subtract(request.getTotalWorkingDays()));
            leaveBalanceRepository.save(balance);
        }

        AuditLog newLog = new AuditLog();
        newLog.setLeaveRequestId(request.getId());
        newLog.setPreviousStatus(previousStatus);
        newLog.setNewStatus(LeaveStatus.CANCELLED);
        newLog.setAssignedTo(principal.role());
        newLog.setActionedBy(principal.userId());
        newLog.setNotes("Leave request cancelled by " + principal.email());
        auditLogRepository.save(newLog);

        return LeaveRequestResponse.from(request, requestOwner, leaveType);
    }

    @Transactional
    public LeaveRequestResponse approveLeaveRequest(AuthenticatedUser principal, UUID requestId) {

        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", "id:", requestId.toString()));

        LeaveType leaveType = leaveTypeRepository.findById(request.getLeaveTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("LeaveType", "id:", request.getLeaveTypeId().toString()));

        validateApprovalPermission(principal, request, leaveType);

        LeaveStatus previousStatus = request.getStatus();

        request.setStatus(LeaveStatus.APPROVED);
        request.setActionedBy(principal.userId());
        leaveRequestRepository.save(request);

        if (leaveType.isRequiresBalanceTracking()) {
            int year = request.getStartDate().getYear();
            LeaveBalance balance = leaveBalanceRepository.findByUserIdAndYear(request.getUserId(), year)
                    .orElseThrow(() -> new ResourceNotFoundException("Leave balance not found for user in " + year));
            balance.setLeaveUsed(balance.getLeaveUsed().add(request.getTotalWorkingDays()));
            balance.setPendingDays(balance.getPendingDays().subtract(request.getTotalWorkingDays()));
            leaveBalanceRepository.save(balance);
        }

        AuditLog newLog = new AuditLog();
        newLog.setLeaveRequestId(request.getId());
        newLog.setPreviousStatus(previousStatus);
        newLog.setNewStatus(LeaveStatus.APPROVED);
        newLog.setAssignedTo(principal.role());
        newLog.setActionedBy(principal.userId());
        newLog.setNotes("Leave request approved by " + principal.email());
        auditLogRepository.save(newLog);

        User requestOwner = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id:", request.getUserId().toString()));

        emailService.sendLeaveApprovalEmail(
                requestOwner.getEmail(),
                requestOwner.getFirstName(),
                leaveType.getName(),
                request.getStartDate().toString(),
                request.getEndDate().toString()
        );

        if (leaveType.isRequiresHrApproval() && principal.role() == Role.HR
                && requestOwner.getRole() == Role.EMPLOYEE && requestOwner.getTeamId() != null) {
            teamRepository.findById(requestOwner.getTeamId()).ifPresent(team ->
                userRepository.findByIdAndIsActiveTrue(team.getManagerId()).ifPresent(manager ->
                    emailService.sendManagerLeaveApprovalNotificationEmail(
                            manager.getEmail(),
                            manager.getFirstName(),
                            requestOwner.getFirstName() + " " + requestOwner.getLastName(),
                            leaveType.getName(),
                            request.getStartDate().toString(),
                            request.getEndDate().toString()
                    )
                )
            );
        }

        return LeaveRequestResponse.from(request, requestOwner, leaveType);
    }

    @Transactional
    public LeaveRequestResponse rejectLeaveRequest(AuthenticatedUser principal, UUID requestId, RejectLeaveRequest rejectRequest) {

        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", "id:", requestId.toString()));

        LeaveType leaveType = leaveTypeRepository.findById(request.getLeaveTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("LeaveType", "id:", request.getLeaveTypeId().toString()));

        validateApprovalPermission(principal, request, leaveType);

        LeaveStatus previousStatus = request.getStatus();

        request.setStatus(LeaveStatus.REJECTED);
        request.setActionedBy(principal.userId());
        leaveRequestRepository.save(request);

        if (leaveType.isRequiresBalanceTracking()) {
            int year = request.getStartDate().getYear();
            LeaveBalance balance = leaveBalanceRepository.findByUserIdAndYear(request.getUserId(), year)
                    .orElseThrow(() -> new ResourceNotFoundException("Leave balance not found for user in " + year));

            balance.setPendingDays(balance.getPendingDays().subtract(request.getTotalWorkingDays()));
            leaveBalanceRepository.save(balance);
        }

        AuditLog newLog = new AuditLog();
        newLog.setLeaveRequestId(request.getId());
        newLog.setPreviousStatus(previousStatus);
        newLog.setNewStatus(LeaveStatus.REJECTED);
        newLog.setAssignedTo(principal.role());
        newLog.setActionedBy(principal.userId());
        newLog.setNotes("Leave request rejected by " + principal.email() + ". Reason: " + rejectRequest.reason());
        auditLogRepository.save(newLog);

        User requestOwner = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id:", request.getUserId().toString()));

        emailService.sendLeaveRejectionEmail(
                requestOwner.getEmail(),
                requestOwner.getFirstName(),
                leaveType.getName(),
                request.getStartDate().toString(),
                request.getEndDate().toString(),
                rejectRequest.reason()
        );

        return LeaveRequestResponse.from(request, requestOwner, leaveType);
    }

    private ApproverResult findLeaveRequestApprover(User submitter, LeaveType leaveType) {

        if (leaveType.isRequiresHrApproval() && (submitter.getRole() == Role.EMPLOYEE || submitter.getRole() == Role.MANAGER)) {
            return new ApproverResult(userRepository.findAllByRoleAndIsActiveTrue(Role.HR), false, Role.HR);
        }

        return switch (submitter.getRole()) {
            case EMPLOYEE -> {
                if (submitter.getTeamId() == null) {
                    yield new ApproverResult(userRepository.findAllByRoleAndIsActiveTrue(Role.HR), true, Role.HR);
                }
                Optional<Team> team = teamRepository.findById(submitter.getTeamId());
                if (team.isEmpty()) {
                    yield new ApproverResult(userRepository.findAllByRoleAndIsActiveTrue(Role.HR), true, Role.HR);
                }

                Optional<User> manager = userRepository.findByIdAndIsActiveTrue(team.get().getManagerId());
                yield manager
                        .map(m -> new ApproverResult(List.of(m), false, Role.MANAGER))
                        .orElseGet(() -> new ApproverResult(userRepository.findAllByRoleAndIsActiveTrue(Role.HR), true, Role.HR));
            }
            case MANAGER -> new ApproverResult(userRepository.findAllByRoleAndIsActiveTrue(Role.HR), false, Role.HR);
            case HR -> {
                List<User> otherHrUsers = userRepository.findAllByRoleAndIsActiveTrue(Role.HR)
                        .stream()
                        .filter(u -> !u.getId().equals(submitter.getId()))
                        .toList();
                yield  otherHrUsers.isEmpty()
                        ? new ApproverResult(userRepository.findAllByRoleAndIsActiveTrue(Role.ADMIN), false, Role.ADMIN)
                        : new ApproverResult(otherHrUsers, false, Role.HR);
                }
            case ADMIN -> throw new ForbiddenException("Admin accounts cannot submit leave requests");
        };
    }

    private void validateApprovalPermission(AuthenticatedUser approver, LeaveRequest request, LeaveType leaveType) {

        if (request.getUserId().equals(approver.userId())) {
            throw new ForbiddenException("You cannot action your own leave request");
        }

        if (request.getStatus() != LeaveStatus.PENDING
                && request.getStatus() != LeaveStatus.ESCALATED
                && request.getStatus() != LeaveStatus.REROUTED_TO_HR) {
            throw new LeaveRequestStateException("Only PENDING, ESCALATED or REROUTED_TO_HR leave requests can be actioned");
        }

        User requestOwner = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id:", request.getUserId().toString()));

        switch (approver.role()) {
            case MANAGER -> {
                if (leaveType.isRequiresHrApproval()) {
                    throw new ForbiddenException("This leave type requires HR approval");
                }
                List<UUID> teamIds = teamRepository.findByManagerId(approver.userId())
                        .stream()
                        .map(Team::getId)
                        .toList();
                if (requestOwner.getTeamId() == null || !teamIds.contains(requestOwner.getTeamId())) {
                    throw new ForbiddenException("You can only action leave requests for your team");
                }
            }
            case HR -> {}
            case ADMIN -> {
                if (requestOwner.getRole() != Role.HR) {
                    throw new ForbiddenException("Admins can only action HR leave requests");
                }
            }
            case EMPLOYEE -> throw new ForbiddenException("You dont have permission to action leave requests");
        }
    }
}
