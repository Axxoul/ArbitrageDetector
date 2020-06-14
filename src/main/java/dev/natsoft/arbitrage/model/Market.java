package dev.natsoft.arbitrage.model;

import dev.natsoft.arbitrage.exchanges.Exchange;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;

public class Market {
    public final String from;
    public final String to;
    public final Exchange exchange;
    private BigDecimal rate;
    private BigDecimal price;
    private Instant lastUpdateTimestamp;

    public Market(String from, String to, Exchange exchange) {
        this.from = from;
        this.to = to;
        this.exchange = exchange;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Market setPrice(BigDecimal price) {
        this.lastUpdateTimestamp = Instant.now();
        this.price = price;
        return this;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public Market setRate(BigDecimal rate) {
        this.lastUpdateTimestamp = Instant.now();
        this.rate = rate;
        return this;
    }

    public BigDecimal getRateWithFees() {
        BigDecimal outRate = rate;
        if (Duration.between(lastUpdateTimestamp, Instant.now()).getSeconds() > 10)
            outRate = BigDecimal.valueOf(0);

        return outRate.multiply(
                new BigDecimal(1)
                        .subtract(exchange.getTakerFee())); // we will pay the taker fee to place a market order
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
