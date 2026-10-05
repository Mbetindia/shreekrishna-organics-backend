package com.shreekrishna.organics.auth.service;

import com.shreekrishna.organics.auth.dto.RegisterRequest;
import com.shreekrishna.organics.auth.dto.RegisterResponse;
import com.shreekrishna.organics.exception.ResourceAlreadyExistsException;
import com.shreekrishna.organics.user.entity.Role;
import com.shreekrishna.organics.user.entity.User;
import com.shreekrishna.organics.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new ResourceAlreadyExistsException(
                    "An account with this email already exists"
            );
        }

        String mobile = normalizeMobile(request.mobile());

        if (mobile != null && userRepository.existsByMobile(mobile)) {
            throw new ResourceAlreadyExistsException(
                    "An account with this mobile number already exists"
            );
        }

        User user = User.builder()
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .email(email)
                .mobile(mobile)
                .password(passwordEncoder.encode(request.password()))
                .role(Role.CUSTOMER)
                .enabled(true)
                .build();

        User savedUser = userRepository.save(user);

        return new RegisterResponse(
                savedUser.getId(),
                savedUser.getFirstName(),
                savedUser.getLastName(),
                savedUser.getEmail(),
                savedUser.getMobile(),
                savedUser.getRole()
        );
    }

    private String normalizeMobile(String mobile) {

        if (mobile == null || mobile.isBlank()) {
            return null;
        }

        return mobile.trim();
    }
}