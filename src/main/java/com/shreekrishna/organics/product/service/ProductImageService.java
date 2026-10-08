
package com.shreekrishna.organics.product.service;

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

        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Product not found")
                );

        Map<String, Object> uploaded =
                cloudinaryService.uploadImage(file);

        String imageUrl = (String) uploaded.get("secure_url");
        String publicId = (String) uploaded.get("public_id");

        if (imageUrl == null || publicId == null) {
            if (publicId != null) {
                cloudinaryService.deleteImage(publicId);
            }

            throw new IllegalStateException(
                    "Cloudinary did not return image details"
            );
        }

        try {
            ProductImage image = new ProductImage();

            image.setProduct(product);
            image.setImageUrl(imageUrl);
            image.setPublicId(publicId);

            long imageCount =
                    imageRepository.countByProductId(productId);

            image.setPrimary(imageCount == 0);
            image.setDisplayOrder((int) imageCount);

            ProductImage saved =
                    imageRepository.saveAndFlush(image);

            return toResponse(saved);

        } catch (RuntimeException exception) {

            try {
                cloudinaryService.deleteImage(publicId);
            } catch (Exception cleanupException) {
                exception.addSuppressed(cleanupException);
            }

            throw exception;
        }
    }

    // GET PRODUCT IMAGES
    @Transactional(readOnly = true)
    public List<ProductImageResponse> getImages(Long productId) {

        if (!productRepository.existsById(productId)) {
            throw new IllegalArgumentException(
                    "Product not found"
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

        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Product not found"
                        )
                );

        ProductImage selectedImage = imageRepository
                .findByIdAndProductId(imageId, product.getId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Image not found for this product"
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

        if (!productRepository.existsById(productId)) {
            throw new IllegalArgumentException(
                    "Product not found"
            );
        }

        ProductImage image = imageRepository
                .findByIdAndProductId(imageId, productId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Image not found for this product"
                        )
                );

        String publicId = image.getPublicId();

        boolean wasPrimary =
                Boolean.TRUE.equals(image.getPrimary());

        imageRepository.delete(image);
        imageRepository.flush();

        // If primary image was deleted,
        // assign another remaining image as primary.
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
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {
                        try {
                            cloudinaryService.deleteImage(publicId);

                            log.info(
                                    "Cloudinary image deleted: {}",
                                    publicId
                            );

                        } catch (Exception exception) {

                            log.error(
                                    "Cloudinary cleanup failed for image: {}",
                                    publicId,
                                    exception
                            );
                        }
                    }
                }
        );
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
