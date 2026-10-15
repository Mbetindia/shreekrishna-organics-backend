package com.shreekrishna.organics.testimonial.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "website_testimonials")
public class WebsiteTestimonial {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String customerName;
    @Column(nullable = false, length = 2000)
    private String comment;
    @Column(nullable = false)
    private Integer rating;
    @Column(length = 2048)
    private String imageUrl;
    @Column(length = 255)
    private String imagePublicId;
    @Column(nullable = false, unique = true)
    private Integer displayOrder;
    @Column(nullable = false)
    private Boolean active = true;

    public Long getId() { return id; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String v) { customerName = v; }
    public String getComment() { return comment; }
    public void setComment(String v) { comment = v; }
    public Integer getRating() { return rating; }
    public void setRating(Integer v) { rating = v; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String v) { imageUrl = v; }
    public String getImagePublicId() { return imagePublicId; }
    public void setImagePublicId(String v) { imagePublicId = v; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer v) { displayOrder = v; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean v) { active = v; }
}
