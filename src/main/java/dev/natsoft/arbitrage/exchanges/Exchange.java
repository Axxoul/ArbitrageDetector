package dev.natsoft.arbitrage.exchanges;

import dev.natsoft.arbitrage.ExchangeRates;

public interface Exchange {
    void startUpdating(ExchangeRates exchangeRates);
}
