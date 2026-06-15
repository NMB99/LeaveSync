package com.leavesync.reports;

import com.leavesync.entity.LeaveRequest;
import com.leavesync.entity.LeaveType;
import com.leavesync.entity.Team;
import com.leavesync.entity.User;
import com.leavesync.enums.LeaveStatus;
import com.leavesync.enums.Role;
import com.leavesync.exception.ForbiddenException;
import com.leavesync.repository.LeaveRequestRepository;
import com.leavesync.repository.LeaveTypeRepository;
import com.leavesync.repository.TeamRepository;
import com.leavesync.repository.UserRepository;
import com.leavesync.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;

    public List<WhosOffResponse> getWhosOff(AuthenticatedUser principal, LocalDate date) {

        if (principal.role() == Role.EMPLOYEE) {

        }

        List<LeaveStatus> statuses = List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED);

        List<LeaveRequest> requests = switch (principal.role()) {
            case EMPLOYEE -> throw new ForbiddenException("Employees are not authorized to view/access this resource");
            case MANAGER -> {
                List<UUID> teamsIds = teamRepository.findByManagerId(principal.userId())
                        .stream()
                        .map(Team::getId)
                        .toList();
                List<UUID> usersIds = userRepository.findByTeamIdIn(teamsIds)
                        .stream()
                        .map(User::getId)
                        .toList();
                yield leaveRequestRepository
                        .findByStatusInAndDateWithinAndUserIdIn(statuses, date, usersIds);
            }
            case HR, ADMIN -> leaveRequestRepository
                        .findByStatusInAndDateWithin(statuses, date);
        };

        Map<UUID, User> userMap = userRepository.findAllById(
                requests.stream()
                        .map(LeaveRequest::getUserId).distinct()
                        .toList()
        ).stream().collect(Collectors.toMap(User::getId, Function.identity()));

        Map<UUID, LeaveType> leaveTypeMap = leaveTypeRepository.findAllById(
                requests.stream()
                        .map(LeaveRequest::getLeaveTypeId).distinct()
                        .toList()
        ).stream().collect(Collectors.toMap(LeaveType::getId, Function.identity()));

        return requests.stream()
                .map(rq -> WhosOffResponse.from(rq, userMap.get(rq.getUserId()), leaveTypeMap.get(rq.getLeaveTypeId())))
                .toList();
    }
}
