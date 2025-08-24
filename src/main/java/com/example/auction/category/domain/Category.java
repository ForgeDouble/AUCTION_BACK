package com.example.auction.category.domain;

import java.util.ArrayList;
import java.util.List;

import com.example.auction.product.domain.Product;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
public class Category {
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long categoryId;
	
	@OneToMany(mappedBy = "tag")
//	@Builder.Default
    private List<Product> products = new ArrayList<>();
	
	@Column(nullable = false)
	private String categoryName;
	
	 // 부모 태그
    @ManyToOne
    @JoinColumn(name = "parent_id")
//    @JsonBackReference
    private Category parent;

    // 자식 태그들
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
//    @JsonManagedReference
//    @Builder.Default
    private List<Category> children = new ArrayList<>();
}
