package com.leavesync.leavebalance;

import com.leavesync.entity.LeaveBalance;
import com.leavesync.entity.Team;
import com.leavesync.entity.User;
import com.leavesync.enums.Role;
import com.leavesync.exception.ForbiddenException;
import com.leavesync.exception.ResourceNotFoundException;
import com.leavesync.repository.LeaveBalanceRepository;
import com.leavesync.repository.TeamRepository;
import com.leavesync.repository.UserRepository;
import com.leavesync.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LeaveBalanceServiceTest {

    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamRepository teamRepository;

    @InjectMocks
    private LeaveBalanceService leaveBalanceService;

    private UUID userId;
    private User user;
    private UUID teamId;
    private Team team;
    private UUID managerId;
    private int year;
    private LeaveBalance leaveBalance;
    private AuthenticatedUser principal;


    @BeforeEach
    public void setUp() {
        userId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        teamId = UUID.randomUUID();
        year = 2025;

        user = new User();
        ReflectionTestUtils.setField(user, "id", userId);
        user.setFirstName("Nilay");
        user.setLastName("Tests");
        user.setEmail("nilay.test@leavesync.com");
        user.setRole(Role.EMPLOYEE);
        user.setActive(true);

        team = new Team();
        ReflectionTestUtils.setField(team, "id", teamId);
        team.setManagerId(UUID.randomUUID());

        leaveBalance = new LeaveBalance();
        leaveBalance.setUserId(userId);
        leaveBalance.setYear(year);
        leaveBalance.setTotalEntitlement(new BigDecimal("25"));
        leaveBalance.setCarriedOver(new BigDecimal("5"));
        leaveBalance.setLeaveUsed(new BigDecimal("10"));
        leaveBalance.setPendingDays(new BigDecimal("2"));

        principal = new AuthenticatedUser(managerId, "manager@test.com", Role.MANAGER);
    }

    @Test
    void createBalance_shouldNotSave_whenBalanceAlreadyExistsForYear() {
        when(leaveBalanceRepository.findByUserIdAndYear(userId, year)).thenReturn(Optional.of(leaveBalance));

        leaveBalanceService.createLeaveBalanceForYear(user, year);
        verify(leaveBalanceRepository, never()).save(any());
    }

    @Test
    void createBalance_shouldThrowResourceNotFoundException_whenPreviousYearBalanceMissing() {
        when(leaveBalanceRepository.findByUserIdAndYear(userId, year)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveBalanceService.createLeaveBalanceForYear(user, year))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("LeaveBalance not found")
                .hasMessageContaining(userId.toString() + "/" + (year - 1));
    }

    @Test
    void createBalance_shouldCapCarryOverAtFive_whenRemainingExceedsFive() {
        when(leaveBalanceRepository.findByUserIdAndYear(userId, year)).thenReturn(Optional.empty());
        when(leaveBalanceRepository.findByUserIdAndYear(userId, year - 1)).thenReturn(Optional.of(leaveBalance));

        leaveBalanceService.createLeaveBalanceForYear(user, year);

        ArgumentCaptor<LeaveBalance> captor = ArgumentCaptor.forClass(LeaveBalance.class);
        verify(leaveBalanceRepository).save(captor.capture());

        LeaveBalance saved =  captor.getValue();
        assertThat(saved.getCarriedOver()).isEqualByComparingTo(BigDecimal.valueOf(5));
    }

    @Test
    void createBalance_shouldCarryOverRemainingDays_whenRemainingIsLessThanFive() {
        when(leaveBalanceRepository.findByUserIdAndYear(userId, year)).thenReturn(Optional.empty());

        leaveBalance.setCarriedOver(BigDecimal.ZERO);
        leaveBalance.setLeaveUsed(BigDecimal.valueOf(20));
        when(leaveBalanceRepository.findByUserIdAndYear(userId, year - 1)).thenReturn(Optional.of(leaveBalance));

        leaveBalanceService.createLeaveBalanceForYear(user, year);

        ArgumentCaptor<LeaveBalance> captor = ArgumentCaptor.forClass(LeaveBalance.class);
        verify(leaveBalanceRepository).save(captor.capture());

        LeaveBalance saved =  captor.getValue();
        assertThat(saved.getCarriedOver()).isEqualByComparingTo(BigDecimal.valueOf(3));
    }

    @Test
    void createBalance_shouldSetCarryOverToZero_whenRemainingIsNegative() {
        when(leaveBalanceRepository.findByUserIdAndYear(userId, year)).thenReturn(Optional.empty());

        leaveBalance.setCarriedOver(BigDecimal.ZERO);
        leaveBalance.setLeaveUsed(BigDecimal.valueOf(20));
        leaveBalance.setPendingDays(BigDecimal.valueOf(7));
        when(leaveBalanceRepository.findByUserIdAndYear(userId, year - 1)).thenReturn(Optional.of(leaveBalance));

        leaveBalanceService.createLeaveBalanceForYear(user, year);

        ArgumentCaptor<LeaveBalance> captor = ArgumentCaptor.forClass(LeaveBalance.class);
        verify(leaveBalanceRepository).save(captor.capture());

        LeaveBalance saved =  captor.getValue();
        assertThat(saved.getCarriedOver()).isEqualByComparingTo(BigDecimal.valueOf(0));
    }

    @Test
    void getById_shouldThrowForbiddenException_whenManagerViewsBalanceOfUserWithNoTeam() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> leaveBalanceService.getLeaveBalanceById(principal, userId, year))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("You are not authorized to view this resource");
    }

    @Test
    void getById_shouldThrowForbiddenException_whenManagerViewsBalanceOutsideTheirTeam() {
        user.setTeamId(teamId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(teamRepository.findById(user.getTeamId())).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> leaveBalanceService.getLeaveBalanceById(principal, userId, year))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("You are not authorized to view this resource");
    }

    @Test
    void getById_shouldReturnBalance_whenManagerViewsOwnTeamMember() {
        user.setTeamId(teamId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        team.setManagerId(managerId);
        when(teamRepository.findById(user.getTeamId())).thenReturn(Optional.of(team));

        when(leaveBalanceRepository.findByUserIdAndYear(userId, year)).thenReturn(Optional.of(leaveBalance));

        LeaveBalanceResponse response = leaveBalanceService.getLeaveBalanceById(principal, userId, year);

        assertThat(response).isNotNull();
        assertThat(response.remainingBalance()).isEqualByComparingTo(BigDecimal.valueOf(18));
    }

    @Test
    void getRemaining_shouldReturnEmpty_whenNoBalanceFound() {
        when(leaveBalanceRepository.findByUserIdAndYear(userId, year)).thenReturn(Optional.empty());

        Optional<BigDecimal> response = leaveBalanceService.getRemainingLeaveBalance(userId, year);

        assertThat(response).isEmpty();
    }

    @Test
    void getRemaining_shouldReturnCorrectRemainingDays() {
        when(leaveBalanceRepository.findByUserIdAndYear(userId, year)).thenReturn(Optional.of(leaveBalance));

        Optional<BigDecimal> response = leaveBalanceService.getRemainingLeaveBalance(userId, year);

        assertThat(response).isPresent();
        assertThat(response.get()).isEqualByComparingTo(BigDecimal.valueOf(18));
    }
}
