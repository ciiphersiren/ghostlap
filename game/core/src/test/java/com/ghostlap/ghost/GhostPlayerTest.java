package com.ghostlap.ghost;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.ghostlap.shared.ghost.Ghost;
import com.ghostlap.shared.ghost.GhostSample;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GhostPlayerTest {

    // Three samples at 30 Hz: t = 0, 1/30 s, 2/30 s
    private final GhostPlayer player = new GhostPlayer(new Ghost(1, 30, 67, List.of(
            new GhostSample(0f, 0f, 0f),
            new GhostSample(30f, 0f, 0f),
            new GhostSample(30f, 60f, 1f))));

    private final Vector2 position = new Vector2();

    @Test
    void exactlyOnASampleReturnsThatSample() {
        player.sample(1f / 30f, position);

        assertThat(position.x).isCloseTo(30f, within(0.001f));
        assertThat(position.y).isCloseTo(0f, within(0.001f));
    }

    @Test
    void betweenSamplesBlendsLinearly() {
        // 25% of the way from sample 1 to sample 2
        float heading = player.sample(1.25f / 30f, position);

        assertThat(position.x).isCloseTo(30f, within(0.001f));
        assertThat(position.y).isCloseTo(15f, within(0.001f));
        assertThat(heading).isCloseTo(0.25f, within(0.001f));
    }

    @Test
    void holdsStillBeforeTheStartAndAfterTheEnd() {
        player.sample(-1f, position);
        assertThat(position).isEqualTo(new Vector2(0f, 0f));

        player.sample(10f, position);
        assertThat(position).isEqualTo(new Vector2(30f, 60f));
        assertThat(player.isFinished(10f)).isTrue();
        assertThat(player.isFinished(0.5f / 30f)).isFalse();
    }

    @Test
    void headingTakesTheShortWayRoundThroughPi() {
        float from = 170f * MathUtils.degreesToRadians;
        float to = -170f * MathUtils.degreesToRadians;

        float halfway = GhostPlayer.lerpAngle(from, to, 0.5f);

        // Halfway between 170 and -170 the short way is 180 degrees, not 0.
        assertThat(Math.abs(halfway)).isCloseTo(MathUtils.PI, within(0.0001f));
    }

    @Test
    void headingTakesTheShortWayRoundTheOtherDirection() {
        float from = -170f * MathUtils.degreesToRadians;
        float to = 170f * MathUtils.degreesToRadians;

        float quarter = GhostPlayer.lerpAngle(from, to, 0.25f);

        assertThat(quarter).isCloseTo(-175f * MathUtils.degreesToRadians, within(0.0001f));
    }

    @Test
    void emptyGhostDoesNotCrash() {
        GhostPlayer empty = new GhostPlayer(new Ghost(1, 30, 0, List.of()));

        empty.sample(1f, position);

        assertThat(position).isEqualTo(Vector2.Zero);
    }
}
