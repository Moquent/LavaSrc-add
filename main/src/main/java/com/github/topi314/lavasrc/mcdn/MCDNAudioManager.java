package com.github.topi314.lavasrc.mcdn;

import com.github.topi314.lavasearch.AudioSearchManager;
import com.github.topi314.lavasearch.result.AudioSearchResult;
import com.github.topi314.lavasearch.result.BasicAudioSearchResult;
import com.github.topi314.lavasrc.LavaSrcTools;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class MCDNAudioManager implements HttpConfigurable, AudioSourceManager, AudioSearchManager {
    public static final String SEARCH_PREFIX = "mcdnsearch:";
    public static final String ISRC_PREFIX = "mcdnisrc:";
    public static final Set<AudioSearchResult.Type> SEARCH_TYPES = Set.of(AudioSearchResult.Type.TRACK);
    private String baseUrl;
    private String apiKey;
    private String userAgent = "Lavasrc";

    private HttpInterfaceManager httpInterfaceManager;
    private static final Logger log = LoggerFactory.getLogger(MCDNAudioManager.class);

    public MCDNAudioManager(String apiKey, String baseUrl, @Nullable String userAgent) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        if (userAgent != null) {
            this.userAgent = userAgent;
        }
        this.httpInterfaceManager = HttpClientTools.createCookielessThreadLocalManager();
    }

    @Override
    public String getSourceName() {
        return "mcdn";
    }

    @Override
    public @Nullable AudioSearchResult loadSearch(@NotNull String s, @NotNull Set<AudioSearchResult.Type> set) {
        if (!set.isEmpty() && !set.stream().allMatch(it -> it.equals(AudioSearchResult.Type.TRACK))) {
            throw new RuntimeException(getSourceName() + " can only search tracks");
        }
        try {
            return getSearchResults(s);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private AudioSearchResult getSearchResults(String s) throws IOException {
        var json = getJson(getSearchUrl(s));
        var tracks = parseTracks(json);
        return new BasicAudioSearchResult(tracks, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager audioPlayerManager, AudioReference audioReference) {
        return this.loadItem(audioReference.identifier);
    }

    @Override
    public boolean isTrackEncodable(AudioTrack audioTrack) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack audioTrack, DataOutput dataOutput) throws IOException {
        // Nothing to do
    }

    public AudioItem loadItem(String identifier) {
        try {
            if (identifier.startsWith(SEARCH_PREFIX)) {
                return this.getSearch(identifier.substring(SEARCH_PREFIX.length()));
            }

            if (identifier.startsWith(ISRC_PREFIX)) {
                return this.getTrackByISRC(identifier.substring(ISRC_PREFIX.length()));
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private AudioItem getTrackByISRC(String isrc) throws IOException {
        var json = this.getJson(getISRCSearchUrl(URLEncoder.encode(isrc, StandardCharsets.UTF_8)));
        if (json == null || json.values().isEmpty() || json.index(0).get("id").isNull()) {
            return AudioReference.NO_TRACK;
        }
        
        // For consistency with CustomSrc, return a single track
        return this.parseTrack(json.index(0));
    }

    private AudioItem getSearch(String query) throws IOException {
        var json = this.getJson(getSearchUrl(URLEncoder.encode(query, StandardCharsets.UTF_8)));
        if (json == null || json.values().isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        return new BasicAudioPlaylist("MCDN Search: " + query, this.parseTracks(json), null, true);
    }

    private AudioTrack parseTrack(JsonBrowser json) {
        var id = json.get("id").text();
        var track = new AudioTrackInfo(
            json.get("title").text(),
            json.get("artist").text(),
            json.get("duration").asLong(0),  // Already in milliseconds in our API
            id,
            false,
            json.get("versions").index(0).get("url").text(),
            json.get("picture").text(),
            json.get("isrc").index(0).text()
        );
        return new MCDNAudioTrack(track, this);
    }

    private List<AudioTrack> parseTracks(JsonBrowser json) {
        var tracks = new ArrayList<AudioTrack>();
        for (var track : json.values()) {
            tracks.add(this.parseTrack(track));
        }
        return tracks;
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo audioTrackInfo, DataInput dataInput) throws IOException {
        return new MCDNAudioTrack(audioTrackInfo, this);
    }

    @Override
    public void shutdown() {
        try {
            this.httpInterfaceManager.close();
        } catch (IOException e) {
            log.error("Failed to close HTTP interface manager", e);
        }
    }

    @Override
    public void configureRequests(Function<RequestConfig, RequestConfig> function) {
        this.httpInterfaceManager.configureRequests(function);
    }

    @Override
    public void configureBuilder(Consumer<HttpClientBuilder> consumer) {
        this.httpInterfaceManager.configureBuilder(consumer);
    }

    public JsonBrowser getJson(String uri) throws IOException {
        var request = new HttpGet(uri);
        request.setHeader("Accept", "application/json");
        request.setHeader("User-Agent", userAgent);
        request.setHeader("Authorization", "Bearer " + apiKey);
        return LavaSrcTools.fetchResponseAsJson(this.httpInterfaceManager.getInterface(), request);
    }

    public String getISRCSearchUrl(String isrc) {
        return baseUrl + "/isrc/" + isrc;
    }

    public String getSearchUrl(String search) {
        return baseUrl + "/search?q=" + search;
    }

    public HttpInterface getHttpInterface() {
        return this.httpInterfaceManager.getInterface();
    }
} 