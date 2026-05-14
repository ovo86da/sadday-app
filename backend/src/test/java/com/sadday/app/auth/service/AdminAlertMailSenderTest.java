package com.sadday.app.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminAlertMailSender — Unit Tests")
class AdminAlertMailSenderTest {

    @Mock JavaMailSender mailSender;

    @InjectMocks AdminAlertMailSender sender;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sender, "mailFrom", "test@sadday.local");
    }

    @Nested
    @DisplayName("sendGeoIpMissingAlert")
    class MissingAlert {

        @Test
        void sinAdminEmail_missingAlert_noEnviaCorreo() {
            ReflectionTestUtils.setField(sender, "adminEmail", "");
            sender.sendGeoIpMissingAlert();
            verify(mailSender, never()).send(any(SimpleMailMessage.class));
        }

        @Test
        void conAdminEmail_missingAlert_enviaCorreo() {
            ReflectionTestUtils.setField(sender, "adminEmail", "admin@sadday.local");
            sender.sendGeoIpMissingAlert();
            verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
        }

        @Test
        void errorEnEnvio_missingAlert_noLanzaExcepcion() {
            ReflectionTestUtils.setField(sender, "adminEmail", "admin@sadday.local");
            doThrow(new RuntimeException("SMTP error")).when(mailSender).send(any(SimpleMailMessage.class));
            assertDoesNotThrow(() -> sender.sendGeoIpMissingAlert());
        }
    }

    @Nested
    @DisplayName("sendGeoIpStaleAlert")
    class StaleAlert {

        @Test
        void sinAdminEmail_staleAlert_noEnviaCorreo() {
            ReflectionTestUtils.setField(sender, "adminEmail", "");
            sender.sendGeoIpStaleAlert(45L);
            verify(mailSender, never()).send(any(SimpleMailMessage.class));
        }

        @Test
        void conAdminEmail_staleAlert_enviaCorreo() {
            ReflectionTestUtils.setField(sender, "adminEmail", "admin@sadday.local");
            sender.sendGeoIpStaleAlert(45L);
            verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
        }

        @Test
        void errorEnEnvio_staleAlert_noLanzaExcepcion() {
            ReflectionTestUtils.setField(sender, "adminEmail", "admin@sadday.local");
            doThrow(new RuntimeException("SMTP error")).when(mailSender).send(any(SimpleMailMessage.class));
            assertDoesNotThrow(() -> sender.sendGeoIpStaleAlert(45L));
        }
    }
}
