package org.faisaldev.video_streaming.controller;

import org.faisaldev.video_streaming.service.VideoUploadService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/videos")
public class VideoController {
    private final VideoUploadService videoUploadService;

    public VideoController(VideoUploadService videoUploadService) {
        this.videoUploadService = videoUploadService;
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public Mono<ResponseEntity<String>> uploadVideo(@RequestPart("file") FilePart file) {
        return videoUploadService.uploadAndConvert(file)
                .map(response -> ResponseEntity.ok("Video uploaded and processed: " + response));
    }
}