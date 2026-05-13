package com.negociadororq.application.service;

import com.negociadororq.domain.model.MessageTemplate;
import com.negociadororq.infrastructure.persistence.repository.MessageTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;


@Service
@RequiredArgsConstructor
public class TemplateRenderServiceImpl implements TemplateRenderService {


    @Override
    public String renderEndosoFormalTemplate(
            String ciudadNegociacion,
            String nombrePagador,
            String direccionPagador,
            String ciudadPagador,
            String nombreProveedor,
            String nitProveedor,
            String numeroFactura,
            String tipoOperacion,
            String nombreFondeador,
            String rutFondeador,
            LocalDateTime fechaOperacion
    ) {

        return """
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                <meta charset="UTF-8"/>
                <title>Endoso</title>
                </head>
                <body style="font-family: Arial; font-size: 14px;">
                
                <p>%s, %s</p>
                
                <p><strong>Señores:</strong><br/>
                %s<br/>
                %s<br/>
                %s</p>
                
                <p><strong>Referencia:</strong> Endoso en propiedad de factura</p>
                
                <p>
                Yo, actuando en calidad de autorizado por la empresa %s con NIT %s,
                como emisor de la factura %s, manifiesto expresa e irrevocablemente
                que he ENDOSADO EN PROPIEDAD Y %s a favor de %s.
                </p>
                
                <p>
                En consecuencia, al vencimiento debe pagarse directamente
                el importe a %s - %s.
                </p>
                
                <p>
                Suscribo el presente documento en la ciudad de %s,
                a los %s.
                </p>
                
                <br/><br/>
                
                _____________________________<br/>
                Firma Autorizada<br/>
                %s<br/>
                NIT: %s
                
                </body>
                </html>
                """.formatted(
                ciudadNegociacion,
                fechaOperacion,
                nombrePagador,
                direccionPagador,
                ciudadPagador,
                nombreProveedor,
                nitProveedor,
                numeroFactura,
                tipoOperacion,
                nombreFondeador,
                nombreFondeador,
                rutFondeador,
                ciudadNegociacion,
                fechaOperacion,
                nombreProveedor,
                nitProveedor
        );
    }
}
