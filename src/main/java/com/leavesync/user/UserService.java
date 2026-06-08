package com.leavesync.user;

import com.leavesync.entity.AuditLog;
import com.leavesync.entity.LeaveRequest;
import com.leavesync.entity.User;
import com.leavesync.enums.LeaveStatus;
import com.leavesync.exception.BusinessRuleException;
import com.leavesync.exception.ConflictException;
import com.leavesync.exception.ForbiddenException;
import com.leavesync.exception.ResourceNotFoundException;
import com.leavesync.exception.TokenException;
import com.leavesync.repository.AuditLogRepository;
import com.leavesync.repository.LeaveRequestRepository;
import com.leavesync.repository.UserRepository;
import com.leavesync.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public UserResponse createUser(CreateUserRequest request) {

        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("User already exists with email: " + request.email());
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

    public List<UserResponse> getAllUsers(AuthenticatedUser principal) {

        List<User> users = switch (principal.role()) {
            case "ADMIN", "HR" -> userRepository.findAll();
            case "MANAGER" -> {
                User manager = userRepository.findById(principal.userId())
                                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.userId().toString()));

                yield  userRepository.findByTeamId(manager.getTeamId());
            }
            default -> throw new ForbiddenException("You are not authorized to view this resource");
        };

        return users.stream()
                .map(UserResponse::from)
                .toList();
    }

    public UserResponse getUserById(UUID userId, AuthenticatedUser principal) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId.toString()));

        switch (principal.role()) {
            case "ADMIN", "HR" -> {}
            case "MANAGER" -> {
                User manager = userRepository.findById(principal.userId())
                        .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.userId().toString()));

                if (!manager.getTeamId().equals(user.getTeamId())) {
                    throw new ForbiddenException("You are not authorized to view this resource");
                }
            }
            case "EMPLOYEE" -> {
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

}
