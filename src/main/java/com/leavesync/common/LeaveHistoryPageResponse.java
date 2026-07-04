package com.leavesync.common;

import com.leavesync.report.LeaveHistoryResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public record LeaveHistoryPageResponse(

        List<LeaveHistoryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last

) {
    public static LeaveHistoryPageResponse from(Page<LeaveHistoryResponse> page) {
        return new LeaveHistoryPageResponse(
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
