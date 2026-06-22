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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    @Transactional
    public TeamResponse createTeam(CreateTeamRequest request) {

        if (teamRepository.existsByName(request.name())) {
            throw new ConflictException("Team with this name already exists");
        }

        User manager = userRepository.findById(request.managerId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.managerId().toString()));

        if (!manager.getRole().equals(Role.MANAGER) || !manager.isActive()) {
            throw new BusinessRuleException("User is not a manager or is not active");
        }

        Team newTeam = new Team();
        newTeam.setName(request.name());
        newTeam.setManagerId(request.managerId());

        Team savedTeam = teamRepository.save(newTeam);

        if (manager.getTeamId() == null) {
            manager.setTeamId(savedTeam.getId());
            userRepository.save(manager);
        }

        return TeamResponse.from(savedTeam);
    }

    public PageResponse<TeamResponse> getAllTeams(AuthenticatedUser principal, Pageable pageable) {

        Page<Team> teams = switch (principal.role()) {
            case MANAGER -> teamRepository.findByManagerId(principal.userId(), pageable);
            case HR, ADMIN -> teamRepository.findAll(pageable);
            case EMPLOYEE -> throw new ForbiddenException("You are not authorized to view this resource");
        };

        return PageResponse.from(teams.map(TeamResponse::from));
    }

    public TeamResponse getTeamById(UUID teamId, AuthenticatedUser principal) {

        Team team = switch (principal.role()) {
            case MANAGER -> {
                Team teamFound = teamRepository.findById(teamId)
                        .orElseThrow(() -> new ResourceNotFoundException("Team", "id", teamId.toString()));
                if (!teamFound.getManagerId().equals(principal.userId())) {
                    throw new ForbiddenException("You are not authorized to view this resource");
                }
                yield teamFound;
            }
            case HR, ADMIN -> teamRepository.findById(teamId)
                    .orElseThrow(() -> new ResourceNotFoundException("Team", "id", teamId.toString()));
            case EMPLOYEE -> throw new ForbiddenException("You are not authorized to view this resource");
        };

        return TeamResponse.from(team);
    }

    @Transactional
    public TeamResponse updateTeam(UUID teamId, UpdateTeamRequest request) {

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team", "id", teamId.toString()));

        if (request.name() != null) {
            team.setName(request.name());
        }

        if (request.managerId() != null) {
            User manager = userRepository.findById(request.managerId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.managerId().toString()));

            if (!manager.getRole().equals(Role.MANAGER) || !manager.isActive()) {
                throw new BusinessRuleException("User is not a manager or is not active");
            }

            team.setManagerId(request.managerId());

            if (manager.getTeamId() == null) {
                manager.setTeamId(team.getId());
                userRepository.save(manager);
            }
        }

        return TeamResponse.from(teamRepository.save(team));
    }

    public void deleteTeam(UUID teamId) {

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team", "id", teamId.toString()));

        if (userRepository.existsByTeamId(teamId)) {
            throw new ConflictException("Cannot delete the team having members.");
        }

        teamRepository.delete(team);
    }
}
