package com.shreekrishna.organics.blog.dto;

import com.shreekrishna.organics.blog.entity.Blog;

import java.time.LocalDateTime;

public record BlogResponse(

        Long id,

        String title,

        String slug,

        String content,

        String imageUrl,

        String imagePublicId,

        String status,

        LocalDateTime createdAt,

        LocalDateTime updatedAt,

        LocalDateTime publishedAt

) {

    public static BlogResponse fromEntity(Blog blog) {

        return new BlogResponse(

                blog.getId(),

                blog.getTitle(),

                blog.getSlug(),

                blog.getContent(),

                blog.getImageUrl(),

                blog.getImagePublicId(),

                blog.getStatus(),

                blog.getCreatedAt(),

                blog.getUpdatedAt(),

                blog.getPublishedAt()
        );
    }
}