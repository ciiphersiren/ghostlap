package com.ghostlap.player.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of register and login. The annotations are checked by Bean Validation (@Valid). */
public record AuthRequest(
        @NotBlank
        @Size(min = 3, max = 20)
        @Pattern(regexp = "[A-Za-z0-9_]+", message = "may only contain letters, digits and _")
        String username,

        // BCrypt only uses the first 72 bytes of a password, so longer ones are refused.
        @NotBlank
        @Size(min = 8, max = 72)
        String password) {
}
