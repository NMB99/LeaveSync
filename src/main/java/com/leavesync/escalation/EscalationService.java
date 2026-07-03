package com.leavesync.escalation;

import com.leavesync.email.EmailService;
import com.leavesync.entity.*;
import com.leavesync.enums.LeaveStatus;
import com.leavesync.enums.Role;
import com.leavesync.repository.*;
import com.leavesync.workingday.WorkingDayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class EscalationService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final AuditLogRepository auditLogRepository;
    private final WorkingDayService workingDayService;
    private final EmailService emailService;

    @Transactional
    public void sendDay3Reminders() {

        LocalDate today = LocalDate.now();
        LocalDate targetDate = workingDayService.subtractWorkingDays(today, 3);

        List<LeaveRequest> staleRequest = leaveRequestRepository
                .findByStatusAndEscalationStartDate(LeaveStatus.PENDING, targetDate);

        for (LeaveRequest request : staleRequest) {
            try {
                User requester = userRepository.findById(request.getUserId()).orElse(null);
                if (requester == null)
                    continue;

                LeaveType leaveType = leaveTypeRepository.findById(request.getLeaveTypeId()).orElse(null);
                if (leaveType == null)
                    continue;

                LeaveStatus previousStatus = request.getStatus();

                List<User> approvers = switch (request.getAssignedTo()) {
                    case MANAGER -> {
                        if (requester.getTeamId() == null) {
                            request.setAssignedTo(Role.HR);
                            leaveRequestRepository.save(request);
                            logIt(request, previousStatus, "Request reassigned to HR - manager no longer available");
                            yield userRepository.findAllByRoleAndIsActiveTrue(Role.HR);
                        }
                        Optional<Team> team = teamRepository.findById(requester.getTeamId());
                        if (team.isEmpty()) {
                            request.setAssignedTo(Role.HR);
                            leaveRequestRepository.save(request);
                            logIt(request, previousStatus, "Request reassigned to HR - manager no longer available");
                            yield userRepository.findAllByRoleAndIsActiveTrue(Role.HR);
                        }
                        Optional<User> manager = userRepository.findByIdAndIsActiveTrue(team.get().getManagerId());
                        if (manager.isEmpty()) {
                            request.setAssignedTo(Role.HR);
                            leaveRequestRepository.save(request);
                            logIt(request, previousStatus, "Request reassigned to HR - manager no longer available");
                            yield userRepository.findAllByRoleAndIsActiveTrue(Role.HR);
                        }
                        yield List.of(manager.get());
                    }
                    case HR -> userRepository.findAllByRoleAndIsActiveTrue(Role.HR);
                    case ADMIN -> userRepository.findAllByRoleAndIsActiveTrue(Role.ADMIN);
                    case EMPLOYEE -> List.of();
                };

                for (User approver : approvers) {
                    emailService.sendDay3ReminderEmail(
                            approver.getEmail(),
                            approver.getFirstName(),
                            requester.getFirstName() + " " + requester.getLastName(),
                            leaveType.getName(),
                            request.getStartDate().toString(),
                            request.getEndDate().toString()
                    );
                }
            }
            catch (Exception e) {
                log.error("Failed to send Day 3 reminder for request {}: {}", request.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void escalateDay5Requests() {

        LocalDate today = LocalDate.now();
        LocalDate targetDate = workingDayService.subtractWorkingDays(today, 5);

        List<LeaveRequest> staleRequests = leaveRequestRepository
                .findByStatusAndEscalationStartDate(LeaveStatus.PENDING, targetDate);

        for (LeaveRequest request : staleRequests) {
            try {
                User requester = userRepository.findById(request.getUserId()).orElse(null);
                if (requester == null)
                    continue;

                LeaveType leaveType = leaveTypeRepository.findById(request.getLeaveTypeId()).orElse(null);
                if (leaveType == null)
                    continue;

                LeaveStatus previousStatus = request.getStatus();

                switch (request.getAssignedTo()) {
                    case MANAGER, ADMIN -> {
                        request.setStatus(LeaveStatus.ESCALATED);
                        request.setAssignedTo(Role.HR);
                        leaveRequestRepository.save(request);

                        String message = "Leave request automatically escalated to HR after 5 working days without action";
                        logIt(request, previousStatus, message);

                        userRepository.findAllByRoleAndIsActiveTrue(Role.HR).forEach(hr ->
                                emailService.sendDay5EscalationEmail(
                                        hr.getEmail(),
                                        hr.getFirstName(),
                                        requester.getFirstName() + " " + requester.getLastName(),
                                        leaveType.getName(),
                                        request.getStartDate().toString(),
                                        request.getEndDate().toString()
                                )
                        );
                    }
                    case HR ->
                            userRepository.findAllByRoleAndIsActiveTrue(Role.HR).forEach(hr ->
                                    emailService.sendDay5EscalationEmail(
                                            hr.getEmail(),
                                            hr.getFirstName(),
                                            requester.getFirstName() + " " + requester.getLastName(),
                                            leaveType.getName(),
                                            request.getStartDate().toString(),
                                            request.getEndDate().toString()
                                    )
                            );
                    case EMPLOYEE -> {}
                }
            }
            catch (Exception e) {
                log.error("Failed to escalate request {}: {}", request.getId(), e.getMessage());
            }
        }
    }

    public void sendUrgentNotifications() {

        LocalDate today = LocalDate.now();
        LocalDate nextWorkingDay = workingDayService.addWorkingDays(today, 1);

        List<LeaveRequest> urgentRequests = leaveRequestRepository.findByStatusInAndStartDate(
                List.of(LeaveStatus.PENDING, LeaveStatus.ESCALATED),
                nextWorkingDay
        );

        for (LeaveRequest request : urgentRequests) {
            try {
                User requester = userRepository.findById(request.getUserId()).orElse(null);
                if (requester == null)
                    continue;

                LeaveType leaveType = leaveTypeRepository.findById(request.getLeaveTypeId()).orElse(null);
                if (leaveType == null)
                    continue;

                List<User> approvers = switch (request.getAssignedTo()) {
                    case MANAGER -> {
                        if (requester.getTeamId() == null) {
                            yield userRepository.findAllByRoleAndIsActiveTrue(Role.HR);
                        }
                        Optional<Team> team = teamRepository.findById(requester.getTeamId());
                        if (team.isEmpty()) {
                            yield userRepository.findAllByRoleAndIsActiveTrue(Role.HR);
                        }
                        Optional<User> manager = userRepository.findByIdAndIsActiveTrue(team.get().getManagerId());
                        yield manager.map(List::of).orElseGet(() -> userRepository.findAllByRoleAndIsActiveTrue(Role.HR));
                    }
                    case HR -> userRepository.findAllByRoleAndIsActiveTrue(Role.HR);
                    case ADMIN -> userRepository.findAllByRoleAndIsActiveTrue(Role.ADMIN);
                    case EMPLOYEE -> List.of();
                };

                for (User approver : approvers) {
                    emailService.sendUrgentLeaveNotificationEmail(
                            approver.getEmail(),
                            approver.getFirstName(),
                            requester.getFirstName() + " " + requester.getLastName(),
                            leaveType.getName(),
                            request.getStartDate().toString(),
                            request.getEndDate().toString()
                    );
                }
            }
            catch (Exception e) {
                log.error("Failed to send urgent notification for request {}: {}", request.getId(), e.getMessage());
            }
        }
    }

    private void logIt(LeaveRequest request, LeaveStatus previousStatus, String message) {
        AuditLog rerouteLog = new AuditLog();
        rerouteLog.setLeaveRequestId(request.getId());
        rerouteLog.setPreviousStatus(previousStatus);
        rerouteLog.setNewStatus(request.getStatus());
        rerouteLog.setAssignedTo(request.getAssignedTo());
        rerouteLog.setActionedBy(null);
        rerouteLog.setNotes(message);
        auditLogRepository.save(rerouteLog);
    }
}
