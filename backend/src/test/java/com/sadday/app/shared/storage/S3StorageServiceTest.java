package com.sadday.app.shared.storage;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("S3StorageService — Unit Tests")
class S3StorageServiceTest {

    @Mock S3Client s3Client;

    @InjectMocks S3StorageService service;

    private static final String BUCKET     = "test-bucket";
    private static final String OBJECT_KEY = "path/to/file.pdf";
    private static final byte[] CONTENT    = "file-content".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "bucket", BUCKET);
    }

    // ── upload ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("upload")
    class Upload {

        @Test
        void upload_retornaEtagSinComillas() {
            PutObjectResponse response = PutObjectResponse.builder().eTag("\"abc123\"").build();
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenReturn(response);

            String etag = service.upload(CONTENT, OBJECT_KEY, "application/pdf");

            assertEquals("abc123", etag);
            verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }

        @Test
        void upload_etagNull_retornaNull() {
            PutObjectResponse response = PutObjectResponse.builder().build();
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenReturn(response);

            String etag = service.upload(CONTENT, OBJECT_KEY, "application/pdf");

            assertNull(etag);
        }
    }

    // ── download ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("download")
    class Download {

        @Test
        void download_retornaBytes() {
            @SuppressWarnings("unchecked")
            ResponseBytes<GetObjectResponse> responseBytes = mock(ResponseBytes.class);
            when(responseBytes.asByteArray()).thenReturn(CONTENT);
            when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

            byte[] result = service.download(OBJECT_KEY);

            assertArrayEquals(CONTENT, result);
        }
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        void delete_llama_s3Client() {
            service.delete(OBJECT_KEY);

            verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        }
    }
}
