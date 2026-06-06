package com.negociadororq.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    private Long ideUser;
    private String username;
    private String password;
    private String createTime;
    private Long rolUserIde;
    private Long companyIde;
    private Long stateUserId;
    private Long personIde;
}
