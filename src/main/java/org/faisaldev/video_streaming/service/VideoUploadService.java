package org.faisaldev.video_streaming.service;

import io.minio.*;
import io.minio.errors.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

@Service
@Slf4j
public class VideoUploadService {
    private final MinioClient minioClient;
    private final String bucketName;
    private final String tempDirectory;

    public VideoUploadService(@Value("${minio.url}") String url,
                              @Value("${minio.access-key}") String accessKey,
                              @Value("${minio.secret-key}") String secretKey,
                              @Value("${minio.bucket-name}") String bucket,
                              @Value("${video.temp.directory}") String tempDirectory) {
        this.minioClient = MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();
        this.bucketName = bucket;
        this.tempDirectory = tempDirectory;
    }

    @PostConstruct
    public void init() {
        new File(tempDirectory).mkdirs();
    }

    public Mono<String> uploadAndConvert(FilePart filePart) {
        return Mono.fromCallable(() -> {
            String fileName = filePart.filename();
            String localPath = tempDirectory + fileName;
            String outputDir = tempDirectory + "hls_" + System.currentTimeMillis();

            try {
                saveFileLocally(filePart, localPath);

                convertToHLS(localPath, outputDir, fileName);

                uploadHLSFilesToMinIO(outputDir);

                return "HLS video ready for streaming!";
            } finally {
                Files.deleteIfExists(Paths.get(localPath));
                deleteDirectory(new File(outputDir));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private void saveFileLocally(FilePart filePart, String path) throws IOException {
        File file = new File(path);
        file.getParentFile().mkdirs();
        filePart.transferTo(file).block();
    }

    private void convertToHLS(String inputPath, String outputDir, String fileName) throws IOException, InterruptedException {
        new File(outputDir).mkdirs();

        String baseName = fileName.replaceFirst("[.][^.]+$", "");
        String outputPlaylist = outputDir + "/" + baseName + ".m3u8";

        String command = String.join(" ", "ffmpeg", "-i", "\"" + inputPath + "\"",
                "-codec:", "copy",
                "-start_number", "0",
                "-hls_time", "10",
                "-hls_list_size", "0",
                "-f", "hls", "\"" + outputPlaylist + "\"");
        System.out.println("Executing FFmpeg command: " + command);

        ProcessBuilder processBuilder = new ProcessBuilder(
                "ffmpeg", "-i", inputPath,
                "-codec:", "copy",
                "-start_number", "0",
                "-hls_time", "10",
                "-hls_list_size", "0",
                "-f", "hls", outputPlaylist
        );
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("FFmpeg output: " + line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("FFmpeg process failed with exit code: " + exitCode);
        }
    }

    private void uploadHLSFilesToMinIO(String hlsDir) throws IOException, ErrorResponseException, InsufficientDataException, InternalException, InvalidKeyException, InvalidResponseException, NoSuchAlgorithmException, ServerException, XmlParserException {
        File folder = new File(hlsDir);
        for (File file : Objects.requireNonNull(folder.listFiles())) {
            try (InputStream is = new FileInputStream(file)) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object("hls/" + file.getName())
                                .stream(is, file.length(), -1)
                                .contentType("video/MP2T")
                                .build()
                );
            }
        }
    }

    private void deleteDirectory(File directory) throws IOException {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        Files.deleteIfExists(file.toPath());
                    }
                }
            }
            Files.deleteIfExists(directory.toPath());
        }
    }
}