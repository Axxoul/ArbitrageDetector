package dev.natsoft.arbitrage;

import dev.natsoft.arbitrage.exchanges.Bitfinex;
import dev.natsoft.arbitrage.exchanges.Exchange;

import java.util.List;

public class ArbitrageDetector {
    private static ExchangeRates exchangeRates;

    public static void main(String[] args) {
        exchangeRates = new ExchangeRates();

        List<Exchange> exchanges = List.of(
                new Bitfinex()
        );

        exchanges.forEach(ex -> ex.startUpdating(exchangeRates));
    }
}
