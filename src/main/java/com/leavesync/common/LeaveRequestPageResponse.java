package com.leavesync.common;

import com.leavesync.leaverequest.LeaveRequestResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public record LeaveRequestPageResponse(

        List<LeaveRequestResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last

) {
    public static LeaveRequestPageResponse from(Page<LeaveRequestResponse> page) {
        return new LeaveRequestPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
