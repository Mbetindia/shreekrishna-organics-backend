
package com.shreekrishna.organics.inventory.service;

import com.shreekrishna.organics.inventory.entity.Inventory;
import com.shreekrishna.organics.inventory.repository.InventoryRepository;
import com.shreekrishna.organics.product.entity.ProductVariant;
import com.shreekrishna.organics.product.repository.ProductVariantRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ProductVariantRepository variantRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private Inventory inventory;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        variant = new ProductVariant();

        inventory = new Inventory();
        inventory.setVariant(variant);
        inventory.setQuantity(100);
        inventory.setReservedQuantity(0);
    }

    @Test
    void createInventoryShouldSaveInventory() {
        when(inventoryRepository.existsByVariant_Id(1L))
                .thenReturn(false);

        when(variantRepository.findById(1L))
                .thenReturn(Optional.of(variant));

        when(inventoryRepository.save(any(Inventory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Inventory result = inventoryService.createInventory(1L, 100);

        assertEquals(100, result.getQuantity());
        assertEquals(0, result.getReservedQuantity());
        assertSame(variant, result.getVariant());

        verify(inventoryRepository).save(any(Inventory.class));
    }

    @Test
    void createInventoryShouldRejectNegativeQuantity() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.createInventory(1L, -1)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void createInventoryShouldRejectDuplicateInventory() {
        when(inventoryRepository.existsByVariant_Id(1L))
                .thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.createInventory(1L, 100)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void createInventoryShouldRejectMissingVariant() {
        when(variantRepository.findById(1L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.createInventory(1L, 100)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void getAvailableStockShouldReturnCorrectQuantity() {
        inventory.setReservedQuantity(20);

        when(inventoryRepository.findByVariant_Id(1L))
                .thenReturn(Optional.of(inventory));

        int available = inventoryService.getAvailableStock(1L);

        assertEquals(80, available);
    }

    @Test
    void getAvailableStockShouldRejectMissingInventory() {
        when(inventoryRepository.findByVariant_Id(1L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.getAvailableStock(1L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void addStockShouldIncreaseQuantity() {
        when(inventoryRepository.findWithLockByVariantId(1L))
                .thenReturn(Optional.of(inventory));

        when(inventoryRepository.save(inventory))
                .thenReturn(inventory);

        Inventory result = inventoryService.addStock(1L, 50);

        assertEquals(150, result.getQuantity());
        verify(inventoryRepository).save(inventory);
    }

    @Test
    void addStockShouldRejectZeroQuantity() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.addStock(1L, 0)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void reserveStockShouldReserveAvailableQuantity() {
        when(inventoryRepository.findWithLockByVariantId(1L))
                .thenReturn(Optional.of(inventory));

        when(inventoryRepository.save(inventory))
                .thenReturn(inventory);

        Inventory result = inventoryService.reserveStock(1L, 30);

        assertEquals(30, result.getReservedQuantity());
        assertEquals(70, result.getAvailableQuantity());
    }

    @Test
    void reserveStockShouldRejectInsufficientStock() {
        when(inventoryRepository.findWithLockByVariantId(1L))
                .thenReturn(Optional.of(inventory));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.reserveStock(1L, 150)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void releaseStockShouldReduceReservedQuantity() {
        inventory.setReservedQuantity(30);

        when(inventoryRepository.findWithLockByVariantId(1L))
                .thenReturn(Optional.of(inventory));

        when(inventoryRepository.save(inventory))
                .thenReturn(inventory);

        Inventory result = inventoryService.releaseStock(1L, 10);

        assertEquals(20, result.getReservedQuantity());
        assertEquals(80, result.getAvailableQuantity());
    }

    @Test
    void releaseStockShouldRejectExcessQuantity() {
        inventory.setReservedQuantity(10);

        when(inventoryRepository.findWithLockByVariantId(1L))
                .thenReturn(Optional.of(inventory));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.releaseStock(1L, 20)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void confirmStockShouldDeductQuantity() {
        inventory.setReservedQuantity(20);

        when(inventoryRepository.findWithLockByVariantId(1L))
                .thenReturn(Optional.of(inventory));

        when(inventoryRepository.save(inventory))
                .thenReturn(inventory);

        Inventory result = inventoryService.confirmStock(1L, 20);

        assertEquals(80, result.getQuantity());
        assertEquals(0, result.getReservedQuantity());
        assertEquals(80, result.getAvailableQuantity());
    }

    @Test
    void confirmStockShouldRejectExcessQuantity() {
        inventory.setReservedQuantity(10);

        when(inventoryRepository.findWithLockByVariantId(1L))
                .thenReturn(Optional.of(inventory));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.confirmStock(1L, 20)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void reserveStockShouldRejectZeroQuantity() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.reserveStock(1L, 0)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void releaseStockShouldRejectNegativeQuantity() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.releaseStock(1L, -5)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void confirmStockShouldRejectZeroQuantity() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.confirmStock(1L, 0)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void addStockShouldRejectMissingInventory() {
        when(inventoryRepository.findWithLockByVariantId(1L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.addStock(1L, 10)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }
}
