package com.negociadororq.infrastructure.auditoria;

import com.factoring.commons.auditoria.AuditoriaActor;
import com.factoring.commons.mdc.MdcConstants;
import com.factoring.commons.util.IpUtils;
import com.negociadororq.application.port.output.ContextoAuditoriaOutputPort;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@link ContextoAuditoriaOutputPort} sobre la request en curso: actor desde la autenticación validada (JWT) y los
 * atributos que deja el converter del JWT; IP de la conexión; requestId del MDC.
 */
@Component
public class ContextoAuditoriaAdapter implements ContextoAuditoriaOutputPort {

    @Override
    public AuditoriaActor actorActual() {
        final HttpServletRequest request = requestActual();
        if (request == null) {
            return AuditoriaActor.anonimo();
        }
        return AuditoriaActor.desdeRequestActual(request);
    }

    @Override
    public String ipActual() {
        final HttpServletRequest request = requestActual();
        if (request == null) {
            return null;
        }
        return IpUtils.obtenerIp(request);
    }

    @Override
    public String requestIdActual() {
        return MDC.get(MdcConstants.REQUEST_ID);
    }

    private static HttpServletRequest requestActual() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
            return atributos.getRequest();
        }
        return null;
    }
}
