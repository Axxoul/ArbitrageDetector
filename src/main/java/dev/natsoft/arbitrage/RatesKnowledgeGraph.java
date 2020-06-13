package dev.natsoft.arbitrage;

import ch.obermuhlner.math.big.BigDecimalMath;
import dev.natsoft.arbitrage.model.Market;
import dev.natsoft.arbitrage.model.TradeChain;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
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
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.collect.Iterators.find;

public class RatesKnowledgeGraph {
    private static final Logger LOGGER = LoggerFactory.getLogger(RatesKnowledgeGraph.class);
    private static Graph<String, Market> exchangeRates;
    private final ReentrantLock graphLock;
    private final Subject<TradeChain> bestTradesStream;
    private TradeChain bestTrades;

    public RatesKnowledgeGraph() {
        exchangeRates = new DefaultDirectedWeightedGraph<>(Market.class);
        graphLock = new ReentrantLock();
        bestTradesStream = PublishSubject.create();
    }

    public TradeChain getBestTrades() {
        return bestTrades;
    }

    public void updateSecurity(Market rate) {
        graphLock.lock();

        // Fixme multigraph for many exchanges?
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
        market.setPrice(rate.getPrice());

        // https://medium.com/@anilpai/currency-arbitrage-using-bellman-ford-algorithm-8938dcea56ea
        // prepare weights for finding shortest path
        double weight = BigDecimalMath.log(rate.getRate(), new MathContext(100))
                .negate()
                .doubleValue();

        exchangeRates.setEdgeWeight(rate.from, rate.to, weight);
        graphLock.unlock();
    }

    public void detectArbitrage() {
        graphLock.lock();

        exchangeRates
                .vertexSet()
                .stream()
                .map(this::findArbitrageForSecurity)
                .map(TradeChain::new)
//                .filter(TradeChain::meetsThreshold)
                .max(Comparator.comparing(TradeChain::getProfitability))
                .ifPresent(this::report);

        graphLock.unlock();
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


    public void report(TradeChain tradeChain) {
        if (tradeChain.getProfitability().compareTo(new BigDecimal(0)) == 0) {
            return;
        }

        bestTrades = tradeChain;

        String profitability = Constants.DF.format(
                tradeChain.getProfitability()
                        .subtract(new BigDecimal(1))
                        .multiply(new BigDecimal(100))
        );

        String message = String.format("Arbitrage detected: profit: %s, trades: %s",
                profitability,
                tradeChain.ilustratePath()
        );

        LOGGER.warn(message);

        try {
            File file = new File("tmp/arbitrage.csv");
            FileWriter fr = new FileWriter(file, true);

            List<String> row = new LinkedList<>();
            row.add(Instant.now().toString());
            row.add(profitability);
            row.add(tradeChain.ilustratePath().replace(",", "|"));

            fr.write(String.join(",", row) + "\n");
            fr.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        bestTradesStream.onNext(tradeChain);
    }

    public void registerTickerStream(Observable<Boolean> ticks) {
        ticks.debounce(50, TimeUnit.MILLISECONDS)
                .subscribe(__ -> detectArbitrage());
    }

    public Observable<TradeChain> getBestTradesStream() {
        return bestTradesStream;
    }
}
