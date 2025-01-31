package org.faisaldev.video_streaming.config;


import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public  class GlobalConfigs{

    @Bean
    public MinioClient VideoUploadService(@Value("${minio.url}") String url,
                                          @Value("${minio.access-key}") String accessKey,
                                          @Value("${minio.secret-key}") String secretKey,
                                          @Value("${minio.bucket-name}") String bucket) {
        return MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();
    }
}