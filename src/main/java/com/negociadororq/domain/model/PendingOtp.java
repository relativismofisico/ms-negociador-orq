package com.negociadororq.domain.model;

import com.negociadororq.application.controller.dto.AprobacionRequestDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "pending_otp")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingOtp {

    @Id
    private String id;

    private String idNegociacion;
    private String rutEmpresa;

    private AprobacionRequestDto request;
}
