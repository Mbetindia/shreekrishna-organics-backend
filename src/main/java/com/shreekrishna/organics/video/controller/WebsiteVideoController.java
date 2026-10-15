package com.shreekrishna.organics.video.controller;

import com.shreekrishna.organics.video.dto.VideoResponse;
import com.shreekrishna.organics.video.service.WebsiteVideoService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/videos")
public class WebsiteVideoController {
    private final WebsiteVideoService service;

    public WebsiteVideoController(WebsiteVideoService service) {
        this.service = service;
    }

    @GetMapping
    public List<VideoResponse> getVideos() {
        return service.getPublicVideos();
    }
}
