package com.shreekrishna.organics.checkout.service;

import com.shreekrishna.organics.address.repository.AddressRepository;
import com.shreekrishna.organics.cart.entity.Cart;
import com.shreekrishna.organics.cart.entity.CartItem;
import com.shreekrishna.organics.cart.repository.CartItemRepository;
import com.shreekrishna.organics.cart.repository.CartRepository;
import com.shreekrishna.organics.checkout.dto.CheckoutDraftResponse;
import com.shreekrishna.organics.checkout.entity.CheckoutSession;
import com.shreekrishna.organics.checkout.entity.CheckoutSessionItem;
import com.shreekrishna.organics.checkout.repository.CheckoutSessionItemRepository;
import com.shreekrishna.organics.checkout.repository.CheckoutSessionRepository;
import com.shreekrishna.organics.inventory.repository.InventoryRepository;
import com.shreekrishna.organics.product.entity.ProductVariant;
import com.shreekrishna.organics.product.repository.ProductVariantRepository;
import com.shreekrishna.organics.user.entity.User;
import com.shreekrishna.organics.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CheckoutDraftService {
    private final UserRepository users;
    private final AddressRepository addresses;
    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final ProductVariantRepository variants;
    private final InventoryRepository inventory;
    private final CheckoutSessionRepository sessions;
    private final CheckoutSessionItemRepository sessionItems;
    private final EntityManager em;

    public CheckoutDraftService(UserRepository users, AddressRepository addresses,
                                CartRepository carts, CartItemRepository cartItems,
                                ProductVariantRepository variants, InventoryRepository inventory,
                                CheckoutSessionRepository sessions,
                                CheckoutSessionItemRepository sessionItems, EntityManager em) {
        this.users = users;
        this.addresses = addresses;
        this.carts = carts;
        this.cartItems = cartItems;
        this.variants = variants;
        this.inventory = inventory;
        this.sessions = sessions;
        this.sessionItems = sessionItems;
        this.em = em;
    }

    @Transactional
    public CheckoutDraftResponse create(String email, Long addressId) {
        User user = users.findByEmail(email).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Customer not found"));
        // Use the same user-level lock as CustomerOrderService.place() and CartService.
        em.lock(user, LockModeType.PESSIMISTIC_WRITE);
        // Do not permit another checkout while an initiated Razorpay payment may still settle.
        if (sessions.existsByUserIdAndStatusInAndIdNot(user.getId(),
                List.of("PAYMENT_INITIATING", "PAYMENT_PENDING"), "__no_matching_session__")) {
            throw error(HttpStatus.CONFLICT,
                    "Earlier online payment is unresolved. Check payment status before starting another checkout");
        }
        var shippingAddress = addresses.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Shipping address not found"));

        Cart cart = carts.findByUserIdForUpdate(user.getId())
                .orElseThrow(() -> error(HttpStatus.CONFLICT, "Cart is empty"));
        List<CartItem> selected = cartItems.findByCartIdOrderByIdAsc(cart.getId());
        if (selected.isEmpty()) throw error(HttpStatus.CONFLICT, "Cart is empty");

        CheckoutSession session = new CheckoutSession();
        session.setId(UUID.randomUUID().toString());
        session.setUserId(user.getId());
        session.setAddressId(addressId);
        session.setShippingFullName(shippingAddress.getFullName());
        session.setShippingMobile(shippingAddress.getMobile());
        session.setShippingAddressLine1(shippingAddress.getAddressLine1());
        session.setShippingAddressLine2(shippingAddress.getAddressLine2());
        session.setShippingLandmark(shippingAddress.getLandmark());
        session.setShippingCity(shippingAddress.getCity());
        session.setShippingState(shippingAddress.getState());
        session.setShippingPostalCode(shippingAddress.getPostalCode());
        session.setShippingCountry(shippingAddress.getCountry());
        session.setStatus("CREATED");
        session.setCurrency("INR");
        session.setStockReserved(false);
        session.setExpiresAt(LocalDateTime.now().plusMinutes(20));

        BigDecimal subtotal = BigDecimal.ZERO;
        List<CheckoutSessionItem> snapshots = new ArrayList<>();
        for (CartItem ci : selected) {
            if (ci.getQuantity() == null || ci.getQuantity() <= 0)
                throw error(HttpStatus.CONFLICT, "Invalid cart quantity");
            ProductVariant variant = variants.findById(ci.getVariantId())
                    .orElseThrow(() -> error(HttpStatus.CONFLICT, "Product variant missing"));
            if (!Boolean.TRUE.equals(variant.getActive())
                    || !Boolean.TRUE.equals(variant.getProduct().getActive()))
                throw error(HttpStatus.CONFLICT, "Product no longer available");
            int available = inventory.findByVariant_Id(variant.getId())
                    .map(inv -> inv.getAvailableQuantity()).orElse(0);
            if (available < ci.getQuantity())
                throw error(HttpStatus.CONFLICT, "Insufficient stock for " + variant.getSku());
            BigDecimal unitPrice = variant.getPrice();
            if (unitPrice == null || unitPrice.signum() < 0)
                throw error(HttpStatus.CONFLICT, "Invalid product price");
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(ci.getQuantity()));
            subtotal = subtotal.add(lineTotal);

            CheckoutSessionItem item = new CheckoutSessionItem();
            item.setSession(session);
            item.setVariantId(variant.getId());
            item.setProductId(variant.getProduct().getId());
            item.setProductName(variant.getProduct().getName());
            item.setSku(variant.getSku());
            item.setSizeValue(variant.getSizeValue());
            item.setSizeUnit(variant.getSizeUnit());
            item.setQuantity(ci.getQuantity());
            item.setUnitPrice(unitPrice);
            item.setUnitMrp(variant.getMrp());
            item.setLineTotal(lineTotal);
            snapshots.add(item);
        }
        if (subtotal.signum() <= 0) throw error(HttpStatus.CONFLICT, "Invalid checkout total");
        session.setSubtotal(subtotal);
        session.setTotalAmount(subtotal);
        sessions.saveAndFlush(session);
        sessionItems.saveAllAndFlush(snapshots);
        // Intentionally: NO order, NO Razorpay transaction, NO stock reservation, NO cart deletion.
        return response(session, snapshots);
    }

    @Transactional(readOnly = true)
    public CheckoutDraftResponse get(String email, String sessionId) {
        User user = users.findByEmail(email).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Customer not found"));
        CheckoutSession session = sessions.findByIdAndUserId(sessionId, user.getId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Checkout draft not found"));
        return response(session, sessionItems.findBySession_IdOrderByIdAsc(sessionId));
    }

    private CheckoutDraftResponse response(CheckoutSession session, List<CheckoutSessionItem> items) {
        return new CheckoutDraftResponse(session.getId(), session.getStatus(), session.getAddressId(),
                session.getSubtotal(), session.getTotalAmount(), session.getCurrency(), session.getExpiresAt(),
                items.stream().map(i -> new CheckoutDraftResponse.Item(i.getVariantId(), i.getProductName(),
                        i.getSku(), i.getQuantity(), i.getUnitPrice(), i.getLineTotal())).toList());
    }

    private ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
