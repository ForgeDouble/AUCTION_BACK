package com.example.auction.product.service;


import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.UnauthorizedAccessException;
import com.example.auction.common.service.S3ObjectService;
import com.example.auction.common.util.FileValidationUtil;
import com.example.auction.common.util.S3KeyUtil;
import com.example.auction.product.domain.Product;
import com.example.auction.product.domain.ProductImage;
import com.example.auction.product.dto.ProductImageDto;
import com.example.auction.product.repository.ProductImageRepository;
import com.example.auction.product.repository.ProductRepository;
import com.example.auction.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@Service
public class ProductImageService {

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final UserRepository userRepository;

    private final FileValidationUtil validator;
    private final S3KeyUtil keyUtil;
    private final S3ObjectService s3;

    private Long currentUserId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).
                orElseThrow(() -> new RuntimeException("로그인 필요")).getUserId();
    }

    private Product loadOwnedProduct(Long productId) {
        var product = productRepository.findByProductIdAndDelYn(productId, DelYN.N)
                .orElseThrow(() -> new RuntimeException("상품이 존재하지 않거나 비활성화"));
        if (!Objects.equals(product.getUser().getUserId(), currentUserId())) {
            throw new UnauthorizedAccessException("본인 상품만 수정 가능합니다.");
        }
        return product;
    }

    // 이미지 추가
    @Transactional
    public List<ProductImageDto> uploadInitial(Long productId, List<MultipartFile> files) {
        Product product = loadOwnedProduct(productId);
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("최소 1장의 이미지가 필요합니다.");
        }
        List<ProductImage> saved = new ArrayList<>();
        List<String> newKeys = new ArrayList<>();

        int startPos = (int) imageRepository.countByProduct_ProductId(productId);

        for (int i = 0; i < files.size(); i++) {
            MultipartFile f = files.get(i);
            validator.ensureImage(f);
            String ext = validator.ext(f.getContentType(), f.getOriginalFilename());
            String key = keyUtil.productImageKey(productId, ext);
            s3.put(key, f);
            newKeys.add(key);

            String url = s3.toPublicUrl(key);
            ProductImage pi = ProductImage.builder()
                    .product(product)
                    .s3Key(key)
                    .url(url)
                    .position(startPos + i)
                    .build();
            saved.add(pi);
        }
        imageRepository.saveAll(saved);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    newKeys.forEach(s3::delete);
                }
            }
        });

        return saved.stream().map(ProductImageDto::from).toList();
    }
}
