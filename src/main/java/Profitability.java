import org.jgrapht.Graph;
import org.jgrapht.GraphPath;

import java.math.BigDecimal;
import java.text.NumberFormat;

public class Profitability {
    private static final BigDecimal PROFITABILITY_THRESHOLD = new BigDecimal(5);
    private final GraphPath<String, Market> path;
    private final Graph<String, Market> graph;


    public Profitability(Graph<String, Market> graph, GraphPath<String, Market> path) {
        this.graph = graph;
        this.path = path;
    }

    public void report() {
        System.out.format("Arbitrage detected: profit: %s, trades: %s\n", this, this.path);
    }

    public BigDecimal getProfitUSD() {
        // TODO get estimate profit in usd from the current XXX:USD exchange rate
        BigDecimal tradeProfitUSD = new BigDecimal(500);
        BigDecimal feesUSD = this.getExpectedFeesUSD();

        return tradeProfitUSD.subtract(feesUSD);
    }

    public BigDecimal getExpectedFeesUSD() {
        // TODO
        return new BigDecimal(1);
    }

    public boolean isProfitable(Graph<String, Market> graph) {
        BigDecimal finalProfit = getProfitUSD();

        return finalProfit.compareTo(PROFITABILITY_THRESHOLD) > 0;
    }

    @Override
    public String toString() {
        return "Profitability{" + NumberFormat.getCurrencyInstance().format(this.getProfitUSD()) + "}";
    }
}
