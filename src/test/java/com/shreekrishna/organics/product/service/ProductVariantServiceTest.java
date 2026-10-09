
package com.shreekrishna.organics.product.service;

import com.shreekrishna.organics.exception.ResourceNotFoundException;
import com.shreekrishna.organics.product.dto.ProductVariantRequest;
import com.shreekrishna.organics.product.dto.ProductVariantResponse;
import com.shreekrishna.organics.product.entity.Product;
import com.shreekrishna.organics.product.entity.ProductVariant;
import com.shreekrishna.organics.product.repository.ProductRepository;
import com.shreekrishna.organics.product.repository.ProductVariantRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductVariantServiceTest {

    @Mock
    private ProductVariantRepository variantRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductVariantService variantService;

    private Product product;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        product = new Product();
        ReflectionTestUtils.setField(product, "id", 1L);

        variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", 10L);
        variant.setProduct(product);
        variant.setSku("OIL-500ML");
        variant.setActive(true);
    }

    private ProductVariantRequest createRequest() {
        ProductVariantRequest request = new ProductVariantRequest();
        request.setProductId(1L);
        request.setSku("OIL-500ML");
        request.setActive(true);
        return request;
    }

    @Test
    void createShouldSaveVariant() {
        ProductVariantRequest request = createRequest();

        when(variantRepository.existsBySku("OIL-500ML"))
                .thenReturn(false);
        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        when(variantRepository.save(any(ProductVariant.class)))
                .thenAnswer(invocation -> {
                    ProductVariant saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 10L);
                    return saved;
                });

        ProductVariantResponse response = variantService.create(request);

        assertNotNull(response);
        assertEquals("OIL-500ML", response.getSku());
        verify(variantRepository).save(any(ProductVariant.class));
    }

    @Test
    void createShouldRejectDuplicateSku() {
        ProductVariantRequest request = createRequest();

        when(variantRepository.existsBySku("OIL-500ML"))
                .thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> variantService.create(request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(variantRepository, never()).save(any());
    }

    @Test
    void createShouldFailWhenProductNotFound() {
        ProductVariantRequest request = createRequest();

        when(productRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> variantService.create(request)
        );

        verify(variantRepository, never()).save(any());
    }

    @Test
    void getByIdShouldReturnVariant() {
        when(variantRepository.findById(10L))
                .thenReturn(Optional.of(variant));

        ProductVariantResponse response = variantService.getById(10L);

        assertEquals("OIL-500ML", response.getSku());
        assertEquals(1L, response.getProductId());
    }

    @Test
    void getByIdShouldFailWhenNotFound() {
        when(variantRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> variantService.getById(99L)
        );
    }

    @Test
    void getByProductShouldReturnVariants() {
        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        when(variantRepository.findByProduct_Id(1L))
                .thenReturn(List.of(variant));

        List<ProductVariantResponse> responses =
                variantService.getByProduct(1L);

        assertEquals(1, responses.size());
        assertEquals("OIL-500ML", responses.get(0).getSku());
    }

    @Test
    void updateShouldSaveChanges() {
        ProductVariantRequest request = createRequest();
        request.setSku("OIL-1L");

        when(variantRepository.findById(10L))
                .thenReturn(Optional.of(variant));
        when(variantRepository.findBySku("OIL-1L"))
                .thenReturn(Optional.empty());
        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));
        when(variantRepository.save(variant))
                .thenReturn(variant);

        ProductVariantResponse response =
                variantService.update(10L, request);

        assertEquals("OIL-1L", response.getSku());
        verify(variantRepository).save(variant);
    }

    @Test
    void updateShouldRejectDuplicateSku() {
        ProductVariantRequest request = createRequest();
        request.setSku("DUPLICATE-SKU");

        ProductVariant existing = new ProductVariant();
        ReflectionTestUtils.setField(existing, "id", 20L);

        when(variantRepository.findById(10L))
                .thenReturn(Optional.of(variant));
        when(variantRepository.findBySku("DUPLICATE-SKU"))
                .thenReturn(Optional.of(existing));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> variantService.update(10L, request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(variantRepository, never()).save(any());
    }

    @Test
    void softDeleteShouldDeactivateVariant() {
        when(variantRepository.findById(10L))
                .thenReturn(Optional.of(variant));

        variantService.softDelete(10L);

        assertFalse(variant.getActive());
        verify(variantRepository).save(variant);
    }

    @Test
    void softDeleteShouldFailWhenNotFound() {
        when(variantRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> variantService.softDelete(99L)
        );

        verify(variantRepository, never()).save(any());
    }
}
