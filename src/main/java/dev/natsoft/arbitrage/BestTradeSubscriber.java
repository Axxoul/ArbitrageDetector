package dev.natsoft.arbitrage;

import dev.natsoft.arbitrage.model.TradeChain;

public interface BestTradeSubscriber {
    void receiveBestTrade(TradeChain tradeChain);
}
