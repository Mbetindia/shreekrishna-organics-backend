package com.shreekrishna.organics.testimonial.dto;

import com.shreekrishna.organics.testimonial.entity.WebsiteTestimonial;

public record TestimonialResponse(
    Long id, String customerName, String comment, Integer rating,
    String imageUrl, Integer displayOrder, Boolean active
) {
    public static TestimonialResponse from(WebsiteTestimonial t) {
        return new TestimonialResponse(t.getId(), t.getCustomerName(), t.getComment(),
            t.getRating(), t.getImageUrl(), t.getDisplayOrder(), t.getActive());
    }
}
