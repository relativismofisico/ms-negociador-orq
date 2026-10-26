package com.negociadororq.infrastructure.persistence.adapter

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.negociadororq.infrastructure.persistence.entity.FondeadorQuotaEntity
import com.negociadororq.infrastructure.persistence.repository.FondeadorQuotaRepository
import org.slf4j.LoggerFactory
import spock.lang.Specification
import spock.lang.Unroll

/**
 * La tabla funder admite varias filas por par fondeador-proveedor / fondeador-pagador. El adaptador
 * elige la asignación efectiva (APROBADO, la más reciente) sin lanzar NonUniqueResultException, que
 * antes convertía la negociación en un error interno.
 */
class FondeadorQuotaAdapterSpec extends Specification {

    FondeadorQuotaRepository repository = Mock()
    FondeadorQuotaAdapter adapter = new FondeadorQuotaAdapter(repository)

    private static FondeadorQuotaEntity fila(Long id, String estado, Boolean activo) {
        FondeadorQuotaEntity.builder().id(id).funderId(10L).supplierId(20L).payerId(0L)
                .estado(estado).activo(activo).aprobadoPorFondeador(true).build()
    }

    @Unroll
    def "proveedor (#variante): #caso -> id #esperado"() {
        given: "las filas del par, como las devuelve el repositorio (id descendente)"
        repository.findByFunderIdAndSupplierIdOrderByIdDesc(10L, 20L) >> filas

        when:
        def r = variante == 'activo'
                ? adapter.findByFunderIdAndSupplierIdActivo(10L, 20L)
                : adapter.findByFunderIdAndSupplierId(10L, 20L)

        then:
        r.map { it.id }.orElse(null) == esperado

        where:
        variante | caso                                       | filas                                                          || esperado
        'activo' | 'APROBADO + RECHAZADO más reciente'         | [fila(2L, 'RECHAZADO', true), fila(1L, 'APROBADO', true)]      || 1L
        'activo' | 'APROBADO + PENDIENTE más reciente'         | [fila(3L, 'PENDIENTE', true), fila(1L, 'APROBADO', true)]      || 1L
        'activo' | 'ninguna APROBADO: la más reciente'         | [fila(3L, 'PENDIENTE', true), fila(2L, 'RECHAZADO', true)]     || 3L
        'activo' | 'APROBADO desactivada: no cuenta'           | [fila(1L, 'APROBADO', false)]                                  || null
        'activo' | 'APROBADO desactivada + PENDIENTE activa'   | [fila(2L, 'PENDIENTE', true), fila(1L, 'APROBADO', false)]     || 2L
        'activo' | 'sin filas'                                 | []                                                             || null
        'todas'  | 'APROBADO + RECHAZADO más reciente'         | [fila(2L, 'RECHAZADO', true), fila(1L, 'APROBADO', true)]      || 1L
        'todas'  | 'APROBADO + PENDIENTE más reciente'         | [fila(3L, 'PENDIENTE', true), fila(1L, 'APROBADO', true)]      || 1L
        'todas'  | 'ninguna APROBADO: la más reciente'         | [fila(3L, 'PENDIENTE', true), fila(2L, 'RECHAZADO', true)]     || 3L
        'todas'  | 'APROBADO desactivada: se encuentra igual'  | [fila(1L, 'APROBADO', false)]                                  || 1L
        'todas'  | 'APROBADO activa gana a APROBADO más nueva desactivada' | [fila(5L, 'APROBADO', false), fila(1L, 'APROBADO', true)] || 1L
    }

    def "pagador: misma regla (APROBADO + RECHAZADO, variante activa y completa)"() {
        given:
        repository.findByFunderIdAndPayerIdOrderByIdDesc(10L, 30L) >> [fila(9L, 'RECHAZADO', true), fila(4L, 'APROBADO', true)]

        expect:
        adapter.findByFunderIdAndPayerIdActivo(10L, 30L).get().id == 4L
        adapter.findByFunderIdAndPayerId(10L, 30L).get().id == 4L
    }

    def "dos APROBADO activas: se elige la más reciente y se avisa en WARN solo con ids"() {
        given:
        Logger logger = (Logger) LoggerFactory.getLogger(FondeadorQuotaAdapter)
        def appender = new ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        repository.findByFunderIdAndSupplierIdOrderByIdDesc(10L, 20L) >> [fila(7L, 'APROBADO', true), fila(6L, 'APROBADO', true)]

        when:
        def r = adapter.findByFunderIdAndSupplierIdActivo(10L, 20L)

        then:
        r.get().id == 7L
        def avisos = appender.list.findAll { it.level.toString() == 'WARN' }*.formattedMessage
        avisos.size() == 1
        avisos[0] == '[FondeadorQuotaAdapter] 2 asignaciones APROBADO para funderId=10 supplierId=20; se usa id=7'

        cleanup:
        logger.detachAppender(appender)
    }

    def "una sola fila APROBADO: sin aviso"() {
        given:
        Logger logger = (Logger) LoggerFactory.getLogger(FondeadorQuotaAdapter)
        def appender = new ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        repository.findByFunderIdAndSupplierIdOrderByIdDesc(10L, 20L) >> [fila(2L, 'RECHAZADO', true), fila(1L, 'APROBADO', true)]

        when:
        adapter.findByFunderIdAndSupplierIdActivo(10L, 20L)

        then:
        appender.list.findAll { it.level.toString() == 'WARN' }.isEmpty()

        cleanup:
        logger.detachAppender(appender)
    }
}
