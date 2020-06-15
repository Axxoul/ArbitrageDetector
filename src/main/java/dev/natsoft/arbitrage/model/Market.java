package dev.natsoft.arbitrage.model;

import dev.natsoft.arbitrage.exchanges.Exchange;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;

public class Market {
    private static final Logger LOGGER = LoggerFactory.getLogger(Market.class);
    private static final int FRESHNESS_SECONDS = 60;
    public final String from;
    public final String to;
    public final Exchange exchange;
    private final DescriptiveStatistics updateTimeStatistics = new DescriptiveStatistics();
    private BigDecimal rate;
    private BigDecimal price;
    private Instant lastUpdateTimestamp;

    public Market(String from, String to, Exchange exchange) {
        this.from = from;
        this.to = to;
        this.exchange = exchange;
        this.lastUpdateTimestamp = Instant.now();
    }

    public Instant getLastUpdateTimestamp() {
        return lastUpdateTimestamp;
    }

    private void updateTimestamp() {
        Instant prevUpdate = lastUpdateTimestamp;
        Instant currentUpdate = Instant.now();
        lastUpdateTimestamp = currentUpdate;
        long secondsSinceLastUpdate = Duration.between(prevUpdate, currentUpdate).getSeconds();
        if (secondsSinceLastUpdate != 0) {
            updateTimeStatistics.addValue(secondsSinceLastUpdate);
        }

        if (secondsSinceLastUpdate > FRESHNESS_SECONDS)
            LOGGER.warn("Outdated update: {}->{} after {}s. Statistics: 50p,75p,95p: {},{},{}",
                    from, to, secondsSinceLastUpdate,
                    updateTimeStatistics.getPercentile(50),
                    updateTimeStatistics.getPercentile(75),
                    updateTimeStatistics.getPercentile(95)
            );
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Market setPrice(BigDecimal price) {
        updateTimestamp();
        this.price = price;
        return this;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public Market setRate(BigDecimal rate) {
        updateTimestamp();
        this.rate = rate;
        return this;
    }

    public BigDecimal getRateWithFees() {
        BigDecimal outRate = rate;
        if (Duration.between(lastUpdateTimestamp, Instant.now()).getSeconds() > FRESHNESS_SECONDS)
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
