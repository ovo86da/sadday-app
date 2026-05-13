package com.sadday.app.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityAlertMailSender — Unit Tests")
class SecurityAlertMailSenderTest {

    @Mock JavaMailSender mailSender;

    @InjectMocks SecurityAlertMailSender alertSender;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(alertSender, "mailFrom", "noreply@sadday.com");
        ReflectionTestUtils.setField(alertSender, "appUrl", "https://app.sadday.com");
    }

    @Test
    void sendNewDeviceAlert_enviaCorreo() {
        alertSender.sendNewDeviceAlert("user@test.com", "Juan", "Chrome", "Windows",
                "Quito", "Ecuador", false);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(2000)).send(captor.capture());

        SimpleMailMessage msg = captor.getValue();
        assertEquals("user@test.com", msg.getTo()[0]);
        assertTrue(msg.getSubject().contains("dispositivo"));
        assertTrue(msg.getText().contains("Juan"));
    }

    @Test
    void sendNewDeviceAlert_conMfa_sinSeccionMfa() {
        alertSender.sendNewDeviceAlert("user@test.com", "Juan", "Firefox", "Linux",
                "Guayaquil", "Ecuador", true);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(2000)).send(captor.capture());

        assertFalse(captor.getValue().getText().contains("autenticación de dos factores"));
    }

    @Test
    void sendNewDeviceAlert_sinMfa_incluyeSeccionMfa() {
        alertSender.sendNewDeviceAlert("user@test.com", "Ana", null, null,
                null, null, false);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(2000)).send(captor.capture());

        assertTrue(captor.getValue().getText().contains("autenticación de dos factores"));
    }

    @Test
    void sendNewCountryAlert_enviaCorreo() {
        alertSender.sendNewCountryAlert("user@test.com", "Pedro", "Brasil",
                "São Paulo", "Safari");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(2000)).send(captor.capture());

        SimpleMailMessage msg = captor.getValue();
        assertEquals("user@test.com", msg.getTo()[0]);
        assertTrue(msg.getSubject().contains("país"));
        assertTrue(msg.getText().contains("Brasil"));
    }

    @Test
    void sendNewCountryAlert_browserNull_usaDesconocido() {
        alertSender.sendNewCountryAlert("user@test.com", "Ana", null, null, null);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(2000)).send(captor.capture());

        assertTrue(captor.getValue().getText().contains("Desconocido"));
    }

    @Test
    void sendCountryChallengeCode_enviaCorreoSincrono() {
        alertSender.sendCountryChallengeCode("user@test.com", "Luis", "123456",
                "Argentina", "Buenos Aires", "Chrome");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        SimpleMailMessage msg = captor.getValue();
        assertTrue(msg.getText().contains("123456"));
        assertTrue(msg.getSubject().contains("verificación"));
    }

    @Test
    void sendCountryChallengeCode_countryNull_usaDesconocido() {
        alertSender.sendCountryChallengeCode("user@test.com", "Carlos", "999999",
                null, null, null);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        assertTrue(captor.getValue().getText().contains("Desconocido"));
    }

    @Test
    void sendNewDeviceAlert_errorEnvio_noLanzaExcepcion() {
        doThrow(new RuntimeException("SMTP error")).when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() ->
                alertSender.sendNewDeviceAlert("fail@test.com", "X", null, null, null, null, true)
        );
    }
}
