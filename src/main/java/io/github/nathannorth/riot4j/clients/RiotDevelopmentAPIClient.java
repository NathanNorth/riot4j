package io.github.nathannorth.riot4j.clients;

import io.github.nathannorth.riot4j.api.account.RiotAccount;
import io.github.nathannorth.riot4j.enums.RiotGame;
import io.github.nathannorth.riot4j.enums.RiotRegion;
import io.github.nathannorth.riot4j.enums.ValLocale;
import io.github.nathannorth.riot4j.enums.ValRegion;
import io.github.nathannorth.riot4j.exceptions.WebFailure;
import io.github.nathannorth.riot4j.json.Mapping;
import io.github.nathannorth.riot4j.json.riotAccount.ActiveShardData;
import io.github.nathannorth.riot4j.json.valContent.ContentData;
import io.github.nathannorth.riot4j.json.valLeaderboard.LeaderboardData;
import io.github.nathannorth.riot4j.json.valLeaderboard.LeaderboardPlayerData;
import io.github.nathannorth.riot4j.json.valPlatform.PlatformStatusData;
import io.github.nathannorth.riot4j.objects.Translator;
import io.github.nathannorth.riot4j.objects.ValActId;
import io.github.nathannorth.riot4j.objects.ValActIdGroup;
import io.github.nathannorth.riot4j.objects.ValStatusUpdateEvent;
import io.github.nathannorth.riot4j.queues.RateLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * A DevelopmentClient is the core of this library. The client contains logic for rate limiting and mapping requests.
 * If there were to be a time when I got access to a production key, a sister client, the RiotProductionClient with
 * more complete access to the API would be written.
 */
public class RiotDevelopmentAPIClient extends RiotAPIClient {

    private static final Logger log = LoggerFactory.getLogger(RiotDevelopmentAPIClient.class);

    protected RiotDevelopmentAPIClient(ClientConfig config) {
        super(config);
    }

    /**
     * ACCOUNTS:
     */

    /**
     * Gets a riot account from a name/tagline. note the parameter is a RIOT REGION, not a VAL REGION. There is no enum
     * class for Riot Regions at this time. See also: {@link #getRiotAccountByName(String, String)} for an alternative that
     * defaults to americas endpoint
     * @param riotRegion a string representation of the desired RIOT region (eg 'americas' instead of 'na')
     * @param name username to search for
     * @param tagLine tag to search for
     * @return returns a {@link WebFailure} with error code 404 if user not found
     */
    public Mono<RiotAccount> getRiotAccountByName(RiotRegion riotRegion, String name, String tagLine) {
        return getRiotAccountData(riotRegion, name, tagLine)
                .map(data -> new RiotAccount(this, data));
    }

    public Mono<RiotAccount> getRiotAccountByName(String name, String tagLine) {
        return getRiotAccountByName(riotRegion, name, tagLine);
    }


    public Mono<RiotAccount> getRiotAccountByPuuid(RiotRegion riotRegion, String puuid) {
        return getRiotAccountData(riotRegion, puuid)
                .map(data -> new RiotAccount(this, data));
    }

    public Mono<RiotAccount> getRiotAccountByPuuid(String puuid) {
        return getRiotAccountByPuuid(riotRegion, puuid);
    }

    /**
     * REGIONS:
     */

    public Mono<ActiveShardData> getActiveShardsByGame(RiotRegion region, RiotGame game, String puuid) {
        return getActiveShardData(region, game, puuid);
    }

    public Mono<ActiveShardData> getActiveShardsVal(String puuid) {
        return getActiveShardsByGame(riotRegion, RiotGame.VALORANT, puuid);
    }

    /**
     * STATUS:
     */

    //todo abstract more data methods to RiotAPIClient

    /**
     * Gets VALORANT's status for a given region.
     * @param region Which VALORANT region to get data from
     * @return a PlatformStatusData containing any incidents / maintenance.
     */
    public Mono<PlatformStatusData> getValStatus(ValRegion region) {
        return buckets.pushToBucket(RateLimits.VAL_STATUS, getValStatusRaw(token, region.toString()))
                .map(Mapping.map(PlatformStatusData.class));
    }

    private PlatformStatusData lastData = null;

