package com.ghostlap.race.leaderboard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Leaderboards")
public class LeaderboardController {

    private final LeaderboardService leaderboard;

    public LeaderboardController(LeaderboardService leaderboard) {
        this.leaderboard = leaderboard;
    }

    @GetMapping("/api/v1/tracks/{trackId}/leaderboard")
    @Operation(summary = "Fastest players on a track (each player's best lap)")
    public List<LeaderboardEntry> leaderboard(@PathVariable int trackId,
                                              @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        return leaderboard.top(trackId, limit);
    }
}
