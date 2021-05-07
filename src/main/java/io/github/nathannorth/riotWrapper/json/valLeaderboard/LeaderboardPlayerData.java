package io.github.nathannorth.riotWrapper.json.valLeaderboard;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.Optional;

@Value.Immutable
@JsonSerialize(as = ImmutableLeaderboardPlayerData.class)
@JsonDeserialize(as = ImmutableLeaderboardPlayerData.class)
public interface LeaderboardPlayerData {
    Optional<String> puuid();
    Optional<String> gameName();
    Optional<String> tagLine();
    long leaderboardRank();
    long rankedRating();
    long numberOfWins();
    int competitiveTier(); //undocumented
}
