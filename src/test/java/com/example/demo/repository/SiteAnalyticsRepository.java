package com.example.demo.repository;

import com.example.demo.model.SiteAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SiteAnalyticsRepository extends JpaRepository<SiteAnalytics, Long> {

    Optional<SiteAnalytics> findByEntityHash(String entityHash);

    @Modifying
    @Query(value = """
        INSERT INTO site_analytics (entity_hash, views_count, last_viewed_at)
        VALUES (:hash, 1, NOW())
        ON CONFLICT (entity_hash)
        DO UPDATE SET
            views_count = site_analytics.views_count + 1,
            last_viewed_at = NOW()
        """, nativeQuery = true)
    void upsertView(@Param("hash") String hash);

    @Query("SELECT COALESCE(SUM(s.viewsCount), 0L) FROM SiteAnalytics s")
    Long sumAllViews();

}
