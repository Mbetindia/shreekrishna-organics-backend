
package com.shreekrishna.organics.inventory.controller;

import com.shreekrishna.organics.inventory.entity.Inventory;
import com.shreekrishna.organics.inventory.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/variants/{variantId}/available")
    public ResponseEntity<Map<String, Object>> getAvailableStock(
            @PathVariable Long variantId) {

        int available = inventoryService.getAvailableStock(variantId);

        return ResponseEntity.ok(Map.of(
                "variantId", variantId,
                "availableQuantity", available
        ));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/variants/{variantId}")
    public ResponseEntity<Map<String, Object>> createInventory(
            @PathVariable Long variantId,
            @RequestBody StockRequest request) {

        Inventory inventory =
                inventoryService.createInventory(
                        variantId, request.quantity());

        return ResponseEntity
                .status(201)
                .body(Map.of(
                        "variantId", variantId,
                        "quantity", inventory.getQuantity(),
                        "reservedQuantity", inventory.getReservedQuantity(),
                        "availableQuantity", inventory.getAvailableQuantity()
                ));
    }


    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/variants/{variantId}/add-stock")
    public ResponseEntity<Map<String, Object>> addStock(
            @PathVariable Long variantId,
            @RequestBody StockRequest request) {

        Inventory inventory =
                inventoryService.addStock(variantId, request.quantity());

        return ResponseEntity.ok(Map.of(
                "variantId", variantId,
                "quantity", inventory.getQuantity(),
                "reservedQuantity", inventory.getReservedQuantity(),
                "availableQuantity", inventory.getAvailableQuantity()
        ));
    }

    public record StockRequest(int quantity) {
    }
}
