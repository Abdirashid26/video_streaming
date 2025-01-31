package org.faisaldev.video_streaming.service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;

import java.util.List;

public class HttpRangeResource extends ResponseEntity<Flux<ResourceRegion>> {
    public HttpRangeResource(Resource resource, List<ResourceRegion> regions) {
        super(Flux.fromIterable(regions), createHeaders(resource, regions), HttpStatus.PARTIAL_CONTENT);
    }

    private static MultiValueMap<String, String> createHeaders(Resource resource, List<ResourceRegion> regions) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentLength(regions.stream().mapToLong(ResourceRegion::getCount).sum());
        return headers;
    }
}