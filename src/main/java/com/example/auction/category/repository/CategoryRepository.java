package com.example.auction.category.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.auction.category.domain.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {
	@Query("SELECT c FROM Category c LEFT JOIN FETCH c.children WHERE c.categoryId = :categoryId")
    Optional<Category> findByIdWithChildren(@Param("tagId") Long tagId);
	
	@Query("SELECT DISTINCT c FROM Category c LEFT JOIN FETCH t.children")
	List<Category> findAllWithChildren();
}
