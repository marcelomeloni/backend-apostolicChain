package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "clergy")
public class Clergy {

    @Id
    @Column(length = 66, nullable = false)
    private String hash;

    @Column(name = "parent_hash", length = 66)
    private String parentHash;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
@Column(name = "role", nullable = false)
private Role role;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "papacy_start_date")
    private LocalDate papacyStartDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum Role {
        BISHOP, POPE, ROOT
    }
}