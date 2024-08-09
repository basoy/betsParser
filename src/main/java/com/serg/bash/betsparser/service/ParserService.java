package com.serg.bash.betsparser.service;

import com.serg.bash.betsparser.dto.response.MarketDto;
import com.serg.bash.betsparser.dto.response.MatchDto;
import com.serg.bash.betsparser.dto.response.OutcomeDto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service

public class ParserService {

    private static final Logger log = LogManager.getLogger(ParserService.class);

    private static final Pattern pattern = Pattern.compile("^/bets/[^/]+$");

    private final ProxyService proxyService;

    private final Scheduler scheduler;

    private static final String BASE_URL = "https://leonbets.com";

    @Autowired
    public ParserService(ProxyService proxyService, Scheduler scheduler) {
        this.proxyService = proxyService;
        this.scheduler = scheduler;
    }

    public Flux<List<MatchDto>> parseStatisticsFromMatches(Flux<String> matchesOfLeagues) {
        return matchesOfLeagues
                .flatMap(html -> {
                    List<MatchDto> result = new ArrayList<>();
                    Document doc = Jsoup.parse(html);
                    Elements elements = doc.select("[class^=crumb__title]");
                    String elementsDay = doc.select("[class^=headline-info__day]").text();
                    String elementsTime = doc.select("[class^=headline-info__time]").text();
                    String id = null;
                    String regex = "\\b\\d{16}\\b";
                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(html);

                    if (matcher.find()) {
                        id = matcher.group();
                    }

                    Elements marketContainers = doc.select("div[class*='market-group']");

                    List<MarketDto> markets = new ArrayList<>();
                    for (int i = 0; i < marketContainers.select("div[class*='market-group-title']").size(); i++) {
                        Elements outcomeElements = marketContainers.get(i).select("button");
                        String marketName = marketContainers.select("div[class*='market-group-title']").get(i).text();
                        List<OutcomeDto> outcomes = new ArrayList<>();
                        for (Element outcomeElement : outcomeElements) {
                            String left = outcomeElement.select("span[class*='sportline-group-market-runner__coefficient--left']").text();
                            String right = outcomeElement.select("span[class*='sportline-group-market-runner__coefficient--right']").text();

                            if (!left.isEmpty() && !right.isEmpty()) {
                                outcomes.add(new OutcomeDto(left, parseDouble(right), null));//fixme didn't find id like 1970325384409972
                            }
                        }
                        if (!outcomes.isEmpty()) {
                            MarketDto marketDto = new MarketDto(marketName, outcomes);
                            markets.add(marketDto);
                        }

                    }

                    MatchDto matchDto = new MatchDto(
                            elements.get(0).text(),
                            elements.get(1).text(),
                            elements.get(3).text(),
                            parseDateTime(elementsDay, elementsTime),
                            id,
                            markets
                    );

                    result.add(matchDto);
                    System.out.println(result);

                    return Flux.just(result);
                })
                .collectList()
                .flatMapMany(Flux::fromIterable);
    }

    public Flux<String> retrieveStatisticsFromMatches(Flux<String> matchesOfLeagues) {
        return matchesOfLeagues
                .flatMap(proxyService::retrieveData)
                .subscribeOn(scheduler)
                .doOnError(e -> log.error("Error processing sports: {}", e.getMessage()));
    }

    private Flux<String> parseMatchesFromLeagueUrl(String leagueUrl) {
        return proxyService.retrieveData(leagueUrl)
                .flatMapMany(html -> {
                    Document doc = Jsoup.parse(html);

                    Elements matches = doc.select("div[class*='sportline-events-list']");
                    Set<String> firstTwoMatches = matches.stream()
                            .flatMap(div -> div.select("a[href]").stream())
                            .map(matchElement -> matchElement.attr("href"))
                            .limit(2)
                            .map(href -> BASE_URL + href)
                            .collect(Collectors.toSet());
                    return Flux.fromIterable(firstTwoMatches).subscribeOn(scheduler);
                })
                .subscribeOn(scheduler)
                .doOnError(e -> System.err.println("Error processing HTML: " + e.getMessage()));
    }

    public Flux<String> parseMatchesInTopLeagues(Flux<String> htmlFlux) {
        return htmlFlux
                .flatMap(html -> {
                    Document doc = Jsoup.parse(html);

                    Elements topLeaguesDivs = doc.select("div[class*='leagues-list--top']");
                    List<String> topLeagueUrls = topLeaguesDivs.stream()
                            .flatMap(div -> div.select("a[href]").stream())
                            .map(link -> link.attr("href"))
                            .map(href -> BASE_URL + href)
                            .limit(2)
                            .toList();

                    if (topLeagueUrls.isEmpty()) {
                        return Flux.empty();
                    }


                    return Flux.fromIterable(topLeagueUrls)
                            .flatMap(this::parseMatchesFromLeagueUrl)
                            .subscribeOn(scheduler);
                })
                .doOnError(e -> System.err.println("Error processing HTML: " + e.getMessage()));
    }

    public Flux<String> retrieveAllSports(Flux<String> sportsFlux) {
        return sportsFlux
                .flatMap(sport -> proxyService.retrieveData(BASE_URL + sport))
                .subscribeOn(scheduler)
                .doOnError(e -> log.error("Error processing sports: {}", e.getMessage()));
    }

    public Flux<String> parseAllSports() {
        return proxyService.retrieveData(BASE_URL)
                .flatMapMany(html -> {
                    Document doc = Jsoup.parse(html);
                    Elements links = doc.select("a[href]");
                    return Flux.fromIterable(links)
                            .map(link -> link.attr("href"))
                            .distinct()
                            .filter(href -> pattern.matcher(href).matches())
                            .take(5);//fixme: reduced for speed, before commit-need delete
                })
                .subscribeOn(scheduler)
                .doOnError(e -> log.error("Error retrieving or processing sports data: {}", e.getMessage()));
    }

    public Flux<List<MatchDto>> parse() {
        return parseStatisticsFromMatches(
                retrieveStatisticsFromMatches(
                        parseMatchesInTopLeagues(
                                retrieveAllSports(
                                        parseAllSports()
                                )
                        )
                )
        );
    }

    public static ZonedDateTime parseDateTime(String dateStr, String timeStr) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMMM d yyyy", Locale.ENGLISH);
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        LocalDate localDate;
        try {
            String dateStrWithYear = dateStr + " " + LocalDate.now().getYear();
            localDate = LocalDate.parse(dateStrWithYear, dateFormatter);
        } catch (DateTimeParseException e) {
            System.err.println("Error parsing date: " + e.getMessage());
            return null;
        }

        LocalTime localTime;
        try {
            localTime = LocalTime.parse(timeStr, timeFormatter);
        } catch (DateTimeParseException e) {
            System.err.println("Error parsing time: " + e.getMessage());
            return null;
        }

        return ZonedDateTime.of(localDate, localTime, ZoneId.of("UTC"));
    }

    public static Double parseDouble(String s) {
        if (s == null) {
            return null;
        }
        Double result = null;
        try {
            result = Double.valueOf(s);
        } catch (NumberFormatException ignored) {
        }
        return result;
    }
}

