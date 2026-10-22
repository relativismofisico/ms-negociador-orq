package com.negociadororq.domain.util

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.negociadororq.application.dto.ConfirmacionOtpRequestDto
import com.negociadororq.domain.event.OtpValidateRequestEvent
import com.negociadororq.domain.event.OtpValidatedEvent
import com.negociadororq.domain.model.Endoso
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.PendingOtp
import com.negociadororq.infrastructure.persistence.entity.EndosoDocument
import com.negociadororq.infrastructure.persistence.entity.FacturaNegociadaEmbedded
import com.negociadororq.infrastructure.persistence.entity.PendingOtpDocument
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.Yaml
import spock.lang.Specification

/**
 * Ningún toString() de un DTO, entidad, evento o propiedades con credenciales (contraseñas, secretos, tokens, códigos
 * OTP) expone su valor: sale como ***. Y los loggers de Spring MVC que imprimen el cuerpo completo de peticiones y
 * respuestas quedan en INFO en el application.yml base.
 */
class CredencialesFueraDeLogsSpec extends Specification {

    /** Valor por defecto de un tipo (los primitivos no admiten null). */
    private static Object porDefecto(Class t) {
        if (t == boolean) return false
        if (t.primitive) return 0.asType(t)
        null
    }

    /** Instancia con el constructor de menos parámetros y valores por defecto. */
    private static Object instanciar(Class tipo) {
        def ctor = null
        for (c in tipo.declaredConstructors) {
            if (ctor == null || c.parameterCount < ctor.parameterCount) {
                ctor = c
            }
        }
        ctor.accessible = true
        Object[] argumentos = new Object[ctor.parameterCount]
        for (int i = 0; i < argumentos.length; i++) {
            argumentos[i] = porDefecto(ctor.parameterTypes[i])
        }
        ctor.newInstance(argumentos)
    }

    /** Instancia con un valor de prueba en el campo sensible. */
    private static Object conValor(Class tipo, String campo, String valor) {
        def instancia = instanciar(tipo)
        def f = tipo.getDeclaredField(campo)
        f.accessible = true
        f.set(instancia, valor)
        instancia
    }

    /** Valor de prueba único por campo, generado en el test (nunca un secreto real). */
    private static String valorDePrueba(String campo) {
        "valor-${campo}-${UUID.randomUUID()}"
    }

    def "el toString() de #tipo.simpleName enmascara #campo"() {
        given:
        String valor = valorDePrueba(campo)

        when:
        String texto = conValor(tipo, campo, valor).toString()

        then:
        !texto.contains(valor)
        texto.contains("${campo}=***")

        where:
        tipo                      | campo
        ConfirmacionOtpRequestDto | 'otp'
        OtpValidateRequestEvent   | 'otp'
        OtpValidatedEvent         | 'otp'
        Endoso                    | 'otp'
        PendingOtp                | 'otp'
        FacturaNegociada          | 'otpEndoso'
        EndosoDocument            | 'otp'
        FacturaNegociadaEmbedded  | 'otpEndoso'
        PendingOtpDocument        | 'otp'
    }

    def "sin valor, el toString() de #tipo.simpleName muestra #campo=null"() {
        expect:
        instanciar(tipo).toString().contains("${campo}=null")

        where:
        tipo                      | campo
        ConfirmacionOtpRequestDto | 'otp'
        OtpValidateRequestEvent   | 'otp'
        OtpValidatedEvent         | 'otp'
        Endoso                    | 'otp'
        PendingOtp                | 'otp'
        FacturaNegociada          | 'otpEndoso'
        EndosoDocument            | 'otp'
        FacturaNegociadaEmbedded  | 'otpEndoso'
        PendingOtpDocument        | 'otp'
    }

    def "un log en DEBUG de ConfirmacionOtpRequestDto no contiene otp"() {
        given: "un appender en memoria sobre un logger en DEBUG"
        Logger logger = (Logger) LoggerFactory.getLogger('test.credenciales')
        def appender = new ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        logger.level = Level.DEBUG
        String valor = valorDePrueba('otp')

        when: "se registra el objeto completo, como hace Spring MVC en DEBUG con el cuerpo"
        logger.debug('Read "application/json" to [{}]', conValor(ConfirmacionOtpRequestDto, 'otp', valor))

        then:
        appender.list.size() == 1
        !appender.list[0].formattedMessage.contains(valor)
        appender.list[0].formattedMessage.contains('otp=***')

        cleanup:
        logger.detachAppender(appender)
    }

    def "application.yml fija en INFO los loggers de Spring MVC que imprimen el cuerpo de peticiones y respuestas"() {
        given:
        def yml = new Yaml().loadAll(new File('src/main/resources/application.yml').text).find { it?.logging }
        Map niveles = yml.logging.level

        expect:
        niveles['org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor'] == 'INFO'
        niveles['org.springframework.web.servlet.mvc.method.annotation.HttpEntityMethodProcessor'] == 'INFO'
    }
}
