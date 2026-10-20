package com.nhom6.motofix.controller;

import com.nhom6.motofix.dto.respond.UploadMediaResponse;
import com.nhom6.motofix.service.SupabaseStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {
    private final SupabaseStorageService storageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<UploadMediaResponse> upload(@RequestPart("files") List<MultipartFile> files)
            throws IOException {
        return storageService.uploadMedia(files);
    }
}