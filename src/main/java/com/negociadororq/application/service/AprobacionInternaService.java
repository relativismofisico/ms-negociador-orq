package com.negociadororq.application.service;

import com.negociadororq.application.controller.dto.AprobacionResponseDto;
import com.negociadororq.domain.model.PendingOtp;

public interface AprobacionInternaService {
    AprobacionResponseDto aprobarInterno(PendingOtp pending);
}
