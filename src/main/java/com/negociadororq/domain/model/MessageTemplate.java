package com.negociadororq.domain.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "messages_templates")
@Data
public class MessageTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "templateName")
    private String templateName;

    @Column(columnDefinition = "TEXT")
    private String content;

    private boolean active;
}

