package com.leavesync.leaverequest;

import com.leavesync.entity.User;
import com.leavesync.enums.Role;

import java.util.List;

public record ApproverResult(

        List<User> approvers,
        boolean rerouted,
        Role assignedTo

) {
}
