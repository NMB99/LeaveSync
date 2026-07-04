package com.leavesync.user;

import com.leavesync.common.UserPageResponse;
import com.leavesync.email.EmailService;
import com.leavesync.entity.*;
import com.leavesync.enums.LeaveStatus;
import com.leavesync.enums.Role;
import com.leavesync.exception.*;
import com.leavesync.repository.*;
import com.leavesync.security.AuthenticatedUser;
import com.leavesync.workingday.WorkingDayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private LeaveTypeRepository leaveTypeRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private WorkingDayService workingDayService;

    @InjectMocks
    private UserService userService;

    UUID userId;
    User user;
    UUID teamId;
    Team team;
    AuthenticatedUser principal;

    @BeforeEach
    public void setUp() {
        userId = UUID.randomUUID();
        teamId = UUID.randomUUID();

        user = new User();
        ReflectionTestUtils.setField(user,  "id", userId);
        user.setFirstName("Nilay");
        user.setLastName("Tests");
        user.setEmail("nilay.test@leavesync.com");
        user.setRole(Role.EMPLOYEE);
        user.setTeamId(teamId);
        user.setActive(true);
    }

    @Test
    void createUser_shouldThrowConflictException_whenEmailAlreadyExists() {
        CreateUserRequest request = new CreateUserRequest(
                "User",
                "Tests",
                "user.tests@test.com",
                null,
                Role.EMPLOYEE,
                null
        );

        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("User already exists")
                .hasMessageContaining(request.email());
    }

    @Test
    void createUser_shouldThrowBusinessRuleException_whenEmployeeHasNoTeamId() {
        CreateUserRequest request = new CreateUserRequest(
                "User",
                "Tests",
                "user.tests@test.com",
                null,
                Role.EMPLOYEE,
                null
        );

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Team ID is required for employee role");
    }

    @Test
    void createUser_shouldNotCreateLeaveBalance_whenRoleIsAdmin() {
        CreateUserRequest request = new CreateUserRequest(
                "Admin",
                "Tests",
                "admin.tests@test.com",
                null,
                Role.ADMIN,
                null
        );

        user.setRole(Role.ADMIN);
        when(userRepository.save(any(User.class))).thenReturn(user);

        UserResponse response = userService.createUser(request);
        verify(leaveBalanceRepository, never()).save(any());

        assertThat(response).isNotNull();
        assertThat(response.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void createUser_shouldCreateLeaveBalance_whenRoleIsNotAdmin() {
        CreateUserRequest request = new CreateUserRequest(
                "User",
                "Tests",
                "user.tests@test.com",
                null,
                Role.EMPLOYEE,
                teamId
        );

        when(userRepository.save(any(User.class))).thenReturn(user);
        when(workingDayService.countWorkingDays(any(), any()))
                .thenReturn(new BigDecimal("261"))
                .thenReturn(new BigDecimal("130"));

        UserResponse response = userService.createUser(request);
        verify(leaveBalanceRepository).save(any());

        assertThat(response).isNotNull();
        assertThat(response.role()).isEqualTo(Role.EMPLOYEE);
    }

    @Test
    void createUser_shouldSendInviteEmail_withCorrectToken() {
        CreateUserRequest request = new CreateUserRequest(
                "User",
                "Tests",
                "user.tests@test.com",
                null,
                Role.EMPLOYEE,
                teamId
        );

        when(userRepository.save(any(User.class))).thenReturn(user);
        when(workingDayService.countWorkingDays(any(), any()))
                .thenReturn(new BigDecimal("261"))
                .thenReturn(new BigDecimal("130"));

        UserResponse response = userService.createUser(request);

        assertThat(response).isNotNull();
        assertThat(response.role()).isEqualTo(Role.EMPLOYEE);

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendInviteEmail(eq(response.email()), eq(response.firstName()), tokenCaptor.capture());
        assertThat(tokenCaptor.getValue()).isNotNull();
    }

    @Test
    void acceptInvite_shouldThrowTokenException_whenTokenNotFound() {
        when(userRepository.findByInviteToken(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.acceptInvite(new AcceptInviteRequest("token", "password")))
                .isInstanceOf(TokenException.class)
                .hasMessage("Invalid or expired invite token");
    }

    @Test
    void acceptInvite_shouldThrowTokenException_whenTokenIsExpired() {
        user.setInviteTokenExpiry(LocalDateTime.now().minusDays(1));
        when(userRepository.findByInviteToken(any())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.acceptInvite(new AcceptInviteRequest("token", "password")))
                .isInstanceOf(TokenException.class)
                .hasMessage("Invalid or expired invite token");
    }

    @Test
    void acceptInvite_shouldUpdatePasswordAndSendWelcomeEmail_whenTokenIsValid() {
        user.setInviteTokenExpiry(LocalDateTime.now().plusHours(1));
        when(userRepository.findByInviteToken(any())).thenReturn(Optional.of(user));

        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.acceptInvite(new AcceptInviteRequest("token", "password"));
        verify(emailService).sendWelcomeEmail(eq(user.getEmail()), eq(user.getFirstName()));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        assertThat(captor.getValue()).isNotNull();
        assertThat(captor.getValue().getInviteToken()).isNull();
        assertThat(captor.getValue().getInviteTokenExpiry()).isNull();
    }

    @Test
    void forgotPassword_shouldReturnSilently_whenEmailNotFound() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        userService.forgotPassword(new ForgotPasswordRequest("unknown@test.com"));

        verify(userRepository, never()).save(any(User.class));
        verify(emailService, never()).sendPasswordResetEmail(any(), any(), any());
    }

    @Test
    void forgotPassword_shouldSaveTokenAndSendEmail_whenEmailFound() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(user));

        userService.forgotPassword(new ForgotPasswordRequest(user.getEmail()));

        verify(userRepository).save(any(User.class));

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq(user.getEmail()), eq(user.getFirstName()), tokenCaptor.capture());
        assertThat(tokenCaptor.getValue()).isNotNull();
    }

    @Test
    void resetPassword_shouldThrowTokenException_whenTokenNotFound() {
        when(userRepository.findByInviteToken(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.resetPassword(new ResetPasswordRequest("token", "password")))
                .isInstanceOf(TokenException.class)
                .hasMessage("Invalid or expired reset token");
    }

    @Test
    void resetPassword_shouldThrowTokenException_whenTokenIsExpired() {
        user.setInviteTokenExpiry(LocalDateTime.now().minusDays(1));
        when(userRepository.findByInviteToken(any())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.resetPassword(new ResetPasswordRequest("token", "password")))
                .isInstanceOf(TokenException.class)
                .hasMessage("Invalid or expired reset token");
    }

    @Test
    void resetPassword_shouldUpdatePasswordAndClearToken_whenTokenIsValid() {
        user.setInviteTokenExpiry(LocalDateTime.now().plusHours(1));
        when(userRepository.findByInviteToken(any())).thenReturn(Optional.of(user));

        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.resetPassword(new ResetPasswordRequest("token", "password"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        assertThat(captor.getValue()).isNotNull();
        assertThat(captor.getValue().getInviteToken()).isNull();
        assertThat(captor.getValue().getInviteTokenExpiry()).isNull();
    }

    @Test
    void getAllUsers_shouldThrowForbiddenException_whenCallerIsEmployee() {
        principal = new AuthenticatedUser(userId, user.getEmail(), user.getRole());

        assertThatThrownBy(() -> userService.getAllUsers(principal, Pageable.unpaged()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not authorized to view this resource");
    }

    @Test
    void getAllUsers_shouldReturnAllUsers_whenCallerIsAdminOrHr() {
        UUID adminId = UUID.randomUUID();

        principal = new AuthenticatedUser(adminId, "admin@test.com", Role.ADMIN);

        Page<User> page = new PageImpl<>(List.of(user));
        when(userRepository.findAll(any(Pageable.class))).thenReturn(page);

        UserPageResponse response = userService.getAllUsers(principal, Pageable.unpaged());
        verify(userRepository).findAll(any(Pageable.class));

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
    }

    @Test
    void getAllUsers_shouldReturnTeamMembers_whenCallerIsManager() {
        UUID managerId = UUID.randomUUID();

        principal = new AuthenticatedUser(managerId, "manager@test.com", Role.MANAGER);

        team = new Team();
        ReflectionTestUtils.setField(team, "id", teamId);
        team.setManagerId(managerId);
        when(teamRepository.findByManagerId(managerId)).thenReturn(List.of(team));

        Page<User> page = new PageImpl<>(List.of(user));
        when(userRepository.findByTeamIdIn(anyList(), any(Pageable.class))).thenReturn(page);

        UserPageResponse response = userService.getAllUsers(principal, Pageable.unpaged());
        verify(teamRepository).findByManagerId(managerId);
        verify(userRepository).findByTeamIdIn(anyList(), any(Pageable.class));

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
    }

    @Test
    void getUserById_shouldThrowResourceNotFoundException_whenUserNotFound() {
        principal = new AuthenticatedUser(userId, user.getEmail(), user.getRole());

        when(userRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(userId, principal))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found")
                .hasMessageContaining(userId.toString());
    }

    @Test
    void getUserById_shouldThrowForbiddenException_whenEmployeeViewsOtherProfile() {
        UUID employeeId = UUID.randomUUID();
        principal = new AuthenticatedUser(employeeId, "employee@test.com", Role.EMPLOYEE);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.getUserById(userId, principal))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not authorized to view this resource");
    }

    @Test
    void getUserById_shouldThrowForbiddenException_whenManagerViewsOutsideTeam() {
        UUID managerId = UUID.randomUUID();

        principal = new AuthenticatedUser(managerId, "manager@test.com", Role.MANAGER);

        user.setTeamId(UUID.randomUUID());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        team = new Team();
        ReflectionTestUtils.setField(team, "id", teamId);
        team.setManagerId(managerId);
        when(teamRepository.findByManagerId(managerId)).thenReturn(List.of(team));

        assertThatThrownBy(() -> userService.getUserById(userId, principal))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not authorized to view this resource");
    }

    @Test
    void getUserById_shouldReturnUser_whenCallerIsHrOrAdmin() {
        UUID hrId = UUID.randomUUID();

        principal = new AuthenticatedUser(hrId, "hr@test.com",  Role.HR);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserResponse response = userService.getUserById(userId, principal);
        verify(userRepository).findById(userId);

        assertThat(response).isNotNull();
        assertThat(response.role()).isEqualTo(Role.EMPLOYEE);
    }

    @Test
    void getUserById_shouldReturnUser_whenEmployeeViewsOwnProfile() {
        principal = new AuthenticatedUser(userId, user.getEmail(), user.getRole());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserResponse response = userService.getUserById(userId, principal);
        verify(userRepository).findById(userId);

        assertThat(response).isNotNull();
        assertThat(response.role()).isEqualTo(principal.role());
    }

    @Test
    void getUserById_shouldReturnUser_whenManagerViewsOwnTeamMember() {
        UUID managerId = UUID.randomUUID();

        principal = new AuthenticatedUser(managerId, "manager@test.com", Role.MANAGER);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        team = new Team();
        ReflectionTestUtils.setField(team, "id", teamId);
        team.setManagerId(managerId);
        when(teamRepository.findByManagerId(managerId)).thenReturn(List.of(team));

        UserResponse response = userService.getUserById(userId, principal);
        verify(userRepository).findById(userId);
        verify(teamRepository).findByManagerId(managerId);

        assertThat(response).isNotNull();
        assertThat(response.role()).isEqualTo(Role.EMPLOYEE);
    }

    @Test
    void updateUser_shouldThrowResourceNotFoundException_whenUserNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(
                userId, new UpdateUserRequest("first", "last", Role.EMPLOYEE, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found")
                .hasMessageContaining(userId.toString());
    }

    @Test
    void updateUser_shouldThrowBusinessRuleException_whenEmployeeHasNoTeamId() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateUser(
                userId, new UpdateUserRequest("first", "last", Role.EMPLOYEE, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Team ID is required for employee role");
    }

    @Test
    void updateUser_shouldSaveAndReturnUser_whenRequestIsValid() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        UserResponse response = userService.updateUser(userId, new UpdateUserRequest("first", "last", Role.EMPLOYEE, teamId));

        assertThat(response).isNotNull();
        assertThat(response.role()).isEqualTo(Role.EMPLOYEE);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo(Role.EMPLOYEE);
        assertThat(saved.getTeamId()).isEqualTo(teamId);
    }

    @Test
    void updateMobile_shouldThrowResourceNotFoundException_whenUserNotFound() {
        principal = new AuthenticatedUser(userId, user.getEmail(), user.getRole());

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateMobile(new UpdateMobileRequest("mobile_number"), principal))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found")
                .hasMessageContaining(userId.toString());
    }

    @Test
    void updateMobile_shouldUpdateAndReturnUser_whenRequestIsValid() {
        principal = new AuthenticatedUser(userId, user.getEmail(), user.getRole());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        UserResponse response = userService.updateMobile(new UpdateMobileRequest("mobile_number"), principal);

        assertThat(response).isNotNull();
        assertThat(response.role()).isEqualTo(Role.EMPLOYEE);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo(Role.EMPLOYEE);
        assertThat(saved.getMobileNumber()).isEqualTo("mobile_number");
    }

    @Test
    void deactivateUser_shouldThrowResourceNotFoundException_whenUserNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deactivateUser(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found")
                .hasMessageContaining(userId.toString());
    }

    @Test
    void deactivateUser_shouldThrowBusinessRuleException_whenUserManagesATeam() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(teamRepository.existsByManagerId(any())).thenReturn(true);

        assertThatThrownBy(() -> userService.deactivateUser(userId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Cannot deactivate user - they are currently managing a team. Reassign the team first.");
    }

    @Test
    void deactivateUser_shouldThrowBusinessRuleException_whenDeactivatingLastAdmin() {
        user.setRole(Role.ADMIN);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.countByRoleAndIsActiveTrue(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.deactivateUser(userId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Cannot deactivate the last admin account");
    }

    @Test
    void deactivateUser_shouldThrowBusinessRuleException_whenUserAlreadyDeactivated() {
        user.setActive(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.deactivateUser(userId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("User is already deactivated");
    }

    @Test
    void deactivateUser_shouldCancelAllUnactionedAndFutureApprovedRequestsAndSaveAuditLog_whenUserIsDeactivated() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        LeaveRequest leaveRequest1 = new LeaveRequest();
        leaveRequest1.setUserId(userId);
        leaveRequest1.setStatus(LeaveStatus.PENDING);

        LeaveRequest leaveRequest2 = new LeaveRequest();
        leaveRequest2.setUserId(userId);
        leaveRequest2.setStatus(LeaveStatus.APPROVED);

        when(leaveRequestRepository.findByUserIdAndStatusIn(
                userId, List.of(LeaveStatus.PENDING, LeaveStatus.ESCALATED, LeaveStatus.REROUTED_TO_HR)
        )).thenReturn(List.of(leaveRequest1));
        when(leaveRequestRepository.findByUserIdAndStatusAndEndDateGreaterThanEqual(
                eq(userId), eq(LeaveStatus.APPROVED), any(LocalDate.class)
        )).thenReturn(List.of(leaveRequest2));

        LeaveType leaveType =  new LeaveType();
        leaveType.setRequiresBalanceTracking(false);
        when(leaveTypeRepository.findById(any())).thenReturn(Optional.of(leaveType));

        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.deactivateUser(userId);

        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository, times(2)).save(captor.capture());

        List<LeaveRequest> leaveRequests = captor.getAllValues();
        assertThat(leaveRequests.get(0).getStatus()).isEqualTo(LeaveStatus.CANCELLED);
        assertThat(leaveRequests.get(1).getStatus()).isEqualTo(LeaveStatus.CANCELLED);

        verify(auditLogRepository, times(2)).save(any());
    }

    @Test
    void deactivateUser_shouldSaveUserAsInactiveAndSendDeboardingMail_whenDeactivationSucceeds() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(leaveRequestRepository.findByUserIdAndStatusIn(
                userId, List.of(LeaveStatus.PENDING, LeaveStatus.ESCALATED, LeaveStatus.REROUTED_TO_HR)
        )).thenReturn(List.of());
        when(leaveRequestRepository.findByUserIdAndStatusAndEndDateGreaterThanEqual(
                eq(userId), eq(LeaveStatus.APPROVED), any(LocalDate.class)
        )).thenReturn(List.of());

        when(userRepository.save(any(User.class))).thenReturn(user);

        UserResponse response = userService.deactivateUser(userId);
        verify(emailService).sendDeboardingEmail(user.getEmail(), user.getFirstName());

        assertThat(response).isNotNull();
        assertThat(response.isActive()).isFalse();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        assertThat(saved.isActive()).isFalse();
    }

    @Test
    void deactivateUser_shouldDecreasePendingDays_whenUnactionedRequestCancelled() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UUID leaveTypeId = UUID.randomUUID();
        LeaveRequest request = new LeaveRequest();
        request.setUserId(userId);
        request.setLeaveTypeId(leaveTypeId);
        request.setStatus(LeaveStatus.PENDING);
        request.setStartDate(LocalDate.now());
        request.setTotalWorkingDays(new BigDecimal("3"));

        when(leaveRequestRepository.findByUserIdAndStatusIn(
                userId, List.of(LeaveStatus.PENDING, LeaveStatus.ESCALATED, LeaveStatus.REROUTED_TO_HR)
        )).thenReturn(List.of(request));
        when(leaveRequestRepository.findByUserIdAndStatusAndEndDateGreaterThanEqual(
                eq(userId), eq(LeaveStatus.APPROVED), any(LocalDate.class)
        )).thenReturn(List.of());

        LeaveType leaveType = new LeaveType();
        leaveType.setRequiresBalanceTracking(true);
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));

        LeaveBalance balance = new LeaveBalance();
        balance.setPendingDays(new BigDecimal("5"));
        when(leaveBalanceRepository.findByUserIdAndYear(userId, request.getStartDate().getYear()))
                .thenReturn(Optional.of(balance));

        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.deactivateUser(userId);

        ArgumentCaptor<LeaveBalance> captor = ArgumentCaptor.forClass(LeaveBalance.class);
        verify(leaveBalanceRepository).save(captor.capture());

        assertThat(captor.getValue()).isNotNull();
        assertThat(captor.getValue().getPendingDays()).isEqualByComparingTo("2");
    }

    @Test
    void deactivateUser_shouldDecreaseLeaveUsed_whenApprovedFutureRequestCancelled() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UUID leaveTypeId = UUID.randomUUID();
        LeaveRequest request = new LeaveRequest();
        request.setUserId(userId);
        request.setLeaveTypeId(leaveTypeId);
        request.setStatus(LeaveStatus.APPROVED);
        request.setStartDate(LocalDate.now().plusDays(4));
        request.setTotalWorkingDays(new BigDecimal("6"));

        when(leaveRequestRepository.findByUserIdAndStatusIn(
                userId, List.of(LeaveStatus.PENDING, LeaveStatus.ESCALATED, LeaveStatus.REROUTED_TO_HR)
        )).thenReturn(List.of());
        when(leaveRequestRepository.findByUserIdAndStatusAndEndDateGreaterThanEqual(
                eq(userId), eq(LeaveStatus.APPROVED), any(LocalDate.class)
        )).thenReturn(List.of(request));

        LeaveType leaveType = new LeaveType();
        leaveType.setRequiresBalanceTracking(true);
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));

        LeaveBalance balance = new LeaveBalance();
        balance.setLeaveUsed(new BigDecimal("15"));
        when(leaveBalanceRepository.findByUserIdAndYear(userId, request.getStartDate().getYear()))
                .thenReturn(Optional.of(balance));

        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.deactivateUser(userId);

        ArgumentCaptor<LeaveBalance> captor = ArgumentCaptor.forClass(LeaveBalance.class);
        verify(leaveBalanceRepository).save(captor.capture());

        assertThat(captor.getValue()).isNotNull();
        assertThat(captor.getValue().getLeaveUsed()).isEqualByComparingTo("9");
    }

    @Test
    void deactivateUser_shouldNotTouchBalance_whenLeaveTypeDoesNotRequireBalanceTracking() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UUID leaveTypeId = UUID.randomUUID();
        LeaveRequest request = new LeaveRequest();
        request.setUserId(userId);
        request.setLeaveTypeId(leaveTypeId);
        request.setStatus(LeaveStatus.PENDING);
        request.setStartDate(LocalDate.now());
        request.setTotalWorkingDays(new BigDecimal("3"));

        when(leaveRequestRepository.findByUserIdAndStatusIn(
                userId, List.of(LeaveStatus.PENDING, LeaveStatus.ESCALATED, LeaveStatus.REROUTED_TO_HR)
        )).thenReturn(List.of(request));
        when(leaveRequestRepository.findByUserIdAndStatusAndEndDateGreaterThanEqual(
                eq(userId), eq(LeaveStatus.APPROVED), any(LocalDate.class)
        )).thenReturn(List.of());

        LeaveType leaveType = new LeaveType();
        leaveType.setRequiresBalanceTracking(false);
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));

        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.deactivateUser(userId);

        verify(leaveBalanceRepository, never()).save(any(LeaveBalance.class));
    }

    @Test
    void deactivateUser_shouldLookupBalanceByRequestStartYear_notCurrentYear() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UUID leaveTypeId = UUID.randomUUID();
        LeaveRequest request = new LeaveRequest();
        request.setUserId(userId);
        request.setLeaveTypeId(leaveTypeId);
        request.setStatus(LeaveStatus.PENDING);
        request.setStartDate(LocalDate.now().plusYears(1));
        request.setTotalWorkingDays(new BigDecimal("3"));

        when(leaveRequestRepository.findByUserIdAndStatusIn(
                userId, List.of(LeaveStatus.PENDING, LeaveStatus.ESCALATED, LeaveStatus.REROUTED_TO_HR)
        )).thenReturn(List.of(request));
        when(leaveRequestRepository.findByUserIdAndStatusAndEndDateGreaterThanEqual(
                eq(userId), eq(LeaveStatus.APPROVED), any(LocalDate.class)
        )).thenReturn(List.of());

        LeaveType leaveType = new LeaveType();
        leaveType.setRequiresBalanceTracking(true);
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));

        LeaveBalance balance = new LeaveBalance();
        balance.setYear(LocalDate.now().getYear() + 1);
        balance.setPendingDays(new BigDecimal("5"));
        when(leaveBalanceRepository.findByUserIdAndYear(userId, request.getStartDate().getYear()))
                .thenReturn(Optional.of(balance));

        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.deactivateUser(userId);

        ArgumentCaptor<LeaveBalance> captor = ArgumentCaptor.forClass(LeaveBalance.class);
        verify(leaveBalanceRepository).save(captor.capture());

        assertThat(captor.getValue()).isNotNull();
        assertThat(captor.getValue().getYear()).isEqualTo(LocalDate.now().getYear() + 1);
    }

}
