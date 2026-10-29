package com.ghostlap.shared.ghost;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GhostCodecTest {

    /** A smooth lap around a circle, like a real ghost: 20 s at 30 Hz. */
    private static Ghost circleLap() {
        List<GhostSample> samples = new ArrayList<>();
        int count = 20 * 30 + 1;
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / (count - 1);
            samples.add(new GhostSample(
                    (float) (1000 * Math.cos(angle)),
                    (float) (1000 * Math.sin(angle)),
                    (float) (angle + Math.PI / 2)));
        }
        return new Ghost(1, 30, 20_000, samples);
    }

    @Test
    void roundTripKeepsEveryField() {
        Ghost original = circleLap();

        Ghost decoded = GhostCodec.decode(GhostCodec.encode(original));

        assertThat(decoded.trackId()).isEqualTo(1);
        assertThat(decoded.sampleRateHz()).isEqualTo(30);
        assertThat(decoded.lapTimeMs()).isEqualTo(20_000);
        // Records compare floats exactly, so this proves no precision was lost.
        assertThat(decoded.samples()).isEqualTo(original.samples());
    }

    @Test
    void roundTripWorksWithNoSamples() {
        Ghost empty = new Ghost(7, 30, 0, List.of());

        assertThat(GhostCodec.decode(GhostCodec.encode(empty))).isEqualTo(empty);
    }

    @Test
    void outputIsGzipWrappingTheGhstHeader() throws IOException {
        byte[] encoded = GhostCodec.encode(circleLap());

        // gzip files always start with 0x1f 0x8b
        assertThat(encoded[0]).isEqualTo((byte) 0x1f);
        assertThat(encoded[1]).isEqualTo((byte) 0x8b);

        byte[] raw = new GZIPInputStream(new ByteArrayInputStream(encoded)).readAllBytes();
        assertThat(Arrays.copyOf(raw, 4)).isEqualTo(GhostCodec.MAGIC);
        assertThat(raw).hasSize(GhostCodec.HEADER_BYTES + 601 * GhostCodec.BYTES_PER_SAMPLE);
    }

    @Test
    void gzipMakesASmoothLapSmaller() {
        Ghost ghost = circleLap();
        int rawSize = GhostCodec.HEADER_BYTES + ghost.sampleCount() * GhostCodec.BYTES_PER_SAMPLE;

        int encodedSize = GhostCodec.encode(ghost).length;

        System.out.printf("circle lap: raw %d bytes, gzipped %d bytes%n", rawSize, encodedSize);
        assertThat(encodedSize).isLessThan(rawSize);
    }

    @Test
    void encodeRejectsBadSampleRate() {
        assertThatThrownBy(() -> GhostCodec.encode(new Ghost(1, 0, 1000, List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- corrupt data: every case must be a GhostFormatException, never another exception ----

    @Test
    void rejectsNullAndEmpty() {
        assertThatThrownBy(() -> GhostCodec.decode(null)).isInstanceOf(GhostFormatException.class);
        assertThatThrownBy(() -> GhostCodec.decode(new byte[0])).isInstanceOf(GhostFormatException.class);
    }

    @Test
    void rejectsDataThatIsNotGzip() {
        byte[] garbage = "definitely not a ghost".getBytes();

        assertThatThrownBy(() -> GhostCodec.decode(garbage))
                .isInstanceOf(GhostFormatException.class)
                .hasMessageContaining("gzip");
    }

    @Test
    void rejectsWrongMagic() throws IOException {
        byte[] data = gzip(header("NOPE", 1, 30, 0, 0));

        assertThatThrownBy(() -> GhostCodec.decode(data))
                .isInstanceOf(GhostFormatException.class)
                .hasMessageContaining("magic");
    }

    @Test
    void rejectsUnknownVersion() throws IOException {
        byte[] data = gzip(header("GHST", 99, 30, 0, 0));

        assertThatThrownBy(() -> GhostCodec.decode(data))
                .isInstanceOf(GhostFormatException.class)
                .hasMessageContaining("version");
    }

    @Test
    void rejectsNegativeOrHugeSampleCount() throws IOException {
        byte[] negative = gzip(header("GHST", 1, 30, -5, 0));
        byte[] huge = gzip(header("GHST", 1, 30, Integer.MAX_VALUE, 0));

        assertThatThrownBy(() -> GhostCodec.decode(negative)).isInstanceOf(GhostFormatException.class);
        assertThatThrownBy(() -> GhostCodec.decode(huge)).isInstanceOf(GhostFormatException.class);
    }

    @Test
    void rejectsZeroSampleRate() throws IOException {
        byte[] data = gzip(header("GHST", 1, 0, 0, 0));

        assertThatThrownBy(() -> GhostCodec.decode(data)).isInstanceOf(GhostFormatException.class);
    }

    @Test
    void rejectsFewerSamplesThanTheHeaderClaims() throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(raw);
        out.write(header("GHST", 1, 30, 100, 3333)); // claims 100 samples...
        for (int i = 0; i < 50; i++) {                  // ...but only has 50
            out.writeFloat(i);
            out.writeFloat(i);
            out.writeFloat(0f);
        }

        assertThatThrownBy(() -> GhostCodec.decode(gzip(raw.toByteArray())))
                .isInstanceOf(GhostFormatException.class)
                .hasMessageContaining("truncated");
    }

    @Test
    void rejectsExtraBytesAfterTheLastSample() throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        raw.write(header("GHST", 1, 30, 0, 0));
        raw.write(42);

        assertThatThrownBy(() -> GhostCodec.decode(gzip(raw.toByteArray())))
                .isInstanceOf(GhostFormatException.class)
                .hasMessageContaining("after the last sample");
    }

    @Test
    void rejectsNaNCoordinates() throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(raw);
        out.write(header("GHST", 1, 30, 1, 0));
        out.writeFloat(Float.NaN);
        out.writeFloat(0f);
        out.writeFloat(0f);

        assertThatThrownBy(() -> GhostCodec.decode(gzip(raw.toByteArray())))
                .isInstanceOf(GhostFormatException.class);
    }

    @Test
    void rejectsACutOffFile() {
        byte[] encoded = GhostCodec.encode(circleLap());
        byte[] cut = Arrays.copyOf(encoded, encoded.length / 2);

        assertThatThrownBy(() -> GhostCodec.decode(cut)).isInstanceOf(GhostFormatException.class);
    }

    @Test
    void rejectsAFlippedByte() {
        byte[] encoded = GhostCodec.encode(circleLap());
        encoded[encoded.length / 2] ^= 0x55; // corrupt the compressed data

        // Either inflating fails or the gzip CRC check at the end catches it.
        assertThatThrownBy(() -> GhostCodec.decode(encoded)).isInstanceOf(GhostFormatException.class);
    }

    // ---- helpers to build hand-made (possibly invalid) payloads ----

    private static byte[] header(String magic, int version, int sampleRate, int sampleCount, int lapTimeMs)
            throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(raw);
        out.writeBytes(magic);
        out.writeByte(version);
        out.writeInt(1);
        out.writeShort(sampleRate);
        out.writeInt(sampleCount);
        out.writeInt(lapTimeMs);
        return raw.toByteArray();
    }

    private static byte[] gzip(byte[] raw) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(raw);
        }
        return bytes.toByteArray();
    }
}
