
package com.shreekrishna.organics.product.service;

import com.shreekrishna.organics.exception.ResourceNotFoundException;
import com.shreekrishna.organics.offer.entity.ProductOffer;
import com.shreekrishna.organics.offer.service.ProductOfferService;
import com.shreekrishna.organics.product.dto.ProductVariantRequest;
import com.shreekrishna.organics.product.dto.ProductVariantResponse;
import com.shreekrishna.organics.product.entity.Product;
import com.shreekrishna.organics.product.entity.ProductVariant;
import com.shreekrishna.organics.product.repository.ProductRepository;
import com.shreekrishna.organics.product.repository.ProductVariantRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProductVariantService {

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final ProductOfferService offerService;

    public ProductVariantService(
            ProductVariantRepository variantRepository,
            ProductRepository productRepository,
            ProductOfferService offerService) {

        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
        this.offerService = offerService;
    }

    public ProductVariantResponse create(ProductVariantRequest request) {

        if (variantRepository.existsBySku(request.getSku())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "SKU already exists");
        }

        Product product = findProduct(request.getProductId());

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        applyRequest(variant, request);

        return toResponse(variantRepository.save(variant));
    }

    @Transactional(readOnly = true)
    public ProductVariantResponse getById(Long id) {
        return toResponse(findVariant(id));
    }

    @Transactional(readOnly = true)
    public List<ProductVariantResponse> getByProduct(Long productId) {

        findProduct(productId);

        Optional<ProductOffer> activeOffer =
                offerService.getActiveOffer(productId);

        return variantRepository.findByProduct_Id(productId)
                .stream()
                .map(variant -> toResponse(variant, activeOffer))
                .toList();
    }

    public ProductVariantResponse update(
            Long id, ProductVariantRequest request) {

        ProductVariant variant = findVariant(id);

        variantRepository.findBySku(request.getSku())
                .ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT, "SKU already exists");
                    }
                });

        Product product = findProduct(request.getProductId());

        variant.setProduct(product);
        applyRequest(variant, request);

        return toResponse(variantRepository.save(variant));
    }

    public void softDelete(Long id) {

        ProductVariant variant = findVariant(id);
        variant.setActive(false);
        variantRepository.save(variant);
    }

    private Product findProduct(Long id) {

        return productRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found: " + id));
    }

    private ProductVariant findVariant(Long id) {

        return variantRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product variant not found: " + id));
    }

    private void applyRequest(
            ProductVariant variant,
            ProductVariantRequest request) {

        variant.setSku(request.getSku());
        variant.setSizeValue(request.getSizeValue());
        variant.setSizeUnit(request.getSizeUnit());
        variant.setPrice(request.getPrice());
        variant.setMrp(request.getMrp());

        variant.setActive(
                request.getActive() != null
                        ? request.getActive()
                        : true
        );
    }

    private ProductVariantResponse toResponse(ProductVariant variant) {

        Optional<ProductOffer> activeOffer =
                offerService.getActiveOffer(
                        variant.getProduct().getId()
                );

        return toResponse(variant, activeOffer);
    }

    private ProductVariantResponse toResponse(
            ProductVariant variant,
            Optional<ProductOffer> activeOffer) {

        ProductVariantResponse response =
                new ProductVariantResponse(
                        variant.getId(),
                        variant.getProduct().getId(),
                        variant.getSku(),
                        variant.getSizeValue(),
                        variant.getSizeUnit(),
                        variant.getPrice(),
                        variant.getMrp(),
                        variant.getActive(),
                        variant.getCreatedAt(),
                        variant.getUpdatedAt()
                );

        // Inactive variants should not display an offer
        if (!Boolean.TRUE.equals(variant.getActive())) {
            return response;
        }

        activeOffer.ifPresent(offer -> {

            response.setDiscountType(
                    offer.getDiscountType()
            );

            response.setDiscountValue(
                    offer.getDiscountValue()
            );

            response.setDiscountedPrice(
                    offerService.calculateDiscountedPrice(
                            variant.getPrice(),
                            offer
                    )
            );

            response.setOfferActive(true);
        });

        return response;
    }
}
