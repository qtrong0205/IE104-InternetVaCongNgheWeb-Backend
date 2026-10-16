package com.nhom6.motofix.controller;

import com.nhom6.motofix.service.SupabaseStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {
    private final SupabaseStorageService supabaseStorageService;

    @PostMapping("/images")
    public String uploadImage(@RequestPart("image") MultipartFile image) throws IOException {
        return supabaseStorageService.uploadImage(image);
    }

    @PostMapping("/videos")
    public String uploadVideo(@RequestPart("video") MultipartFile video) throws IOException {
        return supabaseStorageService.uploadVideo(video);
    }
}
