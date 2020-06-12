package dev.natsoft.arbitrage;

import dev.natsoft.arbitrage.Reports.TradeReport;
import dev.natsoft.arbitrage.exchanges.Exchange;
import dev.natsoft.arbitrage.model.TradeChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Facade for accessing all information about the current state of assets and orders.
 */
public class AssetsManager implements BestTradeSubscriber {
    private static final Logger LOGGER = LoggerFactory.getLogger(AssetsManager.class);
    private final ReentrantLock tradeLock;
    List<Exchange> exchanges;
    RatesKnowledgeGraph ratesKnowledgeGraph;
    private int tradesLeft;

    public AssetsManager(RatesKnowledgeGraph ratesKnowledgeGraph) {
        this.ratesKnowledgeGraph = ratesKnowledgeGraph;
        this.exchanges = new ArrayList<>();
        this.tradeLock = new ReentrantLock();
        this.tradesLeft = 30;
    }

    public void startManaging(Exchange exchange) {
        exchanges.add(exchange);
    }

    @Override
    public void receiveBestTrade(TradeChain tradeChain) {
        if (tradesLeft == 0)
            System.exit(0);

        if (tradeChain.getProfitability().compareTo(new BigDecimal("0.997")) < 0)
            return;

        if (!tradeChain.ilustratePath().contains("USD"))
            return; // Only if contains usd

        try {
            SimpleMarketTradeExecutor te = new SimpleMarketTradeExecutor(tradeChain);
            te.execute();
            reportTrades(te);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }


        tradesLeft -= 1;

    }

    private void reportTrades(SimpleMarketTradeExecutor te) throws IOException {
        new TradeReport(te)
                .saveReport()
                .logReport();
    }
}
