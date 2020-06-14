package dev.natsoft.arbitrage.model;

import org.jgrapht.GraphPath;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Objects;

public class TradeChain {
    public static final BigDecimal PROFITABILITY_THRESHOLD = new BigDecimal("1.001");
    public final GraphPath<String, Market> path;
    private BigDecimal profitability;

    public TradeChain(GraphPath<String, Market> path) {
        this.path = path;
    }

    public boolean meetsThreshold() {
        BigDecimal profitability = getProfitability();
        return Objects.requireNonNull(profitability)
                .compareTo(PROFITABILITY_THRESHOLD) > 0;
    }

    /**
     * @return final profitability after fees as a multiplier (eg. 1.001)
     */
    public BigDecimal getProfitability() {
//        if (profitability == null) {
        profitability = path.getEdgeList()
                .stream()
                .map(Market::getRateWithFees)
                .reduce(BigDecimal::multiply)
                .orElse(new BigDecimal(0));
//        }
        return profitability;
    }


    @Override
    public String toString() {
        return "TradeChain{profit: " + this.getProfitability() + ", path:" + this.path + "}";
    }

    public String ilustratePath() {
        Object[] instruments = path.getEdgeList()
                .stream()
                .map(edge -> String.format("[%s,%s]", edge.from, edge.to))
                .toArray();

        return String.format("{Trades: number: %s, instruments: %s}",
                path.getLength(),
                Arrays.toString(instruments)
        );
    }
}
