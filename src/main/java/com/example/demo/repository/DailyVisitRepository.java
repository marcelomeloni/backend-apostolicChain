package com.example.demo.repository;

import com.example.demo.model.DailyVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface DailyVisitRepository extends JpaRepository<DailyVisit, Long> {

    @Modifying
    @Query(value = """
        INSERT INTO daily_visits (visit_date, total_views, unique_visitors)
        VALUES (CURRENT_DATE, 1, 1)
        ON CONFLICT (visit_date)
        DO UPDATE SET
            total_views = daily_visits.total_views + 1
        """, nativeQuery = true)
    void incrementToday();

    @Query(value = "SELECT COALESCE(total_views, 0) FROM daily_visits WHERE visit_date = CURRENT_DATE", nativeQuery = true)
    Long findTodayViews();
}