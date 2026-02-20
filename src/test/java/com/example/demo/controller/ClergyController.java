package com.example.demo.controller;

import com.example.demo.dto.ClergyDTO;
import com.example.demo.dto.DashboardStatsDTO;
import com.example.demo.dto.GenesisDTO;
import com.example.demo.model.Clergy;
import com.example.demo.service.ClergyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clergy")

public class ClergyController {

    @Autowired
    private ClergyService clergyService;

    @GetMapping("/popes")
    public ResponseEntity<Page<Clergy>> getPopes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
            clergyService.findByRole("POPE", PageRequest.of(page, size, Sort.by("papacy_start_date")))
        );
    }

    @GetMapping("/bishops")
    public ResponseEntity<Page<Clergy>> getBishops(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(
            clergyService.findByRole("BISHOP", PageRequest.of(page, size, Sort.by("start_date")))
        );
    }

    @PostMapping
    public ResponseEntity<?> registerClergy(@RequestBody ClergyDTO request) {
        try {
            Clergy savedClergy = clergyService.createClergy(request);
            return ResponseEntity.ok(savedClergy);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/genesis")
    public ResponseEntity<?> initializeGenesis(@RequestBody GenesisDTO request) {
        try {
            clergyService.initializeGenesis(request);
            return ResponseEntity.ok("{\"success\": true, \"message\": \"Gênesis Inicializado: Jesus -> Pedro\"}");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\": false, \"message\": \"" + e.getMessage() + "\"}");
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsDTO> getStats() {
        return ResponseEntity.ok(clergyService.getDashboardStats());
    }
}

