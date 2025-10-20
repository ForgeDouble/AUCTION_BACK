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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;



@RequiredArgsConstructor
@Service
public class ProductImageService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final UserRepository userRepository;

    private final FileValidationUtil validator;
    private final S3KeyUtil keyUtil;
    private final S3ObjectService s3;

    @Value("${storage.max-images-per-product:10}")
    private int maxImages;

    public record ReplaceSpec(Long imageId, MultipartFile file) {
    }

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
        int current = (int) productImageRepository.countByProduct_ProductId(productId);
        if (current + files.size() > maxImages) {
            throw new IllegalArgumentException("최대 " + maxImages + "장까지 업로드 가능합니다.");
        }

        List<ProductImage> productImages = new ArrayList<>();
        List<String> newKeys = new ArrayList<>();
        int startPosition = current;

        for (int i = 0; i < files.size(); i++) {
            MultipartFile f = files.get(i);
            validator.ensureImage(f);
            String ext = validator.ext(f.getContentType(), f.getOriginalFilename());
            String key = keyUtil.productImageKey(productId, ext);
            s3.put(key, f);
            newKeys.add(key);
            String url = s3.toPublicUrl(key);

            productImages.add(ProductImage.builder()
                    .product(product)
                    .s3Key(key)
                    .url(url)
                    .position(startPosition + i)
                    .build());
        }
        productImageRepository.saveAll(productImages);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) newKeys.forEach(s3::delete);
            }
        });

        return productImages.stream().map(ProductImageDto::from).toList();
    }


    @Transactional
    public void applyOps(Long productId,
                         List<MultipartFile> addFiles,
                         List<Long> deleteIds,
                         List<Long> orderIds) {

        boolean noOps = isEmpty(addFiles) && isEmpty(deleteIds) && isEmpty(orderIds);
        // 번경 없을 경우 이미지 그대로 유지
        if (noOps) return;

        loadOwnedProduct(productId);

        if (!isEmpty(addFiles)) {
            int current = (int) productImageRepository.countByProduct_ProductId(productId);
            if (current + addFiles.size() > maxImages) {
                throw new IllegalArgumentException("최대 " + maxImages + "장까지 업로드 가능합니다.");
            }
            uploadInitial(productId, addFiles);
        }


        if (!isEmpty(deleteIds)) {
            for (Long id : deleteIds) deleteOne(productId, id);
        }

        long remain = productImageRepository.countByProduct_ProductId(productId);
        if (remain == 0) {
            throw new IllegalArgumentException("이미지는 최소 1장 이상이어야 합니다.");
        }

        if (!isEmpty(orderIds)) {
            reorder(productId, orderIds);
        }
    }

    @Transactional
    public void reorder(Long productId, List<Long> ids) {
        loadOwnedProduct(productId);
        var imgs = productImageRepository.findByProduct_ProductIdOrderByPositionAsc(productId);
        if (imgs.size() != ids.size())
            throw new IllegalArgumentException("현재 이미지 수와 id 수가 다릅니다.");

        var map = imgs.stream().collect(Collectors.toMap(ProductImage::getId, x -> x));
        if (!map.keySet().containsAll(ids))
            throw new IllegalArgumentException("잘못된 imageId가 포함되어 있습니다.");

        for (int i = 0; i < ids.size(); i++) {
            map.get(ids.get(i)).setPosition(i);
        }
        productImageRepository.saveAll(imgs);
    }

    @Transactional
    public void deleteOne(Long productId, Long imageId) {
        loadOwnedProduct(productId);
        var img = productImageRepository.findByIdAndProduct_ProductId(imageId, productId)
                .orElseThrow(() -> new RuntimeException("이미지 없음"));

        String oldKey = img.getS3Key();
        productImageRepository.delete(img);

        var remain = productImageRepository.findByProduct_ProductIdOrderByPositionAsc(productId);
        for (int i = 0; i < remain.size(); i++) {
            remain.get(i).setPosition(i);
        }
        productImageRepository.saveAll(remain);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                s3.delete(oldKey);
            }
        });
    }
// 이미지를 대체하는 기능이 불 필요 함 고려하여 주석처리
//    @Transactional
//    public void replaceOne(Long productId, Long imageId, MultipartFile file) {
//        loadOwnedProduct(productId);
//        validator.ensureImage(file);
//
//        var img = productImageRepository.findByIdAndProduct_ProductId(imageId, productId)
//                .orElseThrow(() -> new RuntimeException("이미지 없음"));
//
//        String oldKey = img.getS3Key();
//        String ext = validator.ext(file.getContentType(), file.getOriginalFilename());
//        String newKey = keyUtil.productImageKey(productId, ext);
//        s3.put(newKey, file);
//        String newUrl = s3.toPublicUrl(newKey);
//
//        img.setS3Key(newKey);
//        img.setUrl(newUrl);
//        productImageRepository.save(img);
//
//        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
//            @Override
//            public void afterCommit() {
//                if (oldKey != null && !oldKey.isBlank()) s3.delete(oldKey);
//            }
//
//            @Override
//            public void afterCompletion(int status) {
//                if (status == STATUS_ROLLED_BACK) s3.delete(newKey);
//            }
//        });
//    }

    private static boolean isEmpty(Collection<?> c) {
        return c == null || c.isEmpty();
    }
}
