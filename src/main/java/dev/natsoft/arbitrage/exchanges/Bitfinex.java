package dev.natsoft.arbitrage.exchanges;

import com.github.jnidzwetzki.bitfinex.v2.BitfinexClientFactory;
import com.github.jnidzwetzki.bitfinex.v2.BitfinexOrderBuilder;
import com.github.jnidzwetzki.bitfinex.v2.BitfinexWebsocketClient;
import com.github.jnidzwetzki.bitfinex.v2.BitfinexWebsocketConfiguration;
import com.github.jnidzwetzki.bitfinex.v2.entity.*;
import com.github.jnidzwetzki.bitfinex.v2.entity.currency.BitfinexCurrencyPair;
import com.github.jnidzwetzki.bitfinex.v2.manager.QuoteManager;
import com.github.jnidzwetzki.bitfinex.v2.symbol.BitfinexAccountSymbol;
import com.github.jnidzwetzki.bitfinex.v2.symbol.BitfinexSymbols;
import com.github.jnidzwetzki.bitfinex.v2.symbol.BitfinexTickerSymbol;
import dev.natsoft.arbitrage.ArbitrageDetector;
import dev.natsoft.arbitrage.Constants;
import dev.natsoft.arbitrage.RatesKnowledgeGraph;
import dev.natsoft.arbitrage.model.Market;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Objects;

