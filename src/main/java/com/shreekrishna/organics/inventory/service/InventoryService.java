
package com.shreekrishna.organics.inventory.service;

import com.shreekrishna.organics.inventory.entity.Inventory;
import com.shreekrishna.organics.inventory.repository.InventoryRepository;
import com.shreekrishna.organics.product.entity.ProductVariant;
import com.shreekrishna.organics.product.repository.ProductVariantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductVariantRepository variantRepository;

    public InventoryService(
            InventoryRepository inventoryRepository,
            ProductVariantRepository variantRepository) {
        this.inventoryRepository = inventoryRepository;
        this.variantRepository = variantRepository;
    }

    public Inventory createInventory(Long variantId, int quantity) {
        if (quantity < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Quantity cannot be negative");
        }

        if (inventoryRepository.existsByVariant_Id(variantId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Inventory already exists");
        }

        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Variant not found"));

        Inventory inventory = new Inventory();
        inventory.setVariant(variant);
        inventory.setQuantity(quantity);
        inventory.setReservedQuantity(0);

        return inventoryRepository.save(inventory);
    }

    @Transactional(readOnly = true)
    public int getAvailableStock(Long variantId) {
        Inventory inventory = inventoryRepository
                .findByVariant_Id(variantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Inventory not found"));

        return inventory.getAvailableQuantity();
    }

    public Inventory addStock(Long variantId, int quantity) {
        if (quantity <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Quantity must be positive");
        }

        Inventory inventory = findLockedInventory(variantId);
        inventory.setQuantity(
                Math.addExact(inventory.getQuantity(), quantity));

        return inventoryRepository.save(inventory);
    }

    public Inventory reserveStock(Long variantId, int quantity) {
        validatePositiveQuantity(quantity);

        Inventory inventory = findLockedInventory(variantId);

        if (inventory.getAvailableQuantity() < quantity) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Insufficient stock");
        }

        inventory.setReservedQuantity(
                Math.addExact(inventory.getReservedQuantity(), quantity));

        return inventoryRepository.save(inventory);
    }

    public Inventory releaseStock(Long variantId, int quantity) {
        validatePositiveQuantity(quantity);

        Inventory inventory = findLockedInventory(variantId);

        if (inventory.getReservedQuantity() < quantity) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Not enough reserved stock");
        }

        inventory.setReservedQuantity(
                inventory.getReservedQuantity() - quantity);

        return inventoryRepository.save(inventory);
    }

    public Inventory confirmStock(Long variantId, int quantity) {
        validatePositiveQuantity(quantity);

        Inventory inventory = findLockedInventory(variantId);

        if (inventory.getReservedQuantity() < quantity) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Not enough reserved stock");
        }

        inventory.setReservedQuantity(
                inventory.getReservedQuantity() - quantity);

        inventory.setQuantity(
                inventory.getQuantity() - quantity);

        return inventoryRepository.save(inventory);
    }

    private Inventory findLockedInventory(Long variantId) {
        return inventoryRepository
                .findWithLockByVariantId(variantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Inventory not found"));
    }

    private void validatePositiveQuantity(int quantity) {
        if (quantity <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Quantity must be positive");
        }
    }
}
