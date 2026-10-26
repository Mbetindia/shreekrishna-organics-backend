package com.shreekrishna.organics.product.service;

import com.shreekrishna.organics.exception.ResourceNotFoundException;
import com.shreekrishna.organics.product.dto.ProductImageResponse;
import com.shreekrishna.organics.product.entity.Product;
import com.shreekrishna.organics.product.entity.ProductImage;
import com.shreekrishna.organics.product.entity.ProductVariant;
import com.shreekrishna.organics.product.repository.ProductImageRepository;
import com.shreekrishna.organics.product.repository.ProductRepository;

import jakarta.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ProductImageService {

    private static final Logger log =
            LoggerFactory.getLogger(ProductImageService.class);

    private static final int MAX_NORMAL_IMAGES = 5;

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final CloudinaryImageService cloudinaryService;
    private final EntityManager entityManager;

    public ProductImageService(
            ProductRepository productRepository,
            ProductImageRepository imageRepository,
            CloudinaryImageService cloudinaryService,
            EntityManager entityManager
    ) {
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
        this.cloudinaryService = cloudinaryService;
        this.entityManager = entityManager;
    }

    // =========================================================
    // EXISTING UPLOAD METHOD - BACKWARD COMPATIBILITY
    // =========================================================

    @Transactional
    public ProductImageResponse uploadImage(
            Long productId,
            MultipartFile file
    ) throws IOException {

        return uploadImage(productId, file, null);
    }

    // =========================================================
    // UPLOAD NORMAL OR VARIANT IMAGE
    // =========================================================

    @Transactional
    public ProductImageResponse uploadImage(
            Long productId,
            MultipartFile file,
            Long variantId
    ) throws IOException {

        // Serializes image modifications for the same product.
        Product product = getLockedProduct(productId);

        if (variantId == null) {
            return uploadNormalImage(product, file);
        }

        return uploadVariantImage(product, variantId, file);
    }

    // =========================================================
    // NORMAL PRODUCT IMAGE UPLOAD
    // =========================================================

    private ProductImageResponse uploadNormalImage(
            Product product,
            MultipartFile file
    ) throws IOException {

        Long productId = product.getId();

        List<ProductImage> existingImages =
                imageRepository
                        .findByProductIdAndVariantIsNullOrderByDisplayOrderAscIdAsc(
                                productId
                        );

        // Limit applies only to primary + gallery images.
        if (existingImages.size() >= MAX_NORMAL_IMAGES) {
            throw new IllegalArgumentException(
                    "Maximum 5 product images are allowed "
                            + "(1 primary + 4 gallery images)"
            );
        }

        Map<String, Object> uploaded =
                cloudinaryService.uploadImage(file);

        String imageUrl = getUploadedImageUrl(uploaded);
        String publicId = getUploadedPublicId(uploaded);

        validateUploadedImageDetails(imageUrl, publicId);

        registerUploadRollbackCleanup(publicId);

        ProductImage image = new ProductImage();

        image.setProduct(product);
        image.setVariant(null);
        image.setImageUrl(imageUrl);
        image.setPublicId(publicId);

        // First normal image becomes primary.
        image.setPrimary(existingImages.isEmpty());

        int nextDisplayOrder = existingImages.stream()
                .map(ProductImage::getDisplayOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1) + 1;

        image.setDisplayOrder(nextDisplayOrder);

        ProductImage saved =
                imageRepository.saveAndFlush(image);

        return toResponse(saved);
    }

    // =========================================================
    // VARIANT IMAGE UPLOAD / REPLACE
    // =========================================================

    private ProductImageResponse uploadVariantImage(
            Product product,
            Long variantId,
            MultipartFile file
    ) throws IOException {

        if (variantId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid variant ID"
            );
        }

        ProductVariant variant =
                entityManager.find(ProductVariant.class, variantId);

        if (variant == null) {
            throw new ResourceNotFoundException(
                    "Product variant not found with ID: " + variantId
            );
        }

        // A variant must belong to the requested product.
        if (variant.getProduct() == null
                || !Objects.equals(
                variant.getProduct().getId(),
                product.getId()
        )) {

            throw new IllegalArgumentException(
                    "Variant ID " + variantId
                            + " does not belong to Product ID "
                            + product.getId()
            );
        }

        // The product lock prevents concurrent uploads for
        // the same product through this service.
        ProductImage existingImage =
                imageRepository
                        .findByProductIdAndVariantId(
                                product.getId(),
                                variantId
                        )
                        .orElse(null);

        Map<String, Object> uploaded =
                cloudinaryService.uploadImage(file);

        String imageUrl = getUploadedImageUrl(uploaded);
        String publicId = getUploadedPublicId(uploaded);

        validateUploadedImageDetails(imageUrl, publicId);

        // Delete the newly uploaded Cloudinary image
        // if the database transaction rolls back.
        registerUploadRollbackCleanup(publicId);

        ProductImage image;

        if (existingImage != null) {

            // REPLACE EXISTING VARIANT IMAGE
            String oldPublicId = existingImage.getPublicId();

            existingImage.setImageUrl(imageUrl);
            existingImage.setPublicId(publicId);
            existingImage.setPrimary(false);
            existingImage.setDisplayOrder(0);

            image = imageRepository.saveAndFlush(existingImage);

            // Old image is deleted only after successful DB commit.
            if (!Objects.equals(oldPublicId, publicId)) {
                registerDeleteAfterCommit(oldPublicId);
            }

        } else {

            // FIRST IMAGE FOR THIS VARIANT
            ProductImage newImage = new ProductImage();

            newImage.setProduct(product);
            newImage.setVariant(variant);
            newImage.setImageUrl(imageUrl);
            newImage.setPublicId(publicId);
            newImage.setPrimary(false);
            newImage.setDisplayOrder(0);

            image = imageRepository.saveAndFlush(newImage);
        }

        return toResponse(image);
    }

    // =========================================================
    // GET ALL PRODUCT IMAGES
    // =========================================================

    @Transactional(readOnly = true)
    public List<ProductImageResponse> getImages(Long productId) {

        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException(
                    "Product not found with ID: " + productId
            );
        }

        // Includes normal images and variant images.
        // Frontend distinguishes them using variantId.
        return imageRepository
                .findByProductIdOrderByDisplayOrderAscIdAsc(productId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // =========================================================
    // SET PRIMARY PRODUCT IMAGE
    // =========================================================

    @Transactional
    public ProductImageResponse setPrimaryImage(
            Long productId,
            Long imageId
    ) {

        Product product = getLockedProduct(productId);

        ProductImage selectedImage = imageRepository
                .findByIdAndProductId(imageId, product.getId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Image not found with ID: " + imageId
                                        + " for Product ID: " + productId
                        )
                );

        // Variant-specific images cannot become primary.
        if (selectedImage.getVariant() != null) {
            throw new IllegalArgumentException(
                    "Variant images cannot be set as primary product images"
            );
        }

        // Only normal images participate in primary selection.
        List<ProductImage> images =
                imageRepository
                        .findByProductIdAndVariantIsNullOrderByDisplayOrderAscIdAsc(
                                productId
                        );

        for (ProductImage image : images) {
            image.setPrimary(
                    image.getId().equals(selectedImage.getId())
            );
        }

        imageRepository.saveAll(images);
        imageRepository.flush();

        return toResponse(selectedImage);
    }

    // =========================================================
    // DELETE NORMAL OR VARIANT IMAGE
    // =========================================================

    @Transactional
    public void deleteImage(
            Long productId,
            Long imageId
    ) {

        getLockedProduct(productId);

        ProductImage image = imageRepository
                .findByIdAndProductId(imageId, productId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Image not found with ID: " + imageId
                                        + " for Product ID: " + productId
                        )
                );

        String publicId = image.getPublicId();

        boolean wasNormalPrimary =
                image.getVariant() == null
                        && Boolean.TRUE.equals(image.getPrimary());

        imageRepository.delete(image);
        imageRepository.flush();

        // If a normal primary image was deleted,
        // select the next NORMAL image only.
        if (wasNormalPrimary) {

            List<ProductImage> remainingNormalImages =
                    imageRepository
                            .findByProductIdAndVariantIsNullOrderByDisplayOrderAscIdAsc(
                                    productId
                            );

            if (!remainingNormalImages.isEmpty()) {

                ProductImage newPrimary =
                        remainingNormalImages.get(0);

                newPrimary.setPrimary(true);

                imageRepository.saveAndFlush(newPrimary);
            }
        }

        // Cloudinary deletion only after DB commit.
        registerDeleteAfterCommit(publicId);
    }

    // =========================================================
    // GET PRODUCT WITH DATABASE WRITE LOCK
    // =========================================================

    private Product getLockedProduct(Long productId) {

        return productRepository
                .findByIdForUpdate(productId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found with ID: " + productId
                        )
                );
    }

    // =========================================================
    // EXTRACT CLOUDINARY RESPONSE DETAILS
    // =========================================================

    private String getUploadedImageUrl(
            Map<String, Object> uploaded
    ) {

        return uploaded == null
                ? null
                : (String) uploaded.get("secure_url");
    }

    private String getUploadedPublicId(
            Map<String, Object> uploaded
    ) {

        return uploaded == null
                ? null
                : (String) uploaded.get("public_id");
    }

    private void validateUploadedImageDetails(
            String imageUrl,
            String publicId
    ) {

        if (imageUrl == null || imageUrl.isBlank()
                || publicId == null || publicId.isBlank()) {

            // If Cloudinary returned a public ID,
            // clean up the uploaded resource.
            if (publicId != null && !publicId.isBlank()) {
                cleanupCloudinaryImage(publicId);
            }

            throw new IllegalStateException(
                    "Cloudinary did not return valid image details"
            );
        }
    }

    // =========================================================
    // CLEAN UP NEW UPLOAD IF DB TRANSACTION ROLLS BACK
    // =========================================================

    private void registerUploadRollbackCleanup(
            String publicId
    ) {

        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {

            cleanupCloudinaryImage(publicId);

            throw new IllegalStateException(
                    "No active transaction synchronization for image upload"
            );
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCompletion(int status) {

                        if (status != STATUS_COMMITTED) {

                            log.warn(
                                    "Image upload transaction did not commit. "
                                            + "Cleaning Cloudinary image: {}",
                                    publicId
                            );

                            cleanupCloudinaryImage(publicId);
                        }
                    }
                }
        );
    }

    // =========================================================
    // DELETE OLD IMAGE ONLY AFTER DB COMMIT
    // =========================================================

    private void registerDeleteAfterCommit(
            String publicId
    ) {

        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {

            throw new IllegalStateException(
                    "No active transaction synchronization for image deletion"
            );
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {
                        cleanupCloudinaryImage(publicId);
                    }
                }
        );
    }

    // =========================================================
    // BEST-EFFORT CLOUDINARY CLEANUP
    // =========================================================

    private void cleanupCloudinaryImage(
            String publicId
    ) {

        try {

            cloudinaryService.deleteImage(publicId);

            log.info(
                    "Cloudinary image cleanup successful: {}",
                    publicId
            );

        } catch (Exception exception) {

            log.error(
                    "Cloudinary image cleanup failed for public ID: {}",
                    publicId,
                    exception
            );
        }
    }

    // =========================================================
    // CONVERT ENTITY TO RESPONSE
    // =========================================================

    private ProductImageResponse toResponse(
            ProductImage image
    ) {

        return new ProductImageResponse(
                image.getId(),
                image.getProduct().getId(),
                image.getVariantId(),
                image.getImageUrl(),
                image.getPublicId(),
                image.getPrimary(),
                image.getDisplayOrder(),
                image.getCreatedAt()
        );
    }
}