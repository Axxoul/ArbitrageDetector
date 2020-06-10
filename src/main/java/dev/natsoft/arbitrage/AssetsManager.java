package dev.natsoft.arbitrage;

import dev.natsoft.arbitrage.exchanges.Exchange;
import dev.natsoft.arbitrage.model.TradeChain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;

/*
Facade for accessing all information about the current state of assets and orders.
*/
public class AssetsManager implements BestTradeSubscriber {
    private final ReentrantLock tradeLock;

    List<Exchange> exchanges;
    RatesKnowledgeGraph ratesKnowledgeGraph;

    public AssetsManager(RatesKnowledgeGraph ratesKnowledgeGraph) {
        this.ratesKnowledgeGraph = ratesKnowledgeGraph;
        this.exchanges = new ArrayList<>();
        this.tradeLock = new ReentrantLock();

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateAssetsStatus();
            }
        }, 0, 5000);
    }

    private void updateAssetsStatus() {
        exchanges.forEach(Exchange::updateAssetsStatus);
    }

    public void startManaging(Exchange exchange) {
        exchanges.add(exchange);
    }

    @Override
    public void receiveBestTrade(TradeChain tradeChain) {
        if (tradeChain.getProfitability().compareTo(new BigDecimal("1.001")) < 0) return;
        tradeLock.lock();

        new TradeExecutor().execute(tradeChain);

        // we only want one trade
//        tradeLock.unlock();
    }
}
