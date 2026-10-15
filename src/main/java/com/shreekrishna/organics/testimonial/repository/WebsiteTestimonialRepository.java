package com.shreekrishna.organics.testimonial.repository;

import com.shreekrishna.organics.testimonial.entity.WebsiteTestimonial;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WebsiteTestimonialRepository extends JpaRepository<WebsiteTestimonial, Long> {
    List<WebsiteTestimonial> findAllByOrderByDisplayOrderAsc();
    List<WebsiteTestimonial> findByActiveTrueOrderByDisplayOrderAsc();
}
