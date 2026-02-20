package com.example.demo.controller;

import com.example.demo.dto.PublicStatsDTO;
import com.example.demo.repository.ClergyRepository;
import com.example.demo.repository.DailyVisitRepository;
import com.example.demo.repository.SiteAnalyticsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/stats")
@CrossOrigin(origins = "*")
public class PublicStatsController {

    @Autowired
    private ClergyRepository clergyRepository;

    @Autowired
    private SiteAnalyticsRepository siteAnalyticsRepository;

    @Autowired
    private DailyVisitRepository dailyVisitRepository;

    @GetMapping
    public ResponseEntity<PublicStatsDTO> getPublicStats() {
        PublicStatsDTO stats = new PublicStatsDTO();

        // 1. Contagens básicas
        long bishops = clergyRepository.countBishops();
        long popes   = clergyRepository.countPopes();
        stats.setTotalBishops(bishops);
        stats.setTotalPopes(popes);
        stats.setTotalClergy(bishops + popes);

        // 2. Estatísticas de acesso
        stats.setTotalViews(siteAnalyticsRepository.sumAllViews());
        Long today = dailyVisitRepository.findTodayViews();
        stats.setTodayViews(today != null ? today : 0L);

        // 3. Os 6 Papas Recentes (Otimizado via Banco de Dados)
        stats.setRecentPopes(clergyRepository.findTop6RecentPopes());

        return ResponseEntity.ok(stats);
    }
}