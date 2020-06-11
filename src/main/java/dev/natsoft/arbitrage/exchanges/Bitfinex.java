package dev.natsoft.arbitrage.exchanges;

import com.github.jnidzwetzki.bitfinex.v2.BitfinexClientFactory;
import com.github.jnidzwetzki.bitfinex.v2.BitfinexWebsocketClient;
import com.github.jnidzwetzki.bitfinex.v2.BitfinexWebsocketConfiguration;
import com.github.jnidzwetzki.bitfinex.v2.entity.*;
import com.github.jnidzwetzki.bitfinex.v2.entity.currency.BitfinexCurrencyPair;
import com.github.jnidzwetzki.bitfinex.v2.manager.QuoteManager;
import com.github.jnidzwetzki.bitfinex.v2.symbol.BitfinexSymbols;
import com.github.jnidzwetzki.bitfinex.v2.symbol.BitfinexTickerSymbol;
import dev.natsoft.arbitrage.ArbitrageDetector;
import dev.natsoft.arbitrage.Constants;
import dev.natsoft.arbitrage.RatesKnowledgeGraph;
import dev.natsoft.arbitrage.model.Market;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class Bitfinex implements Exchange {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArbitrageDetector.class);
    private BitfinexWebsocketClient client;
    private RatesKnowledgeGraph ratesKnowledgeGraph;
    private List<BitfinexSubmittedOrder> submittedOrderList;
    private List<BitfinexMyExecutedTrade> executedTradesList;

    public Bitfinex() {
        this.submittedOrderList = new ArrayList<>();
        this.executedTradesList = new ArrayList<>();
    }


    @Override
    public void startUpdating(RatesKnowledgeGraph ratesKnowledgeGraph) {
        this.ratesKnowledgeGraph = ratesKnowledgeGraph;

        // TODO crawler proxy?
        BitfinexCurrencyPair.registerDefaults();
        BitfinexWebsocketConfiguration config = new BitfinexWebsocketConfiguration();
        config.setApiCredentials(
                System.getenv("BFX_API_KEY"),
                System.getenv("BFX_API_SECRET")
        );
        client = BitfinexClientFactory.newPooledClient(config, 30);
        client.connect();

        client.getOrderManager().registerCallback(this::handleSentOrder);
        client.getTradeManager().registerCallback(this::handleTradeExecuted);

        // Todo gracefully Pool and get
        for (BitfinexCurrencyPair pair : BitfinexCurrencyPair.values()) {
            watchInstrument(client, pair);

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    private void handleTradeExecuted(BitfinexMyExecutedTrade bitfinexMyExecutedTrade) {
        executedTradesList.add(0, bitfinexMyExecutedTrade);
    }

    private void handleSentOrder(BitfinexSubmittedOrder submittedOrder) {
        submittedOrderList.add(0, submittedOrder);
    }

    @Override
    public BigDecimal getTakerFee() {
        return new BigDecimal("0.00200") // 0.2%
                .multiply(new BigDecimal(1)
                        .subtract(new BigDecimal("0.15"))); // 15% off for LEO holders
    }

    @Override
    public BitfinexExecutedTrade trade(Market market, BigDecimal amount) {
        LOGGER.info("Trading {}->{}, amount: {}", market.from, market.to, Constants.DF.format(amount));
        BitfinexCurrencyPair pair;
        BigDecimal realAmount;

        try {
            pair = BitfinexCurrencyPair.of(market.from, market.to);
            realAmount = amount;
        } catch (IllegalArgumentException e) {
            pair = BitfinexCurrencyPair.of(market.to, market.from);
            realAmount = amount.negate();
        }

        BitfinexNewOrder order = new BitfinexNewOrder();
        order.setOrderType(BitfinexOrderType.EXCHANGE_MARKET);
        order.setCurrencyPair(pair);
        order.setAmount(realAmount);

        LOGGER.info("Order placed for {}->{}, amount: {}", market.from, market.to, Constants.DF.format(amount));

        client.getOrderManager().placeOrder(order);

        BitfinexSubmittedOrder submittedOrder = waitForOrder(order);
        LOGGER.info("Order: {}", submittedOrder);

        BitfinexExecutedTrade trade = waitForTrade(submittedOrder);
        LOGGER.info("Trade performed: {}", trade);

        return trade;
    }

    private BitfinexExecutedTrade waitForTrade(BitfinexSubmittedOrder submittedOrder) {
        BitfinexExecutedTrade trade = null;

        while (trade == null) {
            trade = executedTradesList
                    .stream()
                    .filter(t -> t.getOrderId().equals(submittedOrder.getOrderId()))
                    .findAny()
                    .orElse(null);

            if (trade == null) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        return trade;
    }

    private BitfinexSubmittedOrder waitForOrder(BitfinexNewOrder order) {
        BitfinexSubmittedOrder submittedOrder = null;

        while (submittedOrder == null) {
            submittedOrder = submittedOrderList
                    .stream()
                    .filter(so -> so.getCurrencyPair() == order.getCurrencyPair())
                    .filter(so -> so.getAmount().equals(order.getAmount()))
                    .findAny()
                    .orElse(null);

            if (submittedOrder == null) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        return submittedOrder;
    }

    private void watchInstrument(BitfinexWebsocketClient client, BitfinexCurrencyPair pair) {
        LOGGER.info("Watching {}", pair);
        final QuoteManager quoteManager = client.getQuoteManager();
        final BitfinexTickerSymbol symbol = BitfinexSymbols.ticker(pair);

        quoteManager.registerTickCallback(symbol, this::handleTick);
        quoteManager.subscribeTicker(symbol);
    }

    private void handleTick(BitfinexTickerSymbol symbol, BitfinexTick tick) {
        try {
            BigDecimal vol = tick.getVolume();
            BigDecimal rate = tick.getBid();
            BigDecimal reverseRate = new BigDecimal(1).divide(tick.getAsk(), 100, RoundingMode.HALF_DOWN);
            BigDecimal spreadRate = new BigDecimal(1).subtract(rate.multiply(reverseRate)).multiply(new BigDecimal(100));

            if (tick.getVolume().compareTo(new BigDecimal(15000)) < 0
                    || spreadRate.compareTo(new BigDecimal("0.7")) > 0) {
                client.getQuoteManager().unsubscribeTicker(symbol);
                LOGGER.info("Dropping {}", symbol);
                return;
            }

            LOGGER.debug(String.format("[%s] (bid: %s, ask: %s, spread: %s)",
                    symbol.getCurrency(),
                    tick.getBid(),
                    tick.getAsk(),
                    tick.getAsk().subtract(tick.getBid())
            ));
            String[] currencies = symbol.getCurrency().toString().split(":");
            String from = currencies[0];
            String to = currencies[1];

            LOGGER.info(String.format("[%s] spread rate: %s, vol: %s",
                    symbol.getCurrency(),
                    Constants.DF.format(spreadRate),
                    Constants.DF.format(vol)
            ));

            ratesKnowledgeGraph.updateSecurity(new Market(from, to, this).setRate(rate));
            ratesKnowledgeGraph.updateSecurity(new Market(to, from, this).setRate(reverseRate));

        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}
