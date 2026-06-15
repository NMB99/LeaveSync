package com.leavesync.leavetype;

import com.leavesync.repository.LeaveTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveTypeService {

    private final LeaveTypeRepository leaveTypeRepository;

    public List<LeaveTypeResponse> getLeaveTypes() {

        return leaveTypeRepository.findAll()
                .stream()
                .map(LeaveTypeResponse::from)
                .toList();
    }

}
