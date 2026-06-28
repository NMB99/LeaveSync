package com.leavesync.leaverequest;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LeaveRequestServiceTest {

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;

    @Mock
    private LeaveTypeRepository leaveTypeRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private WorkingDayService workingDayService;

    @Mock
    private EmailService emailService;

    @Mock
    private LeaveBalanceService leaveBalanceService;

    @InjectMocks
    private LeaveRequestService leaveRequestService;


    private UUID userId;
    private UUID leaveTypeId;
    private User submitter;
    private LeaveType leaveType;
    private SubmitLeaveRequest request;
    private AuthenticatedUser principal;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        leaveTypeId = UUID.randomUUID();

        submitter = new User();
        ReflectionTestUtils.setField(submitter, "id", userId);
        submitter.setFirstName("Nilay");
        submitter.setLastName("Tests");
        submitter.setEmail("nilay.tests@leavesync.com");
        submitter.setRole(Role.EMPLOYEE);
        submitter.setActive(true);

        leaveType = new LeaveType();
        ReflectionTestUtils.setField(leaveType, "id", leaveTypeId);
        leaveType.setName("Annual Leave");
        leaveType.setCode("ANNUAL");
        leaveType.setRequiresBalanceTracking(true);
        leaveType.setRequiresHrApproval(false);
        leaveType.setRequiresReason(false);

        LocalDate startDate = LocalDate.now().plusDays(7);
        LocalDate endDate = startDate.plusDays(4);
        request = new SubmitLeaveRequest(leaveTypeId, startDate, endDate, false, null);

        principal = new AuthenticatedUser(userId, submitter.getEmail(),  submitter.getRole());

    }

    @Test
    void submit_shouldThrowForbiddenException_whenAdminSubmits() {
        User admin = new User();
        admin.setRole(Role.ADMIN);

        when(userRepository.findById(userId)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> leaveRequestService.submit(userId, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("cannot submit");
    }

    @Test
    void submit_shouldThrowInvalidLeaveRequestException_whenStartDateIsInPast() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));

        LocalDate yesterday = LocalDate.now().minusDays(1);
        SubmitLeaveRequest request = new SubmitLeaveRequest(
                leaveTypeId, yesterday, yesterday.plusDays(2), false, null
        );

        assertThatThrownBy(() -> leaveRequestService.submit(userId, request))
                .isInstanceOf(InvalidLeaveRequestException.class)
                .hasMessageContaining("cannot be in the past");
    }

    @Test
    void submit_shouldThrowInvalidLeaveRequestException_whenSickLeaveStartDateIsInFuture() {
        LeaveType leaveType = new LeaveType();
        leaveType.setName("Sick Leave");
        leaveType.setCode("SICK");

        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        SubmitLeaveRequest request = new SubmitLeaveRequest(
                leaveTypeId, tomorrow, tomorrow.plusDays(2), false, null
        );

        assertThatThrownBy(() -> leaveRequestService.submit(userId, request))
                .isInstanceOf(InvalidLeaveRequestException.class)
                .hasMessageContaining("cannot be")
                .hasMessageContaining("future date");
    }

    @Test
    void submit_shouldThrowInvalidLeaveRequestException_whenReasonMissingAndRequired() {
        LeaveType leaveType = new LeaveType();
        leaveType.setName("Unpaid Leave");
        leaveType.setCode("UNPAID");
        leaveType.setRequiresReason(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));

        LocalDate startDate = LocalDate.now().plusDays(6);
        SubmitLeaveRequest request = new SubmitLeaveRequest(
                leaveTypeId, startDate, startDate.plusDays(2), false, null
        );

        assertThatThrownBy(() -> leaveRequestService.submit(userId, request))
                .isInstanceOf(InvalidLeaveRequestException.class)
                .hasMessageContaining("reason is required");
    }

    @Test
    void submit_shouldThrowInvalidLeaveRequestException_whenEndDateBeforeStartDate() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));

        LocalDate startDate = LocalDate.now().plusDays(7);
        SubmitLeaveRequest request = new SubmitLeaveRequest(
                leaveTypeId, startDate, startDate.minusDays(2), false, null
        );

        assertThatThrownBy(() -> leaveRequestService.submit(userId, request))
                .isInstanceOf(InvalidLeaveRequestException.class)
                .hasMessageContaining("End date must be after start date");
    }

    @Test
    void submit_shouldThrowInvalidLeaveRequestException_whenHalfDaySpansMultipleDays() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        SubmitLeaveRequest request = new SubmitLeaveRequest(
                leaveTypeId, tomorrow, tomorrow.plusDays(1), true, null
        );

        assertThatThrownBy(() -> leaveRequestService.submit(userId, request))
                .isInstanceOf(InvalidLeaveRequestException.class)
                .hasMessageContaining("Half-day leave must be a single day");
    }

    @Test
    void submit_shouldThrowInvalidLeaveRequestException_whenZeroWorkingDays() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));
        when(workingDayService.countWorkingDays(any(), any())).thenReturn(BigDecimal.ZERO);

        LocalDate startDate = LocalDate.now().plusDays(7);
        SubmitLeaveRequest request = new SubmitLeaveRequest(
                leaveTypeId, startDate, startDate.plusDays(7), false, null
        );

        assertThatThrownBy(() -> leaveRequestService.submit(userId, request))
                .isInstanceOf(InvalidLeaveRequestException.class)
                .hasMessageContaining("must span at least one working day");
    }

    @Test
    void submit_shouldSaveAndRouteToHr_whenEmployeeHasNoTeam() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));
        when(workingDayService.countWorkingDays(any(), any())).thenReturn(BigDecimal.valueOf(5));
        when(leaveRequestRepository.findOverlappingRequests(any(), any(), any(), any())).thenReturn(List.of());

        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setTotalEntitlement(BigDecimal.valueOf(10));
        leaveBalance.setLeaveUsed(BigDecimal.ZERO);
        leaveBalance.setPendingDays(BigDecimal.valueOf(2));
        leaveBalance.setCarriedOver(BigDecimal.ZERO);
        when(leaveBalanceRepository.findByUserIdAndYear(any(), anyInt())).thenReturn(Optional.of(leaveBalance));

        User hrUser = new User();
        hrUser.setRole(Role.HR);
        hrUser.setEmail("hr@test.com");
        hrUser.setActive(true);
        when(userRepository.findAllByRoleAndIsActiveTrue(Role.HR)).thenReturn(List.of(hrUser));

        when(workingDayService.normaliseToWorkingDay(any())).thenReturn(LocalDate.now().plusDays(10));
        when(leaveRequestRepository.save(any())).thenReturn(new LeaveRequest());

        LocalDate startDate = LocalDate.now().plusDays(7);
        SubmitLeaveRequest leaveRequest = new SubmitLeaveRequest(
                leaveTypeId, startDate, startDate.plusDays(7),  false, null
        );

        LeaveRequestResponse response = leaveRequestService.submit(userId, leaveRequest);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(LeaveStatus.REROUTED_TO_HR);

        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository).save(captor.capture());

        LeaveRequest saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(LeaveStatus.REROUTED_TO_HR);
        assertThat(saved.getAssignedTo()).isEqualTo(Role.HR);
    }

    @Test
    void submit_shouldSetStatusToReroutedToHr_whenManagerIsInactive() {
        UUID teamId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();

        submitter.setTeamId(teamId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));
        when(workingDayService.countWorkingDays(any(), any())).thenReturn(BigDecimal.valueOf(5));
        when(leaveRequestRepository.findOverlappingRequests(any(), any(), any(), any())).thenReturn(List.of());

        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setTotalEntitlement(BigDecimal.valueOf(10));
        leaveBalance.setLeaveUsed(BigDecimal.ZERO);
        leaveBalance.setPendingDays(BigDecimal.valueOf(2));
        leaveBalance.setCarriedOver(BigDecimal.ZERO);
        when(leaveBalanceRepository.findByUserIdAndYear(any(), anyInt())).thenReturn(Optional.of(leaveBalance));

        Team team = new Team();
        team.setManagerId(managerId);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(userRepository.findByIdAndIsActiveTrue(managerId)).thenReturn(Optional.empty());

        User hrUser = new User();
        hrUser.setRole(Role.HR);
        hrUser.setEmail("hr@test.com");
        hrUser.setActive(true);
        when(userRepository.findAllByRoleAndIsActiveTrue(Role.HR)).thenReturn(List.of(hrUser));

        when(workingDayService.normaliseToWorkingDay(any())).thenReturn(LocalDate.now().plusDays(10));
        when(leaveRequestRepository.save(any())).thenReturn(new LeaveRequest());

        LocalDate startDate = LocalDate.now().plusDays(7);
        SubmitLeaveRequest leaveRequest = new SubmitLeaveRequest(
                leaveTypeId, startDate, startDate.plusDays(7), false, null
        );

        LeaveRequestResponse response = leaveRequestService.submit(userId, leaveRequest);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(LeaveStatus.REROUTED_TO_HR);

        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository).save(captor.capture());

        LeaveRequest saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(LeaveStatus.REROUTED_TO_HR);
        assertThat(saved.getAssignedTo()).isEqualTo(Role.HR);
    }

    @Test
    void submit_shouldRouteToManager_whenEmployeeHasActiveManager() {
        UUID teamId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();

        submitter.setTeamId(teamId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));
        when(workingDayService.countWorkingDays(any(), any())).thenReturn(BigDecimal.valueOf(5));
        when(leaveRequestRepository.findOverlappingRequests(any(), any(), any(), any())).thenReturn(List.of());

        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setTotalEntitlement(BigDecimal.valueOf(10));
        leaveBalance.setLeaveUsed(BigDecimal.ZERO);
        leaveBalance.setPendingDays(BigDecimal.valueOf(2));
        leaveBalance.setCarriedOver(BigDecimal.ZERO);
        when(leaveBalanceRepository.findByUserIdAndYear(any(), anyInt())).thenReturn(Optional.of(leaveBalance));

        Team team = new Team();
        team.setManagerId(managerId);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        User managerUser = new User();
        managerUser.setRole(Role.MANAGER);
        managerUser.setEmail("manager@test.com");
        managerUser.setActive(true);
        when(userRepository.findByIdAndIsActiveTrue(managerId)).thenReturn(Optional.of(managerUser));

        when(workingDayService.normaliseToWorkingDay(any())).thenReturn(LocalDate.now().plusDays(10));
        when(leaveRequestRepository.save(any())).thenReturn(new LeaveRequest());

        LocalDate startDate = LocalDate.now().plusDays(7);
        SubmitLeaveRequest leaveRequest = new SubmitLeaveRequest(
                leaveTypeId, startDate, startDate.plusDays(7), false, null
        );

        LeaveRequestResponse response = leaveRequestService.submit(userId, leaveRequest);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(LeaveStatus.PENDING);

        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository).save(captor.capture());

        LeaveRequest saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(saved.getAssignedTo()).isEqualTo(Role.MANAGER);
    }

    @Test
    void submit_shouldRouteToHr_whenSubmitterIsManager() {
        submitter.setRole(Role.MANAGER);
        submitter.setEmail("manager@test.com");

        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));
        when(workingDayService.countWorkingDays(any(), any())).thenReturn(BigDecimal.valueOf(5));
        when(leaveRequestRepository.findOverlappingRequests(any(), any(), any(), any())).thenReturn(List.of());

        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setTotalEntitlement(BigDecimal.valueOf(10));
        leaveBalance.setLeaveUsed(BigDecimal.ZERO);
        leaveBalance.setPendingDays(BigDecimal.valueOf(2));
        leaveBalance.setCarriedOver(BigDecimal.ZERO);
        when(leaveBalanceRepository.findByUserIdAndYear(any(), anyInt())).thenReturn(Optional.of(leaveBalance));

        User hrUser = new User();
        hrUser.setRole(Role.HR);
        hrUser.setEmail("hr@test.com");
        hrUser.setActive(true);
        when(userRepository.findAllByRoleAndIsActiveTrue(Role.HR)).thenReturn(List.of(hrUser));

        when(workingDayService.normaliseToWorkingDay(any())).thenReturn(LocalDate.now().plusDays(10));
        when(leaveRequestRepository.save(any())).thenReturn(new LeaveRequest());

        LocalDate startDate = LocalDate.now().plusDays(7);
        SubmitLeaveRequest leaveRequest = new SubmitLeaveRequest(
                leaveTypeId, startDate, startDate.plusDays(7), false, null
        );

        LeaveRequestResponse response = leaveRequestService.submit(userId, leaveRequest);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(LeaveStatus.PENDING);

        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository).save(captor.capture());

        LeaveRequest saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(saved.getAssignedTo()).isEqualTo(Role.HR);
    }

    @Test
    void submit_shouldRouteToOtherHr_whenHrSubmitsAndOtherHrExists() {
        submitter.setRole(Role.HR);
        submitter.setEmail("hr1@test.com");

        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));
        when(workingDayService.countWorkingDays(any(), any())).thenReturn(BigDecimal.valueOf(5));
        when(leaveRequestRepository.findOverlappingRequests(any(), any(), any(), any())).thenReturn(List.of());

        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setTotalEntitlement(BigDecimal.valueOf(10));
        leaveBalance.setLeaveUsed(BigDecimal.ZERO);
        leaveBalance.setPendingDays(BigDecimal.valueOf(2));
        leaveBalance.setCarriedOver(BigDecimal.ZERO);
        when(leaveBalanceRepository.findByUserIdAndYear(any(), anyInt())).thenReturn(Optional.of(leaveBalance));

        User hrUser = new User();
        ReflectionTestUtils.setField(hrUser, "id", UUID.randomUUID());
        hrUser.setFirstName("Hr2");
        hrUser.setRole(Role.HR);
        hrUser.setEmail("hr2@test.com");
        hrUser.setActive(true);
        when(userRepository.findAllByRoleAndIsActiveTrue(Role.HR)).thenReturn(List.of(submitter, hrUser));

        when(workingDayService.normaliseToWorkingDay(any())).thenReturn(LocalDate.now());
        when(leaveRequestRepository.save(any())).thenReturn(new LeaveRequest());

        LocalDate startDate = LocalDate.now().plusDays(7);
        SubmitLeaveRequest leaveRequest = new SubmitLeaveRequest(
                leaveTypeId, startDate, startDate.plusDays(7), false, null
        );

        LeaveRequestResponse response = leaveRequestService.submit(userId, leaveRequest);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(LeaveStatus.PENDING);

        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository).save(captor.capture());

        LeaveRequest saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(saved.getAssignedTo()).isEqualTo(Role.HR);
    }

    @Test
    void submit_shouldFallbackToAdmin_whenHrSubmitsAndNoOtherHrExists() {
        submitter.setRole(Role.HR);
        submitter.setEmail("hr1@test.com");

        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));
        when(workingDayService.countWorkingDays(any(), any())).thenReturn(BigDecimal.valueOf(5));
        when(leaveRequestRepository.findOverlappingRequests(any(), any(), any(), any())).thenReturn(List.of());

        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setTotalEntitlement(BigDecimal.valueOf(10));
        leaveBalance.setLeaveUsed(BigDecimal.ZERO);
        leaveBalance.setPendingDays(BigDecimal.valueOf(2));
        leaveBalance.setCarriedOver(BigDecimal.ZERO);
        when(leaveBalanceRepository.findByUserIdAndYear(any(), anyInt())).thenReturn(Optional.of(leaveBalance));

        when(userRepository.findAllByRoleAndIsActiveTrue(Role.HR)).thenReturn(List.of(submitter));

        User adminUser = new User();
        adminUser.setRole(Role.ADMIN);
        adminUser.setEmail("admin@test.com");
        adminUser.setActive(true);
        when(userRepository.findAllByRoleAndIsActiveTrue(Role.ADMIN)).thenReturn(List.of(adminUser));

        when(workingDayService.normaliseToWorkingDay(any())).thenReturn(LocalDate.now());
        when(leaveRequestRepository.save(any())).thenReturn(new LeaveRequest());

        LocalDate startDate = LocalDate.now().plusDays(7);
        SubmitLeaveRequest leaveRequest = new SubmitLeaveRequest(
                leaveTypeId, startDate, startDate.plusDays(7), false, null
        );

        LeaveRequestResponse response = leaveRequestService.submit(userId, leaveRequest);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(LeaveStatus.PENDING);

        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository).save(captor.capture());

        LeaveRequest saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(saved.getAssignedTo()).isEqualTo(Role.ADMIN);
    }

    @Test
    void submit_shouldRouteToHr_whenLeaveTypeRequiresHrApproval() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));

        LeaveType leaveType = new LeaveType();
        leaveType.setName("Maternity Leave");
        leaveType.setCode("MATERNITY");
        leaveType.setRequiresHrApproval(true);
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));

        when(workingDayService.countWorkingDays(any(), any())).thenReturn(BigDecimal.valueOf(5));
        when(leaveRequestRepository.findOverlappingRequests(any(), any(), any(), any())).thenReturn(List.of());

        User hrUser = new User();
        hrUser.setRole(Role.HR);
        hrUser.setEmail("hr@test.com");
        hrUser.setActive(true);
        when(userRepository.findAllByRoleAndIsActiveTrue(Role.HR)).thenReturn(List.of(hrUser));

        when(workingDayService.normaliseToWorkingDay(any())).thenReturn(LocalDate.now());
        when(leaveRequestRepository.save(any())).thenReturn(new LeaveRequest());

        LocalDate startDate = LocalDate.now().plusDays(7);
        SubmitLeaveRequest leaveRequest = new SubmitLeaveRequest(
                leaveTypeId, startDate, startDate.plusDays(7), false, null
        );

        LeaveRequestResponse response = leaveRequestService.submit(userId, leaveRequest);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(LeaveStatus.PENDING);

        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository).save(captor.capture());

        LeaveRequest saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(saved.getAssignedTo()).isEqualTo(Role.HR);
    }

    @Test
    void submit_shouldSetNoticePeriodWarning_whenAnnualLeaveIsShortNotice() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));
        when(workingDayService.countWorkingDays(any(), any())).thenReturn(BigDecimal.valueOf(5))
                .thenReturn(BigDecimal.valueOf(3));
        when(leaveRequestRepository.findOverlappingRequests(any(), any(), any(), any())).thenReturn(List.of());

        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setTotalEntitlement(BigDecimal.valueOf(10));
        leaveBalance.setLeaveUsed(BigDecimal.ZERO);
        leaveBalance.setPendingDays(BigDecimal.valueOf(2));
        leaveBalance.setCarriedOver(BigDecimal.ZERO);
        when(leaveBalanceRepository.findByUserIdAndYear(any(), anyInt())).thenReturn(Optional.of(leaveBalance));

        User hrUser = new User();
        hrUser.setRole(Role.HR);
        hrUser.setEmail("hr@test.com");
        hrUser.setActive(true);
        when(userRepository.findAllByRoleAndIsActiveTrue(Role.HR)).thenReturn(List.of(hrUser));

        when(workingDayService.normaliseToWorkingDay(any())).thenReturn(LocalDate.now());
        when(leaveRequestRepository.save(any())).thenReturn(new LeaveRequest());

        LocalDate startDate = LocalDate.now().plusDays(4);
        SubmitLeaveRequest leaveRequest = new SubmitLeaveRequest(
                leaveTypeId, startDate, startDate.plusDays(7), false, null
        );

        LeaveRequestResponse response = leaveRequestService.submit(userId, leaveRequest);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(LeaveStatus.REROUTED_TO_HR);

        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository).save(captor.capture());

        LeaveRequest saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(LeaveStatus.REROUTED_TO_HR);
        assertThat(saved.isNoticePeriodWarning()).isEqualTo(true);
    }

    @Test
    void submit_shouldSetBalanceWarning_whenRequestedDaysExceedBalance() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));
        when(workingDayService.countWorkingDays(any(), any())).thenReturn(BigDecimal.valueOf(5));
        when(leaveRequestRepository.findOverlappingRequests(any(), any(), any(), any())).thenReturn(List.of());

        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setTotalEntitlement(BigDecimal.valueOf(10));
        leaveBalance.setLeaveUsed(BigDecimal.valueOf(5));
        leaveBalance.setPendingDays(BigDecimal.valueOf(2));
        leaveBalance.setCarriedOver(BigDecimal.ZERO);
        when(leaveBalanceRepository.findByUserIdAndYear(any(), anyInt())).thenReturn(Optional.of(leaveBalance));

        User hrUser = new User();
        hrUser.setRole(Role.HR);
        hrUser.setEmail("hr@test.com");
        hrUser.setActive(true);
        when(userRepository.findAllByRoleAndIsActiveTrue(Role.HR)).thenReturn(List.of(hrUser));

        when(workingDayService.normaliseToWorkingDay(any())).thenReturn(LocalDate.now());
        when(leaveRequestRepository.save(any())).thenReturn(new LeaveRequest());

        LocalDate startDate = LocalDate.now().plusDays(4);
        SubmitLeaveRequest leaveRequest = new SubmitLeaveRequest(
                leaveTypeId, startDate, startDate.plusDays(7), false, null
        );

        LeaveRequestResponse response = leaveRequestService.submit(userId, leaveRequest);

        assertThat(response).isNotNull();
        assertThat(response.balanceWarning()).isEqualTo(true);
    }

    @Test
    void getById_shouldThrowResourceNotFoundException_whenRequestNotFound() {
        UUID requestId = UUID.randomUUID();

        when(leaveRequestRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveRequestService.getLeaveRequestById(principal, requestId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void getById_shouldThrowForbiddenException_whenEmployeeViewsOtherUsersRequest() {
        UUID requestId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();

        LeaveRequest request = new LeaveRequest();
        request.setUserId(employeeId);
        request.setStatus(LeaveStatus.PENDING);
        request.setAssignedTo(Role.MANAGER);
        request.setLeaveTypeId(leaveTypeId);
        when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        User otherUser = new User();
        ReflectionTestUtils.setField(otherUser, "id", employeeId);
        when(userRepository.findById(employeeId)).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> leaveRequestService.getLeaveRequestById(principal, requestId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("not authorized to view");
    }

    @Test
    void getById_shouldThrowForbiddenException_whenManagerViewsRequestOutsideTheirTeam() {
        UUID requestId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        LeaveRequest request = new LeaveRequest();
        request.setUserId(userId);
        request.setStatus(LeaveStatus.PENDING);
        when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        submitter.setTeamId(teamId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));

        Team team = new Team();
        team.setManagerId(managerId);
        when(teamRepository.findByManagerId(managerId)).thenReturn(List.of(team));

        AuthenticatedUser managerPrincipal = new AuthenticatedUser(managerId, "manager@test.com", Role.MANAGER);

        assertThatThrownBy(() -> leaveRequestService.getLeaveRequestById(managerPrincipal, requestId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("not authorized to view");
    }

    @Test
    void getById_shouldReturnRequest_whenManagerViewsOwnRequest() {
        UUID requestId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();

        LeaveRequest request = new LeaveRequest();
        request.setUserId(managerId);
        request.setLeaveTypeId(leaveTypeId);
        request.setStatus(LeaveStatus.PENDING);
        when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        submitter.setRole(Role.MANAGER);
        ReflectionTestUtils.setField(submitter, "id", managerId);
        when(userRepository.findById(managerId)).thenReturn(Optional.of(submitter));

        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));

        AuthenticatedUser managerPrincipal = new AuthenticatedUser(managerId, "manager@test.com", Role.MANAGER);

        LeaveRequestResponse response = leaveRequestService.getLeaveRequestById(managerPrincipal, requestId);

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(managerId);
    }

    @Test
    void getById_shouldReturnRequest_whenManagerViewsTeamMembersRequest() {
        UUID requestId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        LeaveRequest request = new LeaveRequest();
        request.setUserId(userId);
        request.setLeaveTypeId(leaveTypeId);
        request.setStatus(LeaveStatus.PENDING);
        when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        submitter.setTeamId(teamId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));

        Team team = new Team();
        team.setManagerId(managerId);
        ReflectionTestUtils.setField(team, "id", teamId);
        when(teamRepository.findByManagerId(managerId)).thenReturn(List.of(team));

        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));

        AuthenticatedUser managerPrincipal = new AuthenticatedUser(managerId, "manager@test.com", Role.MANAGER);

        LeaveRequestResponse response = leaveRequestService.getLeaveRequestById(managerPrincipal, requestId);

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
    }

    @Test
    void getById_shouldReturnRequest_whenHrViewsAnyRequest() {
        UUID requestId = UUID.randomUUID();
        UUID hrId = UUID.randomUUID();

        LeaveRequest request = new LeaveRequest();
        request.setUserId(userId);
        request.setLeaveTypeId(leaveTypeId);
        request.setStatus(LeaveStatus.REROUTED_TO_HR);
        when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));

        AuthenticatedUser hrPrincipal = new AuthenticatedUser(hrId, "hr@test.com",  Role.HR);
        LeaveRequestResponse response = leaveRequestService.getLeaveRequestById(hrPrincipal, requestId);

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
    }

    @Test
    void cancel_shouldThrowResourceNotFoundException_whenRequestNotFound() {
        UUID requestId = UUID.randomUUID();

        when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveRequestService.cancelLeaveRequest(principal,  requestId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found")
                .hasMessageContaining(requestId.toString());
    }

    @Test
    void cancel_shouldThrowResourceNotFoundException_whenUserNotFound() {
        UUID requestId = UUID.randomUUID();

        LeaveRequest request = new LeaveRequest();
        ReflectionTestUtils.setField(request, "id", requestId);
        request.setUserId(userId);
        request.setLeaveTypeId(leaveTypeId);
        when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveRequestService.cancelLeaveRequest(principal, requestId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found")
                .hasMessageContaining(userId.toString());
    }

    @Test
    void cancel_shouldThrowForbiddenException_whenCancellingOtherUsersRequest() {
        UUID requestId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();

        LeaveRequest request = new LeaveRequest();
        ReflectionTestUtils.setField(request, "id", requestId);
        request.setUserId(userId);
        request.setLeaveTypeId(leaveTypeId);
        request.setStatus(LeaveStatus.PENDING);
        when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));

        AuthenticatedUser managerPrincipal = new AuthenticatedUser(managerId, "manager@test.com", Role.MANAGER);

        assertThatThrownBy(() -> leaveRequestService.cancelLeaveRequest(managerPrincipal, requestId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("You can only cancel your own leave request");
    }

    @Test
    void cancel_shouldThrowLeaveRequestStateException_whenRequestIsAlreadyApproved() {
        UUID requestId = UUID.randomUUID();

        LeaveRequest request = new LeaveRequest();
        ReflectionTestUtils.setField(request, "id", requestId);
        request.setUserId(userId);
        request.setLeaveTypeId(leaveTypeId);
        request.setStatus(LeaveStatus.APPROVED);
        when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));

        assertThatThrownBy(() -> leaveRequestService.cancelLeaveRequest(principal, requestId))
                .isInstanceOf(LeaveRequestStateException.class)
                .hasMessageContaining("Only PENDING, ESCALATED OR REROUTED_TO_HR leave requests can be cancelled");
    }

    @Test
    void cancel_shouldCancelAndRestorePendingDays_whenValidCancellation() {
        UUID requestId = UUID.randomUUID();
        LocalDate startDate = LocalDate.now().plusDays(7);

        LeaveRequest request = new LeaveRequest();
        ReflectionTestUtils.setField(request, "id", requestId);
        request.setUserId(userId);
        request.setLeaveTypeId(leaveTypeId);
        request.setStartDate(startDate);
        request.setEndDate(startDate.plusDays(4));
        request.setTotalWorkingDays(BigDecimal.valueOf(5));
        request.setStatus(LeaveStatus.PENDING);
        when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));

        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));

        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setUserId(userId);
        leaveBalance.setTotalEntitlement(BigDecimal.valueOf(10));
        leaveBalance.setLeaveUsed(BigDecimal.ZERO);
        leaveBalance.setPendingDays(BigDecimal.valueOf(5));
        leaveBalance.setCarriedOver(BigDecimal.ZERO);
        when(leaveBalanceRepository.findByUserIdAndYear(eq(userId), anyInt())).thenReturn(Optional.of(leaveBalance));

        when(leaveRequestRepository.save(any())).thenReturn(new LeaveRequest());

        LeaveRequestResponse response = leaveRequestService.cancelLeaveRequest(principal, requestId);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(LeaveStatus.CANCELLED);

        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository).save(captor.capture());
        verify(auditLogRepository).save(any());

        LeaveRequest saved =  captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getStatus()).isEqualTo(LeaveStatus.CANCELLED);

        ArgumentCaptor<LeaveBalance> balanceCaptor = ArgumentCaptor.forClass(LeaveBalance.class);
        verify(leaveBalanceRepository).save(balanceCaptor.capture());

        LeaveBalance balance = balanceCaptor.getValue();
        assertThat(balance.getUserId()).isEqualTo(userId);
        assertThat(balance.getLeaveUsed()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balance.getPendingDays()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void cancel_shouldCancelSuccessfully_whenLeaveTypeDoesNotRequireBalanceTracking() {
        UUID requestId = UUID.randomUUID();
        LocalDate startDate = LocalDate.now().plusDays(7);

        LeaveRequest request = new LeaveRequest();
        ReflectionTestUtils.setField(request, "id", requestId);
        request.setUserId(userId);
        request.setLeaveTypeId(leaveTypeId);
        request.setStartDate(startDate);
        request.setEndDate(startDate.plusDays(4));
        request.setTotalWorkingDays(BigDecimal.valueOf(5));
        request.setStatus(LeaveStatus.PENDING);
        when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        leaveType.setName("Unpaid Leave");
        leaveType.setCode("UNPAID");
        leaveType.setRequiresHrApproval(true);
        leaveType.setRequiresBalanceTracking(false);
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));

        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));
        when(leaveRequestRepository.save(any())).thenReturn(new LeaveRequest());

        LeaveRequestResponse response = leaveRequestService.cancelLeaveRequest(principal, requestId);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(LeaveStatus.CANCELLED);

        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository).save(captor.capture());

        LeaveRequest saved =  captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getStatus()).isEqualTo(LeaveStatus.CANCELLED);
        assertThat(saved.getActionedBy()).isEqualTo(userId);
    }

    @Test
    void approve_shouldThrowResourceNotFoundException_whenRequestNotFound() {
        UUID requestId = UUID.randomUUID();

        when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveRequestService.approveLeaveRequest(principal, requestId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found")
                .hasMessageContaining(requestId.toString());
    }

    @Test
    void approve_shouldThrowResourceNotFoundException_whenLeaveTypeNotFound() {
        UUID requestId = UUID.randomUUID();

        LeaveRequest request = new LeaveRequest();
        ReflectionTestUtils.setField(request, "id", requestId);
        request.setUserId(userId);
        request.setLeaveTypeId(leaveTypeId);
        when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveRequestService.approveLeaveRequest(principal, requestId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found")
                .hasMessageContaining(leaveTypeId.toString());
    }

    @Test
    void approve_shouldThrowForbiddenException_whenApprovingOwnRequest() {
        UUID requestId = UUID.randomUUID();

        LeaveRequest request = new LeaveRequest();
        ReflectionTestUtils.setField(request, "id", requestId);
        request.setUserId(userId);
        request.setLeaveTypeId(leaveTypeId);
        request.setStatus(LeaveStatus.PENDING);
        when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));

        assertThatThrownBy(() -> leaveRequestService.approveLeaveRequest(principal, requestId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("You cannot action your own leave request");
    }

    @Test
    void approve_shouldThrowForbiddenException_whenManagerApprovesHrApprovalLeave() {
        UUID requestId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();

        LeaveRequest request = new LeaveRequest();
        ReflectionTestUtils.setField(request, "id", requestId);
        request.setUserId(userId);
        request.setLeaveTypeId(leaveTypeId);
        request.setStatus(LeaveStatus.PENDING);
        when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        leaveType.setRequiresHrApproval(true);
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));

        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));

        AuthenticatedUser managerPrincipal = new AuthenticatedUser(managerId, "manager@test.com", Role.MANAGER);

        assertThatThrownBy(() -> leaveRequestService.approveLeaveRequest(managerPrincipal, requestId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("requires HR approval");
    }

    @Test
    void approve_shouldThrowForbiddenException_whenManagerApprovesOutsideTheirTeam() {
        UUID requestId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        LeaveRequest request = new LeaveRequest();
        ReflectionTestUtils.setField(request, "id", requestId);
        request.setUserId(userId);
        request.setLeaveTypeId(leaveTypeId);
        request.setStatus(LeaveStatus.PENDING);
        when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));

        submitter.setTeamId(UUID.randomUUID());
        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));

        Team team = new Team();
        ReflectionTestUtils.setField(team, "id", teamId);
        team.setManagerId(managerId);
        when(teamRepository.findByManagerId(managerId)).thenReturn(List.of(team));

        AuthenticatedUser managerPrincipal = new AuthenticatedUser(managerId, "manager@test.com", Role.MANAGER);

        assertThatThrownBy(() -> leaveRequestService.approveLeaveRequest(managerPrincipal, requestId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("can only action leave requests for your team");
    }

    @Test
    void approve_shouldThrowForbiddenException_whenAdminApprovesNonHrRequest() {
        UUID requestId = UUID.randomUUID();
        UUID adminId =  UUID.randomUUID();

        LeaveRequest request = new LeaveRequest();
        ReflectionTestUtils.setField(request, "id", requestId);
        request.setUserId(userId);
        request.setLeaveTypeId(leaveTypeId);
        request.setStatus(LeaveStatus.PENDING);
        when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));
        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));

        AuthenticatedUser adminPrincipal = new AuthenticatedUser(adminId, "admin@test.com", Role.ADMIN);

        assertThatThrownBy(() -> leaveRequestService.approveLeaveRequest(adminPrincipal, requestId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Admins can only action HR leave requests");
    }

    @Test
    void approve_shouldThrowForbiddenException_whenEmployeeTriesToApprove() {
        UUID requestId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();

        LeaveRequest request = new LeaveRequest();
        ReflectionTestUtils.setField(request, "id", requestId);
        request.setUserId(userId);
        request.setLeaveTypeId(leaveTypeId);
        request.setStatus(LeaveStatus.PENDING);
        when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));
        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));

        AuthenticatedUser employeePrincipal = new AuthenticatedUser(employeeId, "employee@test.com", Role.EMPLOYEE);

        assertThatThrownBy(() -> leaveRequestService.approveLeaveRequest(employeePrincipal, requestId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("You dont have permission to action leave requests");
    }

    @Test
    void approve_shouldThrowLeaveRequestStateException_whenRequestAlreadyApproved() {
        UUID requestId = UUID.randomUUID();
        UUID hrId = UUID.randomUUID();

        LeaveRequest request = new LeaveRequest();
        ReflectionTestUtils.setField(request, "id", requestId);
        request.setUserId(userId);
        request.setLeaveTypeId(leaveTypeId);
        request.setStatus(LeaveStatus.APPROVED);
        when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));

        AuthenticatedUser hrPrincipal = new AuthenticatedUser(hrId, "hr@test.com", Role.HR);

        assertThatThrownBy(() -> leaveRequestService.approveLeaveRequest(hrPrincipal, requestId))
                .isInstanceOf(LeaveRequestStateException.class)
                .hasMessageContaining("Only PENDING, ESCALATED or REROUTED_TO_HR leave requests can be actioned");
    }

    @Test
    void approve_shouldApproveAndDeductBalance_whenValidManagerApproves() {
        UUID requestId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        LocalDate startDate = LocalDate.now().plusDays(7);

        LeaveRequest request = new LeaveRequest();
        ReflectionTestUtils.setField(request, "id", requestId);
        request.setUserId(userId);
        request.setLeaveTypeId(leaveTypeId);
        request.setStartDate(startDate);
        request.setEndDate(startDate.plusDays(4));
        request.setTotalWorkingDays(BigDecimal.valueOf(5));
        request.setStatus(LeaveStatus.PENDING);
        when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));

        submitter.setTeamId(teamId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));

        Team team = new Team();
        ReflectionTestUtils.setField(team, "id", teamId);
        team.setManagerId(managerId);
        when(teamRepository.findByManagerId(managerId)).thenReturn(List.of(team));

        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setUserId(userId);
        leaveBalance.setTotalEntitlement(BigDecimal.valueOf(10));
        leaveBalance.setLeaveUsed(BigDecimal.ZERO);
        leaveBalance.setPendingDays(BigDecimal.valueOf(2));
        leaveBalance.setCarriedOver(BigDecimal.ZERO);
        when(leaveBalanceRepository.findByUserIdAndYear(eq(userId), anyInt())).thenReturn(Optional.of(leaveBalance));

        when(leaveRequestRepository.save(any())).thenReturn(new LeaveRequest());

        AuthenticatedUser managerPrincipal = new AuthenticatedUser(managerId, "manager@test.com", Role.MANAGER);

        LeaveRequestResponse response = leaveRequestService.approveLeaveRequest(managerPrincipal, requestId);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(LeaveStatus.APPROVED);

        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository).save(captor.capture());
        verify(auditLogRepository).save(any());

        LeaveRequest saved =  captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getStatus()).isEqualTo(LeaveStatus.APPROVED);

        ArgumentCaptor<LeaveBalance> balanceCaptor = ArgumentCaptor.forClass(LeaveBalance.class);
        verify(leaveBalanceRepository).save(balanceCaptor.capture());

        LeaveBalance balance = balanceCaptor.getValue();
        assertThat(balance.getUserId()).isEqualTo(userId);
        assertThat(balance.getLeaveUsed()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(balance.getPendingDays()).isEqualByComparingTo(BigDecimal.valueOf(-3));
    }

    @Test
    void approve_shouldApproveSuccessfully_whenLeaveTypeDoesNotRequireBalanceTracking() {
        UUID requestId = UUID.randomUUID();
        UUID hrId = UUID.randomUUID();
        LocalDate startDate = LocalDate.now().plusDays(7);

        LeaveRequest request = new LeaveRequest();
        ReflectionTestUtils.setField(request, "id", requestId);
        request.setUserId(userId);
        request.setLeaveTypeId(leaveTypeId);
        request.setStartDate(startDate);
        request.setEndDate(startDate.plusDays(4));
        request.setTotalWorkingDays(BigDecimal.valueOf(5));
        request.setStatus(LeaveStatus.PENDING);
        when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        leaveType.setName("Unpaid Leave");
        leaveType.setCode("UNPAID");
        leaveType.setRequiresHrApproval(true);
        leaveType.setRequiresBalanceTracking(false);
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));

        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));
        when(leaveRequestRepository.save(any())).thenReturn(new LeaveRequest());

        AuthenticatedUser hrPrincipal = new AuthenticatedUser(hrId, "hr@test.com", Role.HR);

        LeaveRequestResponse response = leaveRequestService.approveLeaveRequest(hrPrincipal, requestId);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(LeaveStatus.APPROVED);

        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository).save(captor.capture());

        LeaveRequest saved =  captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(saved.getActionedBy()).isEqualTo(hrId);
    }

    @Test
    void approve_shouldNotifyManager_whenHrApprovesHrApprovalLeaveForEmployeeWithTeam() {
        UUID requestId = UUID.randomUUID();
        UUID hrId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        LocalDate startDate = LocalDate.now().plusDays(7);

        LeaveRequest request = new LeaveRequest();
        ReflectionTestUtils.setField(request, "id", requestId);
        request.setUserId(userId);
        request.setLeaveTypeId(leaveTypeId);
        request.setStartDate(startDate);
        request.setEndDate(startDate.plusDays(4));
        request.setTotalWorkingDays(BigDecimal.valueOf(5));
        request.setStatus(LeaveStatus.PENDING);
        when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        leaveType.setName("Unpaid Leave");
        leaveType.setCode("UNPAID");
        leaveType.setRequiresHrApproval(true);
        leaveType.setRequiresBalanceTracking(false);
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));

        submitter.setTeamId(teamId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));

        Team team = new Team();
        ReflectionTestUtils.setField(team, "id", teamId);
        team.setManagerId(managerId);
        when(teamRepository.findById(submitter.getTeamId())).thenReturn(Optional.of(team));

        User managerUser =  new User();
        ReflectionTestUtils.setField(managerUser, "id", managerId);
        managerUser.setFirstName("manager");
        managerUser.setLastName("tester");
        managerUser.setEmail("manager.tester@test.com");
        managerUser.setRole(Role.MANAGER);
        when(userRepository.findByIdAndIsActiveTrue(managerId)).thenReturn(Optional.of(managerUser));

        when(leaveRequestRepository.save(any())).thenReturn(new LeaveRequest());

        AuthenticatedUser hrPrincipal = new AuthenticatedUser(hrId, "hr@test.com", Role.HR);

        LeaveRequestResponse response = leaveRequestService.approveLeaveRequest(hrPrincipal, requestId);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(LeaveStatus.APPROVED);
        verify(emailService)
                .sendManagerLeaveApprovalNotificationEmail(
                        managerUser.getEmail(),
                        managerUser.getFirstName(),
                        submitter.getFirstName() + " " + submitter.getLastName(),
                        leaveType.getName(),
                        request.getStartDate().toString(),
                        request.getEndDate().toString()
                );
    }

    @Test
    void reject_shouldThrowResourceNotFoundException_whenRequestNotFound() {
        UUID requestId = UUID.randomUUID();

        when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.empty());

        RejectLeaveRequest rejectRequest = new RejectLeaveRequest(
                "Rejection Reason"
        );

        assertThatThrownBy(() -> leaveRequestService.rejectLeaveRequest(principal, requestId, rejectRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found")
                .hasMessageContaining(requestId.toString());
    }

    @Test
    void reject_shouldThrowResourceNotFoundException_whenLeaveTypeNotFound() {
        UUID requestId = UUID.randomUUID();

        LeaveRequest request = new LeaveRequest();
        ReflectionTestUtils.setField(request, "id", requestId);
        request.setUserId(userId);
        request.setLeaveTypeId(leaveTypeId);
        when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.empty());

        RejectLeaveRequest rejectRequest = new RejectLeaveRequest(
                "Rejection Reason"
        );

        assertThatThrownBy(() -> leaveRequestService.rejectLeaveRequest(principal, requestId, rejectRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found")
                .hasMessageContaining(leaveTypeId.toString());
    }

    @Test
    void reject_shouldThrowLeaveRequestStateException_whenRequestAlreadyRejected() {
        UUID requestId = UUID.randomUUID();
        UUID hrId = UUID.randomUUID();

        LeaveRequest request = new LeaveRequest();
        ReflectionTestUtils.setField(request, "id", requestId);
        request.setUserId(userId);
        request.setLeaveTypeId(leaveTypeId);
        request.setStatus(LeaveStatus.APPROVED);
        when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));

        AuthenticatedUser hrPrincipal = new AuthenticatedUser(hrId, "hr@test.com", Role.HR);

        RejectLeaveRequest rejectRequest = new RejectLeaveRequest(
                "Rejection Reason"
        );

        assertThatThrownBy(() -> leaveRequestService.rejectLeaveRequest(hrPrincipal, requestId, rejectRequest))
                .isInstanceOf(LeaveRequestStateException.class)
                .hasMessageContaining("Only PENDING, ESCALATED or REROUTED_TO_HR leave requests can be actioned");
    }

    @Test
    void reject_shouldRejectAndRestorePendingDays_whenValidRejection() {
        UUID requestId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        LocalDate startDate = LocalDate.now().plusDays(7);

        LeaveRequest request = new LeaveRequest();
        ReflectionTestUtils.setField(request, "id", requestId);
        request.setUserId(userId);
        request.setLeaveTypeId(leaveTypeId);
        request.setStartDate(startDate);
        request.setEndDate(startDate.plusDays(4));
        request.setTotalWorkingDays(BigDecimal.valueOf(5));
        request.setStatus(LeaveStatus.PENDING);
        when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));

        submitter.setTeamId(teamId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));

        Team team = new Team();
        ReflectionTestUtils.setField(team, "id", teamId);
        team.setManagerId(managerId);
        when(teamRepository.findByManagerId(managerId)).thenReturn(List.of(team));

        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setUserId(userId);
        leaveBalance.setTotalEntitlement(BigDecimal.valueOf(10));
        leaveBalance.setLeaveUsed(BigDecimal.ZERO);
        leaveBalance.setPendingDays(BigDecimal.valueOf(5));
        leaveBalance.setCarriedOver(BigDecimal.ZERO);
        when(leaveBalanceRepository.findByUserIdAndYear(eq(userId), anyInt())).thenReturn(Optional.of(leaveBalance));

        when(leaveRequestRepository.save(any())).thenReturn(new LeaveRequest());

        AuthenticatedUser managerPrincipal = new AuthenticatedUser(managerId, "manager@test.com", Role.MANAGER);

        RejectLeaveRequest rejectRequest = new RejectLeaveRequest(
                "Rejection Reason"
        );

        LeaveRequestResponse response = leaveRequestService.rejectLeaveRequest(managerPrincipal, requestId, rejectRequest);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(LeaveStatus.REJECTED);

        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository).save(captor.capture());
        verify(auditLogRepository).save(any());

        LeaveRequest saved =  captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getStatus()).isEqualTo(LeaveStatus.REJECTED);

        ArgumentCaptor<LeaveBalance> balanceCaptor = ArgumentCaptor.forClass(LeaveBalance.class);
        verify(leaveBalanceRepository).save(balanceCaptor.capture());

        LeaveBalance balance = balanceCaptor.getValue();
        assertThat(balance.getUserId()).isEqualTo(userId);
        assertThat(balance.getLeaveUsed()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balance.getPendingDays()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void reject_shouldRejectSuccessfully_whenLeaveTypeDoesNotRequireBalanceTracking() {
        UUID requestId = UUID.randomUUID();
        UUID hrId = UUID.randomUUID();
        LocalDate startDate = LocalDate.now().plusDays(7);

        LeaveRequest request = new LeaveRequest();
        ReflectionTestUtils.setField(request, "id", requestId);
        request.setUserId(userId);
        request.setLeaveTypeId(leaveTypeId);
        request.setStartDate(startDate);
        request.setEndDate(startDate.plusDays(4));
        request.setTotalWorkingDays(BigDecimal.valueOf(5));
        request.setStatus(LeaveStatus.PENDING);
        when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        leaveType.setName("Unpaid Leave");
        leaveType.setCode("UNPAID");
        leaveType.setRequiresHrApproval(true);
        leaveType.setRequiresBalanceTracking(false);
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));

        when(userRepository.findById(userId)).thenReturn(Optional.of(submitter));
        when(leaveRequestRepository.save(any())).thenReturn(new LeaveRequest());

        AuthenticatedUser hrPrincipal = new AuthenticatedUser(hrId, "hr@test.com", Role.HR);

        RejectLeaveRequest rejectRequest = new RejectLeaveRequest(
                "Rejection Reason"
        );

        LeaveRequestResponse response = leaveRequestService.rejectLeaveRequest(hrPrincipal, requestId, rejectRequest);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(LeaveStatus.REJECTED);

        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository).save(captor.capture());

        LeaveRequest saved =  captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getStatus()).isEqualTo(LeaveStatus.REJECTED);
        assertThat(saved.getActionedBy()).isEqualTo(hrId);
    }
 }
