package com.leavesync.team;

import com.leavesync.common.PageResponse;
import com.leavesync.entity.Team;
import com.leavesync.entity.User;
import com.leavesync.enums.Role;
import com.leavesync.exception.BusinessRuleException;
import com.leavesync.exception.ConflictException;
import com.leavesync.exception.ForbiddenException;
import com.leavesync.exception.ResourceNotFoundException;
import com.leavesync.repository.TeamRepository;
import com.leavesync.repository.UserRepository;
import com.leavesync.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TeamServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TeamService teamService;

    UUID managerId;
    UUID teamId;
    User manager;
    Team team;
    AuthenticatedUser principal;

    @BeforeEach
    public void setUp() {
        managerId = UUID.randomUUID();
        teamId = UUID.randomUUID();

        manager = new User();
        ReflectionTestUtils.setField(manager, "id", managerId);
        manager.setEmail("manager.tests@test.com");
        manager.setRole(Role.MANAGER);
        manager.setActive(true);

        team = new Team();
        ReflectionTestUtils.setField(team, "id", teamId);
        team.setName("Testing Team");
        team.setManagerId(managerId);
    }

    @Test
    void create_shouldThrowConflictException_whenNameAlreadyExists() {
        when(teamRepository.existsByName(any())).thenReturn(true);

        assertThatThrownBy(() -> teamService.createTeam(new CreateTeamRequest("Team Name", managerId)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Team with this name already exists");
    }

    @Test
    void create_shouldThrowResourceNotFoundException_whenManagerNotFound() {
        when(userRepository.findById(managerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.createTeam(new CreateTeamRequest("Team Name", managerId)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found")
                .hasMessageContaining(managerId.toString());
    }

    @Test
    void create_shouldThrowBusinessRuleException_whenUserIsNotManagerOrNotActive() {
        manager.setRole(Role.HR);
        manager.setActive(false);
        when(userRepository.findById(managerId)).thenReturn(Optional.of(manager));

        assertThatThrownBy(() -> teamService.createTeam(new CreateTeamRequest("Team Name", managerId)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("User is not a manager or is not active");
    }

    @Test
    void create_shouldSetManagerTeamId_whenManagerTeamIdIsNull() {
        when(userRepository.findById(managerId)).thenReturn(Optional.of(manager));
        when(teamRepository.save(any(Team.class))).thenReturn(team);

        teamService.createTeam(new CreateTeamRequest("Team Name", managerId));

        assertThat(manager.getTeamId()).isEqualTo(team.getId());
        verify(userRepository).save(manager);
    }

    @Test
    void create_shouldNotUpdateManagerTeamId_whenManagerAlreadyHasTeamId() {
        UUID otherTeamId = UUID.randomUUID();

        manager.setTeamId(otherTeamId);
        when(userRepository.findById(managerId)).thenReturn(Optional.of(manager));

        when(teamRepository.save(any(Team.class))).thenReturn(team);

        teamService.createTeam(new CreateTeamRequest("Team Name", managerId));

        verify(userRepository, never()).save(manager);
        assertThat(manager.getTeamId()).isEqualTo(otherTeamId);
    }

    @Test
    void getAll_shouldThrowForbiddenException_whenCallerIsEmployee() {
        principal = new AuthenticatedUser(UUID.randomUUID(), "employee@test.com", Role.EMPLOYEE);

        assertThatThrownBy(() -> teamService.getAllTeams(principal, Pageable.unpaged()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not authorized to view this resource");
    }

    @Test
    void getAll_shouldReturnAllTeams_whenCallerIsHrOrAdmin() {
        principal = new AuthenticatedUser(UUID.randomUUID(), "admin@test.com", Role.ADMIN);

        Page<Team> page = new PageImpl<>(List.of(team));
        when(teamRepository.findAll(any(Pageable.class))).thenReturn(page);

        PageResponse<TeamResponse> response = teamService.getAllTeams(principal, Pageable.unpaged());
        verify(teamRepository).findAll(any(Pageable.class));

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
    }

    @Test
    void getAll_shouldReturnOnlyManagedTeams_whenCallerIsManager() {
        principal = new AuthenticatedUser(managerId, manager.getEmail(), manager.getRole());

        Page<Team> page = new PageImpl<>(List.of(team));
        when(teamRepository.findByManagerId(managerId, Pageable.unpaged())).thenReturn(page);

        PageResponse<TeamResponse> response = teamService.getAllTeams(principal, Pageable.unpaged());
        verify(teamRepository).findByManagerId(managerId, Pageable.unpaged());

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
    }

    @Test
    void getById_shouldThrowForbiddenException_whenCallerIsEmployee() {
        principal = new AuthenticatedUser(UUID.randomUUID(), "employee@test.com", Role.EMPLOYEE);

        assertThatThrownBy(() -> teamService.getTeamById(managerId, principal))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not authorized to view this resource");
    }

    @Test
    void getById_shouldThrowResourceNotFoundException_whenTeamNotFound_andCallerIsHrOrAdmin() {
        principal = new AuthenticatedUser(UUID.randomUUID(), "hr@test.com",  Role.HR);

        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.getTeamById(teamId, principal))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Team not found")
                .hasMessageContaining(teamId.toString());
    }

    @Test
    void getById_shouldThrowResourceNotFoundException_whenTeamNotFound_andCallerIsManager() {
        principal = new AuthenticatedUser(managerId, manager.getEmail(), manager.getRole());

        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.getTeamById(teamId, principal))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Team not found")
                .hasMessageContaining(teamId.toString());
    }

    @Test
    void getById_shouldThrowForbiddenException_whenManagerViewsTeamTheyDontManage() {
        principal = new AuthenticatedUser(managerId, manager.getEmail(), manager.getRole());

        team.setManagerId(UUID.randomUUID());
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> teamService.getTeamById(teamId, principal))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not authorized to view this resource");
    }

    @Test
    void getById_shouldReturnTeam_whenCallerIsHrOrAdmin() {
        principal = new AuthenticatedUser(UUID.randomUUID(), "hr@test.com", Role.HR);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        TeamResponse response = teamService.getTeamById(teamId, principal);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(teamId);
        verify(teamRepository).findById(teamId);
    }

    @Test
    void getById_shouldReturnTeam_whenManagerViewsOwnTeam() {
        principal = new AuthenticatedUser(managerId, manager.getEmail(), manager.getRole());

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        TeamResponse response = teamService.getTeamById(teamId, principal);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(teamId);
        assertThat(response.managerId()).isEqualTo(managerId);
        verify(teamRepository).findById(teamId);
    }

    @Test
    void update_shouldThrowResourceNotFoundException_whenTeamNotFound() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.updateTeam(teamId, new UpdateTeamRequest(null, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Team not found")
                .hasMessageContaining(teamId.toString());
    }

    @Test
    void update_shouldThrowBusinessRuleException_whenNameIsBlank() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> teamService.updateTeam(teamId, new UpdateTeamRequest("", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Team name cannot be empty");
    }

    @Test
    void update_shouldThrowConflictException_whenNameAlreadyExistsOnDifferentTeam() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamRepository.existsByName(anyString())).thenReturn(true);

        assertThatThrownBy(() -> teamService.updateTeam(teamId, new UpdateTeamRequest("TeamName", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("A team with name")
                .hasMessageContaining("TeamName already exists");
    }

    @Test
    void update_shouldThrowResourceNotFoundException_whenManagerNotFound() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(userRepository.findById(managerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.updateTeam(teamId, new UpdateTeamRequest("TeamName", managerId)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found")
                .hasMessageContaining(managerId.toString());
    }

    @Test
    void update_shouldThrowBusinessRuleException_whenUserIsNotManagerOrNotActive() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        manager.setRole(Role.EMPLOYEE);
        manager.setActive(false);
        when(userRepository.findById(managerId)).thenReturn(Optional.of(manager));

        assertThatThrownBy(() -> teamService.updateTeam(teamId, new UpdateTeamRequest("TeamName", managerId)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("User is not a manager or is not active");
    }

    @Test
    void update_shouldNotUpdateName_whenNameIsNull() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        when(userRepository.findById(managerId)).thenReturn(Optional.of(manager));
        when(teamRepository.save(any(Team.class))).thenReturn(team);

        TeamResponse response = teamService.updateTeam(teamId, new UpdateTeamRequest(null, managerId));

        verify(userRepository).save(manager);
        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo(team.getName());
    }

    @Test
    void update_shouldSetManagerTeamId_whenNewManagerTeamIdIsNull() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        when(userRepository.findById(managerId)).thenReturn(Optional.of(manager));
        when(teamRepository.save(any(Team.class))).thenReturn(team);

        TeamResponse response = teamService.updateTeam(teamId, new UpdateTeamRequest(null, managerId));

        assertThat(response).isNotNull();
        assertThat(response.managerId()).isEqualTo(managerId);
        assertThat(manager.getTeamId()).isEqualTo(teamId);
        verify(userRepository).save(manager);
        verify(teamRepository).save(team);
    }

    @Test
    void update_shouldNotUpdateManagerTeamId_whenManagerAlreadyHasTeamId() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        manager.setTeamId(UUID.randomUUID());
        when(userRepository.findById(managerId)).thenReturn(Optional.of(manager));
        when(teamRepository.save(any(Team.class))).thenReturn(team);

        TeamResponse response = teamService.updateTeam(teamId, new UpdateTeamRequest("TeamName", managerId));

        assertThat(response).isNotNull();
        assertThat(response.managerId()).isEqualTo(managerId);
        assertThat(manager.getTeamId()).isNotEqualTo(teamId);
        verify(userRepository, never()).save(manager);
        verify(teamRepository).save(team);
    }

    @Test
    void delete_shouldThrowResourceNotFoundException_whenTeamNotFound() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.deleteTeam(teamId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Team not found")
                .hasMessageContaining(teamId.toString());
    }

    @Test
    void delete_shouldThrowConflictException_whenTeamHasMembers() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(userRepository.existsByTeamId(teamId)).thenReturn(true);

        assertThatThrownBy(() -> teamService.deleteTeam(teamId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cannot delete the team having members.");
    }

    @Test
    void delete_shouldDeleteTeam_whenTeamIsEmpty() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        teamService.deleteTeam(teamId);
        verify(teamRepository).delete(team);
    }
}
