package com.example.demo.repository;

import com.example.demo.model.Clergy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PublicClergyRepository extends JpaRepository<Clergy, String> {

    @Query(value = """
        SELECT * FROM clergy
        WHERE role = 'POPE'
        ORDER BY papacy_start_date ASC NULLS LAST
        """, nativeQuery = true)
    List<Clergy> findPopesAndRoot();

    @Query(value = """
        SELECT * FROM clergy
        WHERE name ILIKE CONCAT('%', :searchTerm, '%')
        LIMIT 10
        """, nativeQuery = true)
    List<Clergy> searchByNameLimit10(@Param("searchTerm") String searchTerm);

    @Query(value = """
        WITH RECURSIVE lineage AS (
            SELECT hash, parent_hash, name, role, start_date, papacy_start_date, created_at, 1 AS depth
            FROM clergy
            WHERE hash = :startHash

            UNION ALL

            SELECT c.hash, c.parent_hash, c.name, c.role, c.start_date, c.papacy_start_date, c.created_at, l.depth + 1
            FROM clergy c
            INNER JOIN lineage l ON l.parent_hash = c.hash
            WHERE l.depth < 150
              AND l.parent_hash IS NOT NULL
              AND l.parent_hash NOT IN ('00x00x00', '00X00X00')
        )
        SELECT hash, parent_hash, name, role, start_date, papacy_start_date, created_at
        FROM lineage
        ORDER BY depth ASC
        """, nativeQuery = true)
    List<Clergy> traceLineageToRoot(@Param("startHash") String startHash);

    @Query(value = """
        SELECT * FROM clergy
        WHERE hash = :hash
        LIMIT 1
        """, nativeQuery = true)
    List<Clergy> findByHash(@Param("hash") String hash);
}