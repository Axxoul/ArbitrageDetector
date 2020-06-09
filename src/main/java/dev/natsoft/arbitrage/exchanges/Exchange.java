package dev.natsoft.arbitrage.exchanges;

import dev.natsoft.arbitrage.ExchangeRates;

import java.math.BigDecimal;

public interface Exchange {
    void startUpdating(ExchangeRates exchangeRates);

    BigDecimal getTakerFee();
}
