
package com.shreekrishna.organics.product.service;

import com.shreekrishna.organics.exception.ResourceNotFoundException;
import com.shreekrishna.organics.product.dto.ProductImageResponse;
import com.shreekrishna.organics.product.entity.Product;
import com.shreekrishna.organics.product.entity.ProductImage;
import com.shreekrishna.organics.product.repository.ProductImageRepository;
import com.shreekrishna.organics.product.repository.ProductRepository;

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

@Service
public class ProductImageService {

    private static final Logger log =
            LoggerFactory.getLogger(ProductImageService.class);

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final CloudinaryImageService cloudinaryService;

    public ProductImageService(
            ProductRepository productRepository,
            ProductImageRepository imageRepository,
            CloudinaryImageService cloudinaryService
    ) {
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
        this.cloudinaryService = cloudinaryService;
    }

    // UPLOAD PRODUCT IMAGE
    @Transactional
    public ProductImageResponse uploadImage(
            Long productId,
            MultipartFile file
    ) throws IOException {

        Product product = getLockedProduct(productId);

        Map<String, Object> uploaded =
                cloudinaryService.uploadImage(file);

        String imageUrl = (String) uploaded.get("secure_url");
        String publicId = (String) uploaded.get("public_id");

        if (imageUrl == null || imageUrl.isBlank()
                || publicId == null || publicId.isBlank()) {

            if (publicId != null && !publicId.isBlank()) {
                cleanupCloudinaryImage(publicId);
            }

            throw new IllegalStateException(
                    "Cloudinary did not return valid image details"
            );
        }

        // Register cleanup before database operations.
        // If the transaction rolls back, remove uploaded image.
        registerUploadRollbackCleanup(publicId);

        ProductImage image = new ProductImage();

        image.setProduct(product);
        image.setImageUrl(imageUrl);
        image.setPublicId(publicId);

        List<ProductImage> existingImages =
                imageRepository
                        .findByProductIdOrderByDisplayOrderAscIdAsc(
                                productId
                        );

        // First image becomes primary.
        image.setPrimary(existingImages.isEmpty());

        // Assign the next display order.
        int nextDisplayOrder = existingImages.stream()
                .map(ProductImage::getDisplayOrder)
                .filter(order -> order != null)
                .max(Integer::compareTo)
                .orElse(-1) + 1;

        image.setDisplayOrder(nextDisplayOrder);

        ProductImage saved =
                imageRepository.saveAndFlush(image);

        return toResponse(saved);
    }

    // GET PRODUCT IMAGES
    @Transactional(readOnly = true)
    public List<ProductImageResponse> getImages(Long productId) {

        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException(
                    "Product not found with ID: " + productId
            );
        }

        return imageRepository
                .findByProductIdOrderByDisplayOrderAscIdAsc(productId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // SET PRIMARY PRODUCT IMAGE
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

        List<ProductImage> images =
                imageRepository.findByProductId(productId);

        for (ProductImage image : images) {
            image.setPrimary(
                    image.getId().equals(selectedImage.getId())
            );
        }

        imageRepository.saveAll(images);
        imageRepository.flush();

        return toResponse(selectedImage);
    }

    // DELETE PRODUCT IMAGE
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

        boolean wasPrimary =
                Boolean.TRUE.equals(image.getPrimary());

        imageRepository.delete(image);
        imageRepository.flush();

        // Assign a new primary image if needed.
        if (wasPrimary) {

            List<ProductImage> remainingImages =
                    imageRepository
                            .findByProductIdOrderByDisplayOrderAscIdAsc(
                                    productId
                            );

            if (!remainingImages.isEmpty()) {

                ProductImage newPrimary =
                        remainingImages.get(0);

                newPrimary.setPrimary(true);
                imageRepository.save(newPrimary);
            }
        }

        // Delete Cloudinary image only after DB commit.
        registerDeleteAfterCommit(publicId);
    }

    // GET PRODUCT WITH DATABASE WRITE LOCK
    private Product getLockedProduct(Long productId) {

        return productRepository
                .findByIdForUpdate(productId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found with ID: " + productId
                        )
                );
    }

    // CLEAN UP NEW UPLOAD IF DATABASE TRANSACTION ROLLS BACK
    private void registerUploadRollbackCleanup(String publicId) {

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

    // CLEAN UP DELETED IMAGE ONLY AFTER DATABASE COMMIT
    private void registerDeleteAfterCommit(String publicId) {

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

    // BEST-EFFORT CLOUDINARY CLEANUP
    private void cleanupCloudinaryImage(String publicId) {

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

    // CONVERT ENTITY TO RESPONSE
    private ProductImageResponse toResponse(
            ProductImage image
    ) {

        return new ProductImageResponse(
                image.getId(),
                image.getProduct().getId(),
                image.getImageUrl(),
                image.getPublicId(),
                image.getPrimary(),
                image.getDisplayOrder(),
                image.getCreatedAt()
        );
    }
}
