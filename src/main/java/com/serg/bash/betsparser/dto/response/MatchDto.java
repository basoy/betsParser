package com.serg.bash.betsparser.dto.response;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class MatchDto {
    private String sport;
    private String league;
    private String matchName;
    private ZonedDateTime matchStartTimeUtc;
    private String matchId;
    private List<MarketDto> markets;

    public MatchDto() {
    }

    public MatchDto(String sport, String league, String matchName, ZonedDateTime matchStartTimeUtc, String matchId, List<MarketDto> markets) {
        this.sport = sport;
        this.league = league;
        this.matchName = matchName;
        this.matchStartTimeUtc = matchStartTimeUtc;
        this.matchId = matchId;
        this.markets = markets;
    }

    public String getSport() {
        return sport;
    }

    public void setSport(String sport) {
        this.sport = sport;
    }

    public String getLeague() {
        return league;
    }

    public void setLeague(String league) {
        this.league = league;
    }

    public String getMatchName() {
        return matchName;
    }

    public void setMatchName(String matchName) {
        this.matchName = matchName;
    }

    public ZonedDateTime getMatchStartTimeUtc() {
        return matchStartTimeUtc;
    }

    public void setMatchStartTimeUtc(ZonedDateTime matchStartTimeUtc) {
        this.matchStartTimeUtc = matchStartTimeUtc;
    }

    public String getMatchId() {
        return matchId;
    }

    public void setMatchId(String matchId) {
        this.matchId = matchId;
    }

    public List<MarketDto> getMarkets() {
        return markets;
    }

    public void setMarkets(List<MarketDto> markets) {
        this.markets = markets;
    }

    @Override
    public String toString() {
        String header = String.format("%s, %s", sport, league);

        String matchInfo = String.format("%s - %s, %s",
                matchName,
                matchStartTimeUtc != null ? matchStartTimeUtc.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")) : "N/A",
                matchId
        );

        String marketDetails = (markets != null ? markets.stream()
                .map(market -> {
                    String marketName = market.getMarketName();
                    String oddsInfo = market.getOutcomes() != null ? market.getOutcomes().stream()
                            .map(outcome -> String.format("%s, %.1f, %s", outcome.getOutcomeName(), outcome.getCoefficient(), outcome.getOutcomeId()))
                            .collect(Collectors.joining("\n")) : "No outcomes available";
                    return String.format("%s\n%s", marketName, oddsInfo);
                })
                .collect(Collectors.joining("\n")) : "No markets available");

        return String.format("%s\n%s\n%s", header, matchInfo, marketDetails);
    }

}
