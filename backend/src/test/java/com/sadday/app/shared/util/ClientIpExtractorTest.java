package com.sadday.app.shared.util;

import com.sadday.app.config.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClientIpExtractor — Unit Tests")
class ClientIpExtractorTest {

    @Mock HttpServletRequest request;

    private ClientIpExtractor extractor;

    @BeforeEach
    void setUp() {
        SecurityProperties props = new SecurityProperties();
        props.setTrustedProxyCidrs(List.of("127.0.0.1/32", "172.16.0.0/12"));
        extractor = new ClientIpExtractor(props);
    }

    @Nested
    @DisplayName("extractIp — proxy no confiable")
    class SinProxy {

        @Test
        void remoteAddrDirecta_ignoraHeaders() {
            when(request.getRemoteAddr()).thenReturn("203.0.113.5");

            String ip = extractor.extractIp(request);

            assertEquals("203.0.113.5", ip);
            verify(request, never()).getHeader(anyString());
        }
    }

    @Nested
    @DisplayName("extractIp — proxy confiable")
    class ConProxy {

        @BeforeEach
        void setTrustedRemote() {
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        }

        @Test
        void cfConnectingIpPresente_retornaCfIp() {
            when(request.getHeader("CF-Connecting-IP")).thenReturn("1.2.3.4");

            assertEquals("1.2.3.4", extractor.extractIp(request));
        }

        @Test
        void sinCfIp_xForwardedForPresente_retornaPrimerIp() {
            when(request.getHeader("CF-Connecting-IP")).thenReturn(null);
            when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2");

            assertEquals("10.0.0.1", extractor.extractIp(request));
        }

        @Test
        void sinCfIp_xForwardedForInvalido_retornaRemoteAddr() {
            when(request.getHeader("CF-Connecting-IP")).thenReturn(null);
            when(request.getHeader("X-Forwarded-For")).thenReturn("  ");

            assertEquals("127.0.0.1", extractor.extractIp(request));
        }

        @Test
        void ambosHeadersAusentes_retornaRemoteAddr() {
            when(request.getHeader("CF-Connecting-IP")).thenReturn(null);
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);

            assertEquals("127.0.0.1", extractor.extractIp(request));
        }

        @Test
        void cfIpDemasiadoLarga_retornaXff() {
            String ipLarga = "a".repeat(46);
            when(request.getHeader("CF-Connecting-IP")).thenReturn(ipLarga);
            when(request.getHeader("X-Forwarded-For")).thenReturn("5.5.5.5");

            assertEquals("5.5.5.5", extractor.extractIp(request));
        }
    }

    @Nested
    @DisplayName("extractIpFromContext — sin request activo")
    class SinContexto {

        @Test
        void sinRequestContexto_retornaUnknown() {
            String ip = extractor.extractIpFromContext();
            assertEquals("UNKNOWN", ip);
        }
    }

    @Nested
    @DisplayName("extractUserAgentFromContext — sin request activo")
    class UserAgentSinContexto {

        @Test
        void sinRequestContexto_retornaNull() {
            assertNull(extractor.extractUserAgentFromContext());
        }
    }
}
