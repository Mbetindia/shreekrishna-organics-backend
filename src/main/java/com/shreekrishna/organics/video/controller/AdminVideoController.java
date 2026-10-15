package com.shreekrishna.organics.video.controller;

import com.shreekrishna.organics.video.dto.VideoRequest;
import com.shreekrishna.organics.video.dto.VideoResponse;
import com.shreekrishna.organics.video.service.WebsiteVideoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/videos")
public class AdminVideoController {
    private final WebsiteVideoService service;

    public AdminVideoController(WebsiteVideoService service) {
        this.service = service;
    }

    @GetMapping
    public List<VideoResponse> getAll() {
        return service.getAdminVideos();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VideoResponse create(@Valid @RequestBody VideoRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public VideoResponse update(@PathVariable Long id,
                                @Valid @RequestBody VideoRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
