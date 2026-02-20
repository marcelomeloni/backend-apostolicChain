// DailyVisit.java
package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "daily_visits")
public class DailyVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "visit_date")
    private LocalDate visitDate;

    @Column(name = "total_views")
    private Long totalViews = 0L;

    @Column(name = "unique_visitors")
    private Long uniqueVisitors = 0L;

    // getters/setters
    public Long getId() { return id; }
    public LocalDate getVisitDate() { return visitDate; }
    public void setVisitDate(LocalDate visitDate) { this.visitDate = visitDate; }
    public Long getTotalViews() { return totalViews; }
    public void setTotalViews(Long totalViews) { this.totalViews = totalViews; }
    public Long getUniqueVisitors() { return uniqueVisitors; }
    public void setUniqueVisitors(Long uniqueVisitors) { this.uniqueVisitors = uniqueVisitors; }
}