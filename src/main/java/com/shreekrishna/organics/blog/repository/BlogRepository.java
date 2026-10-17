package com.shreekrishna.organics.blog.repository;

import com.shreekrishna.organics.blog.entity.Blog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BlogRepository extends JpaRepository<Blog, Long> {

    Optional<Blog> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Blog> findByStatusOrderByPublishedAtDesc(String status);

    List<Blog> findAllByOrderByCreatedAtDesc();
}