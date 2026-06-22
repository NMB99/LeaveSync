package com.leavesync.user;

import com.leavesync.common.PageResponse;
import com.leavesync.email.EmailService;
import com.leavesync.entity.*;
import com.leavesync.enums.LeaveStatus;
import com.leavesync.enums.Role;
import com.leavesync.exception.BusinessRuleException;
import com.leavesync.exception.ConflictException;
import com.leavesync.exception.ForbiddenException;
import com.leavesync.exception.ResourceNotFoundException;
import com.leavesync.exception.TokenException;
import com.leavesync.repository.*;
import com.leavesync.security.AuthenticatedUser;
import com.leavesync.workingday.WorkingDayService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AuditLogRepository auditLogRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final TeamRepository teamRepository;
    private final WorkingDayService workingDayService;

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {

        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("User already exists with email: " + request.email());
        }

        if (request.role() == Role.EMPLOYEE && request.teamId() == null) {
            throw new BusinessRuleException("Team ID is required for employee role");
        }

        String inviteToken = UUID.randomUUID().toString();
        LocalDateTime inviteTokenExpiry = LocalDateTime.now().plusHours(72);

        User user = new User();
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email());
        user.setMobileNumber(request.mobileNumber());
        user.setRole(request.role());
        user.setTeamId(request.teamId());
        user.setPasswordHash(passwordEncoder.encode(inviteToken));
        user.setActive(true);
        user.setInviteToken(inviteToken);
        user.setInviteTokenExpiry(inviteTokenExpiry);

        User savedUser = userRepository.save(user);

        if (request.role() != Role.ADMIN) {
            LeaveBalance balance = new LeaveBalance();
            balance.setUserId(savedUser.getId());
            balance.setYear(LocalDate.now().getYear());

            BigDecimal entitlement = calculateProRatedEntitlement(LocalDate.now());
            balance.setTotalEntitlement(entitlement);
            balance.setCarriedOver(BigDecimal.ZERO);
            balance.setLeaveUsed(BigDecimal.ZERO);
            balance.setPendingDays(BigDecimal.ZERO);
            leaveBalanceRepository.save(balance);
        }

        emailService.sendInviteEmail(savedUser.getEmail(), savedUser.getFirstName(), inviteToken);

        return UserResponse.from(savedUser);
    }

    public void acceptInvite(AcceptInviteRequest request) {

        User user = userRepository.findByInviteToken(request.inviteToken())
                .orElseThrow(() -> new TokenException("Invalid or expired invite token"));

        if (user.getInviteTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new TokenException("Invalid or expired invite token");
        }

        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setInviteToken(null);
        user.setInviteTokenExpiry(null);

        userRepository.save(user);

        emailService.sendWelcomeEmail(user.getEmail(), user.getFirstName());
    }

    public void forgotPassword(ForgotPasswordRequest request) {

        Optional<User> optionalUser = userRepository.findByEmail(request.email());

        if (optionalUser.isEmpty()) {
            return;
        }

        User user = optionalUser.get();
        String newResetToken = UUID.randomUUID().toString();

        user.setInviteToken(newResetToken);
        user.setInviteTokenExpiry(LocalDateTime.now().plusHours(1));

        userRepository.save(user);

        emailService.sendPasswordResetEmail(user.getEmail(), user.getFirstName(), newResetToken);
    }

    public void resetPassword(ResetPasswordRequest request) {

        User user = userRepository.findByInviteToken(request.resetToken())
                .orElseThrow(() -> new TokenException("Invalid or expired reset token"));

        if (user.getInviteTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new TokenException("Invalid or expired reset token");
        }

        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setInviteToken(null);
        user.setInviteTokenExpiry(null);

        userRepository.save(user);
    }

    public PageResponse<UserResponse> getAllUsers(AuthenticatedUser principal, Pageable pageable) {

        Page<User> users = switch (principal.role()) {
            case ADMIN, HR -> userRepository.findAll(pageable);
            case MANAGER -> {
                List<UUID> teamIds = teamRepository.findByManagerId(principal.userId())
                        .stream()
                        .map(Team::getId)
                        .toList();

                yield  userRepository.findByTeamIdIn(teamIds, pageable);
            }
            default -> throw new ForbiddenException("You are not authorized to view this resource");
        };

        return PageResponse.from(users.map(UserResponse::from));
    }

    public UserResponse getUserById(UUID userId, AuthenticatedUser principal) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId.toString()));

        switch (principal.role()) {
            case ADMIN, HR -> {}
            case MANAGER -> {
                List<UUID> teamIds = teamRepository.findByManagerId(principal.userId())
                        .stream()
                        .map(Team::getId)
                        .toList();

                if (!teamIds.contains(user.getTeamId())) {
                    throw new ForbiddenException("You are not authorized to view this resource");
                }
            }
            case EMPLOYEE -> {
                if (!user.getId().equals(principal.userId())) {
                    throw new ForbiddenException("You are not authorized to view this resource");
                }
            }
            default -> throw new ForbiddenException("You are not authorized to view this resource");
        };

        return UserResponse.from(user);
    }

    public UserResponse updateUser(UUID userId, UpdateUserRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId.toString()));

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setRole(request.role());
        user.setTeamId(request.teamId());

        return UserResponse.from(userRepository.save(user));
    }

    public UserResponse updateMobile(UpdateMobileRequest request, AuthenticatedUser principal) {

        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.userId().toString()));

        user.setMobileNumber(request.mobileNumber());

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse deactivateUser(UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId.toString()));

        if (teamRepository.existsByManagerId(userId)) {
            throw new BusinessRuleException("Cannot deactivate user - they are currently managing a team. Reassign the team first.");
        }

        if (!user.isActive()) {
            throw new BusinessRuleException("User is already deactivated");
        }

        List<LeaveRequest> pendingRequest = leaveRequestRepository.findByUserIdAndStatus(userId, LeaveStatus.PENDING);

        List<LeaveRequest> approvedRequest = leaveRequestRepository
                .findByUserIdAndStatusAndEndDateGreaterThanEqual(userId, LeaveStatus.APPROVED, LocalDate.now());

        List<LeaveRequest> requestToCancel = new ArrayList<>();
        requestToCancel.addAll(pendingRequest);
        requestToCancel.addAll(approvedRequest);

        for (LeaveRequest request : requestToCancel) {
            LeaveStatus previousStatus = request.getStatus();
            request.setStatus(LeaveStatus.CANCELLED);
            leaveRequestRepository.save(request);

            AuditLog auditLog = new AuditLog();
            auditLog.setLeaveRequestId(request.getId());
            auditLog.setPreviousStatus(previousStatus);
            auditLog.setNewStatus(LeaveStatus.CANCELLED);
            auditLog.setActionedBy(null);
            auditLog.setNotes("User account deactivated");
            auditLogRepository.save(auditLog);
        }

        user.setActive(false);
        User deactivatedUser = userRepository.save(user);

        emailService.sendDeboardingEmail(user.getEmail(), user.getFirstName());

        return UserResponse.from(deactivatedUser);
    }

    private BigDecimal calculateProRatedEntitlement(LocalDate joiningDate) {

        LocalDate startYear = LocalDate.of(joiningDate.getYear(), 1, 1);
        LocalDate endYear = LocalDate.of(joiningDate.getYear(), 12, 31);

        BigDecimal totalWorkingDays = workingDayService.countWorkingDays(startYear, endYear);
        BigDecimal remainingWorkingDays = workingDayService.countWorkingDays(joiningDate, endYear);

        BigDecimal proRatedEntitlement = remainingWorkingDays
                .multiply(new BigDecimal("25"))
                .divide(totalWorkingDays, 10, RoundingMode.HALF_UP);

        return proRatedEntitlement
                .multiply(new BigDecimal("2"))
                .setScale(0, RoundingMode.CEILING)
                .divide(new BigDecimal("2"), 1, RoundingMode.UNNECESSARY);
    }

}
