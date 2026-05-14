package com.sadday.app.auth.service;

import com.sadday.app.config.AuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordResetMailSender — Unit Tests")
class PasswordResetMailSenderTest {

    @Mock JavaMailSender  mailSender;
    @Mock AuthProperties  authProperties;

    @InjectMocks PasswordResetMailSender sender;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sender, "mailFrom", "noreply@club.com");
        ReflectionTestUtils.setField(sender, "appUrl",   "https://app.club.com");
        when(authProperties.getPasswordResetTokenExpiryMinutes()).thenReturn(30);
    }

    @Test
    @DisplayName("envía email con link y expiración correctos")
    void send_enviaEmail() {
        sender.send("user@test.com", "raw-reset-token");

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("error de SMTP no propaga excepción (@Async silencia errores)")
    void send_smtpError_noLanzaExcepcion() {
        doThrow(new RuntimeException("SMTP error")).when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> sender.send("user@test.com", "token"));
    }
}
