package com.enterprise.agentplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.agentplatform.common.api.ErrorCode;
import com.enterprise.agentplatform.common.exception.BusinessException;
import com.enterprise.agentplatform.knowledge.service.LocalFileStorageService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class LocalFileStorageServiceTest {

    @TempDir
    private Path tempDir;

    @Test
    void shouldSaveUploadsInsideStorageBaseDirWithSanitizedName() throws Exception {
        Path storageDir = tempDir.resolve("storage");
        LocalFileStorageService storageService = new LocalFileStorageService(storageDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../unsafe name.md",
                "text/markdown",
                "safe content".getBytes(StandardCharsets.UTF_8)
        );

        Path savedPath = Path.of(storageService.save(file));

        assertThat(savedPath).startsWith(storageDir.toAbsolutePath().normalize());
        assertThat(savedPath.getFileName().toString()).endsWith("-.._unsafe_name.md");
        assertThat(Files.readString(savedPath)).isEqualTo("safe content");
    }

    @Test
    void shouldRejectResolvingObjectKeyOutsideStorageBaseDir() throws Exception {
        Path storageDir = tempDir.resolve("storage");
        Path outsideFile = tempDir.resolve("outside.md");
        Files.writeString(outsideFile, "do not read", StandardCharsets.UTF_8);
        LocalFileStorageService storageService = new LocalFileStorageService(storageDir.toString());

        assertThatThrownBy(() -> storageService.resolve(outsideFile.toString()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void shouldRejectRelativeTraversalObjectKeyOutsideStorageBaseDir() {
        Path storageDir = tempDir.resolve("storage");
        LocalFileStorageService storageService = new LocalFileStorageService(storageDir.toString());

        assertThatThrownBy(() -> storageService.resolve("../outside.md"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void shouldRejectBlankObjectKey() {
        Path storageDir = tempDir.resolve("storage");
        LocalFileStorageService storageService = new LocalFileStorageService(storageDir.toString());

        assertThatThrownBy(() -> storageService.resolve(" "))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void shouldNotDeleteObjectKeyOutsideStorageBaseDir() throws Exception {
        Path storageDir = tempDir.resolve("storage");
        Path outsideFile = tempDir.resolve("outside.md");
        Files.writeString(outsideFile, "do not delete", StandardCharsets.UTF_8);
        LocalFileStorageService storageService = new LocalFileStorageService(storageDir.toString());

        storageService.deleteIfExists(outsideFile.toString());

        assertThat(outsideFile).exists();
    }
}
