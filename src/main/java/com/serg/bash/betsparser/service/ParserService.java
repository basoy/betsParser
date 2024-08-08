package com.serg.bash.betsparser.service;

import com.serg.bash.betsparser.dto.response.MarketDto;
import com.serg.bash.betsparser.dto.response.MatchDto;
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

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

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
        List<MatchDto> result = new ArrayList<>();
        return matchesOfLeagues
                .flatMap(html -> {
                    Document doc = Jsoup.parse(html);
                    Elements elements = doc.select("[class^=crumb__title]");
                    String elementsDay = doc.select("[class^=headline-info__day]").text();
                    String elementsTime = doc.select("[class^=headline-info__time]").text();
                    String id = null;
                    String regex = "\\b\\d{16}\\b";
                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(html);
                    Element parentContainer = doc.select("div:has(> section)").first();

                    if (matcher.find()) {
                        id =  matcher.group();
                    }

                    MatchDto matchDto = new MatchDto(
                            elements.get(0).text(),
                            elements.get(1).text(),
                            elements.get(3).text(),
                            parseDateTime(elementsDay, elementsTime),
                            id,
                            List.of(new MarketDto(null, emptyList()))
                    );
                    result.add(matchDto);
                    System.out.println(result);

                    return Flux.just(result);
                });
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
            // Добавляем текущий год к строке даты
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

        // Создаем ZonedDateTime в UTC
        ZonedDateTime zonedDateTime = ZonedDateTime.of(localDate, localTime, ZoneId.of("UTC"));
        return zonedDateTime;
    }
}

