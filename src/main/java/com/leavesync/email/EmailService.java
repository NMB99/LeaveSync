package com.leavesync.email;

import com.leavesync.yearend.YearEndWarningEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.from}")
    private String from;

    public void sendInviteEmail(String toEmail, String firstName, String inviteToken) {

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject("Invitation to Join LeaveSync");
        message.setText("Hi " + firstName + ",\n\n"
                + "You have been invited to join LeaveSync.\n\n"
                + "Your account has been created. Click the link below to accept the invitation and set the password:\n\n"
                + inviteToken + "\n\n"
                + "This invitation is valid for 72 hours.\n\n"
                + "The LeaveSync team"
        );

        mailSender.send(message);
    }

    public void sendPasswordResetEmail(String toEmail, String firstName, String resetToken) {

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject("Password Reset Request");
        message.setText("Hi " + firstName + ",\n\n"
                + "You have requested to reset your password. Click the link below to set a new password:\n\n"
                + resetToken + "\n\n"
                + "This link is valid for 1 hour. If you did not request this, please ignore this email.\n\n"
                + "The LeaveSync team"
        );

        mailSender.send(message);
    }

    public void sendWelcomeEmail(String toEmail, String firstName) {

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject("Welcome to LeaveSync");
        message.setText("Hi " + firstName + ",\n\n"
                + "Thank you for joining LeaveSync. We are excited to have you on board.\n\n"
                + "Your account is now active. You can log in and start managing your leave.\n\n"
                + "The LeaveSync team"
        );

        mailSender.send(message);
    }

    public void sendDeboardingEmail (String toEmail, String firstName) {

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject("Goodbye from LeaveSync");
        message.setText("Hi " + firstName + ",\n\n"
                + "Thank you for being part of LeaveSync. We wish you all the best.\n\n"
                + "Your LeaveSync account has been deactivated by an administrator.\n\n"
                + "If you believe this is a mistake, please contact your HR team.\n\n"
                + "The LeaveSync team"
        );

        mailSender.send(message);
    }

    public void sendLeaveRequestEmailToApprover (
            String approverEmail,
            String approverFirstName,
            String employeeFullName,
            String leaveType,
            String startDate,
            String endDate,
            BigDecimal workingDays
    ) {

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(approverEmail);
        message.setSubject("New Leave Request: Action Required");
        message.setText("Hi " + approverFirstName + ",\n\n"
                + employeeFullName + " has submitted a leave request for your review.\n\n"
                + "Leave Type: " + leaveType + "\n"
                + "From: " + startDate + "\n"
                + "To: " + endDate + "\n"
                + "Working Days: " + workingDays + "\n\n"
                + "Please log in to LeaveSync to approve or reject this request.\n\n"
                + "The LeaveSync team"
        );

        mailSender.send(message);
    }

    public void sendLeaveApprovalEmail (
            String toEmail,
            String firstName,
            String leaveType,
            String startDate,
            String endDate
    ) {

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject("Leave Request Approved");
        message.setText("Hi " + firstName + ",\n\n"
                + "Your " + leaveType + " leave has been approved.\n\n"
                + "From: " + startDate +"\n"
                + "To: " + endDate + "\n\n"
                + "The LeaveSync team"
        );

        mailSender.send(message);
    }

    public void sendLeaveRejectionEmail (
            String toEmail,
            String firstName,
            String leaveType,
            String startDate,
            String endDate,
            String reason
    ) {

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject("Leave Request Rejected");
        message.setText("Hi " + firstName + ",\n\n"
                + "Your " + leaveType + " leave has been rejected.\n\n"
                + "From: " + startDate +"\n"
                + "To: " + endDate + "\n"
                + "Reason: " + reason + "\n\n"
                + "If you have any questions, please contact your manager.\n\n"
                + "The LeaveSync team"
        );

        mailSender.send(message);
    }

    public void sendYearEndWarningEmail (
            String toEmail,
            String firstName,
            BigDecimal remainingBalance,
            BigDecimal daysAtRisk
    ) {

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject("Action Required: Annual Leave Expiring Soon");
        message.setText("Hi " + firstName + ",\n\n"
                + "This is a reminder that you have " + remainingBalance + " days of annual leave remaining.\n\n"
                + "Up to 5 days will carry over into the new year. The remaining " + daysAtRisk + " day(s) will expire on 31st December if not taken.\n\n"
                + "Please log in to LeaveSync to review your leave balance and take necessary actions.\n\n"
                + "The LeaveSync team"
        );

        mailSender.send(message);
    }

    public void sendYearEndSummaryEmail (
            String toEmail,
            String hrFirstName,
            List<YearEndWarningEntry> entries
    ) {

        StringBuilder sb = new StringBuilder();
        sb.append("Hi ").append(hrFirstName).append(",\n\n")
                .append("The following employees have significant unused annual leave at risk of expiring on 31st December.\n\n");

        for (YearEndWarningEntry entry : entries) {
            sb.append("- ")
                    .append(entry.employeeFullName()).append(": ")
                    .append(entry.remainingBalance()).append(" days remaining, ")
                    .append(entry.daysAtRisk()).append(" day(s) at risk\n");
        }

        sb.append("\nPlease review and follow up with relevant manager as needed.\n\n");
        sb.append("The LeaveSync team");

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject("Annual Leave Summary");
        message.setText(sb.toString());

        mailSender.send(message);
    }

}
