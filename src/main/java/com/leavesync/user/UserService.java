package com.leavesync.user;

import com.leavesync.entity.User;
import com.leavesync.exception.ConflictException;
import com.leavesync.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
}
