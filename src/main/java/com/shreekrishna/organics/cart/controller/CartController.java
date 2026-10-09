package com.shreekrishna.organics.cart.controller;

import com.shreekrishna.organics.cart.dto.AddCartItemRequest;
import com.shreekrishna.organics.cart.dto.UpdateCartItemRequest;
import com.shreekrishna.organics.cart.dto.CartResponse;
import com.shreekrishna.organics.cart.service.CartService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse getCart(Authentication authentication) {
        return cartService.getCart(authentication.getName());
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.OK)
    public CartResponse addItem(
            Authentication authentication,
            @Valid @RequestBody AddCartItemRequest request
    ) {
        return cartService.addItem(
                authentication.getName(),
                request
        );
    }

    @PutMapping("/items/{itemId}")
    public CartResponse updateItem(
            Authentication authentication,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemRequest request
    ) {
        return cartService.updateItem(
                authentication.getName(),
                itemId,
                request
        );
    }

    @DeleteMapping("/items/{itemId}")
    public CartResponse removeItem(
            Authentication authentication,
            @PathVariable Long itemId
    ) {
        return cartService.removeItem(
                authentication.getName(),
                itemId
        );
    }

    @DeleteMapping
    public CartResponse clearCart(
            Authentication authentication
    ) {
        return cartService.clearCart(
                authentication.getName()
        );
    }
}