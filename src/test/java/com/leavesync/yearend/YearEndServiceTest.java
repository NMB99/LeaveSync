package com.leavesync.yearend;

import com.leavesync.email.EmailService;
import com.leavesync.entity.PublicHoliday;
import com.leavesync.entity.User;
import com.leavesync.enums.Role;
import com.leavesync.leavebalance.LeaveBalanceService;
import com.leavesync.repository.PublicHolidayRepository;
import com.leavesync.repository.UserRepository;
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

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class YearEndServiceTest {

    @Mock
    private LeaveBalanceService leaveBalanceService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PublicHolidayRepository publicHolidayRepository;

    @InjectMocks
    private YearEndService yearEndService;

    UUID employeeId;
    User employee;
    UUID managerId;
    UUID adminId;
    User manager;
    UUID hrId;
    User hr;
    User admin;

    @BeforeEach
    public void setUp() {
        employeeId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        hrId = UUID.randomUUID();
        adminId = UUID.randomUUID();

        employee = new User();
        ReflectionTestUtils.setField(employee, "id", employeeId);
        employee.setFirstName("Employee");
        employee.setLastName("Tests");
        employee.setEmail("nilay@test.com");
        employee.setRole(Role.EMPLOYEE);

        manager = new User();
        ReflectionTestUtils.setField(manager, "id", managerId);
        manager.setFirstName("Manager");
        manager.setLastName("Tests");
        manager.setEmail("manager@test.com");
        manager.setRole(Role.MANAGER);

        hr = new User();
        ReflectionTestUtils.setField(hr, "id", hrId);
        hr.setFirstName("HR");
        hr.setLastName("Tests");
        hr.setEmail("hr@test.com");
        hr.setRole(Role.HR);

        admin = new User();
        ReflectionTestUtils.setField(admin, "id", adminId);
        admin.setFirstName("Admin");
        admin.setLastName("Tests");
        admin.setEmail("admin@test.com");
        admin.setRole(Role.ADMIN);
    }

    @Test
    void processAnnualRollover_shouldCallCreateBalanceForEachUser_whenAllSucceed() {
        when(userRepository.findByIsActiveTrue()).thenReturn(List.of(employee, manager, hr));

        yearEndService.processAnnualRollover();

        verify(leaveBalanceService, times(3)).createLeaveBalanceForYear(any(User.class), eq(LocalDate.now().getYear()));
        verifyNoMoreInteractions(leaveBalanceService);
    }

    @Test
    void processAnnualRollover_shouldContinueProcessing_whenOneUserThrows() {
        when(userRepository.findByIsActiveTrue()).thenReturn(List.of(employee, manager, hr));
        doThrow(new RuntimeException("Balance creation failed"))
                .when(leaveBalanceService)
                .createLeaveBalanceForYear(eq(employee), anyInt());

        yearEndService.processAnnualRollover();

        verify(leaveBalanceService).createLeaveBalanceForYear(eq(employee), anyInt());
        verify(leaveBalanceService).createLeaveBalanceForYear(eq(manager), anyInt());
        verify(leaveBalanceService).createLeaveBalanceForYear(eq(hr), anyInt());
        verifyNoMoreInteractions(leaveBalanceService);
    }

    @Test
    void processAnnualRollover_shouldNotCallCreateBalance_whenNoActiveUsers() {
        when(userRepository.findByIsActiveTrue()).thenReturn(List.of());

        yearEndService.processAnnualRollover();

        verify(leaveBalanceService, never()).createLeaveBalanceForYear(any(), anyInt());
    }

    @Test
    void sendYearEndWarnings_shouldSendEmailAndAddEntry_whenBalanceExceedsFive() {
        when(userRepository.findByIsActiveTrue()).thenReturn(List.of(employee, manager, hr));
        when(leaveBalanceService.getRemainingLeaveBalance(any(), anyInt())).thenReturn(Optional.of(BigDecimal.valueOf(7)));
        when(userRepository.findAllByRoleAndIsActiveTrue(Role.HR)).thenReturn(List.of(hr));

        yearEndService.sendYearEndWarnings();

        verify(leaveBalanceService, times(3)).getRemainingLeaveBalance(any(), anyInt());

        ArgumentCaptor<BigDecimal> daysAtRiskCaptor = ArgumentCaptor.forClass(BigDecimal.class);

        verify(emailService, times(3)).sendYearEndWarningEmail(any(), any(), any(), daysAtRiskCaptor.capture());
        daysAtRiskCaptor.getAllValues().forEach(value ->
                assertThat(value).isEqualByComparingTo(new BigDecimal("2"))
        );
        verify(emailService, times(1)).sendYearEndSummaryEmail(any(), any(), any());
        verifyNoMoreInteractions(leaveBalanceService);
        verifyNoMoreInteractions(emailService);
    }

    @Test
    void sendYearEndWarnings_shouldNotSendEmail_whenBalanceIsExactlyFive() {
        when(userRepository.findByIsActiveTrue()).thenReturn(List.of(employee, manager, hr));
        when(leaveBalanceService.getRemainingLeaveBalance(any(), anyInt())).thenReturn(Optional.of(BigDecimal.valueOf(5)));

        yearEndService.sendYearEndWarnings();

        verify(leaveBalanceService, times(3)).getRemainingLeaveBalance(any(), anyInt());
        verify(emailService, never()).sendYearEndWarningEmail(any(), any(), any(), any());
        verify(emailService, never()).sendYearEndSummaryEmail(any(), any(), any());
        verifyNoMoreInteractions(leaveBalanceService);
        verifyNoMoreInteractions(emailService);
    }

    @Test
    void sendYearEndWarnings_shouldNotSendEmail_whenBalanceIsBelowFive() {
        when(userRepository.findByIsActiveTrue()).thenReturn(List.of(employee, manager, hr));
        when(leaveBalanceService.getRemainingLeaveBalance(any(), anyInt())).thenReturn(Optional.of(BigDecimal.valueOf(3)));

        yearEndService.sendYearEndWarnings();

        verify(leaveBalanceService, times(3)).getRemainingLeaveBalance(any(), anyInt());
        verify(emailService, never()).sendYearEndWarningEmail(any(), any(), any(), any());
        verify(emailService, never()).sendYearEndSummaryEmail(any(), any(), any());
        verifyNoMoreInteractions(leaveBalanceService);
        verifyNoMoreInteractions(emailService);
    }

    @Test
    void sendYearEndWarnings_shouldSkipUser_whenNoBalanceFound() {
        when(userRepository.findByIsActiveTrue()).thenReturn(List.of(employee, manager, hr));
        when(leaveBalanceService.getRemainingLeaveBalance(eq(employeeId), anyInt())).thenReturn(Optional.empty());
        when(leaveBalanceService.getRemainingLeaveBalance(eq(managerId), anyInt())).thenReturn(Optional.of(BigDecimal.valueOf(6)));
        when(leaveBalanceService.getRemainingLeaveBalance(eq(hrId), anyInt())).thenReturn(Optional.of(BigDecimal.valueOf(8)));
        when(userRepository.findAllByRoleAndIsActiveTrue(Role.HR)).thenReturn(List.of(hr));

        yearEndService.sendYearEndWarnings();

        verify(leaveBalanceService, times(3)).getRemainingLeaveBalance(any(), anyInt());
        verify(emailService, times(2)).sendYearEndWarningEmail(any(), any(), any(), any());
        verify(emailService, times(1)).sendYearEndSummaryEmail(any(), any(), any());
        verifyNoMoreInteractions(leaveBalanceService);
        verifyNoMoreInteractions(emailService);
    }

    @Test
    void sendYearEndWarnings_shouldSortEntriesByDaysAtRiskDescending_whenMultipleFlaggedUsers() {
        when(userRepository.findByIsActiveTrue()).thenReturn(List.of(employee, manager, hr));
        when(leaveBalanceService.getRemainingLeaveBalance(eq(employeeId), anyInt())).thenReturn(Optional.of(BigDecimal.valueOf(7)));
        when(leaveBalanceService.getRemainingLeaveBalance(eq(managerId), anyInt())).thenReturn(Optional.of(BigDecimal.valueOf(6)));
        when(leaveBalanceService.getRemainingLeaveBalance(eq(hrId), anyInt())).thenReturn(Optional.of(BigDecimal.valueOf(8)));
        when(userRepository.findAllByRoleAndIsActiveTrue(Role.HR)).thenReturn(List.of(hr));

        yearEndService.sendYearEndWarnings();

        verify(leaveBalanceService, times(3)).getRemainingLeaveBalance(any(), anyInt());
        verify(emailService, times(3)).sendYearEndWarningEmail(any(), any(), any(), any());

        ArgumentCaptor<List<YearEndWarningEntry>> entitiesCaptor = ArgumentCaptor.forClass(List.class);
        verify(emailService, times(1)).sendYearEndSummaryEmail(any(), any(), entitiesCaptor.capture());

        List<YearEndWarningEntry> entities = entitiesCaptor.getValue();
        assertThat(entities).hasSize(3);
        assertThat(entities.get(0).daysAtRisk()).isEqualByComparingTo(new BigDecimal("3"));
        assertThat(entities.get(1).daysAtRisk()).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(entities.get(2).daysAtRisk()).isEqualByComparingTo(new BigDecimal("1"));

        verifyNoMoreInteractions(leaveBalanceService);
        verifyNoMoreInteractions(emailService);
    }

    @Test
    void sendYearEndWarnings_shouldNotSendHrSummary_whenNoFlaggedUsers() {
        when(userRepository.findByIsActiveTrue()).thenReturn(List.of(employee, manager, hr));
        when(leaveBalanceService.getRemainingLeaveBalance(any(), anyInt())).thenReturn(Optional.of(new BigDecimal(3)));

        yearEndService.sendYearEndWarnings();

        verify(leaveBalanceService, times(3)).getRemainingLeaveBalance(any(), anyInt());
        verify(emailService, never()).sendYearEndWarningEmail(any(), any(), any(), any());
        verify(emailService, never()).sendYearEndSummaryEmail(any(), any(), any());
        verifyNoMoreInteractions(leaveBalanceService);
        verifyNoMoreInteractions(emailService);
    }

    @Test
    void sendYearEndWarnings_shouldSendSummaryToEachHrUser_whenMultipleHrUsers() {
        User hr1 = new User();
        ReflectionTestUtils.setField(hr1, "id", UUID.randomUUID());
        hr1.setFirstName("HR1");
        hr1.setLastName("Tests");
        hr1.setEmail("hr1@test.com");
        hr1.setRole(Role.HR);

        when(userRepository.findByIsActiveTrue()).thenReturn(List.of(employee, manager, hr, hr1));
        when(leaveBalanceService.getRemainingLeaveBalance(any(), anyInt())).thenReturn(Optional.of(BigDecimal.valueOf(7)));
        when(userRepository.findAllByRoleAndIsActiveTrue(Role.HR)).thenReturn(List.of(hr, hr1));

        yearEndService.sendYearEndWarnings();

        verify(leaveBalanceService, times(4)).getRemainingLeaveBalance(any(), anyInt());
        verify(emailService, times(4)).sendYearEndWarningEmail(any(), any(), any(), any());
        verify(emailService, times(2)).sendYearEndSummaryEmail(any(), any(), any());
        verifyNoMoreInteractions(leaveBalanceService);
        verifyNoMoreInteractions(emailService);
    }

    @Test
    void sendYearEndWarnings_shouldNotSendSummary_whenFlaggedUsersExistButNoHrUsers() {
        when(userRepository.findByIsActiveTrue()).thenReturn(List.of(employee, manager));
        when(leaveBalanceService.getRemainingLeaveBalance(any(), anyInt())).thenReturn(Optional.of(new BigDecimal(8)));
        when(userRepository.findAllByRoleAndIsActiveTrue(Role.HR)).thenReturn(List.of());

        yearEndService.sendYearEndWarnings();

        verify(leaveBalanceService, times(2)).getRemainingLeaveBalance(any(), anyInt());
        verify(emailService, times(2)).sendYearEndWarningEmail(any(), any(), any(), any());
        verify(emailService, never()).sendYearEndSummaryEmail(any(), any(), any());
        verifyNoMoreInteractions(leaveBalanceService);
        verifyNoMoreInteractions(emailService);
    }

    @Test
    void sendYearEndWarnings_shouldContinueProcessing_whenEmailThrowsForOneUser() {
        when(userRepository.findByIsActiveTrue()).thenReturn(List.of(employee, manager, hr));
        when(leaveBalanceService.getRemainingLeaveBalance(any(), anyInt())).thenReturn(Optional.of(BigDecimal.valueOf(7)));
        when(userRepository.findAllByRoleAndIsActiveTrue(Role.HR)).thenReturn(List.of());
        lenient().doThrow(new RuntimeException("email failed"))
                .when(emailService)
                .sendYearEndWarningEmail(eq(manager.getEmail()), any(), any(), any());

        yearEndService.sendYearEndWarnings();

        verify(leaveBalanceService, times(3)).getRemainingLeaveBalance(any(), anyInt());
        verify(emailService, times(3)).sendYearEndWarningEmail(any(), any(), any(), any());
        verify(emailService, never()).sendYearEndSummaryEmail(any(), any(), any());
        verifyNoMoreInteractions(leaveBalanceService);
        verifyNoMoreInteractions(emailService);
    }

    @Test
    void checkNewYearPublicHolidays_shouldNotSendEmail_whenAllRegionsHaveData() {
        when(publicHolidayRepository.findDistinctRegions()).thenReturn(List.of("ENGLAND", "SCOTLAND", "WALES", "NORTH IRELAND"));
        when(publicHolidayRepository.findByRegionAndDateBetween(any(), any(), any())).thenReturn(List.of(new PublicHoliday()));

        yearEndService.checkNewYearPublicHolidays();

        verify(userRepository, never()).findAllByRoleAndIsActiveTrue(any(Role.class));
        verify(emailService, never()).sendMissingPublicHolidaysEmail(any(), any(), anyInt(), any());
        verifyNoMoreInteractions(emailService);
    }

    @Test
    void checkNewYearPublicHolidays_shouldNotifyHrAndAdmin_whenSomeRegionsMissingData() {
        when(publicHolidayRepository.findDistinctRegions()).thenReturn(List.of("ENGLAND", "SCOTLAND", "WALES",  "NORTH IRELAND"));
        when(publicHolidayRepository.findByRegionAndDateBetween(eq("ENGLAND"), any(), any())).thenReturn(List.of(new PublicHoliday()));
        when(publicHolidayRepository.findByRegionAndDateBetween(eq("SCOTLAND"), any(), any())).thenReturn(List.of(new PublicHoliday()));
        when(publicHolidayRepository.findByRegionAndDateBetween(eq("WALES"), any(), any())).thenReturn(List.of(new PublicHoliday()));
        when(publicHolidayRepository.findByRegionAndDateBetween(eq("NORTH IRELAND"), any(), any())).thenReturn(List.of());
        when(userRepository.findAllByRoleAndIsActiveTrue(Role.HR)).thenReturn(List.of(hr));
        when(userRepository.findAllByRoleAndIsActiveTrue(Role.ADMIN)).thenReturn(List.of(admin));

        yearEndService.checkNewYearPublicHolidays();

        verify(userRepository, times(2)).findAllByRoleAndIsActiveTrue(any(Role.class));
        verify(emailService, times(2)).sendMissingPublicHolidaysEmail(any(), any(), anyInt(), any());
        verifyNoMoreInteractions(userRepository);
        verifyNoMoreInteractions(emailService);
    }

    @Test
    void checkNewYearPublicHolidays_shouldNotSendEmail_whenNoRegionsInDb() {
        when(publicHolidayRepository.findDistinctRegions()).thenReturn(List.of());

        yearEndService.checkNewYearPublicHolidays();

        verify(publicHolidayRepository, never()).findByRegionAndDateBetween(any(), any(), any());
        verify(userRepository, never()).findAllByRoleAndIsActiveTrue(any(Role.class));
        verify(emailService, never()).sendMissingPublicHolidaysEmail(any(), any(), anyInt(), any());
    }

    @Test
    void checkNewYearPublicHolidays_shouldContinueProcessing_whenEmailThrowsForOneRecipient() {
        when(publicHolidayRepository.findDistinctRegions()).thenReturn(List.of("ENGLAND", "SCOTLAND", "WALES", "NORTH IRELAND"));
        when(publicHolidayRepository.findByRegionAndDateBetween(any(), any(), any())).thenReturn(List.of());
        when(userRepository.findAllByRoleAndIsActiveTrue(Role.HR)).thenReturn(List.of(hr));
        when(userRepository.findAllByRoleAndIsActiveTrue(Role.ADMIN)).thenReturn(List.of(admin));
        lenient().doThrow(new RuntimeException("email failed"))
                .when(emailService)
                .sendMissingPublicHolidaysEmail(eq(admin.getEmail()), any(), anyInt(), any());

        yearEndService.checkNewYearPublicHolidays();

        verify(publicHolidayRepository, times(4)).findByRegionAndDateBetween(any(), any(), any());
        verify(userRepository, times(2)).findAllByRoleAndIsActiveTrue(any(Role.class));
        verify(emailService, times(2)).sendMissingPublicHolidaysEmail(any(), any(), anyInt(), any());
        verifyNoMoreInteractions(userRepository);
        verifyNoMoreInteractions(emailService);
    }

    @Test
    void checkNewYearPublicHolidays_shouldUseNextYear_whenCalculatingTargetYear() {
        when(publicHolidayRepository.findDistinctRegions()).thenReturn(List.of("ENGLAND", "SCOTLAND", "WALES", "NORTH IRELAND"));
        when(publicHolidayRepository.findByRegionAndDateBetween(any(), any(), any())).thenReturn(List.of());
        when(userRepository.findAllByRoleAndIsActiveTrue(Role.HR)).thenReturn(List.of(hr));
        when(userRepository.findAllByRoleAndIsActiveTrue(Role.ADMIN)).thenReturn(List.of(admin));

        yearEndService.checkNewYearPublicHolidays();

        ArgumentCaptor<Integer> yearCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(emailService, times(2)).sendMissingPublicHolidaysEmail(any(), any(), yearCaptor.capture(), any());
        yearCaptor.getAllValues().forEach(year ->
                assertThat(year).isEqualTo(LocalDate.now().getYear() + 1));
    }
}
