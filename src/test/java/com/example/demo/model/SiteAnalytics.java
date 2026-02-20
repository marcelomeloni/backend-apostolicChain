// SiteAnalytics.java
package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "site_analytics")
public class SiteAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_hash")
    private String entityHash;

    @Column(name = "views_count")
    private Long viewsCount = 0L;

    @Column(name = "last_viewed_at")
    private LocalDateTime lastViewedAt;

    // getters/setters
    public Long getId() { return id; }
    public String getEntityHash() { return entityHash; }
    public void setEntityHash(String entityHash) { this.entityHash = entityHash; }
    public Long getViewsCount() { return viewsCount; }
    public void setViewsCount(Long viewsCount) { this.viewsCount = viewsCount; }
    public LocalDateTime getLastViewedAt() { return lastViewedAt; }
    public void setLastViewedAt(LocalDateTime lastViewedAt) { this.lastViewedAt = lastViewedAt; }
}