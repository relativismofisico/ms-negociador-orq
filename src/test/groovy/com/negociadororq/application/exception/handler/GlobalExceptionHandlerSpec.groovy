package com.negociadororq.application.exception.handler

import com.negociadororq.application.exception.dto.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import spock.lang.Specification

class GlobalExceptionHandlerSpec extends Specification {

    GlobalExceptionHandler handler = new GlobalExceptionHandler()
    HttpServletRequest request = Mock() {
        getRequestURI() >> '/negociar/factura'
    }

    private static MethodArgumentNotValidException validationException(List<FieldError> fieldErrors) {
        def bindingResult = new BeanPropertyBindingResult(new Object(), 'target')
        fieldErrors.each { bindingResult.addError(it) }
        def method = String.class.getMethod('valueOf', Object.class)
        def parameter = new MethodParameter(method, 0)
        new MethodArgumentNotValidException(parameter, bindingResult)
    }

    def "handleValidationException incluye fieldErrors estructurado ademas de detalles concatenado"() {
        given:
        def ex = validationException([
                new FieldError('target', 'userName', 'no puede ser nulo'),
                new FieldError('target', 'email', 'formato invalido'),
        ])

        when:
        def response = handler.handleValidationException(ex, request)

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
        ErrorResponse body = response.body
        body.detalles == ['userName: no puede ser nulo', 'email: formato invalido']
        body.fieldErrors.size() == 2
        body.fieldErrors[0].field() == 'userName'
        body.fieldErrors[0].message() == 'no puede ser nulo'
        body.fieldErrors[1].field() == 'email'
        body.fieldErrors[1].message() == 'formato invalido'
    }

    def "handleValidationException incluye el valor rechazado como texto cuando esta disponible, y null cuando no aplica"() {
        given:
        def ex = validationException([
                new FieldError('target', 'edad', -1, false, null, null, 'debe ser mayor a 0'),
                new FieldError('target', 'userName', 'no puede ser nulo'),
        ])

        when:
        def response = handler.handleValidationException(ex, request)

        then:
        ErrorResponse body = response.body
        body.fieldErrors[0].rejectedValue() == '-1'
        body.fieldErrors[1].rejectedValue() == null
    }
}
