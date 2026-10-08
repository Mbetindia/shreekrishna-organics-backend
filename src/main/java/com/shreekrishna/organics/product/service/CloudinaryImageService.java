
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

    private static final long MAX_FILE_SIZE =
            5 * 1024 * 1024;

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    private final Cloudinary cloudinary;

    public CloudinaryImageService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    // UPLOAD IMAGE TO CLOUDINARY
    public Map<String, Object> uploadImage(MultipartFile file)
            throws IOException {

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

        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException(
                    "Only JPG, PNG and WEBP images are allowed"
            );
        }

        return cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                        "folder", "shreekrishna/products",
                        "resource_type", "image"
                )
        );
    }

    // DELETE IMAGE FROM CLOUDINARY
    public void deleteImage(String publicId)
            throws IOException {

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
