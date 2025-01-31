package org.faisaldev.video_streaming.controller;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.faisaldev.video_streaming.service.HttpRangeResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@RestController
@RequestMapping("/stream")
public class VideoStreamController {
    private final MinioClient minioClient;
    private final String bucketName = "myvideos";

    public VideoStreamController(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @GetMapping(value = "/{fileName}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ResponseEntity<?>> streamVideo(@PathVariable String fileName, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            try {
                Path tempFile = Files.createTempFile("minio-", fileName);
                try (InputStream inputStream = minioClient.getObject(
                        GetObjectArgs.builder()
                                .bucket(bucketName)
                                .object("hls/" + fileName)
                                .build()
                )) {
                    Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
                }

                Resource resource = new FileSystemResource(tempFile);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(fileName.endsWith(".m3u8") ? MediaType.APPLICATION_OCTET_STREAM : MediaType.valueOf("video/MP2T"));
                headers.setContentLength(resource.contentLength());

                if (exchange.getRequest().getHeaders().getRange().isEmpty()) {
                    // No range request, return the full resource
                    return ResponseEntity.ok()
                            .headers(headers)
                            .body(resource);
                } else {
                    // Handle range request
                    List<HttpRange> ranges = exchange.getRequest().getHeaders().getRange();
                    List<ResourceRegion> regions = HttpRange.toResourceRegions(ranges, resource);

                    return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                            .headers(headers)
                            .body(new HttpRangeResource(resource, regions));
                }
            } catch (ErrorResponseException e) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: " + fileName, e);
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error streaming file: " + fileName, e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}