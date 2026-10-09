package com.shreekrishna.organics.cart.service;

import com.shreekrishna.organics.cart.dto.*;
import com.shreekrishna.organics.cart.entity.Cart;
import com.shreekrishna.organics.cart.entity.CartItem;
import com.shreekrishna.organics.cart.repository.CartRepository;
import com.shreekrishna.organics.cart.repository.CartItemRepository;
import com.shreekrishna.organics.inventory.entity.Inventory;
import com.shreekrishna.organics.inventory.repository.InventoryRepository;
import com.shreekrishna.organics.product.entity.Product;
import com.shreekrishna.organics.product.entity.ProductVariant;
import com.shreekrishna.organics.product.repository.ProductVariantRepository;
import com.shreekrishna.organics.user.repository.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryRepository inventoryRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductVariantRepository variantRepository,
            InventoryRepository inventoryRepository,
            UserRepository userRepository,
            EntityManager entityManager
    ) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.variantRepository = variantRepository;
        this.inventoryRepository = inventoryRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    public CartResponse getCart(String email) {
        Long userId = getUserId(email);

        return cartRepository.findByUserId(userId)
                .map(this::buildResponse)
                .orElseGet(this::emptyCart);
    }

    public CartResponse addItem(
            String email,
            AddCartItemRequest request
    ) {
        Long userId = lockUserAndGetId(email);

        ProductVariant variant = getPurchasableVariant(
                request.variantId()
        );

        Cart cart = getOrCreateCart(userId);

        CartItem item = cartItemRepository
                .findByCartIdAndVariantId(
                        cart.getId(),
                        variant.getId()
                )
                .orElse(null);

        int existingQuantity = item == null
                ? 0
                : item.getQuantity();

        long requestedTotal = (long) existingQuantity
                + request.quantity();

        if (requestedTotal > Integer.MAX_VALUE) {
            throw conflict("Cart quantity is too large");
        }

        int newQuantity = (int) requestedTotal;

        validateStock(variant.getId(), newQuantity);

        if (item == null) {
            item = new CartItem();
            item.setCartId(cart.getId());
            item.setVariantId(variant.getId());
        }

        item.setQuantity(newQuantity);
        cartItemRepository.save(item);

        return buildResponse(cart);
    }

    public CartResponse updateItem(
            String email,
            Long itemId,
            UpdateCartItemRequest request
    ) {
        Long userId = lockUserAndGetId(email);
        Cart cart = getExistingCart(userId);

        CartItem item = cartItemRepository
                .findByIdAndCartId(itemId, cart.getId())
                .orElseThrow(() -> notFound(
                        "Cart item not found"
                ));

        getPurchasableVariant(item.getVariantId());

        validateStock(
                item.getVariantId(),
                request.quantity()
        );

        item.setQuantity(request.quantity());
        cartItemRepository.save(item);

        return buildResponse(cart);
    }

    public CartResponse removeItem(
            String email,
            Long itemId
    ) {
        Long userId = lockUserAndGetId(email);
        Cart cart = getExistingCart(userId);

        CartItem item = cartItemRepository
                .findByIdAndCartId(itemId, cart.getId())
                .orElseThrow(() -> notFound(
                        "Cart item not found"
                ));

        cartItemRepository.delete(item);
        cartItemRepository.flush();

        return buildResponse(cart);
    }

    public CartResponse clearCart(String email) {
        Long userId = lockUserAndGetId(email);

        Cart cart = cartRepository
                .findByUserIdForUpdate(userId)
                .orElse(null);

        if (cart == null) {
            return emptyCart();
        }

        cartItemRepository.deleteByCartId(cart.getId());
        cartItemRepository.flush();

        return buildResponse(cart);
    }

    private Long getUserId(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> notFound(
                        "Authenticated user not found"
                ))
                .getId();
    }

    private Long lockUserAndGetId(String email) {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> notFound(
                        "Authenticated user not found"
                ));

        // Serialize cart mutations for the same customer.
        // This also protects the initial cart creation.
        entityManager.lock(user, LockModeType.PESSIMISTIC_WRITE);

        return user.getId();
    }

    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> {
                    Cart cart = new Cart();
                    cart.setUserId(userId);
                    return cartRepository.saveAndFlush(cart);
                });
    }

    private Cart getExistingCart(Long userId) {
        return cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> notFound(
                        "Cart not found"
                ));
    }

    private ProductVariant getPurchasableVariant(
            Long variantId
    ) {
        ProductVariant variant = variantRepository
                .findById(variantId)
                .orElseThrow(() -> notFound(
                        "Product variant not found"
                ));

        Product product = variant.getProduct();

        if (!Boolean.TRUE.equals(variant.getActive())
                || !Boolean.TRUE.equals(product.getActive())) {
            throw conflict(
                    "This product variant is not available"
            );
        }

        return variant;
    }

    private void validateStock(
            Long variantId,
            int requestedQuantity
    ) {
        Inventory inventory = inventoryRepository
                .findByVariant_Id(variantId)
                .orElseThrow(() -> conflict(
                        "Inventory is not available"
                ));

        int available = inventory.getAvailableQuantity();

        if (requestedQuantity > available) {
            throw conflict(
                    "Insufficient stock. Available: " + available
            );
        }
    }

    private CartResponse buildResponse(Cart cart) {
        List<CartItem> cartItems =
                cartItemRepository.findByCartIdOrderByIdAsc(
                        cart.getId()
                );

        List<CartItemResponse> responses = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int totalItems = 0;

        for (CartItem item : cartItems) {
            ProductVariant variant = variantRepository
                    .findById(item.getVariantId())
                    .orElseThrow(() -> notFound(
                            "Variant referenced by cart not found"
                    ));

            Product product = variant.getProduct();

            int availableStock = inventoryRepository
                    .findByVariant_Id(variant.getId())
                    .map(Inventory::getAvailableQuantity)
                    .orElse(0);

            BigDecimal lineTotal = variant.getPrice()
                    .multiply(BigDecimal.valueOf(
                            item.getQuantity()
                    ));

            boolean available =
                    Boolean.TRUE.equals(product.getActive())
                            && Boolean.TRUE.equals(variant.getActive())
                            && availableStock >= item.getQuantity();

            responses.add(new CartItemResponse(
                    item.getId(),
                    product.getId(),
                    product.getName(),
                    variant.getId(),
                    variant.getSizeValue(),
                    variant.getSizeUnit(),
                    variant.getPrice(),
                    variant.getMrp(),
                    item.getQuantity(),
                    lineTotal,
                    availableStock,
                    available
            ));

            subtotal = subtotal.add(lineTotal);
            totalItems = Math.addExact(
                    totalItems,
                    item.getQuantity()
            );
        }

        return new CartResponse(
                cart.getId(),
                responses,
                totalItems,
                subtotal
        );
    }

    private CartResponse emptyCart() {
        return new CartResponse(
                null,
                List.of(),
                0,
                BigDecimal.ZERO
        );
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                message
        );
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message
        );
    }
}