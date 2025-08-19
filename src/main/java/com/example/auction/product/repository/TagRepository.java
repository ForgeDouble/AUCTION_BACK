package com.example.auction.product.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.auction.product.domain.Tag;

public interface TagRepository extends JpaRepository<Tag, Long> {
	@Query("SELECT t FROM Tag t LEFT JOIN FETCH t.children WHERE t.tagId = :tagId")
    Optional<Tag> findByIdWithChildren(@Param("tagId") Long tagId);
}
