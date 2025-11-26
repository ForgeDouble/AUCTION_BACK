package com.example.auction.chat.controller;

import com.example.auction.chat.dto.ChatFileRequest;
import com.example.auction.common.dto.CommonResDto;
import com.example.auction.common.service.S3ObjectService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/chat/file")
public class ChatFileController {

    private final S3ObjectService s3ObjectService;

    public ChatFileController(S3ObjectService s3ObjectService) {
        this.s3ObjectService = s3ObjectService;
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/upload")
    public ResponseEntity<CommonResDto> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }

        String originalName = (file.getOriginalFilename() != null) ? file.getOriginalFilename() : "file";
        String key = "chat/" + UUID.randomUUID() + "_" + originalName;

        String savedKey = s3ObjectService.put(key, file);
        String publicUrl = s3ObjectService.toPublicUrl(savedKey);

        ChatFileRequest dto = new ChatFileRequest();
        dto.setFileName(originalName);
        dto.setFileUrl(publicUrl);

        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "파일 업로드 성공", dto));
    }
}
