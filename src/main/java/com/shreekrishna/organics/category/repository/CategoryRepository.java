package com.shreekrishna.organics.category.repository;

import com.shreekrishna.organics.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findBySlug(String slug);

    boolean existsByNameIgnoreCase(String name);

    boolean existsBySlug(String slug);

    List<Category> findByActiveTrueOrderByDisplayOrderAsc();
}