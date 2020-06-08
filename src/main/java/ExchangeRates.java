import ch.obermuhlner.math.big.BigDecimalMath;
import model.Market;
import model.Profitability;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.BellmanFordShortestPath;
import org.jgrapht.alg.shortestpath.NegativeCycleDetectedException;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.MathContext;
import java.util.Comparator;
import java.util.Set;

import static com.google.common.collect.Iterators.find;

public class ExchangeRates {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExchangeRates.class);
    private static Graph<String, Market> exchangeRates;

    public ExchangeRates() {
        exchangeRates = new DefaultDirectedWeightedGraph<>(Market.class);
    }


    public void detectArbitrage() {
        exchangeRates
                .vertexSet()
                .stream()
                .map(this::findArbitrageForSecurity)
                .map(Profitability::new)
                .filter(Profitability::meetsThreshold)
                .max(Comparator.comparing(Profitability::getProfitability))
                .ifPresent(this::report);
    }

    public void report(Profitability profit) {
        LOGGER.warn(String.format("Arbitrage detected: profit: %s, trades: %s", profit.getProfitability(), profit.path));
    }

    private GraphPath<String, Market> findArbitrageForSecurity(String s) {
        try {
            return new BellmanFordShortestPath<>(exchangeRates)
                    .getPath(s, s);
        } catch (NegativeCycleDetectedException e) {
            // This is what we are looking for.
            // https://medium.com/@anilpai/currency-arbitrage-using-bellman-ford-algorithm-8938dcea56ea
            return (GraphPath<String, Market>) e.getCycle();
        }
    }

    public void updateSecurity(Market rate) {
        Set<Market> markets = exchangeRates.getAllEdges(rate.from, rate.to);

        Market market = null;
        if (markets != null) {
            market = find(
                    markets.iterator(),
                    (Market mark) -> {
                        assert mark != null;
                        return mark.exchange.equals(rate.exchange);
                    },
                    null
            );
        }

        if (market == null) {
            market = new Market(rate.from, rate.to, rate.exchange);

            if (!exchangeRates.containsVertex(rate.from)) exchangeRates.addVertex(rate.from);
            if (!exchangeRates.containsVertex(rate.to)) exchangeRates.addVertex(rate.to);

            exchangeRates.addEdge(rate.from, rate.to, market);
        }

        market.setRate(rate.getRate());

        // https://medium.com/@anilpai/currency-arbitrage-using-bellman-ford-algorithm-8938dcea56ea
        // prepare weights for finding shortest path
        double weight = BigDecimalMath.log(rate.getRate(), new MathContext(100))
                .negate()
                .doubleValue();

        exchangeRates.setEdgeWeight(rate.from, rate.to, weight);
    }
}
