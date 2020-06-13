package dev.natsoft.arbitrage.exchanges;

import com.github.jnidzwetzki.bitfinex.v2.entity.BitfinexSubmittedOrder;
import dev.natsoft.arbitrage.RatesKnowledgeGraph;
import dev.natsoft.arbitrage.model.Market;
import io.reactivex.rxjava3.core.Observable;

import java.math.BigDecimal;

public interface Exchange {
    void startUpdating(RatesKnowledgeGraph ratesKnowledgeGraph);

    BigDecimal getTakerFee();

    /**
     * Should be blocking until the trade comes through
     *
     * @return executed trade information
     */
    BitfinexSubmittedOrder trade(Market market, BigDecimal amount) throws Exception;

    BigDecimal getUSDBalance();

    Observable<BigDecimal> getUSDUpdatesStream();
}
