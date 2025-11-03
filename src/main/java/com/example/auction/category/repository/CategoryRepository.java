package com.example.auction.category.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.auction.category.domain.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {
//	@Query("SELECT c FROM Category c LEFT JOIN FETCH c.children WHERE c.categoryId = :categoryId")
//    Optional<Category> findByIdWithChildren(@Param("categoryId") Long categoryId);
	
//	@Query("SELECT DISTINCT c FROM Category c LEFT JOIN FETCH c.children")
//	List<Category> findAllWithChildren();

    List<Category> findAllByParent_CategoryId(Long parentId);

    Optional<Category> findByCategoryName(String categoryName);

    @Query("SELECT c FROM Category c WHERE c.categoryId IN :ids")
    List<Category> findAllByIds(@Param("ids") List<Long> ids);

    @EntityGraph(attributePaths = {
            "children",           // 1단계
            "children.children",  // 2단계
            "children.children.children"  // 3단계
    })
    @Query("SELECT c FROM Category c WHERE c.parent IS NULL")
    List<Category> findAllParentCategoriesWithChildren();
}
