package com.shreekrishna.organics.blog.service;

import com.shreekrishna.organics.blog.dto.BlogRequest;
import com.shreekrishna.organics.blog.dto.BlogResponse;
import com.shreekrishna.organics.blog.entity.Blog;
import com.shreekrishna.organics.blog.repository.BlogRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class BlogService {

    private final BlogRepository blogRepository;

    public BlogService(BlogRepository blogRepository) {
        this.blogRepository = blogRepository;
    }

    @Transactional
    public BlogResponse createBlog(BlogRequest request) {
        Blog blog = new Blog();
        blog.setTitle(request.title().trim());
        blog.setSlug(generateUniqueSlug(request.title()));
        blog.setContent(request.content());
        blog.setImageUrl(request.imageUrl());
        blog.setImagePublicId(request.imagePublicId());
        blog.setStatus("DRAFT");

        return BlogResponse.fromEntity(blogRepository.save(blog));
    }

    @Transactional(readOnly = true)
    public List<BlogResponse> getAllBlogs() {
        return blogRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(BlogResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public BlogResponse getAdminBlog(Long id) {
        return BlogResponse.fromEntity(findBlog(id));
    }

    @Transactional
    public BlogResponse updateBlog(Long id, BlogRequest request) {
        Blog blog = findBlog(id);

        String newTitle = request.title().trim();

        if (!blog.getTitle().equals(newTitle)) {
            blog.setSlug(generateUniqueSlug(newTitle));
        }

        blog.setTitle(newTitle);
        blog.setContent(request.content());
        blog.setImageUrl(request.imageUrl());
        blog.setImagePublicId(request.imagePublicId());

        return BlogResponse.fromEntity(blogRepository.save(blog));
    }

    @Transactional
    public BlogResponse publishBlog(Long id) {
        Blog blog = findBlog(id);

        if (!"PUBLISHED".equals(blog.getStatus())) {
            blog.setStatus("PUBLISHED");
            blog.setPublishedAt(LocalDateTime.now());
        }

        return BlogResponse.fromEntity(blogRepository.save(blog));
    }

    @Transactional
    public BlogResponse moveToDraft(Long id) {
        Blog blog = findBlog(id);

        blog.setStatus("DRAFT");
        blog.setPublishedAt(null);

        return BlogResponse.fromEntity(blogRepository.save(blog));
    }

    @Transactional
    public void deleteBlog(Long id) {
        Blog blog = findBlog(id);
        blogRepository.delete(blog);
    }

    @Transactional(readOnly = true)
    public List<BlogResponse> getPublishedBlogs() {
        return blogRepository.findByStatusOrderByPublishedAtDesc("PUBLISHED")
                .stream()
                .map(BlogResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public BlogResponse getPublishedBlogBySlug(String slug) {
        Blog blog = blogRepository.findBySlug(slug)
                .filter(b -> "PUBLISHED".equals(b.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Blog not found"
                ));

        return BlogResponse.fromEntity(blog);
    }

    private Blog findBlog(Long id) {
        return blogRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Blog not found"
                ));
    }

    private String generateUniqueSlug(String title) {
        String baseSlug = Normalizer.normalize(
                        title.toLowerCase(Locale.ROOT),
                        Normalizer.Form.NFD
                )
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");

        if (baseSlug.isBlank()) {
            baseSlug = "blog";
        }

        if (baseSlug.length() > 200) {
            baseSlug = baseSlug.substring(0, 200)
                    .replaceAll("-+$", "");
        }

        String slug = baseSlug;

        while (blogRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" +
                    UUID.randomUUID().toString().substring(0, 8);
        }

        return slug;
    }
}