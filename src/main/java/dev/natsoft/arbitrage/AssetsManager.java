package dev.natsoft.arbitrage;

import dev.natsoft.arbitrage.Reports.TradeReport;
import dev.natsoft.arbitrage.model.Market;
import dev.natsoft.arbitrage.model.TradeChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.Math.max;

/**
 * Facade for accessing all information about the current state of assets and orders.
 */
public class AssetsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AssetsManager.class);
    private final RatesKnowledgeGraph ratesKnowledgeGraph;
    private final List<TradeReport> reports;
    private BigDecimal startUSD;
    private int tradesLeft;
    private BigDecimal lastUSD;

    public AssetsManager(RatesKnowledgeGraph ratesKnowledgeGraph) {
        reports = TradeReport.readReportHistory();
        tradesLeft = 50;
        lastUSD = new BigDecimal(0);
        startUSD = new BigDecimal(0);

        this.ratesKnowledgeGraph = ratesKnowledgeGraph;
        this.ratesKnowledgeGraph.getBestTradesStream()
                .map(this::modifyChainToStartFromUSD)
                .filter(this::shouldExecute)
                .map(SimpleMarketTradeExecutor::new)
                .mapOptional(SimpleMarketTradeExecutor::execute)
                .subscribe(this::reportTrades);
    }

    private TradeChain modifyChainToStartFromUSD(TradeChain tradeChain) {
        if (!tradeChain.ilustratePath().contains("USD"))
            return tradeChain;

        List<Market> markets = tradeChain.path.getEdgeList();

        String startAsset = markets.get(0).from;
        while (!startAsset.equals("USD")) {
            Market m = markets.remove(markets.size() - 1);
            markets.add(0, m);
            startAsset = markets.get(0).from;
        }

        return tradeChain;
    }

    private boolean shouldExecute(TradeChain tradeChain) {
        if (tradesLeft == 0) {
            LOGGER.info("Trade count finished, exiting");
            System.exit(0);
        }


        if (!tradeChain.ilustratePath().contains("USD"))
            return false;

        String expectedPath = tradeChain.ilustratePath();

        List<TradeReport> relevantReports = reports.stream()
                .filter(rep -> rep.tradePath.equals(expectedPath))
                .limit(5)
                .collect(Collectors.toList());

        double avgExpectedProfit = relevantReports.stream()
                .mapToDouble(tradeReport -> new BigDecimal(tradeReport.expectedProfitability).doubleValue())
                .average()
                .orElse(1);

        double avgActualProfit = relevantReports.stream()
                .mapToDouble(tradeReport -> new BigDecimal(tradeReport.actualProfitability).doubleValue())
                .average()
                .orElse(1);

        BigDecimal threshold = BigDecimal.valueOf(max(avgExpectedProfit - avgActualProfit + 1, 1.001));
//        BigDecimal threshold = BigDecimal.valueOf(0.992); // for quick testing

        LOGGER.info("Threshold: {}, TradeChain: {}", Constants.DF.format(threshold), tradeChain.ilustratePath());

        if (tradeChain.getProfitability().compareTo(threshold) < 0)
            return false;

        if (lastUSD.compareTo(startUSD.subtract(new BigDecimal(10))) < 0) {
            LOGGER.warn("Lost more than 10 USD, exit");
            System.exit(0);
        }

        return true;
    }

    private void reportTrades(SimpleMarketTradeExecutor te) throws IOException {
        tradesLeft -= 1;

        if (startUSD.compareTo(new BigDecimal(0)) == 0) {
            startUSD = te.getInitialUSD();
        }

        lastUSD = te.getFinalUSD();

        TradeReport report = new TradeReport(te)
                .saveReport()
                .logReport();

        reports.add(0, report);

    }
}
