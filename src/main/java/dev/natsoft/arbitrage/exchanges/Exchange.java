package dev.natsoft.arbitrage.exchanges;

import com.github.jnidzwetzki.bitfinex.v2.entity.BitfinexExecutedTrade;
import dev.natsoft.arbitrage.RatesKnowledgeGraph;
import dev.natsoft.arbitrage.model.Market;

import java.math.BigDecimal;

public interface Exchange {
    void startUpdating(RatesKnowledgeGraph ratesKnowledgeGraph);

    BigDecimal getTakerFee();

    /**
     * Should be blocking until the trade comes through
     * @return executed trade information
     */
    BitfinexExecutedTrade trade(Market market, BigDecimal amount);
}
