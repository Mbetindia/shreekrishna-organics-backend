
package com.shreekrishna.organics.auth.service;

import com.shreekrishna.organics.auth.dto.AuthResponse;
import com.shreekrishna.organics.auth.entity.UsedOtpToken;
import com.shreekrishna.organics.auth.repository.UsedOtpTokenRepository;
import com.shreekrishna.organics.security.CustomUserDetailsService;
import com.shreekrishna.organics.security.JwtService;
import com.shreekrishna.organics.user.entity.Role;
import com.shreekrishna.organics.user.entity.User;
import com.shreekrishna.organics.user.repository.UserRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class CustomerOtpAuthService {

    private final UserRepository userRepository;
    private final UsedOtpTokenRepository usedOtpTokenRepository;
    private final Msg91VerificationService msg91VerificationService;
    private final CustomUserDetailsService customUserDetailsService;
    private final JwtService jwtService;

    public CustomerOtpAuthService(
            UserRepository userRepository,
            UsedOtpTokenRepository usedOtpTokenRepository,
            Msg91VerificationService msg91VerificationService,
            CustomUserDetailsService customUserDetailsService,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.usedOtpTokenRepository = usedOtpTokenRepository;
        this.msg91VerificationService = msg91VerificationService;
        this.customUserDetailsService = customUserDetailsService;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse loginWithOtp(String accessToken) {

        if (accessToken == null || accessToken.isBlank()
                || accessToken.length() > 8192) {
            throw new IllegalArgumentException(
                    "Invalid OTP access token"
            );
        }

        // 1. Verify the access token with MSG91.
        // Never trust a mobile number supplied by the frontend.
        String mobile =
                msg91VerificationService.verifyAndGetMobile(
                        accessToken
                );

        // 2. Hash the verified token.
        String tokenHash = sha256(accessToken);

        // 3. Consume token using the database UNIQUE constraint.
        // saveAndFlush forces the INSERT before JWT generation.
        try {
            usedOtpTokenRepository.saveAndFlush(
                    new UsedOtpToken(tokenHash)
            );
        } catch (DataIntegrityViolationException ex) {
            throw new SecurityException(
                    "This OTP verification token has already been used"
            );
        }

        // 4. Find an existing customer or create a new one.
        User user = userRepository.findByMobile(mobile)
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .mobile(mobile)
                            .role(Role.CUSTOMER)
                            .enabled(true)
                            .build();

                    return userRepository.saveAndFlush(newUser);
                });

        // 5. OTP login is for customers only.
        if (user.getRole() != Role.CUSTOMER) {
            throw new SecurityException(
                    "OTP login is available only for customers"
            );
        }

        // 6. Reject disabled accounts.
        if (!user.isEnabled()) {
            throw new SecurityException(
                    "This customer account is disabled"
            );
        }

        // 7. Load mobile-based user details.
        UserDetails userDetails =
                customUserDetailsService.loadUserByUsername(
                        mobile
                );

        // 8. Generate JWT.
        String accessJwt =
                jwtService.generateToken(userDetails);

        // 9. Return the authentication response.
        return new AuthResponse(
                accessJwt,
                "Bearer",
                jwtService.getExpirationSeconds(),
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole(),
                user.getMobile()
        );
    }

    private String sha256(String value) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable",
                    ex
            );
        }
    }
}
