package com.shreekrishna.organics.blog.controller;

import com.shreekrishna.organics.blog.dto.BlogResponse;
import com.shreekrishna.organics.blog.service.BlogService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/blogs")
public class BlogController {

    private final BlogService blogService;

    public BlogController(BlogService blogService) {
        this.blogService = blogService;
    }

    // GET ALL PUBLISHED BLOGS

    @GetMapping
    public ResponseEntity<List<BlogResponse>> getPublishedBlogs() {

        return ResponseEntity.ok(
                blogService.getPublishedBlogs()
        );
    }

    // GET SINGLE PUBLISHED BLOG BY SLUG

    @GetMapping("/{slug}")
    public ResponseEntity<BlogResponse> getBlogBySlug(
            @PathVariable String slug
    ) {

        return ResponseEntity.ok(
                blogService.getPublishedBlogBySlug(slug)
        );
    }
}