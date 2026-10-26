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
import com.shreekrishna.organics.product.service.VariantDeliveryPricingService;
import com.shreekrishna.organics.user.entity.User;
import com.shreekrishna.organics.user.repository.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
    private final VariantDeliveryPricingService deliveryPricing;
    private final EntityManager em;

    public CheckoutDraftService(
            UserRepository users,
            AddressRepository addresses,
            CartRepository carts,
            CartItemRepository cartItems,
            ProductVariantRepository variants,
            InventoryRepository inventory,
            CheckoutSessionRepository sessions,
            CheckoutSessionItemRepository sessionItems,
            VariantDeliveryPricingService deliveryPricing,
            EntityManager em
    ) {
        this.users = users;
        this.addresses = addresses;
        this.carts = carts;
        this.cartItems = cartItems;
        this.variants = variants;
        this.inventory = inventory;
        this.sessions = sessions;
        this.sessionItems = sessionItems;
        this.deliveryPricing = deliveryPricing;
        this.em = em;
    }

    @Transactional
    public CheckoutDraftResponse create(String username, Long addressId) {
        return createInternal(username, addressId, null, null);
    }

    /** Buy Now is based on exactly one selected variant, never on the cart. */
    @Transactional
    public CheckoutDraftResponse createBuyNow(String username, Long addressId,
                                              Long variantId, Integer quantity) {
        if (variantId == null || quantity == null || quantity <= 0 || quantity > 100) {
            throw error(HttpStatus.BAD_REQUEST, "Invalid Buy Now variant or quantity");
        }
        return createInternal(username, addressId, variantId, quantity);
    }

    private CheckoutDraftResponse createInternal(String username, Long addressId,
                                                 Long buyNowVariantId, Integer buyNowQuantity) {
        User user = findAuthenticatedCustomer(username);

        // Same user-level lock as CustomerOrderService and CartService.
        em.lock(user, LockModeType.PESSIMISTIC_WRITE);

        // Block new drafts while another online payment is unresolved.
        if (sessions.existsByUserIdAndStatusInAndIdNot(
                user.getId(),
                List.of("PAYMENT_INITIATING", "PAYMENT_PENDING"),
                "__no_matching_session__"
        )) {
            throw error(
                    HttpStatus.CONFLICT,
                    "Earlier online payment is unresolved. Check payment status before starting another checkout"
            );
        }

        var shippingAddress = addresses
                .findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() ->
                        error(HttpStatus.NOT_FOUND, "Shipping address not found"));

        List<CartItem> selected;
        if (buyNowVariantId == null) {
            Cart cart = carts.findByUserIdForUpdate(user.getId())
                    .orElseThrow(() -> error(HttpStatus.CONFLICT, "Cart is empty"));
            selected = cartItems.findByCartIdOrderByIdAsc(cart.getId());
            if (selected.isEmpty()) throw error(HttpStatus.CONFLICT, "Cart is empty");
        } else {
            // Do not read, update, or clear the customer's existing cart.
            CartItem single = new CartItem();
            single.setVariantId(buyNowVariantId);
            single.setQuantity(buyNowQuantity);
            selected = List.of(single);
        }

        CheckoutSession session = new CheckoutSession();

        session.setId(UUID.randomUUID().toString());
        session.setUserId(user.getId());
        session.setCheckoutSource(buyNowVariantId == null ? "CART" : "BUY_NOW");
        session.setAddressId(addressId);

        // Immutable shipping address snapshot.
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
        BigDecimal additionalTax = BigDecimal.ZERO;

        List<CheckoutSessionItem> snapshots = new ArrayList<>();

        for (CartItem ci : selected) {

            if (ci.getQuantity() == null || ci.getQuantity() <= 0) {
                throw error(HttpStatus.CONFLICT, "Invalid cart quantity");
            }

            ProductVariant variant = variants.findById(ci.getVariantId())
                    .orElseThrow(() ->
                            error(HttpStatus.CONFLICT, "Product variant missing"));

            if (!Boolean.TRUE.equals(variant.getActive())
                    || !Boolean.TRUE.equals(variant.getProduct().getActive())) {
                throw error(
                        HttpStatus.CONFLICT,
                        "Product no longer available"
                );
            }

            int available = inventory.findByVariant_Id(variant.getId())
                    .map(inv -> inv.getAvailableQuantity())
                    .orElse(0);

            if (available < ci.getQuantity()) {
                throw error(
                        HttpStatus.CONFLICT,
                        "Insufficient stock for " + variant.getSku()
                );
            }

            BigDecimal unitPrice = variant.getPrice();

            if (unitPrice == null || unitPrice.signum() < 0) {
                throw error(HttpStatus.CONFLICT, "Invalid product price");
            }

            BigDecimal lineTotal = unitPrice.multiply(
                    BigDecimal.valueOf(ci.getQuantity())
            );

            subtotal = subtotal.add(lineTotal);

            BigDecimal gstRate = variant.getProduct().getGstRate();
            if (gstRate == null) gstRate = BigDecimal.ZERO;
            if (gstRate.signum() < 0 || gstRate.compareTo(new BigDecimal("100")) > 0) {
                throw error(HttpStatus.CONFLICT, "Invalid GST rate for " + variant.getSku());
            }
            boolean includesGst = Boolean.TRUE.equals(variant.getProduct().getPriceIncludesGst());
            BigDecimal taxAmount = BigDecimal.ZERO.setScale(2);
            if (gstRate.signum() > 0) {
                taxAmount = includesGst
                        ? lineTotal.multiply(gstRate)
                        .divide(new BigDecimal("100").add(gstRate), 2, RoundingMode.HALF_UP)
                        : lineTotal.multiply(gstRate)
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                if (!includesGst) additionalTax = additionalTax.add(taxAmount);
            }

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
            item.setGstRate(gstRate);
            item.setPriceIncludesGst(includesGst);
            item.setTaxAmount(taxAmount);

            snapshots.add(item);
        }

        if (subtotal.signum() <= 0) {
            throw error(HttpStatus.CONFLICT, "Invalid checkout total");
        }

        // Calculate delivery charges using backend variant delivery rules.
        BigDecimal deliveryCharge;

        try {
            deliveryCharge = deliveryPricing.calculateTotalDeliveryCharge(
                    snapshots.stream()
                            .map(item ->
                                    new VariantDeliveryPricingService.VariantQuantity(
                                            item.getVariantId(),
                                            item.getQuantity()
                                    )
                            )
                            .toList()
            );
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw error(
                    HttpStatus.CONFLICT,
                    "Delivery pricing unavailable: " + ex.getMessage()
            );
        }

        if (deliveryCharge == null || deliveryCharge.signum() < 0) {
            throw error(
                    HttpStatus.CONFLICT,
                    "Invalid delivery charge"
            );
        }

        // Inclusive GST is already part of subtotal; only exclusive GST is added.
        BigDecimal totalAmount = subtotal.add(deliveryCharge).add(additionalTax);

        // Store both the product subtotal and delivery charge.
        session.setSubtotal(subtotal);
        session.setDeliveryCharge(deliveryCharge);
        session.setTotalAmount(totalAmount);

        sessions.saveAndFlush(session);
        sessionItems.saveAllAndFlush(snapshots);

        // No order, Razorpay transaction, stock reservation or cart deletion.
        return response(session, snapshots);
    }

    @Transactional(readOnly = true)
    public CheckoutDraftResponse get(String username, String sessionId) {

        User user = findAuthenticatedCustomer(username);

        CheckoutSession session = sessions
                .findByIdAndUserId(sessionId, user.getId())
                .orElseThrow(() ->
                        error(HttpStatus.NOT_FOUND, "Checkout draft not found"));

        return response(
                session,
                sessionItems.findBySession_IdOrderByIdAsc(sessionId)
        );
    }

    private User findAuthenticatedCustomer(String username) {
        if (username == null || username.isBlank()) {
            throw error(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        String identity = username.trim();
        return (identity.contains("@")
                ? users.findByEmail(identity.toLowerCase(java.util.Locale.ROOT))
                : users.findByMobile(identity))
                .filter(user -> user.isEnabled()
                        && user.getRole() != null && "CUSTOMER".equals(user.getRole().name()))
                .orElseThrow(() ->
                        error(HttpStatus.NOT_FOUND, "Customer not found"));
    }

    private CheckoutDraftResponse response(
            CheckoutSession session,
            List<CheckoutSessionItem> items
    ) {
        return new CheckoutDraftResponse(
                session.getId(),
                session.getStatus(),
                session.getAddressId(),
                session.getSubtotal(),
                session.getTotalAmount(),
                session.getCurrency(),
                // Session timestamps are stored as server-local LocalDateTime.
                // Send an explicit UTC instant so browsers in any timezone
                // interpret the same expiration moment.
                session.getExpiresAt() == null ? null : session.getExpiresAt()
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .truncatedTo(ChronoUnit.MILLIS)
                        .toString(),
                items.stream()
                        .map(i -> new CheckoutDraftResponse.Item(
                                i.getVariantId(),
                                i.getProductName(),
                                i.getSku(),
                                i.getQuantity(),
                                i.getUnitPrice(),
                                i.getLineTotal(),
                                i.getGstRate(),
                                i.getPriceIncludesGst(),
                                i.getTaxAmount()
                        ))
                        .toList(),
                session.getDeliveryCharge(),
                items.stream().map(i -> i.getTaxAmount() == null ? BigDecimal.ZERO : i.getTaxAmount())
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                items.stream().filter(i -> Boolean.TRUE.equals(i.getPriceIncludesGst()))
                        .map(i -> i.getTaxAmount() == null ? BigDecimal.ZERO : i.getTaxAmount())
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                items.stream().filter(i -> !Boolean.TRUE.equals(i.getPriceIncludesGst()))
                        .map(i -> i.getTaxAmount() == null ? BigDecimal.ZERO : i.getTaxAmount())
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );
    }

    private ResponseStatusException error(
            HttpStatus status,
            String message
    ) {
        return new ResponseStatusException(status, message);
    }
}
