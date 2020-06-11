package dev.natsoft.arbitrage;

import com.github.jnidzwetzki.bitfinex.v2.entity.BitfinexSubmittedOrder;
import dev.natsoft.arbitrage.model.Market;
import dev.natsoft.arbitrage.model.TradeChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SimpleMarketTradeExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleMarketTradeExecutor.class);
    private final List<BigDecimal> executedTrades;

    public SimpleMarketTradeExecutor() {
        this.executedTrades = new ArrayList<>();
    }

    public List<BigDecimal> execute(TradeChain tradeChain) throws Exception {
        List<Market> markets = tradeChain.path.getEdgeList();

        // cycle through trades to find USD starting point
        String startAsset = markets.get(0).from;
        while (!startAsset.equals("USD")) {
            Market m = markets.remove(markets.size() - 1);
            markets.add(0, m);
            startAsset = markets.get(0).from;
        }

        LOGGER.info("Staring trades: {}", tradeChain.ilustratePath());
        LOGGER.info("Initial USD: {}", Constants.DF.format(markets.get(0).exchange.getUSDBalance()));
        BigDecimal amount = new BigDecimal(30); // let's start with 30 USD

        for (Market market : markets) {
            BitfinexSubmittedOrder order = market.exchange.trade(market, amount);
//            amount = new BigDecimal(1).subtract(market.exchange.getTakerFee()).multiply(trade.getAmount());
//            amount = trade.getAmount().add(trade.getFee());
            BigDecimal atCreation = order.getAmountAtCreation();
            if (atCreation.compareTo(new BigDecimal(0)) < 0) {
                atCreation = atCreation.negate().multiply(order.getPriceAverage());
            }
            amount = new BigDecimal(1).subtract(market.exchange.getTakerFee()).multiply(atCreation);
            executedTrades.add(amount);
        }

        LOGGER.info("Finished trading");

        // wait for balance updates
        Thread.sleep(TimeUnit.SECONDS.toMillis(5));
        LOGGER.info("After trading USD: {}", Constants.DF.format(markets.get(0).exchange.getUSDBalance()));
        return executedTrades; // only start from USD for now
    }

    public List<BigDecimal> getExecutedTrades() {
        return executedTrades;
    }
}