public class Bitfinex implements Exchange {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArbitrageDetector.class);

    private final Subject<Boolean> ticks;
    private final Subject<BigDecimal> USDUpdates;

    private boolean busy;
    private BitfinexWebsocketClient privateClient;
    private BitfinexWebsocketClient publicClient;
    private RatesKnowledgeGraph ratesKnowledgeGraph;

    public Bitfinex() {
        this.ticks = PublishSubject.create();
        this.USDUpdates = PublishSubject.create();

        this.busy = false;
    }

    private void handleWalletUpdate(BitfinexAccountSymbol bitfinexAccountSymbol, Collection<BitfinexWallet> bitfinexWallets) {
        bitfinexWallets
                .stream()
                .filter(wal -> wal.getCurrency().equals("USD"))
                .map(BitfinexWallet::getBalanceAvailable)
                .filter(Objects::nonNull)
                .forEach(usdVal -> {
                    LOGGER.info("Got USD balance update: {} USD", Constants.DF.format(usdVal));
                    USDUpdates.onNext(usdVal);
                });
    }

    public Observable<Boolean> getTickerStream() {
        return ticks;
    }

    public Observable<BigDecimal> getUSDUpdatesStream() {
        return USDUpdates;
    }

    @Override
    public void startUpdating(RatesKnowledgeGraph ratesKnowledgeGraph) {
        this.ratesKnowledgeGraph = ratesKnowledgeGraph;
        ratesKnowledgeGraph.registerTickerStream(ticks);
        busy = true;

        BitfinexCurrencyPair.registerDefaults();
        BitfinexWebsocketConfiguration config = new BitfinexWebsocketConfiguration();
        config.setApiCredentials(
                System.getenv("BFX_API_KEY"),
                System.getenv("BFX_API_SECRET")
        );
        privateClient = BitfinexClientFactory.newPooledClient(config, 20);
        privateClient.connect();

        LOGGER.info("=== Orders ===");
        privateClient.getOrderManager().getOrders().forEach(or -> {
            LOGGER.info("= {}", or);

        });

        LOGGER.info("=== Balances ===");
        privateClient.getWalletManager()
                .getWallets()
                .forEach(wal -> {
                    BigDecimal bal = wal.getBalanceAvailable();
                    bal = bal != null ? bal : new BigDecimal(0);
                    LOGGER.info("= {} : {}", wal.getCurrency(), Constants.DF.format(bal));
                });

        publicClient = BitfinexClientFactory.newPooledClient();
        publicClient.connect();
        publicClient.getCallbacks().onMyWalletEvent(this::handleWalletUpdate);

        LOGGER.info("Starting tickers subscriptsions");
        for (BitfinexCurrencyPair pair : BitfinexCurrencyPair.values()) {
            watchInstrument(pair);
        }
        LOGGER.info("Done subscribing to tickers");

        busy = false;
    }

    @Override
    public BigDecimal getTakerFee() {
        return new BigDecimal("0.00207") // 0.2% with 0.005% wiggle room to avoid INSUFFICIENT BALANCE (U1) was: PARTIALLY FILLED @ 0.00095551(-142.55525736)
//                .multiply(new BigDecimal(1)
//                        .subtract(new BigDecimal("0.15"))) // 15% off for LEO holders
                ;
    }

    @Override
    public BitfinexSubmittedOrder trade(Market market, BigDecimal sourceAmount) throws Exception {
        if (busy) {
            LOGGER.warn("Bitfinex client too busy to trade.");
            throw new Exception("Bitfinex client too busy");
        }

        busy = true;

        LOGGER.info("Trading {}->{}, amount: {}", market.from, market.to, Constants.DF.format(sourceAmount));

        BitfinexCurrencyPair pair;
        BigDecimal targetAmount;
        try {
            pair = BitfinexCurrencyPair.of(market.from, market.to);
            targetAmount = sourceAmount.negate();
        } catch (IllegalArgumentException e) {
            pair = BitfinexCurrencyPair.of(market.to, market.from);
            targetAmount = sourceAmount.divide(market.getPrice(), 100, RoundingMode.HALF_DOWN);
        }

        BitfinexNewOrder order = BitfinexOrderBuilder.create(
                pair,
                BitfinexOrderType.EXCHANGE_MARKET,
                targetAmount
        ).build();

        LOGGER.info("Order placed for {}->{}({}), amount: {}", market.from, market.to, pair.toString(), Constants.DF.format(sourceAmount));

        privateClient.getOrderManager().placeOrder(order);

        BitfinexSubmittedOrder submittedOrder = waitForOrder(order);
        LOGGER.info("Order finished: {}", submittedOrder);
        if (submittedOrder.getStatus() == BitfinexSubmittedOrderStatus.ERROR) {
            busy = false;
            throw new Exception(submittedOrder.getStatusDescription());
        }

        Thread.sleep(100); // Websocket crashes for some reason
        busy = false;
        return submittedOrder;
    }

    @Override
    public BigDecimal getUSDBalance() {
        return privateClient.getWalletManager()
                .getWallets()
                .stream()
                .filter(wal -> wal.getCurrency().equals("USD"))
                .map(BitfinexWallet::getBalanceAvailable)
                .filter(Objects::nonNull)
                .findAny()
                .orElse(new BigDecimal(0));
    }

    private BitfinexSubmittedOrder waitForOrder(BitfinexNewOrder order) {
        BitfinexSubmittedOrder submittedOrder = null;

        while (submittedOrder == null) {
            submittedOrder = privateClient.getOrderManager().getOrders()
                    .stream()
                    .filter(so -> so.getClientId() == order.getClientId())
                    .filter(so -> so.getStatus() == BitfinexSubmittedOrderStatus.EXECUTED
                            || so.getStatus() == BitfinexSubmittedOrderStatus.CANCELED)
                    .findAny()
                    .orElse(null);

            if (submittedOrder == null) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        return submittedOrder;
    }

    private void watchInstrument(BitfinexCurrencyPair pair) {
        LOGGER.info("Watching {}", pair);
        final QuoteManager quoteManager = publicClient.getQuoteManager();
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

            LOGGER.info(String.format("[%s] spread rate: %s, vol: %s",
                    symbol.getCurrency(),
                    Constants.DF.format(spreadRate),
                    Constants.DF.format(vol)
            ));

            if (tick.getVolume().compareTo(new BigDecimal(4000)) < 0
                    || spreadRate.compareTo(new BigDecimal("1.5")) > 0) {
                publicClient.getQuoteManager().unsubscribeTicker(symbol);
                LOGGER.info("Dropping {} for spread or volume thresholds", symbol);
                return;
            }
            ticks.onNext(true);

            LOGGER.debug(String.format("[%s] (bid: %s, ask: %s, spread: %s)",
                    symbol.getCurrency(),
                    tick.getBid(),
                    tick.getAsk(),
                    tick.getAsk().subtract(tick.getBid())
            ));
            String[] currencies = symbol.getCurrency().toString().split(":");
            String from = currencies[0];
            String to = currencies[1];

            ratesKnowledgeGraph.updateSecurity(new Market(from, to, this).setRate(rate).setPrice(tick.getBid()));
            ratesKnowledgeGraph.updateSecurity(new Market(to, from, this).setRate(reverseRate).setPrice(tick.getAsk()));

        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

}
