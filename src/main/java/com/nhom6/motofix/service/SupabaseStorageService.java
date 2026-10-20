package com.nhom6.motofix.service;

import com.nhom6.motofix.dto.respond.UploadMediaResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SupabaseStorageService {

    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;   // 5 MB
    private static final long MAX_VIDEO_SIZE = 50L * 1024 * 1024;  // 50 MB
    private static final int MAX_FILES = 10;

    @Value("${spring.supabase.url}")
    private String supabaseUrl;

    @Value("${spring.supabase.key}")
    private String supabaseKey;

    @Value("${spring.supabase.bucket-images}")
    private String imageBucket;

    @Value("${spring.supabase.bucket-videos}")
    private String videoBucket;

    private final RestClient restClient = RestClient.create();

    public String uploadImage(MultipartFile file) throws IOException {
        validate(file, MAX_IMAGE_SIZE, "image/");

        String extension = getFileExtension(file.getOriginalFilename());
        MediaType mediaType = getImageMediaType(extension);

        return upload(file, imageBucket, extension, mediaType);
    }

    public String uploadVideo(MultipartFile file) throws IOException {
        validate(file, MAX_VIDEO_SIZE, "video/");

        String extension = getFileExtension(file.getOriginalFilename());
        MediaType mediaType = getVideoMediaType(extension);

        return upload(file, videoBucket, extension, mediaType);
    }

    public UploadMediaResponse uploadMedia(MultipartFile file) throws IOException {
        var type = checkFile(file);

        String url = (type == com.nhom6.motofix.enums.MediaType.IMAGE)
                ? uploadImage(file)
                : uploadVideo(file);

        return new UploadMediaResponse(url, type);
    }

    public List<UploadMediaResponse> uploadMedia(List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("No files provided");
        }
        if (files.size() > MAX_FILES) {
            throw new IllegalArgumentException("Too many files (max " + MAX_FILES + ")");
        }

        // Check hết trước, file nào sai thì từ chối cả lô, khỏi upload dở dang
        files.forEach(this::checkFile);

        List<UploadMediaResponse> results = new ArrayList<>();
        for (MultipartFile file : files) {
            results.add(uploadMedia(file));
        }
        return results;
    }

    /**
     * Xác định type (IMAGE/VIDEO) và validate size, content type, đuôi file.
     * Chưa upload gì cả.
     */
    private com.nhom6.motofix.enums.MediaType checkFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        String contentType = file.getContentType();
        String extension = getFileExtension(file.getOriginalFilename());

        if (contentType != null && contentType.startsWith("image/")) {
            validate(file, MAX_IMAGE_SIZE, "image/");
            getImageMediaType(extension); // ném lỗi nếu đuôi không hỗ trợ
            return com.nhom6.motofix.enums.MediaType.IMAGE;
        }
        if (contentType != null && contentType.startsWith("video/")) {
            validate(file, MAX_VIDEO_SIZE, "video/");
            getVideoMediaType(extension);
            return com.nhom6.motofix.enums.MediaType.VIDEO;
        }
        throw new IllegalArgumentException("Only image or video files are allowed");
    }

    private String upload(MultipartFile file, String bucket, String extension, MediaType mediaType)
            throws IOException {
        String storedName = UUID.randomUUID() + extension;

        String uploadUrl = String.format(
                "%s/storage/v1/object/%s/%s",
                supabaseUrl,
                bucket,
                storedName
        );

        restClient.post()
                .uri(uploadUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + supabaseKey)
                .header("apikey", supabaseKey)
                .contentType(mediaType)
                .body(file.getBytes())
                .retrieve()
                .toBodilessEntity();

        return String.format(
                "%s/storage/v1/object/public/%s/%s",
                supabaseUrl,
                bucket,
                storedName
        );
    }

    private void validate(MultipartFile file, long maxSize, String contentTypePrefix) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException(
                    "File is too large (max " + (maxSize / 1024 / 1024) + " MB)");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith(contentTypePrefix)) {
            throw new IllegalArgumentException("Invalid file content type");
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }

        return fileName.substring(fileName.lastIndexOf(".")).toLowerCase();
    }

    private MediaType getImageMediaType(String extension) {
        return switch (extension) {
            case ".jpg", ".jpeg" -> MediaType.IMAGE_JPEG;
            case ".png" -> MediaType.IMAGE_PNG;
            case ".webp" -> MediaType.parseMediaType("image/webp");
            default -> throw new IllegalArgumentException("Unsupported image type");
        };
    }

    private MediaType getVideoMediaType(String extension) {
        return switch (extension) {
            case ".mp4" -> MediaType.parseMediaType("video/mp4");
            case ".mov" -> MediaType.parseMediaType("video/quicktime");
            case ".webm" -> MediaType.parseMediaType("video/webm");
            default -> throw new IllegalArgumentException("Unsupported video type");
        };
    }
}