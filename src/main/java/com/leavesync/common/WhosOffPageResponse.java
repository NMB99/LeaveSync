package com.leavesync.common;

import com.leavesync.report.WhosOffResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public record WhosOffPageResponse(

        List<WhosOffResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last

) {
    public static WhosOffPageResponse from(Page<WhosOffResponse> page) {
        return new WhosOffPageResponse(
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
