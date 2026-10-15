package com.shreekrishna.organics.user.controller;

import com.shreekrishna.organics.user.dto.AdminCustomerResponse;
import com.shreekrishna.organics.user.entity.Role;
import com.shreekrishna.organics.user.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/customers")
public class AdminCustomerController {
    private final UserRepository userRepository;

    public AdminCustomerController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<AdminCustomerResponse>> getCustomers() {
        List<AdminCustomerResponse> customers = userRepository
                .findByRoleOrderByCreatedAtDesc(Role.CUSTOMER)
                .stream()
                .map(user -> new AdminCustomerResponse(
                        user.getId(),
                        user.getFirstName(),
                        user.getLastName(),
                        user.getEmail(),
                        user.getMobile(),
                        user.isEnabled(),
                        user.getCreatedAt()
                ))
                .toList();
        return ResponseEntity.ok(customers);
    }
}
