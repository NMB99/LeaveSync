package com.leavesync.leavetype;

import com.leavesync.entity.LeaveType;
import com.leavesync.exception.ConflictException;
import com.leavesync.exception.ResourceNotFoundException;
import com.leavesync.repository.LeaveTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeaveTypeService {

    private final LeaveTypeRepository leaveTypeRepository;

    @Cacheable(value = "leaveTypes", key = "'all'")
    public List<LeaveTypeResponse> getLeaveTypes() {

        return leaveTypeRepository.findAll()
                .stream()
                .map(LeaveTypeResponse::from)
                .toList();
    }

    @CacheEvict(value = "leaveTypes", allEntries = true)
    public LeaveTypeResponse updateLeaveType(UUID id, UpdateLeaveTypeRequest request) {

        LeaveType type = leaveTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveType", "id", id.toString()));

        if (!type.getName().equalsIgnoreCase(request.name())
                && leaveTypeRepository.existsByName(request.name())) {
            throw new ConflictException("Leave type with name " + request.name() + " already exists");
        }

        type.setName(request.name());
        type.setRequiresHrApproval(request.requiresHrApproval());
        type.setRequiresReason(request.requiresReason());
        type.setRequiresBalanceTracking(request.requiresBalanceTracking());

        return LeaveTypeResponse.from(leaveTypeRepository.save(type));
    }

}
