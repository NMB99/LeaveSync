package com.leavesync.common;

import com.leavesync.leavebalance.LeaveBalanceResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public record LeaveBalancePageResponse(

        List<LeaveBalanceResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last

) {
    public static LeaveBalancePageResponse from(Page<LeaveBalanceResponse> page) {
        return new LeaveBalancePageResponse(
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
