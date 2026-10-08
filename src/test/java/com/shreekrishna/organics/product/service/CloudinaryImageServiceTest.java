
package com.shreekrishna.organics.product.service;

import com.cloudinary.Cloudinary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CloudinaryImageServiceTest {

    private CloudinaryImageService imageService;

    @BeforeEach
    void setUp() {
        Cloudinary cloudinary = mock(Cloudinary.class);
        imageService = new CloudinaryImageService(cloudinary);
    }

    @Test
    void shouldRejectEmptyImage() {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> imageService.uploadImage(file)
        );
    }

    @Test
    void shouldRejectImageLargerThan5MB() {

        byte[] largeFile = new byte[5 * 1024 * 1024 + 1];

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.jpg",
                "image/jpeg",
                largeFile
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> imageService.uploadImage(file)
        );
    }

    @Test
    void shouldRejectUnsupportedFormat() {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                new byte[]{1, 2, 3}
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> imageService.uploadImage(file)
        );
    }

    @Test
    void shouldRejectFakeJpeg() {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fake.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3, 4}
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> imageService.uploadImage(file)
        );
    }

    @Test
    void shouldRejectFakePng() {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fake.png",
                "image/png",
                new byte[]{1, 2, 3, 4}
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> imageService.uploadImage(file)
        );
    }

    @Test
    void shouldRejectFakeWebp() {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fake.webp",
                "image/webp",
                new byte[]{1, 2, 3, 4}
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> imageService.uploadImage(file)
        );
    }

    @Test
    void shouldRejectMissingImage() {

        assertThrows(
                IllegalArgumentException.class,
                () -> imageService.uploadImage(null)
        );
    }

    @Test
    void shouldRejectMissingPublicId() {

        assertThrows(
                IllegalArgumentException.class,
                () -> imageService.deleteImage("")
        );
    }
}
