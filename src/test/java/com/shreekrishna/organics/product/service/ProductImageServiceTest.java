
package com.shreekrishna.organics.product.service;

import com.shreekrishna.organics.exception.ResourceNotFoundException;
import com.shreekrishna.organics.product.dto.ProductImageResponse;
import com.shreekrishna.organics.product.entity.Product;
import com.shreekrishna.organics.product.entity.ProductImage;
import com.shreekrishna.organics.product.repository.ProductImageRepository;
import com.shreekrishna.organics.product.repository.ProductRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductImageServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductImageRepository imageRepository;

    @Mock
    private CloudinaryImageService cloudinaryService;

    @InjectMocks
    private ProductImageService imageService;

    private Product product;

    @BeforeEach
    void setUp() {

        product = new Product();

        // Set generated database ID for unit testing.
        ReflectionTestUtils.setField(product, "id", 1L);
    }

    // TEST 1 - PRODUCT NOT FOUND DURING UPLOAD
    @Test
    void uploadImageShouldFailWhenProductNotFound() {

        when(productRepository.findByIdForUpdate(99L))
                .thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );

        assertThrows(
                ResourceNotFoundException.class,
                () -> imageService.uploadImage(99L, file)
        );

        verifyNoInteractions(cloudinaryService);
    }

    // TEST 2 - GET IMAGES RETURNS EMPTY LIST
    @Test
    void getImagesShouldReturnEmptyList() {

        when(productRepository.existsById(1L))
                .thenReturn(true);

        when(imageRepository
                .findByProductIdOrderByDisplayOrderAscIdAsc(1L))
                .thenReturn(List.of());

        List<ProductImageResponse> images =
                imageService.getImages(1L);

        assertTrue(images.isEmpty());
    }

    // TEST 3 - PRODUCT NOT FOUND DURING GET
    @Test
    void getImagesShouldFailWhenProductNotFound() {

        when(productRepository.existsById(99L))
                .thenReturn(false);

        assertThrows(
                ResourceNotFoundException.class,
                () -> imageService.getImages(99L)
        );
    }

    // TEST 4 - IMAGE NOT FOUND DURING SET PRIMARY
    @Test
    void setPrimaryImageShouldFailWhenImageNotFound() {

        when(productRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(product));

        when(imageRepository.findByIdAndProductId(99L, 1L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> imageService.setPrimaryImage(1L, 99L)
        );
    }

    // TEST 5 - SET PRIMARY IMAGE
    @Test
    void setPrimaryImageShouldUpdatePrimaryFlags() {

        ProductImage first = new ProductImage();

        ReflectionTestUtils.setField(first, "id", 10L);

        first.setProduct(product);
        first.setPrimary(true);

        ProductImage second = new ProductImage();

        ReflectionTestUtils.setField(second, "id", 20L);

        second.setProduct(product);
        second.setPrimary(false);

        when(productRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(product));

        when(imageRepository.findByIdAndProductId(20L, 1L))
                .thenReturn(Optional.of(second));

        when(imageRepository.findByProductId(1L))
                .thenReturn(List.of(first, second));

        ProductImageResponse response =
                imageService.setPrimaryImage(1L, 20L);

        assertFalse(first.getPrimary());
        assertTrue(second.getPrimary());
        assertEquals(20L, response.getId());

        verify(imageRepository).saveAll(anyList());
        verify(imageRepository).flush();
    }

    // TEST 6 - CLOUDINARY UPLOAD WITHOUT TRANSACTION
    @Test
    void uploadImageShouldCallCloudinary() throws IOException {

        when(productRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(product));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );

        when(cloudinaryService.uploadImage(file))
                .thenReturn(Map.of(
                        "secure_url",
                        "https://res.cloudinary.com/test/image.jpg",
                        "public_id",
                        "shreekrishna/products/test"
                ));

        // Without active transaction synchronization,
        // the service should reject the operation
        // and clean up the uploaded Cloudinary image.
        assertThrows(
                IllegalStateException.class,
                () -> imageService.uploadImage(1L, file)
        );

        verify(cloudinaryService).uploadImage(file);

        verify(cloudinaryService).deleteImage(
                "shreekrishna/products/test"
        );

        verify(imageRepository, never()).saveAndFlush(any());
    }
}
