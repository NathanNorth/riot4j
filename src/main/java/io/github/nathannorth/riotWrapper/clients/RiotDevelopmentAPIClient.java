package io.github.nathannorth.riotWrapper.clients;

import io.github.nathannorth.riotWrapper.json.Mapping;
import io.github.nathannorth.riotWrapper.json.valContent.ContentData;
import io.github.nathannorth.riotWrapper.json.valLeaderboard.LeaderboardData;
import io.github.nathannorth.riotWrapper.json.valLeaderboard.LeaderboardPlayerData;
import io.github.nathannorth.riotWrapper.json.valPlatform.PlatformStatusData;
import io.github.nathannorth.riotWrapper.objects.ValActId;
import io.github.nathannorth.riotWrapper.objects.ValLocale;
import io.github.nathannorth.riotWrapper.queues.CleanLimitedQueue;
import io.github.nathannorth.riotWrapper.objects.ValRegion;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;

public class RiotDevelopmentAPIClient extends RiotAPIClient {

    private final CleanLimitedQueue rateLimiter = new CleanLimitedQueue();

    public RiotDevelopmentAPIClient(String token) {
        super(token);
    }

    //todo this should have a default locale and region with overrides(?)


    //todo better builder structure
    public static RiotDevelopmentAPIClientBuilder builder() {
        return new RiotDevelopmentAPIClientBuilder();
    }

    public Mono<PlatformStatusData> getValStatus(ValRegion region) {
        return rateLimiter.push(getValStatusRaw(token, region.getValue()))
                .map(Mapping.map(PlatformStatusData.class));
    }

    //we only support getting by local because anything else is stupid
    public Mono<ContentData> getValContent(ValRegion region, ValLocale locale) {
        return rateLimiter.push(getValContentRaw(token, region.getValue(), locale.getValue()))
                .map(Mapping.map(ContentData.class));
    }

    public Mono<LeaderboardData> getValLeaderboards(ValRegion region, ValActId id, int startIndex, int size) {
        System.out.println("start index " + startIndex + " size " + size);
        if(startIndex < 0) throw new IndexOutOfBoundsException("Start cannot be negative!");
        if(size > 200) throw new IndexOutOfBoundsException("Size cannot be greater than 200!");
        return rateLimiter.push(getValLeaderboardRaw(token, region.getValue(), id.getValue(), size + "", startIndex + ""))
                .map(Mapping.map(LeaderboardData.class));
    }

    public Flux<LeaderboardPlayerData> getLeaderboardInfinite(ValRegion region, ValActId id, int startIndex, long endIndex) {
        if(startIndex < 0) throw new IndexOutOfBoundsException("Start cannot be negative!");
        return getValLeaderboards(region, id, 0, 1)
                //todo make more efficient
                .flatMapMany(data -> {
                    ArrayList<Integer> temp = new ArrayList<>();
                    for(int i = startIndex; i < (int) Math.min(endIndex, data.totalPlayers() - 1); i += 200) {
                        temp.add(i);
                    }
                    System.out.println(temp);
                    return Flux.fromIterable(temp);
                })
                        .flatMap(num -> getValLeaderboards(region, id, num, (int) Math.min(endIndex - num, 200))
                                .flatMapMany(result -> Flux.fromIterable(result.players())));
    }
}
