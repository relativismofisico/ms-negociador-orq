package com.negociadororq.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "person")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "IDE")
    private Long ide;

    @Column(name = "firts_name")
    private String firtsName;

    @Column(name = "second_name")
    private String secondName;

    @Column(name = "firts_lastname")
    private String firtsLastname;

    @Column(name = "second_lastname")
    private String secondLastname;

    @Column(name = "document_number")
    private String documentNumber;

    @Column(name = "document_type_ide")
    private Long documentTypeIde;
}
