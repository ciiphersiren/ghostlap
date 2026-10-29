package com.ghostlap.player.player;

import com.ghostlap.player.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/players")
@Tag(name = "Players")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class PlayerController {

    private final PlayerRepository players;

    public PlayerController(PlayerRepository players) {
        this.players = players;
    }

    /** The token's subject ("sub" claim) is the player id, put there at login. */
    @GetMapping("/me")
    @Operation(summary = "The logged-in player's profile")
    public PlayerResponse me(@AuthenticationPrincipal Jwt jwt) {
        long playerId = Long.parseLong(jwt.getSubject());
        Player player = players.findById(playerId).orElseThrow(() -> new PlayerNotFoundException(playerId));
        return new PlayerResponse(player.getId(), player.getUsername(), player.getCreatedAt());
    }
}
