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
import java.util.ArrayList;
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

                List<User> approvers = resolveApprover(requester, leaveType);
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
                request.setStatus(LeaveStatus.ESCALATED);
                leaveRequestRepository.save(request);

                AuditLog newLog = new AuditLog();
                newLog.setLeaveRequestId(request.getId());
                newLog.setPreviousStatus(previousStatus);
                newLog.setNewStatus(LeaveStatus.ESCALATED);
                newLog.setActionedBy(null);
                newLog.setNotes("Leave request automatically escalated to HR after 5 working days without action");
                auditLogRepository.save(newLog);

                List<User> hrUsers = userRepository.findAllByRoleAndIsActiveTrue(Role.HR);
                for (User hr : hrUsers) {
                    emailService.sendDay5EscalationEmail(
                            hr.getEmail(),
                            hr.getFirstName(),
                            requester.getFirstName() + " " + requester.getLastName(),
                            leaveType.getName(),
                            request.getStartDate().toString(),
                            request.getEndDate().toString()
                    );
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

                List<User> approvers;

                if (request.getStatus() == LeaveStatus.ESCALATED) {
                    approvers = userRepository.findAllByRoleAndIsActiveTrue(Role.HR);
                }
                else {
                    approvers = new ArrayList<>(resolveApprover(requester, leaveType));
                    List<User> hrUsers = userRepository.findAllByRoleAndIsActiveTrue(Role.HR);
                    approvers.addAll(hrUsers);
                    approvers = approvers.stream()
                            .distinct()
                            .toList();
                }

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

    private List<User> resolveApprover(User requester, LeaveType leaveType) {

        if (leaveType.isRequiresHrApproval() &&
                (requester.getRole() == Role.EMPLOYEE || requester.getRole() == Role.MANAGER)) {
            return userRepository.findAllByRoleAndIsActiveTrue(Role.HR);
        }

        return switch (requester.getRole()) {
            case EMPLOYEE -> {
                if (requester.getTeamId() == null) {
                    yield userRepository.findAllByRoleAndIsActiveTrue(Role.HR);
                }
                Optional<Team> team = teamRepository.findById(requester.getTeamId());
                if (team.isEmpty()) {
                    yield userRepository.findAllByRoleAndIsActiveTrue(Role.HR);
                }
                Optional<User> manager = userRepository.findByIdAndIsActiveTrue(team.get().getManagerId());
                yield manager
                        .map(List::of)
                        .orElseGet(() -> userRepository.findAllByRoleAndIsActiveTrue(Role.HR));
            }
            case MANAGER, ADMIN -> userRepository.findAllByRoleAndIsActiveTrue(Role.HR);
            case HR -> userRepository.findAllByRoleAndIsActiveTrue(Role.ADMIN);
        };
    }
}
