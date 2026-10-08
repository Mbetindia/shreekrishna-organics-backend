package com.shreekrishna.organics.user.service;

import com.shreekrishna.organics.exception.ResourceAlreadyExistsException;
import com.shreekrishna.organics.exception.ResourceNotFoundException;
import com.shreekrishna.organics.user.dto.UpdateProfileRequest;
import com.shreekrishna.organics.user.dto.UserProfileResponse;
import com.shreekrishna.organics.user.entity.User;
import com.shreekrishna.organics.user.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String email) {

        User user = getUserByEmail(email);

        return mapToResponse(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(
            String email,
            UpdateProfileRequest request) {

        User user = getUserByEmail(email);

        String mobile = normalizeMobile(request.mobile());

        if (mobile != null
                && !mobile.equals(user.getMobile())
                && userRepository.existsByMobile(mobile)) {

            throw new ResourceAlreadyExistsException(
                    "An account with this mobile number already exists"
            );
        }

        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setMobile(mobile);

        User savedUser = userRepository.save(user);

        return mapToResponse(savedUser);
    }

    private User getUserByEmail(String email) {

        if (email == null || email.isBlank()) {
            throw new ResourceNotFoundException(
                    "Authenticated user could not be found"
            );
        }

        return userRepository
                .findByEmail(email.trim().toLowerCase())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Authenticated user could not be found"
                        )
                );
    }

    private UserProfileResponse mapToResponse(User user) {

        return new UserProfileResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getMobile(),
                user.getRole()
        );
    }

    private String normalizeMobile(String mobile) {

        if (mobile == null || mobile.isBlank()) {
            return null;
        }

        return mobile.trim();
    }
}