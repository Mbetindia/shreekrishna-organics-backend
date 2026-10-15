package com.shreekrishna.organics.testimonial.controller;

import com.shreekrishna.organics.testimonial.dto.TestimonialResponse;
import com.shreekrishna.organics.testimonial.service.WebsiteTestimonialService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/testimonials")
public class WebsiteTestimonialController {
    private final WebsiteTestimonialService service;
    public WebsiteTestimonialController(WebsiteTestimonialService service) { this.service = service; }
    @GetMapping
    public List<TestimonialResponse> list() { return service.publicList(); }
}
