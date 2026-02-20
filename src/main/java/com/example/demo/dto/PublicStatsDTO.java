package com.example.demo.dto;

import com.example.demo.model.Clergy;
import lombok.Data;
import java.util.List; 

@Data
public class PublicStatsDTO {
    private long totalBishops;
    private long totalPopes;
    private long totalClergy;
    private long totalViews;
    private long todayViews;
    private List<Clergy> recentPopes; 
}