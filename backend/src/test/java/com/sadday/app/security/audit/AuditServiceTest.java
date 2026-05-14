package com.sadday.app.security.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditService — Unit Tests")
class AuditServiceTest {

    @Mock JdbcClient jdbcClient;

    @InjectMocks AuditService auditService;

    @Mock JdbcClient.StatementSpec statementSpec;

    @BeforeEach
    void setUp() {
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param(anyString(), any())).thenReturn(statementSpec);
    }

    @Test
    @DisplayName("jdbcClient falla — registrar no propaga excepción")
    void registrar_jdbcClientFalla_noLanzaExcepcion() {
        doThrow(new RuntimeException("DB error")).when(statementSpec).update();

        assertDoesNotThrow(() -> auditService.registrar(
                "actor", "LOGIN_FAILED", "socios", UUID.randomUUID(),
                null, null, "127.0.0.1", "Mozilla/5.0", "FAILED", "Bad credentials"));
    }
}
