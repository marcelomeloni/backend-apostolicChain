package com.example.demo.repository;

import com.example.demo.model.Clergy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ClergyRepository extends JpaRepository<Clergy, String> {

    @Query(value = "SELECT * FROM clergy WHERE CAST(role AS text) = :role", nativeQuery = true)
    List<Clergy> findByRole(@Param("role") String role);

    @Query(
        value = "SELECT * FROM clergy WHERE CAST(role AS text) = :role",
        countQuery = "SELECT count(*) FROM clergy WHERE CAST(role AS text) = :role",
        nativeQuery = true
    )
    Page<Clergy> findByRole(@Param("role") String role, Pageable pageable);

    // 👇 SOLUÇÃO INFALÍVEL: SQL direto e sem parâmetros de Enum!
    @Query(value = "SELECT count(*) FROM clergy WHERE role = 'BISHOP'", nativeQuery = true)
    long countBishops();

    @Query(value = "SELECT count(*) FROM clergy WHERE role = 'POPE'", nativeQuery = true)
    long countPopes();

    @Query(value = "SELECT * FROM clergy WHERE role = 'POPE' AND papacy_start_date IS NOT NULL ORDER BY papacy_start_date DESC LIMIT 6", nativeQuery = true)
    List<Clergy> findTop6RecentPopes();

}