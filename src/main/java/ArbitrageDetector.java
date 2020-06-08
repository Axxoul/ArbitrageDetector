import com.github.jnidzwetzki.bitfinex.v2.BitfinexClientFactory;
import com.github.jnidzwetzki.bitfinex.v2.BitfinexWebsocketClient;
import com.github.jnidzwetzki.bitfinex.v2.entity.BitfinexTick;
import com.github.jnidzwetzki.bitfinex.v2.entity.currency.BitfinexCurrencyPair;
import com.github.jnidzwetzki.bitfinex.v2.manager.QuoteManager;
import com.github.jnidzwetzki.bitfinex.v2.symbol.BitfinexSymbols;
import com.github.jnidzwetzki.bitfinex.v2.symbol.BitfinexTickerSymbol;
import model.Market;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ArbitrageDetector {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArbitrageDetector.class);
    private static ExchangeRates exchangeRates;
    private static BitfinexWebsocketClient client;

    public static void main(String[] args) {
        exchangeRates = new ExchangeRates();

        BitfinexCurrencyPair.registerDefaults();

        client = BitfinexClientFactory.newSimpleClient();
        client.connect();


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

    private static void watchInstrument(BitfinexWebsocketClient client, BitfinexCurrencyPair pair) {
        final QuoteManager quoteManager = client.getQuoteManager();
        final BitfinexTickerSymbol symbol = BitfinexSymbols.ticker(pair);

        quoteManager.registerTickCallback(symbol, ArbitrageDetector::handleTick);
        quoteManager.subscribeTicker(symbol);
    }

    private static void handleTick(BitfinexTickerSymbol symbol, BitfinexTick tick) {
        try {
            BigDecimal vol = tick.getVolume();
            BigDecimal rate = tick.getBid();
            BigDecimal reverseRate = new BigDecimal(1).divide(tick.getAsk(), 100, RoundingMode.HALF_DOWN);
            BigDecimal spreadRate = new BigDecimal(1).subtract(rate.multiply(reverseRate)).multiply(new BigDecimal(100));

            if (tick.getVolume().compareTo(new BigDecimal(15000)) < 0
                    || spreadRate.compareTo(new BigDecimal("0.7")) > 0) {
                client.getQuoteManager().unsubscribeTicker(symbol);
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

            exchangeRates.updateSecurity(new Market(from, to, Constants.BITFINEX).setRate(rate)); // Bid vs ask?
            exchangeRates.updateSecurity(new Market(to, from, Constants.BITFINEX).setRate(reverseRate)); // Bid vs ask?

            exchangeRates.detectArbitrage();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }


}
