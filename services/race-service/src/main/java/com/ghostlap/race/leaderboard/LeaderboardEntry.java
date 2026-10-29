package com.ghostlap.race.leaderboard;

public record LeaderboardEntry(long rank, String username, int lapTimeMs, long lapId) {
}
