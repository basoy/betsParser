package com.serg.bash.betsparser.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.annotation.PreDestroy;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
public class ProxyService {

    private static final int BUFFER_SIZE = 128 * 1024 * 1024;
    private final String apiKey;
    private final WebClient webClient;
    private final static ExchangeStrategies strategies;

    private static final Logger log = LogManager.getLogger(ProxyService.class);

    private final Scheduler scheduler;

    public ProxyService(@Value("${proxy-service.api-key}") String apiKey, Scheduler scheduler) {
        this.apiKey = apiKey;
        this.webClient = WebClient.builder()
                .baseUrl("https://api.zyte.com/v1/extract")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate")
                .defaultHeader(HttpHeaders.AUTHORIZATION, buildAuthHeader())
                .exchangeStrategies(strategies)
                .build();
        this.scheduler = scheduler;
    }

    static {
        strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(BUFFER_SIZE))
                .build();
    }

    public Mono<String> retrieveData(String url) {
        Map<String, Object> parameters = Map.of("url", url, "browserHtml", true);

        return webClient.post()
                .bodyValue(parameters)
                .retrieve()
                .bodyToMono(String.class)
                .map(apiResponse -> {
                    JsonObject jsonObject = JsonParser.parseString(apiResponse).getAsJsonObject();
                    return jsonObject.get("browserHtml").getAsString();
                })
                .doOnError(e -> log.error("Error retrieving data from {}: {}", url, e.getMessage()))
                .subscribeOn(scheduler);
    }

    private String buildAuthHeader() {
        String auth = apiKey + ":";
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encodedAuth;
    }

    @PreDestroy
    public void shutdownScheduler() {
        scheduler.dispose();
    }
}
