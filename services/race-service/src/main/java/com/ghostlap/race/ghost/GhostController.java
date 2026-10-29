package com.ghostlap.race.ghost;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Ghosts")
public class GhostController {

    private final GhostService ghostService;

    public GhostController(GhostService ghostService) {
        this.ghostService = ghostService;
    }

    @GetMapping("/api/v1/tracks/{trackId}/ghosts/top")
    @Operation(summary = "Ghost of the fastest accepted lap on a track")
    public GhostResponse topGhost(@PathVariable int trackId) {
        return ghostService.topGhost(trackId);
    }

    @GetMapping("/api/v1/laps/{lapId}/ghost")
    @Operation(summary = "Ghost of one accepted lap")
    public GhostResponse ghostOfLap(@PathVariable long lapId) {
        return ghostService.ghostOfLap(lapId);
    }
}
