
package com.shreekrishna.organics.combo.service;

import com.shreekrishna.organics.combo.dto.ComboItemRequest;
import com.shreekrishna.organics.combo.dto.ComboItemResponse;
import com.shreekrishna.organics.combo.dto.ComboOfferRequest;
import com.shreekrishna.organics.combo.dto.ComboOfferResponse;
import com.shreekrishna.organics.combo.entity.ComboOffer;
import com.shreekrishna.organics.combo.entity.ComboOfferItem;
import com.shreekrishna.organics.combo.repository.ComboOfferItemRepository;
import com.shreekrishna.organics.combo.repository.ComboOfferRepository;
import com.shreekrishna.organics.product.entity.ProductVariant;
import com.shreekrishna.organics.product.repository.ProductVariantRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.shreekrishna.organics.product.repository.ProductImageRepository;
import com.shreekrishna.organics.inventory.entity.Inventory;
import com.shreekrishna.organics.inventory.repository.InventoryRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class ComboOfferService {

    private final ComboOfferRepository comboOfferRepository;
    private final ComboOfferItemRepository comboOfferItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final InventoryRepository inventoryRepository;


    public ComboOfferService(
            ComboOfferRepository comboOfferRepository,
            ComboOfferItemRepository comboOfferItemRepository,
            ProductVariantRepository productVariantRepository,
            ProductImageRepository productImageRepository,
            InventoryRepository inventoryRepository) {

        this.comboOfferRepository = comboOfferRepository;
        this.comboOfferItemRepository = comboOfferItemRepository;
        this.productVariantRepository = productVariantRepository;
        this.productImageRepository = productImageRepository;
        this.inventoryRepository = inventoryRepository;
    }


    // CREATE COMBO OFFER
    @Transactional
    public ComboOfferResponse createCombo(ComboOfferRequest request) {

        if (request == null || request.getName() == null
                || request.getName().isBlank()) {
            throw new IllegalArgumentException(
                    "Combo name is required");
        }

        if (request.getComboPrice() == null
                || request.getComboPrice().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Combo price must be greater than zero");
        }

        if (request.getItems() == null
                || request.getItems().isEmpty()) {
            throw new IllegalArgumentException(
                    "Combo must contain at least one product");
        }

        List<ComboOfferItem> items = new ArrayList<>();
        List<Long> variantIds = new ArrayList<>();

        // VALIDATE PRODUCTS
        for (ComboItemRequest itemRequest : request.getItems()) {

            if (itemRequest == null
                    || itemRequest.getProductVariantId() == null
                    || itemRequest.getQuantity() == null
                    || itemRequest.getQuantity() <= 0) {

                throw new IllegalArgumentException(
                        "Valid product variant and quantity are required");
            }

            Long variantId = itemRequest.getProductVariantId();

            if (variantIds.contains(variantId)) {
                throw new IllegalArgumentException(
                        "Duplicate product variant: " + variantId);
            }

            variantIds.add(variantId);

            ProductVariant productVariant = productVariantRepository
                    .findById(variantId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Product variant not found: " + variantId));

// CHECK AVAILABLE STOCK
            validateComboStock(variantId, itemRequest.getQuantity());

            ComboOfferItem comboItem = new ComboOfferItem();
            comboItem.setProductVariant(productVariant);
            comboItem.setQuantity(itemRequest.getQuantity());

            items.add(comboItem);
        }

        // SAVE COMBO
        ComboOffer combo = new ComboOffer();
        combo.setName(request.getName());
        combo.setDescription(request.getDescription());
        combo.setComboPrice(request.getComboPrice());
        combo.setActive(
                request.getActive() == null || request.getActive()
        );

        ComboOffer savedCombo = comboOfferRepository.save(combo);

        // SAVE COMBO ITEMS
        for (ComboOfferItem item : items) {
            item.setComboOffer(savedCombo);
            comboOfferItemRepository.save(item);
        }


        return buildResponse(savedCombo);
    }
    @Transactional(readOnly = true)
    public ComboOfferResponse getComboById(Long comboId) {

        ComboOffer combo = comboOfferRepository.findById(comboId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Combo offer not found: " + comboId));

        return buildResponse(combo);
    }
    @Transactional(readOnly = true)
    public List<ComboOfferResponse> getActiveCombos() {

        return comboOfferRepository.findByActiveTrue()
                .stream()
                .map(this::buildResponse)
                .toList();
    }
    @Transactional
    public ComboOfferResponse deactivateCombo(Long comboId) {

        ComboOffer combo = comboOfferRepository.findById(comboId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Combo offer not found: " + comboId));

        combo.setActive(false);

        ComboOffer savedCombo = comboOfferRepository.save(combo);

        return buildResponse(savedCombo);
    }
    @Transactional
    public ComboOfferResponse activateCombo(Long comboId) {

        ComboOffer combo = comboOfferRepository.findById(comboId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Combo offer not found: " + comboId));

        combo.setActive(true);

        ComboOffer savedCombo = comboOfferRepository.save(combo);

        return buildResponse(savedCombo);
    }
    @Transactional
    public ComboOfferResponse updateComboDetails(
            Long comboId, String name, String description) {

        ComboOffer combo = comboOfferRepository.findById(comboId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Combo offer not found: " + comboId));

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Combo name is required");
        }

        combo.setName(name.trim());
        combo.setDescription(description);

        ComboOffer savedCombo = comboOfferRepository.save(combo);

        return buildResponse(savedCombo);
    }

    // UPDATE FIXED COMBO PRICE
    @Transactional
    public ComboOfferResponse updateComboPrice(
            Long comboId, BigDecimal newPrice) {

        if (newPrice == null || newPrice.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Combo price must be greater than zero");
        }

        ComboOffer combo = comboOfferRepository.findById(comboId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Combo offer not found: " + comboId));

        combo.setComboPrice(newPrice);

        ComboOffer savedCombo = comboOfferRepository.save(combo);

        return buildResponse(savedCombo);
    }
    // UPDATE COMBO PRODUCTS AND QUANTITIES
    @Transactional
    public ComboOfferResponse updateComboItems(
            Long comboId, List<ComboItemRequest> itemRequests) {

        ComboOffer combo = comboOfferRepository.findById(comboId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Combo offer not found: " + comboId));

        if (itemRequests == null || itemRequests.isEmpty()) {
            throw new IllegalArgumentException(
                    "Combo must contain at least one product");
        }

        List<ComboOfferItem> newItems = new ArrayList<>();
        List<Long> variantIds = new ArrayList<>();

        // VALIDATE ALL ITEMS BEFORE CHANGING DATABASE
        for (ComboItemRequest itemRequest : itemRequests) {

            if (itemRequest == null
                    || itemRequest.getProductVariantId() == null
                    || itemRequest.getQuantity() == null
                    || itemRequest.getQuantity() <= 0) {

                throw new IllegalArgumentException(
                        "Valid product variant and quantity are required");
            }

            Long variantId = itemRequest.getProductVariantId();

            if (variantIds.contains(variantId)) {
                throw new IllegalArgumentException(
                        "Duplicate product variant: " + variantId);
            }

            variantIds.add(variantId);

            ProductVariant productVariant = productVariantRepository
                    .findById(variantId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Product variant not found: " + variantId));

// CHECK AVAILABLE STOCK
            validateComboStock(variantId, itemRequest.getQuantity());

            ComboOfferItem item = new ComboOfferItem();
            item.setComboOffer(combo);
            item.setProductVariant(productVariant);
            item.setQuantity(itemRequest.getQuantity());

            newItems.add(item);
        }

        // DELETE OLD ITEMS
        comboOfferItemRepository.deleteByComboOfferId(comboId);
        comboOfferItemRepository.flush();

        // SAVE NEW ITEMS
        comboOfferItemRepository.saveAll(newItems);

        return buildResponse(combo);
    }

    // BUILD RESPONSE WITH PRODUCTS
    private ComboOfferResponse buildResponse(ComboOffer combo) {

        ComboOfferResponse response = new ComboOfferResponse();

        response.setId(combo.getId());
        response.setName(combo.getName());
        response.setDescription(combo.getDescription());
        response.setComboPrice(combo.getComboPrice());
        response.setActive(combo.isActive());

        List<ComboItemResponse> itemResponses = new ArrayList<>();

        for (ComboOfferItem item :
                comboOfferItemRepository.findByComboOfferId(combo.getId())) {

            ComboItemResponse itemResponse = new ComboItemResponse();

            itemResponse.setId(item.getId());
            itemResponse.setProductVariantId(
                    item.getProductVariant().getId()
            );
            itemResponse.setQuantity(item.getQuantity());

            ProductVariant variant = item.getProductVariant();

            itemResponse.setProductName(
                    variant.getProduct().getName()
            );

            itemResponse.setSizeValue(
                    variant.getSizeValue()
            );

            itemResponse.setSizeUnit(
                    variant.getSizeUnit()
            );

            itemResponse.setPrice(
                    variant.getPrice()
            );

            itemResponse.setMrp(
                    variant.getMrp()
            );

            Long productId = variant.getProduct().getId();

            productImageRepository
                    .findByProductIdAndPrimaryTrue(productId)
                    .ifPresent(image ->
                            itemResponse.setImageUrl(
                                    image.getImageUrl()
                            )
                    );



            itemResponses.add(itemResponse);
        }

        response.setItems(itemResponses);

        return response;
    }

    // VALIDATE COMBO STOCK
    private void validateComboStock(Long variantId, int requiredQuantity) {

        Inventory inventory = inventoryRepository
                .findByVariant_Id(variantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Inventory not found for variant: " + variantId
                ));

        int availableStock = inventory.getAvailableQuantity();

        if (availableStock < requiredQuantity) {
            throw new IllegalArgumentException(
                    "Insufficient stock for variant: " + variantId
                            + ". Required: " + requiredQuantity
                            + ", Available: " + availableStock
            );
        }
    }
}