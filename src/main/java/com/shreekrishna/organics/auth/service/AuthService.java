package com.shreekrishna.organics.auth.service;

import com.shreekrishna.organics.auth.dto.AuthResponse;
import com.shreekrishna.organics.auth.dto.LoginRequest;
import com.shreekrishna.organics.auth.dto.RegisterRequest;
import com.shreekrishna.organics.auth.dto.RegisterResponse;
import com.shreekrishna.organics.exception.ResourceAlreadyExistsException;
import com.shreekrishna.organics.security.JwtService;
import com.shreekrishna.organics.user.entity.Role;
import com.shreekrishna.organics.user.entity.User;
import com.shreekrishna.organics.user.repository.UserRepository;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    // =========================
    // REGISTER
    // =========================

    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        String email = request.email()
                .trim()
                .toLowerCase();

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

    // =========================
    // LOGIN
    // =========================

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {

        String email = request.email()
                .trim()
                .toLowerCase();

        // Spring Security verifies:
        // 1. User exists
        // 2. Password matches BCrypt hash
        // 3. User is enabled
        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                email,
                                request.password()
                        )
                );

        /*
         * If authentication fails, Spring Security throws
         * BadCredentialsException / AuthenticationException.
         *
         * GlobalExceptionHandler will return 401.
         */

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated user could not be found"
                        )
                );

        UserDetails userDetails =
                (UserDetails) authentication.getPrincipal();

        String accessToken =
                jwtService.generateToken(userDetails);

        return new AuthResponse(
                accessToken,
                "Bearer",
                jwtService.getExpirationSeconds(),
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole()
        );
    }

    // =========================
    // HELPERS
    // =========================

    private String normalizeMobile(String mobile) {

        if (mobile == null || mobile.isBlank()) {
            return null;
        }

        return mobile.trim();
    }
}