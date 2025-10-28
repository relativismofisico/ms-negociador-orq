package com.negociadororq.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.*;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;

@Entity
@Table(name = "user_quota")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quota", nullable = false)
    private BigDecimal quota;

    @Column(name = "current_quota", nullable = false)
    private BigDecimal currentQuota;

    // Relación ManyToOne con User
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "ide_user", nullable = false)
    private User user;
}
