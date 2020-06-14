package dev.natsoft.arbitrage;

import ch.obermuhlner.math.big.BigDecimalMath;
import dev.natsoft.arbitrage.model.Market;
import dev.natsoft.arbitrage.model.TradeChain;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.cycle.SzwarcfiterLauerSimpleCycles;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.GraphWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.google.common.collect.Iterators.find;

public class RatesKnowledgeGraph {
    private static final Logger LOGGER = LoggerFactory.getLogger(RatesKnowledgeGraph.class);
    private static Graph<String, Market> exchangeRates;
    private final ReentrantLock graphLock;
    private Subject<TradeChain> bestTradesStream;

    public RatesKnowledgeGraph() {
        exchangeRates = new DefaultDirectedWeightedGraph<>(Market.class);
        graphLock = new ReentrantLock();
        bestTradesStream = PublishSubject.create();
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

    public Optional<TradeChain> detectArbitrage(String tickerSymbol) {
        graphLock.lock();

        Optional<TradeChain> tradeChain = findArbitrageForSecurity().stream()
//        exchangeRates
//                .vertexSet()
//                .stream()
//                .map(this::findArbitrageForSecurity)
//                .flatMap(List::stream)
                .map(TradeChain::new)
                .filter(tc -> tc.ilustratePath().contains("USD"))
//                .filter(TradeChain::meetsThreshold)
                .max(Comparator.comparing(TradeChain::getProfitability));
//                .ifPresent(this::report);


        graphLock.unlock();

        return tradeChain;
    }

    private List<GraphPath<String, Market>> findArbitrageForSecurity() {
//        try {
//            return new BellmanFordShortestPath<>(exchangeRates)
//                    .getPath(s, s);
//        } catch (NegativeCycleDetectedException e) {
//            // This is what we are looking for.
//            // https://medium.com/@anilpai/currency-arbitrage-using-bellman-ford-algorithm-8938dcea56ea
//            return (GraphPath<String, Market>) e.getCycle();
//        }

        // TODO compare with BellmanFord and check out other simple cycle algos
        List<GraphPath<String, Market>> paths = new SzwarcfiterLauerSimpleCycles<>(exchangeRates)
                .findSimpleCycles()
                .stream()
                .filter(c -> c.size() >= 3)
                .filter(c -> c.size() <= 4)
                .filter(c -> !c.contains("LEO"))
                .map(this::createPathFromVList)
                .collect(Collectors.toList());


        return paths;
    }

    private GraphPath<String, Market> createPathFromVList(List<String> cycle) {
        cycle.add(cycle.get(0));
        return new GraphWalk<>(exchangeRates, cycle, 10);
    }


    public Optional<TradeChain> report(TradeChain tradeChain) {
        if (tradeChain.getProfitability().compareTo(new BigDecimal(0)) == 0) {
            LOGGER.info("Trade chain has 0 profitablity: {}", tradeChain.ilustratePath());
            return Optional.empty();
        }

        String profitability = Constants.DF.format(
                tradeChain.getProfitability()
                        .subtract(new BigDecimal(1))
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

        return Optional.of(tradeChain);
    }

    public void registerTickerStream(Flowable<String> ticks) {
        ticks
                .debounce(100, TimeUnit.MILLISECONDS)
                .onBackpressureDrop()
                .observeOn(Schedulers.io(), true, 1)
                .mapOptional(this::detectArbitrage)
                .mapOptional(this::report)
                .subscribe(
                        bestTradesStream::onNext,
                        e -> LOGGER.error(e.getMessage(), e)
                );
    }

    public Observable<TradeChain> getBestTradesStream() {
        return bestTradesStream;
    }
}
