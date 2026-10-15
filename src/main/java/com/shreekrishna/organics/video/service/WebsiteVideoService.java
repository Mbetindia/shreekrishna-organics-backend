package com.shreekrishna.organics.video.service;

import com.shreekrishna.organics.video.dto.VideoRequest;
import com.shreekrishna.organics.video.dto.VideoResponse;
import com.shreekrishna.organics.video.entity.WebsiteVideo;
import com.shreekrishna.organics.video.repository.WebsiteVideoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class WebsiteVideoService {
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{11}");
    private final WebsiteVideoRepository repository;

    public WebsiteVideoService(WebsiteVideoRepository repository) {
        this.repository = repository;
    }

    public List<VideoResponse> getPublicVideos() {
        return repository.findByActiveTrueOrderByDisplayOrderAscIdDesc()
                .stream().map(this::toResponse).toList();
    }

    public List<VideoResponse> getAdminVideos() {
        return repository.findAllByOrderByDisplayOrderAscIdDesc()
                .stream().map(this::toResponse).toList();
    }

    public VideoResponse create(VideoRequest request) {
        WebsiteVideo video = new WebsiteVideo();
        applyRequest(video, request);
        return toResponse(repository.save(video));
    }

    public VideoResponse update(Long id, VideoRequest request) {
        WebsiteVideo video = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Video not found"));
        applyRequest(video, request);
        return toResponse(repository.save(video));
    }

    public void delete(Long id) {
        WebsiteVideo video = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Video not found"));
        repository.delete(video);
    }

    private void applyRequest(WebsiteVideo video, VideoRequest request) {
        String url = request.youtubeUrl().trim();
        video.setTitle(request.title().trim());
        video.setYoutubeUrl(url);
        video.setVideoId(extractVideoId(url));
        video.setDescription(request.description());
        video.setActive(request.active() == null ? true : request.active());
        video.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
    }

    private String extractVideoId(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getPort() != -1) {
                throw new IllegalArgumentException();
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath();
            String id = null;

            if (host.equals("youtu.be") || host.equals("www.youtu.be")) {
                id = firstPathSegment(path);
            } else if (host.equals("youtube.com")
                    || host.equals("www.youtube.com")
                    || host.equals("m.youtube.com")
                    || host.equals("youtube-nocookie.com")
                    || host.equals("www.youtube-nocookie.com")) {
                if ("/watch".equals(path)) {
                    String query = uri.getRawQuery();
                    if (query != null) {
                        for (String part : query.split("&")) {
                            String[] pair = part.split("=", 2);
                            if (pair.length == 2 && pair[0].equals("v")) {
                                id = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                                break;
                            }
                        }
                    }
                } else if (path != null && (path.startsWith("/shorts/")
                        || path.startsWith("/embed/")
                        || path.startsWith("/live/"))) {
                    String[] parts = path.split("/");
                    if (parts.length == 3) id = parts[2];
                }
            }

            if (id == null || !ID_PATTERN.matcher(id).matches()) {
                throw new IllegalArgumentException();
            }
            return id;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Please provide a valid HTTPS YouTube video URL");
        }
    }

    private String firstPathSegment(String path) {
        if (path == null || path.length() < 2) return null;
        String[] segments = path.substring(1).split("/");
        return segments.length == 1 ? segments[0] : null;
    }

    private VideoResponse toResponse(WebsiteVideo video) {
        return new VideoResponse(
                video.getId(),
                video.getTitle(),
                video.getYoutubeUrl(),
                video.getVideoId(),
                "https://img.youtube.com/vi/" + video.getVideoId() + "/hqdefault.jpg",
                video.getDescription(),
                video.getActive(),
                video.getDisplayOrder()
        );
    }
}
