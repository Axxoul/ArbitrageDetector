package dev.natsoft.arbitrage.model;

import dev.natsoft.arbitrage.exchanges.Exchange;

import java.math.BigDecimal;
import java.text.NumberFormat;

public class Market {
    public final String from;
    public final String to;
    public final Exchange exchange;
    private BigDecimal rate;

    public Market(String from, String to, Exchange exchange) {
        this.from = from;
        this.to = to;
        this.exchange = exchange;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public BigDecimal getRateWithFees() {
        return rate.multiply(new BigDecimal(1).subtract(exchange.getTakerFee())); // we will pay the taker fee to place this market order
    }

    public Market setRate(BigDecimal rate) {
        this.rate = rate;
        return this;
    }

    @Override
    public String toString() {

        return "Market{" +
                "from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", exchange='" + exchange + '\'' +
                ", rate=" + NumberFormat.getCurrencyInstance().format(rate) +
                '}';
    }
}
