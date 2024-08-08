package com.serg.bash.betsparser.dto.response;

public class OutcomeDto {
    private String outcomeName;
    private double coefficient;
    private String outcomeId;

    public OutcomeDto() {}

    public OutcomeDto(String outcomeName, double coefficient, String outcomeId) {
        this.outcomeName = outcomeName;
        this.coefficient = coefficient;
        this.outcomeId = outcomeId;
    }

    public String getOutcomeName() {
        return outcomeName;
    }

    public void setOutcomeName(String outcomeName) {
        this.outcomeName = outcomeName;
    }

    public double getCoefficient() {
        return coefficient;
    }

    public void setCoefficient(double coefficient) {
        this.coefficient = coefficient;
    }

    public String getOutcomeId() {
        return outcomeId;
    }

    public void setOutcomeId(String outcomeId) {
        this.outcomeId = outcomeId;
    }
}
