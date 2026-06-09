package com.leavesync.leaverequest;

import com.leavesync.entity.*;
import com.leavesync.enums.LeaveStatus;
import com.leavesync.enums.Role;
import com.leavesync.exception.InvalidLeaveRequestException;
import com.leavesync.exception.ResourceNotFoundException;
import com.leavesync.repository.*;
import com.leavesync.user.EmailService;
import com.leavesync.workingday.WorkingDayService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final WorkingDayService workingDayService;
    private final EmailService emailService;

    @Transactional
    public LeaveRequestResponse submit(UUID userId, SubmitLeaveRequest request) {

        User submitter = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id:", userId.toString()));

        LeaveType leaveType = leaveTypeRepository.findById(request.leaveTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("LeaveType", "id:", request.leaveTypeId().toString()));

        LocalDate today = LocalDate.now();

        boolean sickLeave = leaveType.getName().equalsIgnoreCase("SICK");
        if (!sickLeave && request.startDate().isBefore(today)) {
            throw new InvalidLeaveRequestException("Start date cannot be in the past");
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
        if (leaveType.getName().equalsIgnoreCase("ANNUAL")) {
            BigDecimal noticeDays = workingDayService.countWorkingDays(today.plusDays(1), request.startDate());
            noticePeriodWarning = noticeDays.compareTo(new BigDecimal("5")) < 0;
        }
        else if (leaveType.getName().equalsIgnoreCase("UNPAID")) {
            BigDecimal noticeDays = workingDayService.countWorkingDays(today.plusDays(1), request.startDate());
            noticePeriodWarning = noticeDays.compareTo(new BigDecimal("10")) < 0;
        }

        boolean balanceWarning = false;
        LeaveBalance balance = null;

        if (leaveType.isRequiresBalanceTracking()) {
            int year = request.startDate().getYear();
            balance = leaveBalanceRepository.findByUserIdAndYear(userId, year)
                    .orElseThrow(() -> new ResourceNotFoundException("No leave balance found for user in " + year));

            BigDecimal remaining = balance.getTotalEntitlement()
                    .add(balance.getCarriedOver())
                    .subtract(balance.getLeaveUsed())
                    .subtract(balance.getPendingDays());

            balanceWarning = requestedDays.compareTo(remaining) > 0;
        }

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setUserId(userId);
        leaveRequest.setLeaveTypeId(request.leaveTypeId());
        leaveRequest.setStartDate(request.startDate());
        leaveRequest.setEndDate(request.endDate());
        leaveRequest.setHalfDay(request.isHalfDay());
        leaveRequest.setReason(request.reason());
        leaveRequest.setTotalWorkingDays(requestedDays);
        leaveRequest.setStatus(LeaveStatus.PENDING);
        leaveRequest.setNoticePeriodWarning(noticePeriodWarning);
        leaveRequest.setOverlapWarning(overlapWarning);

        leaveRequestRepository.save(leaveRequest);

        if (leaveType.isRequiresBalanceTracking() && balance != null) {
            balance.setPendingDays(balance.getPendingDays().add(requestedDays));
            leaveBalanceRepository.save(balance);
        }

        final BigDecimal finalRequestedDays = requestedDays;

        findLeaveRequestApprover(submitter).ifPresent(approver ->
                emailService.sendLeaveRequestEmailToApprover(
                        approver.getEmail(),
                        approver.getFirstName(),
                        submitter.getFirstName() + " " + submitter.getLastName(),
                        leaveType.getName(),
                        request.startDate().toString(),
                        request.endDate().toString(),
                        finalRequestedDays
                )
        );

        return LeaveRequestResponse.from(leaveRequest, balanceWarning);
    }

    private Optional<User> findLeaveRequestApprover (User submitter) {

        return switch (submitter.getRole()) {
            case EMPLOYEE -> {
                if (submitter.getTeamId() == null) {
                    yield userRepository.findFirstByRoleAndIsActiveTrue(Role.HR);
                }
                Optional<Team> team = teamRepository.findById(submitter.getTeamId());
                if (team.isEmpty()) {
                    yield userRepository.findFirstByRoleAndIsActiveTrue(Role.HR);
                }

                Optional<User> manager = userRepository.findById(team.get().getManagerId());
                yield manager.isPresent() ? manager
                        : userRepository.findFirstByRoleAndIsActiveTrue(Role.HR);
            }
            case MANAGER -> {
                Optional<User> hr = userRepository.findFirstByRoleAndIsActiveTrue(Role.HR);
                yield hr.isPresent() ? hr
                        : userRepository.findFirstByRoleAndIsActiveTrue(Role.ADMIN);
            }
            case HR -> userRepository.findFirstByRoleAndIsActiveTrue(Role.ADMIN);
            case ADMIN -> userRepository.findFirstByRoleAndIsActiveTrue(Role.HR);
        };
    }
}
