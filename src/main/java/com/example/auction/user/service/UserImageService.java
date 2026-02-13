package com.example.auction.user.service;

import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.UnauthorizedAccessException;
import com.example.auction.common.service.S3ObjectService;
import com.example.auction.common.util.FileValidationUtil;
import com.example.auction.common.util.S3KeyUtil;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class UserImageService {

    private final UserRepository userRepository;
    private final FileValidationUtil validator;
    private final S3KeyUtil keyUtil;
    private final S3ObjectService s3;

    private Long currentUserId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new UnauthorizedAccessException("INVALID_USER", "유효하지 않은 유저입니다. email:" + email))
                .getUserId();
    }

    @Transactional
    public String uploadOrReplace(MultipartFile file) throws IOException {
        Long userId = currentUserId();
        validator.ensureImage(file);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedAccessException("INVALID_USER", "유효하지 않은 유저입니다."));

        String ext = validator.ext(file.getContentType(), file.getOriginalFilename());
        String newKey = keyUtil.userAvatarKey(userId, ext);

        s3.put(newKey, file);
        String url = s3.toPublicUrl(newKey);

        String oldKey = user.getProfileImageKey();
        user.setProfileImageKey(newKey);
        user.setProfileImageUrl(url);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                if (oldKey != null && !oldKey.isBlank()) s3.delete(oldKey);
            }
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) s3.delete(newKey);
            }
        });
        return url;
    }

    @Transactional
    public void deleteAvatar() {
        Long userId = currentUserId();
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User"));

        String oldKey = user.getProfileImageKey();
        user.setProfileImageKey(null);
        user.setProfileImageUrl(null);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                if (oldKey != null && !oldKey.isBlank()) s3.delete(oldKey);
            }
        });
    }
}