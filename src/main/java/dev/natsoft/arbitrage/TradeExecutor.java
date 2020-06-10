package dev.natsoft.arbitrage;

import dev.natsoft.arbitrage.model.Market;
import dev.natsoft.arbitrage.model.TradeChain;

public class TradeExecutor {
    public void execute(TradeChain tradeChain) {
        for (Market market : tradeChain.path.getEdgeList()) {
            market.exchange.trade(market);
        }
    }
}
