package com.serg.bash.betsparser.dto.response;

import java.util.List;

public class MarketDto {
    private String marketName;
    private List<OutcomeDto> outcomes;

    public MarketDto() {}

    public MarketDto(String marketName, List<OutcomeDto> outcomes) {
        this.marketName = marketName;
        this.outcomes = outcomes;
    }

    public String getMarketName() {
        return marketName;
    }

    public void setMarketName(String marketName) {
        this.marketName = marketName;
    }

    public List<OutcomeDto> getOutcomes() {
        return outcomes;
    }

    public void setOutcomes(List<OutcomeDto> outcomes) {
        this.outcomes = outcomes;
    }
}
