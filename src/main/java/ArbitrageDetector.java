import com.github.jnidzwetzki.bitfinex.v2.BitfinexClientFactory;
import com.github.jnidzwetzki.bitfinex.v2.BitfinexWebsocketClient;
import com.github.jnidzwetzki.bitfinex.v2.entity.BitfinexTick;
import com.github.jnidzwetzki.bitfinex.v2.entity.currency.BitfinexCurrencyPair;
import com.github.jnidzwetzki.bitfinex.v2.manager.QuoteManager;
import com.github.jnidzwetzki.bitfinex.v2.symbol.BitfinexSymbols;
import com.github.jnidzwetzki.bitfinex.v2.symbol.BitfinexTickerSymbol;
import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.StackBFSFundamentalCycleBasis;
import org.jgrapht.graph.DirectedPseudograph;

import java.math.BigDecimal;
import java.util.Set;

import static com.google.common.collect.Iterators.find;

public class ArbitrageDetector {
    public static final String BITFINEX = "bitfinex";

    private static Graph<String, Market> graph;

    public static void main(String[] args) {
        graph = new DirectedPseudograph<>(Market.class);

        BitfinexCurrencyPair.registerDefaults();
        final BitfinexWebsocketClient client = BitfinexClientFactory.newSimpleClient();
        client.connect();

        watchInstrument(client, "BTC");
        watchInstrument(client, "ETH");
    }

    private static void watchInstrument(BitfinexWebsocketClient client, String instrument) {
        final QuoteManager quoteManager = client.getQuoteManager();
        final BitfinexTickerSymbol symbol = BitfinexSymbols.ticker(
                BitfinexCurrencyPair.of(instrument, "USD")
        );

        quoteManager.registerTickCallback(symbol, ArbitrageDetector::handleTick);
        quoteManager.subscribeTicker(symbol);
    }

    private static void handleTick(BitfinexTickerSymbol symbol, BitfinexTick tick) {
        System.out.format("[%s] (bid: %s, ask: %s, spread: %s)\n", symbol.getCurrency(), tick.getBid(), tick.getAsk(), tick.getAsk().subtract(tick.getBid()));
        String[] currencies = symbol.getCurrency().toString().split(":");
        String from = currencies[0];
        String to = currencies[1];

        setEdge(from, to, tick.getBid()); // Bid vs ask?
        setEdge(to, from, tick.getAsk());

        detectArbitrage();
    }

    private static void detectArbitrage() {
        // FIXME nie działa gówno
        new StackBFSFundamentalCycleBasis<>(graph)
                .getCycleBasis()
                .getCyclesAsGraphPaths()
                .stream()
                .map(path -> new Profitability(graph, path))
                .filter(prof -> prof.isProfitable(graph))
//                .sorted(); // TODO pick the best one
                .forEach(Profitability::report);
    }


    private static void setEdge(String from, String to, BigDecimal rate) {
        Set<Market> markets = graph.getAllEdges(from, to);

        Market market = null;
        if (markets != null) {
            market = find(
                    markets.iterator(),
                    (Market mark) -> {
                        assert mark != null;
                        return mark.exchange.equals(BITFINEX);
                    },
                    null
            );
        }

        if (market == null) {
            market = new Market(from, to, BITFINEX);

            if (!graph.containsVertex(from)) graph.addVertex(from);
            if (!graph.containsVertex(to)) graph.addVertex(to);

            graph.addEdge(from, to, market);
        }
        market.setRate(rate);
    }
}
