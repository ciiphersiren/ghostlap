package com.ghostlap.ghost;

import com.ghostlap.shared.ghost.Ghost;
import com.ghostlap.shared.ghost.GhostCodec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;

/** Saves and loads the player's best ghost per track, as GhostCodec bytes in a local file. */
public class GhostStorage {

    private final Path directory;

    public GhostStorage(Path directory) {
        this.directory = directory;
    }

    /** {@code %USERPROFILE%\.ghostlap\ghosts}: outside the repo, so saved laps are never committed. Used offline. */
    public static GhostStorage inUserHome() {
        return new GhostStorage(rootFolder());
    }

    /**
     * {@code %USERPROFILE%\.ghostlap\ghosts\players\<username>}: each account keeps its own personal
     * best, so two players sharing a computer don't overwrite each other's ghost. Usernames only
     * contain letters, digits and _ (checked by player-service), so they are safe folder names.
     */
    public static GhostStorage forPlayer(String username) {
        return new GhostStorage(rootFolder().resolve("players").resolve(username.toLowerCase(Locale.ROOT)));
    }

    private static Path rootFolder() {
        return Path.of(System.getProperty("user.home"), ".ghostlap", "ghosts");
    }

    public Path fileFor(int trackId) {
        return directory.resolve("track-" + trackId + ".ghost");
    }

    /** Writes the ghost and returns the file size in bytes. */
    public long save(Ghost ghost) throws IOException {
        Files.createDirectories(directory);
        Path target = fileFor(ghost.trackId());
        // Write to a temp file, then move it into place, so a crash mid-write can't corrupt the old best.
        Path temp = directory.resolve(target.getFileName() + ".tmp");
        Files.write(temp, GhostCodec.encode(ghost));
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return Files.size(target);
    }

    /**
     * Loads the saved ghost, or empty if there is none yet.
     *
     * @throws com.ghostlap.shared.ghost.GhostFormatException if the file is corrupt
     */
    public Optional<Ghost> load(int trackId) throws IOException {
        Path file = fileFor(trackId);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        return Optional.of(GhostCodec.decode(Files.readAllBytes(file)));
    }
}
