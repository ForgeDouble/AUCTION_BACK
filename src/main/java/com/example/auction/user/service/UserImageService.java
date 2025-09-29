package com.example.auction.user.service;

import com.example.auction.common.domain.DelYN;
import com.example.auction.common.service.S3ObjectService;
import com.example.auction.common.util.FileValidationUtil;
import com.example.auction.common.util.S3KeyUtil;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
                .orElseThrow(() -> new RuntimeException("User"))
                .getUserId();
    }

    @Transactional
    public String uploadOrReplace(MultipartFile file) throws IOException {
        Long userId = currentUserId();
        validator.ensureImage(file);
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User"));

        String ext = validator.ext(file.getContentType());
        String newKey = keyUtil.userAvatarKey(userId, ext);

        s3.put(newKey, file);
        s3.delete(user.getProfileImageKey());

        String url = s3.toPublicUrl(newKey);
        user.setProfileImageKey(newKey);
        user.setProfileImageUrl(url);
        return url;
    }

    @Transactional
    public void deleteAvatar() {
        Long userId = currentUserId();
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User"));
        s3.delete(user.getProfileImageKey());
        user.setProfileImageKey(null);
        user.setProfileImageUrl(null);
    }
}
