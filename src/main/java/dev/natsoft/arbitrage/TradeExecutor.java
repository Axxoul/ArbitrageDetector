package dev.natsoft.arbitrage;

import com.github.jnidzwetzki.bitfinex.v2.entity.BitfinexExecutedTrade;
import dev.natsoft.arbitrage.model.Market;
import dev.natsoft.arbitrage.model.TradeChain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class TradeExecutor {

    private final List<BitfinexExecutedTrade> executedTrades;

    public TradeExecutor() {
        this.executedTrades = new ArrayList<>();
    }

    public List<BitfinexExecutedTrade> execute(TradeChain tradeChain) {
        List<Market> markets = tradeChain.path.getEdgeList();
        if (!markets.get(0).from.equals("USD")) {
            return executedTrades; // only start from USD for now
        }
        BigDecimal amount = new BigDecimal(10); // let's start with 10 USD

        for (Market market : markets) {
            BitfinexExecutedTrade trade = market.exchange.trade(market, amount);
            executedTrades.add(trade);
        }

        return executedTrades; // only start from USD for now
    }

    public List<BitfinexExecutedTrade> getExecutedTrades() {
        return executedTrades;
    }
}
