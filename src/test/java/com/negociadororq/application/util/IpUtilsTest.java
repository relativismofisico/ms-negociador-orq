package com.negociadororq.application.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
@DisplayName("IpUtils — extracción de IP del cliente")
class IpUtilsTest {

    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    @DisplayName("Retorna el primer IP de X-Forwarded-For cuando hay múltiples")
    void xForwardedFor_multiplesIps_retornaPrimero() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2, 10.0.0.3");

        String ip = IpUtils.obtenerIp(request);

        assertThat(ip).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("Retorna la IP única de X-Forwarded-For")
    void xForwardedFor_unaIp_retornaDirecta() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.100");

        String ip = IpUtils.obtenerIp(request);

        assertThat(ip).isEqualTo("192.168.1.100");
    }

    @Test
    @DisplayName("Ignora X-Forwarded-For con valor 'unknown' y usa X-Real-IP")
    void xForwardedForUnknown_usaXRealIp() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
        when(request.getHeader("X-Real-IP")).thenReturn("172.16.0.5");

        String ip = IpUtils.obtenerIp(request);

        assertThat(ip).isEqualTo("172.16.0.5");
    }

    @Test
    @DisplayName("Cuando X-Forwarded-For es null usa X-Real-IP")
    void xForwardedForNulo_usaXRealIp() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("172.16.0.10");

        String ip = IpUtils.obtenerIp(request);

        assertThat(ip).isEqualTo("172.16.0.10");
    }

    @Test
    @DisplayName("Cuando X-Forwarded-For es vacío usa X-Real-IP")
    void xForwardedForVacio_usaXRealIp() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("");
        when(request.getHeader("X-Real-IP")).thenReturn("10.10.0.1");

        String ip = IpUtils.obtenerIp(request);

        assertThat(ip).isEqualTo("10.10.0.1");
    }

    @Test
    @DisplayName("Usa remoteAddr cuando X-Forwarded-For y X-Real-IP son null")
    void ambosCabecerasNull_usaRemoteAddr() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        String ip = IpUtils.obtenerIp(request);

        assertThat(ip).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("Ignora X-Real-IP con valor 'unknown' (case insensitive) y usa remoteAddr")
    void xRealIpUnknown_usaRemoteAddr() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("UNKNOWN");
        when(request.getRemoteAddr()).thenReturn("::1");

        String ip = IpUtils.obtenerIp(request);

        assertThat(ip).isEqualTo("::1");
    }

    @Test
    @DisplayName("X-Forwarded-For con espacios extra en la IP devuelve IP sin espacios")
    void xForwardedForConEspacios_retornaIpSinEspacios() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("  10.1.2.3  , 10.4.5.6");

        String ip = IpUtils.obtenerIp(request);

        assertThat(ip).isEqualTo("10.1.2.3");
    }

    @Test
    @DisplayName("X-Real-IP vacío cae al remoteAddr (cubre branch isEmpty() de X-Real-IP)")
    void xRealIpVacio_usaRemoteAddr() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("");
        when(request.getRemoteAddr()).thenReturn("10.20.30.40");

        String ip = IpUtils.obtenerIp(request);

        assertThat(ip).isEqualTo("10.20.30.40");
    }
}