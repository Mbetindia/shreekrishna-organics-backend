package com.shreekrishna.organics.video.dto;

public record VideoResponse(
        Long id,
        String title,
        String youtubeUrl,
        String videoId,
        String thumbnailUrl,
        String description,
        Boolean active,
        Integer displayOrder
) {}
