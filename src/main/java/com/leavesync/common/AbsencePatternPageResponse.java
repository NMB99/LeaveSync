package com.leavesync.common;

import com.leavesync.report.AbsencePatternResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public record AbsencePatternPageResponse(

        List<AbsencePatternResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last

) {
    public static AbsencePatternPageResponse from(Page<AbsencePatternResponse> page) {
        return new AbsencePatternPageResponse(
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
