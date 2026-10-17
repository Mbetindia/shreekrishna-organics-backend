package com.shreekrishna.organics.blog.controller;

import com.shreekrishna.organics.blog.dto.BlogRequest;
import com.shreekrishna.organics.blog.dto.BlogResponse;
import com.shreekrishna.organics.blog.service.BlogService;
import com.shreekrishna.organics.product.service.CloudinaryImageService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/blogs")
public class AdminBlogController {

    private final BlogService blogService;
    private final CloudinaryImageService cloudinaryImageService;

    public AdminBlogController(
            BlogService blogService,
            CloudinaryImageService cloudinaryImageService
    ) {
        this.blogService = blogService;
        this.cloudinaryImageService = cloudinaryImageService;
    }

    // CREATE BLOG - DEFAULT DRAFT

    @PostMapping
    public ResponseEntity<BlogResponse> createBlog(
            @Valid @RequestBody BlogRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(blogService.createBlog(request));
    }

    // GET ALL BLOGS - ADMIN

    @GetMapping
    public ResponseEntity<List<BlogResponse>> getAllBlogs() {
        return ResponseEntity.ok(blogService.getAllBlogs());
    }

    // GET SINGLE BLOG - ADMIN

    @GetMapping("/{id}")
    public ResponseEntity<BlogResponse> getBlog(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(blogService.getAdminBlog(id));
    }

    // UPDATE BLOG

    @PutMapping("/{id}")
    public ResponseEntity<BlogResponse> updateBlog(
            @PathVariable Long id,
            @Valid @RequestBody BlogRequest request
    ) {
        return ResponseEntity.ok(blogService.updateBlog(id, request));
    }

    // PUBLISH BLOG

    @PatchMapping("/{id}/publish")
    public ResponseEntity<BlogResponse> publishBlog(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(blogService.publishBlog(id));
    }

    // MOVE BLOG TO DRAFT

    @PatchMapping("/{id}/draft")
    public ResponseEntity<BlogResponse> moveToDraft(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(blogService.moveToDraft(id));
    }

    // DELETE BLOG

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBlog(
            @PathVariable Long id
    ) {
        blogService.deleteBlog(id);
        return ResponseEntity.noContent().build();
    }

    // UPLOAD BLOG IMAGE TO CLOUDINARY

    @PostMapping(
            value = "/images",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Map<String, String>> uploadBlogImage(
            @RequestParam("file") MultipartFile file
    ) throws IOException {

        Map<String, Object> result =
                cloudinaryImageService.uploadBlogImage(file);

        String imageUrl = String.valueOf(
                result.get("secure_url")
        );

        String publicId = String.valueOf(
                result.get("public_id")
        );

        return ResponseEntity.ok(
                Map.of(
                        "imageUrl", imageUrl,
                        "imagePublicId", publicId
                )
        );
    }
}