package dev.natsoft.arbitrage;

import com.github.jnidzwetzki.bitfinex.v2.entity.BitfinexSubmittedOrder;
import dev.natsoft.arbitrage.model.Market;
import dev.natsoft.arbitrage.model.TradeChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SimpleMarketTradeExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleMarketTradeExecutor.class);
    public final BigDecimal firstUSDAmount = new BigDecimal(30);
    public final TradeChain tradeChain;
    private final List<BitfinexSubmittedOrder> executedTrades;
    public boolean executed;
    private BigDecimal initialUSD;
    private BigDecimal finalUSD;

    public SimpleMarketTradeExecutor(TradeChain tradeChain) {
        this.tradeChain = tradeChain;
        this.executedTrades = new ArrayList<BitfinexSubmittedOrder>();
        executed = false;
    }

    public BigDecimal getInitialUSD() {
        return initialUSD;
    }

    public BigDecimal getFinalUSD() {
        return finalUSD;
    }

    public Optional<SimpleMarketTradeExecutor> execute() throws Exception {
        LOGGER.info("Staring trades: {}", tradeChain.ilustratePath());

        List<Market> markets = tradeChain.path.getEdgeList();
        initialUSD = markets.get(0).exchange.getUSDBalance();
        LOGGER.info("Initial USD: {}", Constants.DF.format(initialUSD));

        BigDecimal amount = firstUSDAmount; // USD

        try {
            for (Market market : markets) {
                BitfinexSubmittedOrder order = market.exchange.trade(market, amount);
                executedTrades.add(order);

                BigDecimal atCreation = order.getAmountAtCreation();
                if (atCreation.compareTo(new BigDecimal(0)) < 0) {
                    atCreation = atCreation.negate().multiply(order.getPriceAverage());
                }
                amount = new BigDecimal(1).subtract(market.exchange.getTakerFee()).multiply(atCreation);
            }

            LOGGER.info("Finished trading {}", tradeChain.ilustratePath());

            // TODO get rid of blocking operations
            finalUSD = markets.get(0).exchange.getUSDUpdatesStream().blockingFirst();
        } catch (Exception e) {
            if (e.getMessage().contains("client too busy")) return Optional.empty();
            LOGGER.error("Error while trading: {}", e.getMessage(), e);
            return Optional.empty();
        }
        LOGGER.info("After trading USD: {}", Constants.DF.format(finalUSD));

        return Optional.of(this);
    }

    public List<BitfinexSubmittedOrder> getExecutedTrades() {
        return executedTrades;
    }
}
