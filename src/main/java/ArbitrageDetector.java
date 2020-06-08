import com.github.jnidzwetzki.bitfinex.v2.BitfinexClientFactory;
import com.github.jnidzwetzki.bitfinex.v2.BitfinexWebsocketClient;
import com.github.jnidzwetzki.bitfinex.v2.entity.BitfinexTick;
import com.github.jnidzwetzki.bitfinex.v2.entity.currency.BitfinexCurrencyPair;
import com.github.jnidzwetzki.bitfinex.v2.manager.QuoteManager;
import com.github.jnidzwetzki.bitfinex.v2.symbol.BitfinexSymbols;
import com.github.jnidzwetzki.bitfinex.v2.symbol.BitfinexTickerSymbol;
import model.Market;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArbitrageDetector {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArbitrageDetector.class);
    private static ExchangeRates exchangeRates;

    public static void main(String[] args) {
        exchangeRates = new ExchangeRates();

        BitfinexCurrencyPair.registerDefaults();
        final BitfinexWebsocketClient client = BitfinexClientFactory.newSimpleClient();
        client.connect();

        watchInstrument(client, "BTC", "USD");
        watchInstrument(client, "ETH", "USD");
        watchInstrument(client, "ETH", "BTC");
    }

    private static void watchInstrument(BitfinexWebsocketClient client, String instrument1, String instrument2) {
        final QuoteManager quoteManager = client.getQuoteManager();
        final BitfinexTickerSymbol symbol = BitfinexSymbols.ticker(
                BitfinexCurrencyPair.of(instrument1, instrument2)
        );

        quoteManager.registerTickCallback(symbol, ArbitrageDetector::handleTick);
        quoteManager.subscribeTicker(symbol);
    }

    private static void handleTick(BitfinexTickerSymbol symbol, BitfinexTick tick) {
        try {
            LOGGER.info(String.format("[%s] (bid: %s, ask: %s, spread: %s)",
                    symbol.getCurrency(),
                    tick.getBid(),
                    tick.getAsk(),
                    tick.getAsk().subtract(tick.getBid())
            ));
            String[] currencies = symbol.getCurrency().toString().split(":");
            String from = currencies[0];
            String to = currencies[1];


            BigDecimal rate = tick.getBid();
            BigDecimal reverseRate = new BigDecimal(1).divide(tick.getAsk(), 100, RoundingMode.HALF_DOWN);

            LOGGER.debug(String.format("spread rate: %s",
                    Constants.DF.format(new BigDecimal(1).subtract(rate.multiply(reverseRate)).multiply(new BigDecimal(100))))
            );

            exchangeRates.updateSecurity(new Market(from, to, Constants.BITFINEX).setRate(rate)); // Bid vs ask?
            exchangeRates.updateSecurity(new Market(to, from, Constants.BITFINEX).setRate(reverseRate)); // Bid vs ask?

            exchangeRates.detectArbitrage();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }


}
