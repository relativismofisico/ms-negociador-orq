package com.negociadororq.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

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

    // puedes mapear las otras FK si las usas, o dejarlas como Long por ahora
    @Column(name = "rol_user_IDE")
    private Long rolUserIde;

    @Column(name = "company_ide")
    private Long companyIde;

    @Column(name = "state_user_id")
    private Long stateUserId;

    @Column(name = "person_IDE")
    private Long personIde;
    // Relación OneToMany con UserQuota
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserQuota> quotas;
}
