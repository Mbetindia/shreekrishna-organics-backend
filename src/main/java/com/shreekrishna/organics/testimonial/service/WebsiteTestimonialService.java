package com.shreekrishna.organics.testimonial.service;

import com.shreekrishna.organics.product.service.CloudinaryImageService;
import com.shreekrishna.organics.testimonial.dto.TestimonialRequest;
import com.shreekrishna.organics.testimonial.dto.TestimonialResponse;
import com.shreekrishna.organics.testimonial.entity.WebsiteTestimonial;
import com.shreekrishna.organics.testimonial.repository.WebsiteTestimonialRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class WebsiteTestimonialService {
    private final WebsiteTestimonialRepository repo;
    private final CloudinaryImageService images;

    public WebsiteTestimonialService(WebsiteTestimonialRepository repo, CloudinaryImageService images) {
        this.repo = repo;
        this.images = images;
    }

    @Transactional(readOnly = true)
    public List<TestimonialResponse> publicList() {
        return repo.findByActiveTrueOrderByDisplayOrderAsc().stream().map(TestimonialResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TestimonialResponse> adminList() {
        return repo.findAllByOrderByDisplayOrderAsc().stream().map(TestimonialResponse::from).toList();
    }

    @Transactional
    public TestimonialResponse update(Long id, TestimonialRequest request) {
        WebsiteTestimonial t = get(id);
        t.setCustomerName(request.customerName().trim());
        t.setComment(request.comment().trim());
        t.setRating(request.rating());
        t.setActive(request.active());
        return TestimonialResponse.from(repo.save(t));
    }

    // Create the four initial slots using this operation once. It is capped at four.
    @Transactional
    public TestimonialResponse create(TestimonialRequest request) {
        List<WebsiteTestimonial> existing = repo.findAllByOrderByDisplayOrderAsc();
        if (existing.size() >= 4) throw new ResponseStatusException(HttpStatus.CONFLICT, "Maximum four testimonials");
        WebsiteTestimonial t = new WebsiteTestimonial();
        t.setCustomerName(request.customerName().trim());
        t.setComment(request.comment().trim());
        t.setRating(request.rating());
        t.setActive(request.active());
        int slot = 1;
        while (true) {
            final int candidate = slot;
            if (existing.stream().noneMatch(x -> x.getDisplayOrder().equals(candidate))) break;
            slot++;
        }
        t.setDisplayOrder(slot);
        return TestimonialResponse.from(repo.save(t));
    }

    // Upload first, save URL, then attempt to remove old asset. Old deletion failure
    // must not roll back the new URL. Configure cloudinary folder isolation separately.
    public TestimonialResponse replacePhoto(Long id, MultipartFile file) throws IOException {
        get(id); // Validate existence before upload
        Map<String, Object> uploaded = images.uploadTestimonialImage(file);
        String url = (String) uploaded.get("secure_url");
        String publicId = (String) uploaded.get("public_id");
        if (url == null || publicId == null) throw new IOException("Cloudinary response missing URL or public ID");
        String oldId;
        try {
            WebsiteTestimonial t = get(id);
            oldId = t.getImagePublicId();
            t.setImageUrl(url);
            t.setImagePublicId(publicId);
            repo.save(t);
        } catch (RuntimeException e) {
            try { images.deleteImage(publicId); } catch (IOException cleanupError) { e.addSuppressed(cleanupError); }
            throw e;
        }
        if (oldId != null && !oldId.isBlank()) {
            try { images.deleteImage(oldId); }
            catch (IOException e) { /* Log/queue old asset cleanup in production */ }
        }
        return TestimonialResponse.from(get(id));
    }

    private WebsiteTestimonial get(Long id) {
        return repo.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Testimonial not found"));
    }
}
