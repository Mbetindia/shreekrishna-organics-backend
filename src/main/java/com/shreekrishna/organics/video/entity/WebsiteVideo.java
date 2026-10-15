package com.shreekrishna.organics.video.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "website_videos")
public class WebsiteVideo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "youtube_url", nullable = false, length = 1000)
    private String youtubeUrl;

    @Column(name = "video_id", nullable = false, length = 50)
    private String videoId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String value) { this.title = value; }
    public String getYoutubeUrl() { return youtubeUrl; }
    public void setYoutubeUrl(String value) { this.youtubeUrl = value; }
    public String getVideoId() { return videoId; }
    public void setVideoId(String value) { this.videoId = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { this.description = value; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean value) { this.active = value; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer value) { this.displayOrder = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
