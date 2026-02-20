// AnalyticsService.java
package com.example.demo.service;

import com.example.demo.repository.DailyVisitRepository;
import com.example.demo.repository.SiteAnalyticsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {

    @Autowired
    private SiteAnalyticsRepository siteAnalyticsRepository;

    @Autowired
    private DailyVisitRepository dailyVisitRepository;

    // ✅ Async — não bloqueia a resposta da API
    @Async
    @Transactional
    public void recordView(String entityHash) {
        try {
            siteAnalyticsRepository.upsertView(entityHash);
            dailyVisitRepository.incrementToday();
        } catch (Exception e) {
            System.err.println("Analytics error: " + e.getMessage());
        }
    }
}