package com.leavesync.user;

import com.leavesync.entity.User;
import com.leavesync.exception.ConflictException;
import com.leavesync.exception.TokenException;
import com.leavesync.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    public UserResponse createUser(CreateUserRequest request) {

        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("User already exists with email: " + request.email());
        }

        String inviteToken = UUID.randomUUID().toString();
        LocalDateTime inviteTokenExpiry = LocalDateTime.now().plusHours(72);

        User user = new User();
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email());
        user.setMobileNumber(request.mobileNumber());
        user.setRole(request.role());
        user.setTeamId(request.teamId());
        user.setPasswordHash(passwordEncoder.encode(inviteToken));
        user.setActive(true);
        user.setInviteToken(inviteToken);
        user.setInviteTokenExpiry(inviteTokenExpiry);

        User savedUser = userRepository.save(user);
        emailService.sendInviteEmail(savedUser.getEmail(), savedUser.getFirstName(), inviteToken);

        return UserResponse.from(savedUser);
    }

    public void acceptInvite(AcceptInviteRequest request) {

        User user = userRepository.findByInviteToken(request.inviteToken())
                .orElseThrow(() -> new TokenException("Invalid or expired invite token"));

        if (user.getInviteTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new TokenException("Invalid or expired invite token");
        }

        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setInviteToken(null);
        user.setInviteTokenExpiry(null);

        userRepository.save(user);
    }

    public void forgotPassword(ForgotPasswordRequest request) {

        Optional<User> optionalUser = userRepository.findByEmail(request.email());

        if (optionalUser.isEmpty()) {
            return;
        }

        User user = optionalUser.get();
        String newResetToken = UUID.randomUUID().toString();

        user.setInviteToken(newResetToken);
        user.setInviteTokenExpiry(LocalDateTime.now().plusHours(1));

        userRepository.save(user);

        emailService.sendPasswordResetEmail(user.getEmail(), user.getFirstName(), newResetToken);
    }

    public void resetPassword(ResetPasswordRequest request) {

        User user = userRepository.findByInviteToken(request.resetToken())
                .orElseThrow(() -> new TokenException("Invalid or expired reset token"));

        if (user.getInviteTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new TokenException("Invalid or expired reset token");
        }

        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setInviteToken(null);
        user.setInviteTokenExpiry(null);

        userRepository.save(user);
    }
}
