// PublicClergyController.java
package com.example.demo.controller;

import com.example.demo.model.Clergy;
import com.example.demo.service.PublicClergyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public/clergy")
public class PublicClergyController {

    @Autowired
    private PublicClergyService publicClergyService;

    @GetMapping("/main-chain")
    public ResponseEntity<List<Clergy>> getMainChain() {
        return ResponseEntity.ok(publicClergyService.getInitialChain());
    }

    @GetMapping("/search")
    public ResponseEntity<List<Clergy>> search(@RequestParam String name) {
        return ResponseEntity.ok(publicClergyService.searchByName(name));
    }


    @GetMapping("/trace/{hash}")
    public ResponseEntity<List<Clergy>> traceLineage(@PathVariable String hash) {
        List<Clergy> lineage = publicClergyService.getTracePath(hash);
        if (lineage.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(lineage);
    }


    @GetMapping("/node/{hash}")
    public ResponseEntity<List<Clergy>> getNode(@PathVariable String hash) {
        List<Clergy> result = publicClergyService.getByHash(hash);
        if (result.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(result);
    }
    

}
