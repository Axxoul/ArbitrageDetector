package dev.natsoft.arbitrage.exchanges;

import dev.natsoft.arbitrage.RatesKnowledgeGraph;
import dev.natsoft.arbitrage.model.Market;

import java.math.BigDecimal;

public interface Exchange {
    void startUpdating(RatesKnowledgeGraph ratesKnowledgeGraph);

    BigDecimal getTakerFee();

    void updateAssetsStatus();

    void trade(Market market, BigDecimal amount);
}
