package com.leavesync.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

    public void sendInviteEmail(String toEmail, String firstName, String inviteToken) {
        log.info("INVITE EMAIL -> To: {}, Name: {}, Token: {}", toEmail, firstName, inviteToken);
    }

}
