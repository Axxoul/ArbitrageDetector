package dev.natsoft.arbitrage.model;

import org.jgrapht.GraphPath;

import java.math.BigDecimal;
import java.util.Objects;

public class Profitability {
    public static final BigDecimal PROFITABILITY_THRESHOLD = new BigDecimal("1.001");
    public final GraphPath<String, Market> path;
    private BigDecimal profitability;

    public Profitability(GraphPath<String, Market> path) {
        this.path = path;
    }

    public boolean meetsThreshold() {
        BigDecimal profitability = getProfitability();
        return Objects.requireNonNull(profitability)
                .compareTo(PROFITABILITY_THRESHOLD) > 0;
    }

    public BigDecimal getProfitability() {
        if (profitability == null) {
            profitability = path.getEdgeList()
                    .stream()
                    .map(Market::getRate)
                    .reduce(BigDecimal::multiply)
                    .orElse(new BigDecimal(0));
        }
        return profitability;
    }


    @Override
    public String toString() {
        return "dev.natsoft.arbitrage.model.Profitability{profit: " + this.getProfitability() + ", path:" + this.path + "}";
    }
}
