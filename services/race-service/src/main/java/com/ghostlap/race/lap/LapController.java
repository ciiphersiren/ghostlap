package com.ghostlap.race.lap;

import com.ghostlap.race.config.OpenApiConfig;
import com.ghostlap.race.config.SecurityConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Laps")
public class LapController {

    private final LapService lapService;

    public LapController(LapService lapService) {
        this.lapService = lapService;
    }

    /** Always 201: a rejected lap is still stored, with status REJECTED and a reason code. */
    @PostMapping("/api/v1/laps")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a lap with its ghost; the server validates it")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    public LapSubmissionResponse submit(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LapSubmissionRequest request) {
        long playerId = Long.parseLong(jwt.getSubject());
        String username = jwt.getClaimAsString(SecurityConfig.USERNAME_CLAIM);
        return lapService.submit(playerId, username, request);
    }
}
