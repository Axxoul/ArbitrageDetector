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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class TradeReport {
    private static final Logger LOGGER = LoggerFactory.getLogger(AssetsManager.class);

    public final String timestamp;
    public final BigDecimal USDTraded;
    public final Class executorClass;
    public final String tradePath;
    public final String expectedProfitability;
    public final String actualProfitability;
    public final String USDBefore;
    public final String USDAfter;
    public final String trades;
    public final SimpleMarketTradeExecutor te;

    public TradeReport(SimpleMarketTradeExecutor te) {
        this.te = te;

        USDTraded = te.firstUSDAmount;
        timestamp = Instant.now().toString();
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

    public static List<TradeReport> readReportHistory() {
        return new ArrayList<>();
    }

    public TradeReport logReport() {
        LOGGER.info("=== TRADE REPORT ===");
        LOGGER.info("= Executor Class: {}", executorClass);
        LOGGER.info("= Trade Path: {}", tradePath);
        LOGGER.info("= Expected Profitability: {}", expectedProfitability);
        LOGGER.info("= Actual Profitability: {} (USD {}->{})", actualProfitability, USDBefore, USDAfter);
        LOGGER.info("= Trade size: {} USD", USDTraded);
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
                "Timestamp",
                "USD Before",
                "USD After",
                "USD Traded",
                "Expected Profitability",
                "Actual Profitability",
                "Path",
                "Executor",
                "Trades"
        ).withSkipHeaderRecord(exists));

        csvPrinter.printRecord(
                timestamp,
                USDBefore,
                USDAfter,
                USDTraded,
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

