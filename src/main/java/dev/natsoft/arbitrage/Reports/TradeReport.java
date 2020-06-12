package dev.natsoft.arbitrage.Reports;

import dev.natsoft.arbitrage.AssetsManager;
import dev.natsoft.arbitrage.Constants;
import dev.natsoft.arbitrage.SimpleMarketTradeExecutor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.stream.Collectors;

public class TradeReport {
    private static final Logger LOGGER = LoggerFactory.getLogger(AssetsManager.class);

    private final Class executorClass;
    private final String tradePath;
    private final String expectedProfitability;
    private final String actualProfitability;
    private final String USDBefore;
    private final String USDAfter;
    private final String trades;
    private final SimpleMarketTradeExecutor te;

    public TradeReport(SimpleMarketTradeExecutor te) {
        this.te = te;

        executorClass = te.getClass();
        tradePath = te.tradeChain.ilustratePath();
        expectedProfitability = Constants.DF.format(te.tradeChain.getProfitability());
        actualProfitability = Constants.DF.format(te.getFinalUSD().divide(te.getInitialUSD(), 100, RoundingMode.HALF_DOWN));
        USDBefore = Constants.DF.format(te.getInitialUSD());
        USDAfter = Constants.DF.format(te.getFinalUSD());
        trades = te.getExecutedTrades()
                .stream()
                .map(Objects::toString)
                .collect(Collectors.joining(","));
    }

    public TradeReport logReport() {
        LOGGER.info("=== TRADE REPORT ===");
        LOGGER.info("= Executor Class: {}", executorClass);
        LOGGER.info("= Trade Path: {}", tradePath);
        LOGGER.info("= Expected Profitability: {}", expectedProfitability);
        LOGGER.info("= Actual Profitability: {} (USD {}->{})", actualProfitability, USDBefore, USDAfter);
        te.getExecutedTrades().forEach(trade -> {
            LOGGER.info("== Trade: {}", trade);
        });
        LOGGER.info("=== END REPORT ===");

        return this;
    }

    public TradeReport saveReport() throws IOException {
        File reportFile = new File("tmp/trades_report.csv");
        boolean exists = reportFile.exists();
        FileWriter pw = new FileWriter(reportFile, true);
        CSVPrinter csvPrinter = new CSVPrinter(pw, CSVFormat.DEFAULT.withHeader(
                "USDBefore",
                "USDAfter",
                "Expected Profitability",
                "Actual Profitability",
                "Path",
                "Executor",
                "Trades"
        ).withSkipHeaderRecord(exists));

        csvPrinter.printRecord(
                USDBefore,
                USDAfter,
                expectedProfitability,
                actualProfitability,
                tradePath,
                executorClass,
                trades
        );
        csvPrinter.flush();

        return this;
    }

}

