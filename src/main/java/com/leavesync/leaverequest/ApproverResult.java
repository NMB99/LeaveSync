package com.leavesync.leaverequest;

import com.leavesync.entity.User;

import java.util.List;

public record ApproverResult(

        List<User> approvers,
        boolean rerouted

) {
}