    /**
     * Gets a flux that emits statusUpdateEvents whenever riots status for a given region changes. Checks are made on a given duration.
     * @param region Region to get status from
     * @param duration Duration to check for updates, not recommended to be set to anything under 1 second to avoid overflowing the ratelimit sink
     * @return
     */
    public Flux<ValStatusUpdateEvent> getStatusUpdates(ValRegion region, Duration duration) { //todo test or remove this
        return Flux.interval(duration)
                .doOnNext(e -> log.debug("Trying to get a new status update"))
                .flatMap(num -> getValStatus(region))
                .doOnNext(e -> log.debug("Succeeded in getting new status update"))
                .filter(status -> !status.equals(lastData)) //data must be changed
                .map(newStatus -> {
                    PlatformStatusData oldStatus = lastData;
                    lastData = newStatus;
                    return new ValStatusUpdateEvent(oldStatus, newStatus);
                });
    }

    /**
     * Gets a ton of data from the game. Notably used to get ActIDs and game updates.
     * @param region Which VALORANT region to get data from
     * @param locale What language to return data in - technically optional on an API level but its just a headache to deal with every language at once
     * @return a content object containing all characters, maps, chromas, skin(Levels)s, equips, gameModes, spray(Levels)s, charm(Levels)s, playerCards, playerTitles, and acts.
     */
    public Mono<ContentData> getValContent(ValRegion region, ValLocale locale) {
        return buckets.pushToBucket(RateLimits.VAL_CONTENT, getValContentRaw(token, region.toString(), locale.toString()))
                .map(Mapping.map(ContentData.class));
    }

    /**
     * Get all ValActIds in an organized object
     * @return a mono that evaluates to a ValActIdSet
     */
    public Mono<ValActIdGroup> getActs() {
        return getValContent(valRegion, ValLocale.US_ENGLISH)
                .map(contentData -> new ValActIdGroup(contentData.acts()));
    }

    /**
     * Get a new translator object based on latest data
     * @return a translator object up do date as of when the mono evaluates
     */
    public Mono<Translator> getTranslator() {
        return getValContent(valRegion, ValLocale.US_ENGLISH)
                .map(contentData -> new Translator(contentData));
    }

    /**
     * Gets a chunk of the leaderboards and surrounds it with some other helpful data. See also: {@link #getValLeaderboards(ValRegion, ValActId, long, long)} for a user friendly method.
     * @param region Which VALORANT region to get data from
     * @param actId Which act to get data from - will 404 from any act before Episode Two
     * @param startIndex Where in the leaderboard get players from
     * @param size How many players to get
     * @return a leaderboard object with a list of LeaderboardPlayerData
     */
    public Mono<LeaderboardData> getValLeaderboardChunk(ValRegion region, ValActId actId, long startIndex, long size) {
        if(startIndex < 0) return Mono.error(new IndexOutOfBoundsException("Start cannot be negative!"));
        if(size > 200) return Mono.error(new IndexOutOfBoundsException("Size cannot be greater than 200"));
        return getLeaderboardData(region, actId, startIndex, size);
    }

    public Flux<LeaderboardPlayerData> getValLeaderboards(ValRegion region, ValActId act, long start) {
        return getValLeaderboards(region, act, start, Long.MAX_VALUE);
    }

    public Flux<LeaderboardPlayerData> getValLeaderboards(ValRegion region, ValActId act, long start, long cap) {
        if(start < 0) return Flux.error(new IndexOutOfBoundsException("Start cannot be negative!"));
        //there is technically the possibility of the leaderboards shrinking while you process chunks causing you to hit an invalid index.
        return getLeaderboardData(region, act, 0L, 1L).flatMapMany(it ->
                recurValLeaderboards(region, act, start, Math.min(it.totalPlayers(), cap))
        );
    }

    private Flux<LeaderboardPlayerData> recurValLeaderboards(ValRegion region, ValActId act, long start, long cap) {
        long size = 200;
        if(start + 200 >= cap) { //if we are getting players at the very end of the leaderboards
            size = cap - start;
        }
        if(size <= 0) return Flux.empty();
        return Flux.concat(
                getLeaderboardData(region, act, start, size).flatMapMany(it -> Flux.fromIterable(it.players())),
                Flux.defer(() -> recurValLeaderboards(region, act, start + 200, cap))
        );
    }

    @Override
    public Mono<RiotAPIClient> test() {
        return this.getValStatus(valRegion).thenReturn(this);
    }
}
