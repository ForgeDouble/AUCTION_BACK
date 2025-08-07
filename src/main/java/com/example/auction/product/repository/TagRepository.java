package com.example.auction.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.auction.product.domain.Tag;

public interface TagRepository extends JpaRepository<Tag, Long> {

}
