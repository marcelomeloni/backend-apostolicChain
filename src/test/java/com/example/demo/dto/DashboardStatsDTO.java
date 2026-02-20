// src/main/java/com/example/demo/dto/DashboardStatsDTO.java
package com.example.demo.dto;

import lombok.Data;

@Data
public class DashboardStatsDTO {
    private boolean isInitialized;
    private long totalBishops;
    private long totalPopes;
    private long totalViews;
}