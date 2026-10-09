package com.shreekrishna.organics.cart.service;

import com.shreekrishna.organics.cart.dto.*;
import com.shreekrishna.organics.cart.entity.Cart;
import com.shreekrishna.organics.cart.entity.CartItem;
import com.shreekrishna.organics.cart.repository.CartItemRepository;
import com.shreekrishna.organics.cart.repository.CartRepository;
import com.shreekrishna.organics.inventory.entity.Inventory;
import com.shreekrishna.organics.inventory.repository.InventoryRepository;
import com.shreekrishna.organics.product.entity.Product;
import com.shreekrishna.organics.product.entity.ProductVariant;
import com.shreekrishna.organics.product.repository.ProductVariantRepository;
import com.shreekrishna.organics.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {
    private static final String EMAIL = "customer@example.com";
    private static final Long USER_ID = 7L;
    private static final Long CART_ID = 11L;
    private static final Long VARIANT_ID = 21L;
    private static final Long ITEM_ID = 31L;

    @Mock CartRepository cartRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock ProductVariantRepository variantRepository;
    @Mock InventoryRepository inventoryRepository;
    @Mock UserRepository userRepository;
    @Mock EntityManager entityManager;
    @Mock com.shreekrishna.organics.user.entity.User user;
    @Mock Cart cart;
    @Mock ProductVariant variant;
    @Mock Product product;
    @Mock Inventory inventory;

    @InjectMocks CartService service;

    @BeforeEach
    void setup() {
        // No global stubbing: each test declares only the collaborators it needs.
    }

    private void userExists() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(USER_ID);
    }

    private void cartExistsForMutation() {
        userExists();
        when(cartRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(cart));
        when(cart.getId()).thenReturn(CART_ID);
    }

    private void purchasableVariant() {
        when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant));
        when(variant.getProduct()).thenReturn(product);
        when(variant.getActive()).thenReturn(true);
        when(product.getActive()).thenReturn(true);
    }

    private void availableStock(int stock) {
        when(inventoryRepository.findByVariant_Id(VARIANT_ID)).thenReturn(Optional.of(inventory));
        when(inventory.getAvailableQuantity()).thenReturn(stock);
    }

    private void emptyResponseItems() {
        when(cartItemRepository.findByCartIdOrderByIdAsc(CART_ID)).thenReturn(List.of());
    }

    private void assertStatus(HttpStatus status, Runnable action) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(status, ex.getStatusCode());
    }

    @Test
    void getCart_whenNoCart_returnsEmptyResponse() {
        userExists();
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        CartResponse response = service.getCart(EMAIL);
        assertNull(response.cartId());
        assertTrue(response.items().isEmpty());
        assertEquals(0, response.totalItems());
        assertEquals(0, response.subtotal().compareTo(BigDecimal.ZERO));
        verifyNoInteractions(entityManager);
    }

    @Test
    void getCart_whenUserMissing_returns404() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        assertStatus(HttpStatus.NOT_FOUND, () -> service.getCart(EMAIL));
    }

    @Test
    void addItem_newVariant_savesQuantityAndDoesNotReserveStock() {
        cartExistsForMutation();
        purchasableVariant();
        when(variant.getId()).thenReturn(VARIANT_ID);
        when(cartItemRepository.findByCartIdAndVariantId(CART_ID, VARIANT_ID)).thenReturn(Optional.empty());
        availableStock(10);
        emptyResponseItems();

        service.addItem(EMAIL, new AddCartItemRequest(VARIANT_ID, 2));

        var captor = org.mockito.ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(captor.capture());
        assertEquals(CART_ID, captor.getValue().getCartId());
        assertEquals(VARIANT_ID, captor.getValue().getVariantId());
        assertEquals(2, captor.getValue().getQuantity());
        verify(entityManager).lock(user, LockModeType.PESSIMISTIC_WRITE);
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void addItem_existingVariant_increasesQuantity() {
        cartExistsForMutation();
        purchasableVariant();
        when(variant.getId()).thenReturn(VARIANT_ID);
        CartItem item = new CartItem();
        item.setQuantity(3);
        when(cartItemRepository.findByCartIdAndVariantId(CART_ID, VARIANT_ID)).thenReturn(Optional.of(item));
        availableStock(10);
        emptyResponseItems();

        service.addItem(EMAIL, new AddCartItemRequest(VARIANT_ID, 2));
        assertEquals(5, item.getQuantity());
        verify(cartItemRepository).save(item);
    }

    @Test
    void addItem_insufficientStock_returns409() {
        cartExistsForMutation();
        purchasableVariant();
        when(variant.getId()).thenReturn(VARIANT_ID);
        when(cartItemRepository.findByCartIdAndVariantId(CART_ID, VARIANT_ID)).thenReturn(Optional.empty());
        availableStock(1);
        assertStatus(HttpStatus.CONFLICT, () -> service.addItem(EMAIL, new AddCartItemRequest(VARIANT_ID, 2)));
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void addItem_inactiveVariant_returns409() {
        userExists();
        when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant));
        when(variant.getProduct()).thenReturn(product);
        when(variant.getActive()).thenReturn(false);
        assertStatus(HttpStatus.CONFLICT, () -> service.addItem(EMAIL, new AddCartItemRequest(VARIANT_ID, 1)));
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void addItem_missingVariant_returns404() {
        userExists();
        when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.empty());
        assertStatus(HttpStatus.NOT_FOUND, () -> service.addItem(EMAIL, new AddCartItemRequest(VARIANT_ID, 1)));
    }

    @Test
    void addItem_quantityOverflow_returns409() {
        cartExistsForMutation();
        purchasableVariant();
        when(variant.getId()).thenReturn(VARIANT_ID);
        CartItem item = new CartItem();
        item.setQuantity(Integer.MAX_VALUE);
        when(cartItemRepository.findByCartIdAndVariantId(CART_ID, VARIANT_ID)).thenReturn(Optional.of(item));
        assertStatus(HttpStatus.CONFLICT, () -> service.addItem(EMAIL, new AddCartItemRequest(VARIANT_ID, 1)));
        verify(inventoryRepository, never()).findByVariant_Id(anyLong());
    }

    @Test
    void updateItem_updatesQuantity() {
        cartExistsForMutation();
        CartItem item = new CartItem();
        item.setVariantId(VARIANT_ID);
        when(cartItemRepository.findByIdAndCartId(ITEM_ID, CART_ID)).thenReturn(Optional.of(item));
        purchasableVariant();
        availableStock(10);
        emptyResponseItems();

        service.updateItem(EMAIL, ITEM_ID, new UpdateCartItemRequest(4));
        assertEquals(4, item.getQuantity());
        verify(cartItemRepository).save(item);
    }

    @Test
    void updateItem_otherUsersItem_returns404() {
        cartExistsForMutation();
        when(cartItemRepository.findByIdAndCartId(ITEM_ID, CART_ID)).thenReturn(Optional.empty());
        assertStatus(HttpStatus.NOT_FOUND, () -> service.updateItem(EMAIL, ITEM_ID, new UpdateCartItemRequest(2)));
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void removeItem_deletesAndFlushes() {
        cartExistsForMutation();
        CartItem item = new CartItem();
        when(cartItemRepository.findByIdAndCartId(ITEM_ID, CART_ID)).thenReturn(Optional.of(item));
        emptyResponseItems();
        service.removeItem(EMAIL, ITEM_ID);
        verify(cartItemRepository).delete(item);
        verify(cartItemRepository).flush();
    }

    @Test
    void clearCart_existingCart_deletesItems() {
        cartExistsForMutation();
        emptyResponseItems();
        service.clearCart(EMAIL);
        verify(cartItemRepository).deleteByCartId(CART_ID);
        verify(cartItemRepository).flush();
    }

    @Test
    void clearCart_missingCart_returnsEmptyResponse() {
        userExists();
        when(cartRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.empty());
        CartResponse response = service.clearCart(EMAIL);
        assertTrue(response.items().isEmpty());
        verify(cartItemRepository, never()).deleteByCartId(anyLong());
    }
}
