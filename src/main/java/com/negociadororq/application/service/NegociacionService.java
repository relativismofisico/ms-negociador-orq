package com.negociadororq.application.service;

import com.negociadororq.application.controller.dto.AprobacionFacturaRequestDto;
import com.negociadororq.application.controller.dto.NegociacionRequestDto;
import com.negociadororq.application.controller.dto.NegociacionResponseDto;
import com.negociadororq.domain.model.Negociacion;
import java.util.List;

public interface NegociacionService {
    NegociacionResponseDto crearNegociacion(NegociacionRequestDto negociacion);
    void aprobarFactura(String idNegociacion, String rutEmpresa, List<AprobacionFacturaRequestDto> facturasDto);
}
