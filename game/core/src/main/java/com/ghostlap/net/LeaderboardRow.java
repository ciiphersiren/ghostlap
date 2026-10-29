package com.ghostlap.net;

public record LeaderboardRow(int rank, String username, int lapTimeMs, long lapId) {
}
