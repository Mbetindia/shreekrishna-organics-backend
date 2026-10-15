package com.shreekrishna.organics.admin.dashboard.service;

import com.shreekrishna.organics.admin.dashboard.dto.AdminDashboardResponse;
import com.shreekrishna.organics.order.repository.OrderRepository;
import com.shreekrishna.organics.product.repository.ProductRepository;
import com.shreekrishna.organics.user.entity.Role;
import com.shreekrishna.organics.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminDashboardService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public AdminDashboardService(
            ProductRepository productRepository,
            OrderRepository orderRepository,
            UserRepository userRepository
    ) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }


        @Transactional(readOnly = true)
        public AdminDashboardResponse getDashboardSummary() {

            long totalProducts = productRepository.count();
            long totalOrders = orderRepository.count();
            long totalCustomers = userRepository.countByRole(Role.CUSTOMER);

            long paidOrders =
                    orderRepository.countByPaymentStatus(
                            com.shreekrishna.organics.order.entity.PaymentStatus.PAID
                    );

            long pendingOrders =
                    orderRepository.countByPaymentStatus(
                            com.shreekrishna.organics.order.entity.PaymentStatus.PENDING
                    );

            long codOrders =
                    orderRepository.countByPaymentMethod(
                            com.shreekrishna.organics.order.entity.PaymentMethod.COD
                    );

            long razorpayOrders =
                    orderRepository.countByPaymentMethod(
                            com.shreekrishna.organics.order.entity.PaymentMethod.RAZORPAY
                    );

            return new AdminDashboardResponse(
                    totalProducts,
                    totalOrders,
                    totalCustomers,
                    orderRepository.calculateRevenue(),
                    paidOrders,
                    pendingOrders,
                    codOrders,
                    razorpayOrders
            );
        }
    }
