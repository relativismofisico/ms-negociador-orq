package com.negociadororq.application.service.notificacion.impl;


import com.negociadororq.application.service.notificacion.CuerpoNotificacionBuilder;
import com.negociadororq.domain.model.User;
import org.springframework.stereotype.Component;

@Component
public class PersonaCuerpoBuilder implements CuerpoNotificacionBuilder<User> {

    @Override
    public Class<User> getTipo() {
        return User.class;
    }

    @Override
    public String construir(User p) {
        return "Nueva persona registrada.\n\n" +
                "Nombre: " + p.getUsername() + "\n" +
                "Identificación: " + p.getIdeUser() + "\n";
    }
}
