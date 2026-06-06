package com.sadday.app.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentAuditService — Unit Tests")
class DocumentAuditServiceTest {

    @Mock JdbcClient jdbcClient;

    @InjectMocks DocumentAuditService service;

    @Test
    @DisplayName("log — sin SecurityContext → no lanza excepción (actorId null)")
    void log_sinSecurityContext_noException() {
        SecurityContextHolder.clearContext();

        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec<?> mappedSpec = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbcClient.sql(anyString())).thenReturn(spec);
        when(spec.param(anyString(), any())).thenReturn(spec);
        when(spec.update()).thenReturn(1);

        assertThatCode(() ->
                service.log(AuditAction.MEDICAL_INFO_VIEWED, "MEDICAL_INFO", UUID.randomUUID())
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("log — excepción en JdbcClient → capturada silenciosamente")
    void log_jdbcException_swallowed() {
        when(jdbcClient.sql(anyString())).thenThrow(new RuntimeException("DB error"));

        assertThatCode(() ->
                service.log(AuditAction.LEGAL_DOCUMENT_ACCEPTED, "legal_documents", UUID.randomUUID())
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("log con metadata → SQL ejecutado con metadataJson")
    void log_conMetadata_ejecutaInsert() {
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class);
        when(jdbcClient.sql(anyString())).thenReturn(spec);
        when(spec.param(anyString(), any())).thenReturn(spec);
        when(spec.update()).thenReturn(1);

        UUID resourceId = UUID.randomUUID();
        service.log(AuditAction.EMERGENCY_CONTACT_UPDATED, "EMERGENCY_CONTACTS", resourceId,
                null, null, Map.of("count", 2));

        verify(jdbcClient).sql(anyString());
        verify(spec, atLeastOnce()).param(eq("action"), eq("EMERGENCY_CONTACT_UPDATED"));
        verify(spec, atLeastOnce()).param(eq("resourceType"), eq("EMERGENCY_CONTACTS"));
        verify(spec, atLeastOnce()).param(eq("resourceId"), eq(resourceId));
    }

    @Test
    @DisplayName("log sin metadata → metadataJson es null")
    void log_sinMetadata_metadataNull() {
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class);
        when(jdbcClient.sql(anyString())).thenReturn(spec);
        when(spec.param(anyString(), any())).thenReturn(spec);
        when(spec.update()).thenReturn(1);

        service.log(AuditAction.SOCIO_RETIRED, "socios", UUID.randomUUID());

        verify(spec).param(eq("metadataJson"), isNull());
    }

    @Test
    @DisplayName("AuditAction — todos los valores tienen nombre válido")
    void auditAction_allValues_hasName() {
        for (AuditAction action : AuditAction.values()) {
            assertThatCode(action::name).doesNotThrowAnyException();
        }
    }
}
