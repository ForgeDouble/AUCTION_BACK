package com.example.auction.category.domain;

import java.util.ArrayList;
import java.util.List;

import com.example.auction.product.domain.Product;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Fetch;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@BatchSize(size = 100)
public class Category {
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long categoryId;
	
	@OneToMany(mappedBy = "category")
//	@Builder.Default
    private List<Product> products = new ArrayList<>();
	
	@Column(nullable = false)
	private String categoryName;
	
	 // 부모 태그
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")

//    @JsonBackReference
    private Category parent;

    // 자식 태그들
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
//    @JsonManagedReference
//    @Builder.Default
    private List<Category> children = new ArrayList<>();

    public List<Category> getPath() {
        List<Category> path = new ArrayList<>();
        Category current = this;
        while (current != null) {
            path.add(0, current); // 앞에 추가
            current = current.parent;
        }
        return path;
    }
}
