package dev.natsoft.arbitrage;

import com.github.jnidzwetzki.bitfinex.v2.entity.BitfinexSubmittedOrder;
import dev.natsoft.arbitrage.model.Market;
import dev.natsoft.arbitrage.model.TradeChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class SimpleMarketTradeExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleMarketTradeExecutor.class);
    public final TradeChain tradeChain;
    private final List<BitfinexSubmittedOrder> executedTrades;
    private BigDecimal initialUSD;
    private BigDecimal finalUSD;

    public SimpleMarketTradeExecutor(TradeChain tradeChain) {
        this.tradeChain = tradeChain;
        this.executedTrades = new ArrayList<BitfinexSubmittedOrder>();
    }

    public BigDecimal getInitialUSD() {
        return initialUSD;
    }

    public BigDecimal getFinalUSD() {
        return finalUSD;
    }

    public List<BitfinexSubmittedOrder> execute() throws Exception {
        List<Market> markets = tradeChain.path.getEdgeList();

        // cycle through trades to find USD starting point
        String startAsset = markets.get(0).from;
        while (!startAsset.equals("USD")) {
            Market m = markets.remove(markets.size() - 1);
            markets.add(0, m);
            startAsset = markets.get(0).from;
        }

        LOGGER.info("Staring trades: {}", tradeChain.ilustratePath());
        initialUSD = markets.get(0).exchange.getUSDBalance();
        LOGGER.info("Initial USD: {}", Constants.DF.format(initialUSD));
        float randomNum = ThreadLocalRandom.current().nextFloat() * 10;

        BigDecimal amount = new BigDecimal(30 + randomNum); // let's start with 30 USD

        for (Market market : markets) {
            BitfinexSubmittedOrder order = market.exchange.trade(market, amount);
            executedTrades.add(order);
//            amount = new BigDecimal(1).subtract(market.exchange.getTakerFee()).multiply(trade.getAmount());
//            amount = trade.getAmount().add(trade.getFee());
            BigDecimal atCreation = order.getAmountAtCreation();
            if (atCreation.compareTo(new BigDecimal(0)) < 0) {
                atCreation = atCreation.negate().multiply(order.getPriceAverage());
            }
            amount = new BigDecimal(1).subtract(market.exchange.getTakerFee()).multiply(atCreation);
        }

        LOGGER.info("Finished trading {}", tradeChain.ilustratePath());

        // wait for balance updates
        Thread.sleep(TimeUnit.SECONDS.toMillis(5));
        finalUSD = markets.get(0).exchange.getUSDBalance();
        LOGGER.info("After trading USD: {}", Constants.DF.format(finalUSD));
        return executedTrades; // only start from USD for now
    }

    public List<BitfinexSubmittedOrder> getExecutedTrades() {
        return executedTrades;
    }
}
