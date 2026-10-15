package com.shreekrishna.organics.video.repository;

import com.shreekrishna.organics.video.entity.WebsiteVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WebsiteVideoRepository extends JpaRepository<WebsiteVideo, Long> {
    List<WebsiteVideo> findAllByOrderByDisplayOrderAscIdDesc();
    List<WebsiteVideo> findByActiveTrueOrderByDisplayOrderAscIdDesc();
}
