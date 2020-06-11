package dev.natsoft.arbitrage;

import dev.natsoft.arbitrage.exchanges.Exchange;
import dev.natsoft.arbitrage.model.TradeChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/*
Facade for accessing all information about the current state of assets and orders.
*/
public class AssetsManager implements BestTradeSubscriber {
    private static final Logger LOGGER = LoggerFactory.getLogger(AssetsManager.class);
    private final ReentrantLock tradeLock;

    List<Exchange> exchanges;
    RatesKnowledgeGraph ratesKnowledgeGraph;

    public AssetsManager(RatesKnowledgeGraph ratesKnowledgeGraph) {
        this.ratesKnowledgeGraph = ratesKnowledgeGraph;
        this.exchanges = new ArrayList<>();
        this.tradeLock = new ReentrantLock();
    }

    public void startManaging(Exchange exchange) {
        exchanges.add(exchange);
    }

    @Override
    public void receiveBestTrade(TradeChain tradeChain) {
        if (tradeChain.getProfitability().compareTo(new BigDecimal("1.001")) < 0) return;
        LOGGER.info("Locking new trades");
        tradeLock.lock();

        TradeExecutor te = new TradeExecutor();
        te.execute(tradeChain);

        reportTrades(te);

        // we only want one trade for now
//        tradeLock.unlock();
    }

    private void reportTrades(TradeExecutor te) {
        LOGGER.info("=== TRADE REPORT ===");
        LOGGER.info("= Executor Class: {}", te.getClass());
        te.getExecutedTrades().forEach(trade -> {
            LOGGER.info("= Trade: {}", trade);
        });
        LOGGER.info("=== END REPORT ===");
    }
}
