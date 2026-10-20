
package com.shreekrishna.organics.security;

import com.shreekrishna.organics.user.entity.User;
import com.shreekrishna.organics.user.repository.UserRepository;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("Invalid user");
        }

        String normalizedUsername = username.trim().toLowerCase();

        User user;

        // Existing Admin and Email-based Login
        if (normalizedUsername.contains("@")) {

            user = userRepository.findByEmail(normalizedUsername)
                    .orElseThrow(() ->
                            new UsernameNotFoundException(
                                    "Invalid email or password"
                            )
                    );

        } else {

            // Customer Mobile OTP Login
            user = userRepository.findByMobile(normalizedUsername)
                    .orElseThrow(() ->
                            new UsernameNotFoundException(
                                    "User not found"
                            )
                    );
        }

        // IMPORTANT:
        // Use the same identifier that was used to load the user.
        // Email JWT -> Email username
        // Mobile JWT -> Mobile username
        String loginIdentifier = normalizedUsername;

        return org.springframework.security.core.userdetails.User
                .withUsername(loginIdentifier)
                .password(
                        user.getPassword() != null
                                ? user.getPassword()
                                : ""
                )
                .roles(user.getRole().name())
                .disabled(!user.isEnabled())
                .build();
    }
}
