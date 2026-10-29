package com.ghostlap.shared.ghost;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Converts a {@link Ghost} to bytes and back. Layout before gzip (big-endian, as DataOutputStream writes):
 * <pre>
 *   magic        4 bytes  "GHST"
 *   version      1 byte
 *   trackId      int
 *   sampleRateHz short
 *   sampleCount  int
 *   lapTimeMs    int
 *   samples      sampleCount x (float x, float y, float heading)
 * </pre>
 * The whole thing is then gzipped.
 */
public final class GhostCodec {

    public static final byte[] MAGIC = {'G', 'H', 'S', 'T'};
    public static final byte VERSION = 1;
    public static final int HEADER_BYTES = 4 + 1 + 4 + 2 + 4 + 4; // 19
    public static final int BYTES_PER_SAMPLE = 3 * 4;              // 12
    /** 10 minutes at 30 Hz. Stops a corrupt header from making us allocate a huge list. */
    public static final int MAX_SAMPLES = 10 * 60 * 30;

    private GhostCodec() {
    }

    public static byte[] encode(Ghost ghost) {
        if (ghost.sampleRateHz() <= 0 || ghost.sampleRateHz() > Short.MAX_VALUE) {
            throw new IllegalArgumentException("sampleRateHz out of range: " + ghost.sampleRateHz());
        }
        if (ghost.sampleCount() > MAX_SAMPLES) {
            throw new IllegalArgumentException("too many samples: " + ghost.sampleCount());
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        // try-with-resources closes the streams; closing the gzip stream writes its trailer.
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.write(MAGIC);
            out.writeByte(VERSION);
            out.writeInt(ghost.trackId());
            out.writeShort(ghost.sampleRateHz());
            out.writeInt(ghost.sampleCount());
            out.writeInt(ghost.lapTimeMs());
            for (GhostSample s : ghost.samples()) {
                out.writeFloat(s.x());
                out.writeFloat(s.y());
                out.writeFloat(s.heading());
            }
        } catch (IOException e) {
            // Only in-memory streams are involved, so this cannot really happen.
            throw new IllegalStateException("failed to encode ghost", e);
        }
        return bytes.toByteArray();
    }

    /** @throws GhostFormatException if the data is not a valid ghost */
    public static Ghost decode(byte[] data) {
        if (data == null || data.length == 0) {
            throw new GhostFormatException("ghost data is empty");
        }
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(data)))) {
            byte[] magic = new byte[MAGIC.length];
            in.readFully(magic);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new GhostFormatException("bad magic, not a ghost file");
            }
            byte version = in.readByte();
            if (version != VERSION) {
                throw new GhostFormatException("unsupported version " + version);
            }
            int trackId = in.readInt();
            int sampleRateHz = in.readShort();
            int sampleCount = in.readInt();
            int lapTimeMs = in.readInt();
            if (sampleRateHz <= 0) {
                throw new GhostFormatException("bad sample rate " + sampleRateHz);
            }
            if (sampleCount < 0 || sampleCount > MAX_SAMPLES) {
                throw new GhostFormatException("bad sample count " + sampleCount);
            }

            List<GhostSample> samples = new ArrayList<>(sampleCount);
            for (int i = 0; i < sampleCount; i++) {
                float x = in.readFloat();
                float y = in.readFloat();
                float heading = in.readFloat();
                if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(heading)) {
                    throw new GhostFormatException("sample " + i + " is not a finite number");
                }
                samples.add(new GhostSample(x, y, heading));
            }

            // Reading one more byte must hit the end. This also makes gzip verify its checksum.
            if (in.read() != -1) {
                throw new GhostFormatException("unexpected data after the last sample");
            }
            return new Ghost(trackId, sampleRateHz, lapTimeMs, samples);
        } catch (EOFException e) {
            throw new GhostFormatException("ghost data is truncated", e);
        } catch (IOException e) {
            // ZipException (not gzip, bad checksum) is a subclass of IOException.
            throw new GhostFormatException("ghost data is not valid gzip", e);
        }
    }
}
