package com.leavesync.common;

import com.leavesync.team.TeamResponse;
import com.leavesync.user.UserResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public record TeamPageResponse(

        List<TeamResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last

) {
    public static TeamPageResponse from(Page<TeamResponse> page) {
        return new TeamPageResponse(
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
