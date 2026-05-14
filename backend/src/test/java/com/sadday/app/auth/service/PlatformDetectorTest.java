package com.sadday.app.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PlatformDetector — Unit Tests")
class PlatformDetectorTest {

    @Nested
    @DisplayName("Devuelve WEB")
    class DevuelveWeb {

        @Test
        void ua_null_retorna_web() {
            assertEquals(PlatformDetector.WEB, PlatformDetector.detect(null));
        }

        @Test
        void ua_blank_retorna_web() {
            assertEquals(PlatformDetector.WEB, PlatformDetector.detect("   "));
        }

        @Test
        void ua_chrome_retorna_web() {
            assertEquals(PlatformDetector.WEB, PlatformDetector.detect(
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"));
        }
    }

    @Nested
    @DisplayName("Devuelve MOBILE")
    class DevuelveMobile {

        @Test
        void ua_okhttp_retorna_mobile() {
            assertEquals(PlatformDetector.MOBILE, PlatformDetector.detect("OkHttp/4.9"));
        }

        @Test
        void ua_dart_retorna_mobile() {
            assertEquals(PlatformDetector.MOBILE, PlatformDetector.detect("Dart/2.18 (dart:io)"));
        }

        @Test
        void ua_flutter_retorna_mobile() {
            assertEquals(PlatformDetector.MOBILE, PlatformDetector.detect("Flutter/3.7"));
        }

        @Test
        void ua_cfnetwork_retorna_mobile() {
            assertEquals(PlatformDetector.MOBILE, PlatformDetector.detect("CFNetwork/1335.0.3 Darwin"));
        }

        @Test
        void ua_nsurlsession_retorna_mobile() {
            assertEquals(PlatformDetector.MOBILE, PlatformDetector.detect("NSURLSession/1.0"));
        }

        @Test
        void ua_okhttp_mayusculas_retorna_mobile() {
            assertEquals(PlatformDetector.MOBILE, PlatformDetector.detect("OkHttp/3.14.9 Android"));
        }
    }
}
