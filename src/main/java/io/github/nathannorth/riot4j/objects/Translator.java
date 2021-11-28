package io.github.nathannorth.riot4j.objects;

import io.github.nathannorth.riot4j.json.valContent.ContentData;
import io.github.nathannorth.riot4j.json.valContent.ContentItemData;
import io.github.nathannorth.riot4j.json.valMatch.MatchInfoData;

import java.util.HashMap;

/**
 * A translator parses data in the content endpoint to help turn outputs from other endpoints like the match endpoint into human-readable information
 */
public class Translator {
    private final ContentData data;
    private final HashMap<String, String> maps = new HashMap<>();
    private final HashMap<String, String> gameModes = new HashMap<>();

    /**
     * Instantiate a new Translator. A  translator may become out of date given a multi-week lifespan as it does not make any api call itself
     * @param data
     */
    public Translator(ContentData data) {
        this.data = data;

        for(ContentItemData map: data.maps()) {
            if(map.assetPath().isPresent()) { //ignore random api noise
                maps.put(map.assetPath().get(), map.name());
            }
        }
        for(ContentItemData mode: data.gameModes()) {
            gameModes.put(mode.assetPath().get(), mode.name());
        }
    }

    public String getMapName(String assetName) {
        return maps.get(assetName);
    }

    /**
     * A game TYPE is not the same as a game MODE or a game QUEUE. The queue represents the distinction between rated and unrated modes. The mode represents whether it is bomb mode or deathmatch, escalation, etc. The type is human readable description that provides only non-implied information.
     * @param matchInfoData
     * @return What a user would expect a description for a mode to be.
     */
    public String getGameTypeHuman(MatchInfoData matchInfoData) {
        String prefix = "";
        if(matchInfoData.queueId().equals(""))
            prefix = "Custom ";

        String suffix = "";
        String gameMode = gameModes.get(matchInfoData.gameMode());
        if(!gameMode.equals("Standard"))
            suffix = gameMode;

        String base = "";
        if(matchInfoData.queueId().equals("unrated")) base = "Unrated";
        if(matchInfoData.queueId().equals("competitive")) base = "Competitive";

        return prefix + base + suffix;
    }
}
