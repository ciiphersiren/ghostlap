package com.ghostlap.player.auth;

import com.ghostlap.player.player.Player;
import com.ghostlap.player.player.PlayerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final PlayerRepository players;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(PlayerRepository players, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.players = players;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @Transactional
    public AuthResponse register(AuthRequest request) {
        if (players.existsByUsernameIgnoreCase(request.username())) {
            throw new UsernameTakenException(request.username());
        }
        Player player;
        try {
            player = players.saveAndFlush(new Player(request.username(), passwordEncoder.encode(request.password())));
        } catch (DataIntegrityViolationException e) {
            // Two sign-ups with the same name at the same moment: the database's unique index catches the second.
            throw new UsernameTakenException(request.username());
        }
        return new AuthResponse(player.getId(), tokenService.issue(player));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(AuthRequest request) {
        Player player = players.findByUsernameIgnoreCase(request.username())
                .filter(p -> passwordEncoder.matches(request.password(), p.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        return new AuthResponse(player.getId(), tokenService.issue(player));
    }
}
