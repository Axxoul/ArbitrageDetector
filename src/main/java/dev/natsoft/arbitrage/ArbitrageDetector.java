package dev.natsoft.arbitrage;

import dev.natsoft.arbitrage.exchanges.Bitfinex;
import dev.natsoft.arbitrage.exchanges.Exchange;

import java.util.ArrayList;
import java.util.List;

public class ArbitrageDetector {
    private static RatesKnowledgeGraph ratesKnowledgeGraph;
    private static AssetsManager assetsManager;

    public static void main(String[] args) {
        ratesKnowledgeGraph = new RatesKnowledgeGraph();
        assetsManager = new AssetsManager(ratesKnowledgeGraph);

        List<Exchange> exchanges = new ArrayList<Exchange>() {{
            add(new Bitfinex());
        }};

        exchanges.forEach(exchange -> {
            exchange.startUpdating(ratesKnowledgeGraph);
        });

    }
}
