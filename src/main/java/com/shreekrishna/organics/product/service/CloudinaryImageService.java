
package com.shreekrishna.organics.product.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Service
public class CloudinaryImageService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    private final Cloudinary cloudinary;

    public CloudinaryImageService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    // ==========================================
    // PRODUCT IMAGE UPLOAD
    // ==========================================

    public Map<String, Object> uploadImage(
            MultipartFile file
    ) throws IOException {

        return uploadToFolder(
                file,
                "shreekrishna/products"
        );
    }

    // ==========================================
    // WEBSITE TESTIMONIAL IMAGE UPLOAD
    // ==========================================

    public Map<String, Object> uploadTestimonialImage(
            MultipartFile file
    ) throws IOException {

        return uploadToFolder(
                file,
                "shreekrishna/testimonials"
        );
    }

    // ==========================================
    // BLOG IMAGE UPLOAD - NEW
    // ==========================================

    public Map<String, Object> uploadBlogImage(
            MultipartFile file
    ) throws IOException {

        return uploadToFolder(
                file,
                "shreekrishna/blogs"
        );
    }

    // ==========================================
    // COMMON CLOUDINARY UPLOAD METHOD
    // ==========================================

    private Map<String, Object> uploadToFolder(
            MultipartFile file,
            String folder
    ) throws IOException {

        byte[] imageBytes = validateImage(file);

        return cloudinary.uploader().upload(
                imageBytes,
                ObjectUtils.asMap(
                        "folder", folder,
                        "resource_type", "image"
                )
        );
    }

    // ==========================================
    // IMAGE VALIDATION
    // ==========================================

    private byte[] validateImage(
            MultipartFile file
    ) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Please select an image"
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "Image size must be 5MB or less"
            );
        }

        String contentType = file.getContentType();

        if (contentType == null ||
                !ALLOWED_TYPES.contains(contentType)) {

            throw new IllegalArgumentException(
                    "Only JPG, PNG and WEBP images are allowed"
            );
        }

        byte[] imageBytes = file.getBytes();

        if (imageBytes.length == 0 ||
                imageBytes.length > MAX_FILE_SIZE) {

            throw new IllegalArgumentException(
                    "Invalid image size"
            );
        }

        if (!isValidImageSignature(
                imageBytes,
                contentType
        )) {

            throw new IllegalArgumentException(
                    "Invalid image content or file format"
            );
        }

        return imageBytes;
    }

    // ==========================================
    // VALIDATE IMAGE SIGNATURE
    // ==========================================

    private boolean isValidImageSignature(
            byte[] bytes,
            String contentType
    ) {

        return switch (contentType) {
            case "image/jpeg" -> isJpeg(bytes);
            case "image/png" -> isPng(bytes);
            case "image/webp" -> isWebp(bytes);
            default -> false;
        };
    }

    // ==========================================
    // JPEG MAGIC BYTES
    // ==========================================

    private boolean isJpeg(byte[] bytes) {

        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    // ==========================================
    // PNG MAGIC BYTES
    // ==========================================

    private boolean isPng(byte[] bytes) {

        return bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && (bytes[1] & 0xFF) == 0x50
                && (bytes[2] & 0xFF) == 0x4E
                && (bytes[3] & 0xFF) == 0x47
                && (bytes[4] & 0xFF) == 0x0D
                && (bytes[5] & 0xFF) == 0x0A;
    }

    // ==========================================
    // WEBP MAGIC BYTES
    // ==========================================

    private boolean isWebp(byte[] bytes) {

        return bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P';
    }

    // ==========================================
    // DELETE IMAGE FROM CLOUDINARY
    // ==========================================

    public void deleteImage(
            String publicId
    ) throws IOException {

        if (publicId == null || publicId.isBlank()) {
            throw new IllegalArgumentException(
                    "Cloudinary Public ID is required"
            );
        }

        Map<?, ?> result = cloudinary.uploader().destroy(
                publicId,
                ObjectUtils.asMap(
                        "resource_type", "image",
                        "invalidate", true
                )
        );

        Object status = result.get("result");

        if (!"ok".equals(status)
                && !"not found".equals(status)) {

            throw new IOException(
                    "Cloudinary image deletion failed: " + status
            );
        }
    }
}
