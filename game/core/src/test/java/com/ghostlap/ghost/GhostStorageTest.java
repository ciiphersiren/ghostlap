package com.ghostlap.ghost;

import com.ghostlap.shared.ghost.Ghost;
import com.ghostlap.shared.ghost.GhostFormatException;
import com.ghostlap.shared.ghost.GhostSample;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GhostStorageTest {

    /**
     * A fresh folder per test under build/tmp (removed by `gradlew clean`). We don't use JUnit's
     * {@code @TempDir}: on Windows, a background scanner can briefly hold a just-replaced file open,
     * so deleting the folder right after the test fails at random.
     */
    private Path tempDir;

    @BeforeEach
    void createFolder() throws IOException {
        tempDir = Files.createDirectories(Path.of("build", "tmp", "ghost-storage-test", UUID.randomUUID().toString()));
    }

    private final Ghost ghost = new Ghost(1, 30, 12_345, List.of(
            new GhostSample(1f, 2f, 0.5f),
            new GhostSample(3f, 4f, 0.6f)));

    @Test
    void savedGhostLoadsBack() throws IOException {
        GhostStorage storage = new GhostStorage(tempDir.resolve("ghosts"));

        long size = storage.save(ghost);

        assertThat(size).isPositive();
        assertThat(storage.load(1)).contains(ghost);
        // The temp file used for the safe write is gone. (Files.list must be closed, or Windows keeps the folder locked.)
        try (Stream<Path> files = Files.list(tempDir.resolve("ghosts"))) {
            assertThat(files).containsExactly(storage.fileFor(1));
        }
    }

    @Test
    void savingAgainReplacesTheOldGhost() throws IOException {
        GhostStorage storage = new GhostStorage(tempDir);
        storage.save(ghost);
        Ghost faster = new Ghost(1, 30, 10_000, List.of(new GhostSample(9f, 9f, 0f)));

        storage.save(faster);

        assertThat(storage.load(1)).contains(faster);
    }

    @Test
    void missingFileMeansNoGhost() throws IOException {
        assertThat(new GhostStorage(tempDir).load(1)).isEmpty();
    }

    @Test
    void corruptFileIsReported() throws IOException {
        GhostStorage storage = new GhostStorage(tempDir);
        Files.writeString(storage.fileFor(1), "garbage");

        assertThatThrownBy(() -> storage.load(1)).isInstanceOf(GhostFormatException.class);
    }
}
