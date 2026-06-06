package com.negociadororq.domain.model;

import lombok.Data;

@Data
public class MessageTemplate {

    private Long id;
    private String templateName;
    private String content;
    private boolean active;
}
