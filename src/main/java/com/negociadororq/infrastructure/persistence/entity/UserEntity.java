package com.negociadororq.infrastructure.persistence.entity;

import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persistencia JPA de un {@link com.negociadororq.domain.model.User}, con sus cupos
 * individuales asociados ({@link UserQuotaEntity}).
 */
@Entity
@Table(name = "user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ide_user")
    private Long ideUser;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "create_time")
    private String createTime;

    @Column(name = "rol_user_IDE")
    private Long rolUserIde;

    @Column(name = "company_ide")
    private Long companyIde;

    @Column(name = "state_user_id")
    private Long stateUserId;

    @Column(name = "person_IDE")
    private Long personIde;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserQuotaEntity> quotas;
}
