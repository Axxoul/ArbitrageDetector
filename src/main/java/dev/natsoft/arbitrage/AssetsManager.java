package dev.natsoft.arbitrage;

import dev.natsoft.arbitrage.Reports.TradeReport;
import dev.natsoft.arbitrage.model.Market;
import dev.natsoft.arbitrage.model.TradeChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Facade for accessing all information about the current state of assets and orders.
 */
public class AssetsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AssetsManager.class);
    BigDecimal startUSD;
    private int tradesLeft;
    private List<TradeReport> reports;

    public AssetsManager() {
        reports = new ArrayList<>();
        this.tradesLeft = 10;

    }

    public void receiveBestTrade(TradeChain tradeChain) {
        modifyChainToStartFromUSD(tradeChain);

        if (!shouldExecute(tradeChain))
            return;

        try {
            SimpleMarketTradeExecutor te = new SimpleMarketTradeExecutor(tradeChain);
            te.execute();
            reportTrades(te);
            if (startUSD == null) {
                startUSD = te.getInitialUSD();
            }
            if (te.getFinalUSD().compareTo(startUSD.subtract(new BigDecimal(10))) < 0) {
                LOGGER.warn("Lost more than 10 USD, exit");
                System.exit(0);
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        tradesLeft -= 1;
    }

    private void modifyChainToStartFromUSD(TradeChain tradeChain) {
        if (!tradeChain.ilustratePath().contains("USD"))
            return;

        List<Market> markets = tradeChain.path.getEdgeList();

        String startAsset = markets.get(0).from;
        while (!startAsset.equals("USD")) {
            Market m = markets.remove(markets.size() - 1);
            markets.add(0, m);
            startAsset = markets.get(0).from;
        }
    }

    private boolean shouldExecute(TradeChain tradeChain) {
//        if (tradesLeft == 0)
//            System.exit(0);

        if (!tradeChain.ilustratePath().contains("USD"))
            return false;

        String expectedPath = tradeChain.ilustratePath();

        Stream<TradeReport> currentTradeChainHistory = reports.stream()
                .filter(rep -> rep.tradePath.equals(expectedPath));

        double avgExpectedProfit = currentTradeChainHistory
                .mapToDouble(tradeReport -> new BigDecimal(tradeReport.expectedProfitability).doubleValue())
                .average()
                .orElse(1);

        double avgActualProfit = currentTradeChainHistory
                .mapToDouble(tradeReport -> new BigDecimal(tradeReport.actualProfitability).doubleValue())
                .average()
                .orElse(1);

        BigDecimal threshold = new BigDecimal(avgExpectedProfit - avgActualProfit + 1);

        LOGGER.info("Threshold: {}, TradeChain: {}", Constants.DF.format(threshold), tradeChain);

        if (tradeChain.getProfitability().compareTo(threshold) < 0)
            return false;

        return true;
    }

    private void reportTrades(SimpleMarketTradeExecutor te) throws IOException {
        reports.add(
                new TradeReport(te)
                        .saveReport()
                        .logReport()
        );
    }
}
