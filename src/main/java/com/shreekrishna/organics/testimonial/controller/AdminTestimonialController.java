package com.shreekrishna.organics.testimonial.controller;

import com.shreekrishna.organics.testimonial.dto.*;
import com.shreekrishna.organics.testimonial.service.WebsiteTestimonialService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/testimonials")
public class AdminTestimonialController {
    private final WebsiteTestimonialService service;
    public AdminTestimonialController(WebsiteTestimonialService service) { this.service = service; }

    @GetMapping
    public List<TestimonialResponse> list() { return service.adminList(); }

    @PostMapping
    public ResponseEntity<TestimonialResponse> create(@Valid @RequestBody TestimonialRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public TestimonialResponse update(@PathVariable Long id, @Valid @RequestBody TestimonialRequest request) {
        return service.update(id, request);
    }

    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TestimonialResponse photo(@PathVariable Long id, @RequestParam("file") MultipartFile file) throws IOException {
        return service.replacePhoto(id, file);
    }
}
