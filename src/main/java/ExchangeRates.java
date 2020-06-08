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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.*;

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
//                .filter(Profitability::meetsThreshold)
                .max(Comparator.comparing(Profitability::getProfitability))
                .ifPresent(this::report);
    }

    public void report(Profitability profit) {
        if (profit.getProfitability().compareTo(new BigDecimal(0)) >= 0) {
            return;
        }

        String message = String.format("Arbitrage detected: profit: %s, trades: %s",
                Constants.DF.format(profit.getProfitability()),
                ilustratePath(profit.path)
        );

        LOGGER.warn(message);

        try {

            File file = new File("target/arbitrage.csv");
            FileWriter fr = new FileWriter(file, true);

            List<String> row = new LinkedList<>();
            row.add(Instant.now().toString());
            row.add(Constants.DF.format(profit.getProfitability()));
            row.add(profit.path.toString());

            fr.write(String.join(",", row) + "\n");
            fr.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String ilustratePath(GraphPath<String, Market> path) {
        Object[] instruments = path.getEdgeList()
                .stream()
                .map(edge -> String.format("[%s,%s]", edge.from, edge.to))
                .toArray();

        return String.format("{Trades: number: %s, instruments: %s}",
                path.getLength(),
                Arrays.toString(instruments)
        );
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
