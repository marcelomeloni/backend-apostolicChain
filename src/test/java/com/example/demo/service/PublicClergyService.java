// PublicClergyService.java
package com.example.demo.service;

import com.example.demo.model.Clergy;
import com.example.demo.repository.PublicClergyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PublicClergyService {

    @Autowired
    private PublicClergyRepository publicClergyRepository;

    @Autowired
    private AnalyticsService analyticsService;

    public List<Clergy> getInitialChain() {
        return publicClergyRepository.findPopesAndRoot();
    }

    public List<Clergy> searchByName(String term) {
        if (term == null || term.trim().isEmpty()) return List.of();
        List<Clergy> results = publicClergyRepository.searchByNameLimit10(term.trim());
        results.forEach(c -> analyticsService.recordView(c.getHash()));
        return results;
    }

    public List<Clergy> getTracePath(String hash) {
        List<Clergy> lineage = publicClergyRepository.traceLineageToRoot(hash);
        if (!lineage.isEmpty()) analyticsService.recordView(hash);
        return lineage;
    }

    public List<Clergy> getByHash(String hash) {
        List<Clergy> result = publicClergyRepository.findByHash(hash);
        if (!result.isEmpty()) analyticsService.recordView(hash);
        return result;
    }

}
