package dev.natsoft.arbitrage.Reports;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import dev.natsoft.arbitrage.Constants;
import dev.natsoft.arbitrage.SimpleMarketTradeExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@JsonPropertyOrder({
        "timestamp",
        "USDBefore",
        "USDAfter",
        "USDTraded",
        "expectedProfitability",
        "actualProfitability",
        "tradePath",
        "executorClass",
        "trades"
})
public class TradeReport {
    private static final Logger LOGGER = LoggerFactory.getLogger(TradeReport.class);
    private static final File reportFile = new File("tmp/trades_report.csv");

    @JsonIgnore
    public SimpleMarketTradeExecutor te;

    @JsonProperty("Timestamp")
    public String timestamp;
    @JsonProperty("USD Traded")
    public String USDTraded;
    @JsonProperty("Executor")
    public String executorClass;
    @JsonProperty("Path")
    public String tradePath;
    @JsonProperty("Expected Profitability")
    public String expectedProfitability;
    @JsonProperty("Actual Profitability")
    public String actualProfitability;
    @JsonProperty("USD Before")
    public String USDBefore;
    @JsonProperty("USD After")
    public String USDAfter;
    @JsonProperty("Trades")
    public String trades;

    // Required for Jackson serialization
    public TradeReport() {
    }

    public TradeReport(SimpleMarketTradeExecutor te) {
        this.te = te;

        USDTraded = Constants.DF.format(te.firstUSDAmount);
        timestamp = Instant.now().toString();
        executorClass = te.getClass().toString();
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
        try {
            CsvMapper mapper = new CsvMapper();
            CsvSchema schema = mapper.schemaFor(TradeReport.class).withSkipFirstDataRow(true);
            ObjectReader reader = mapper.readerFor(TradeReport.class).with(schema);
            MappingIterator<TradeReport> it = reader.readValues(reportFile);

            return it.readAll();
        } catch (IOException e) {
            LOGGER.error("Can't read TradeReport history CSV: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }


    public TradeReport saveReport() throws IOException {
        boolean exists = reportFile.exists();

        CsvMapper mapper = new CsvMapper();
        CsvSchema schema = mapper.schemaFor(TradeReport.class).withUseHeader(!exists);
        ObjectWriter myObjectWriter = mapper.writer(schema);

        FileOutputStream tempFileOutputStream = new FileOutputStream(reportFile, true);
        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(tempFileOutputStream, 1024);
        OutputStreamWriter writerOutputStream = new OutputStreamWriter(bufferedOutputStream, StandardCharsets.UTF_8);
        myObjectWriter.writeValue(writerOutputStream, this);

        return this;
    }

    public TradeReport logReport() {
        LOGGER.info("=== TRADE REPORT ===");
        LOGGER.info("= Executor Class: {}", executorClass);
        LOGGER.info("= Trade Path: {}", tradePath);
        LOGGER.info("= Expected Profitability: {}", expectedProfitability);
        LOGGER.info("= Actual Profitability: {} (USD {}->{})", actualProfitability, USDBefore, USDAfter);
        LOGGER.info("= Trade size: {} USD", USDTraded);
        if (te != null) {
            te.getExecutedTrades().forEach(trade -> {
                LOGGER.info("== Trade: {}", trade);
            });
        } else {
            LOGGER.info("== Trades: {}", trades);
        }
        LOGGER.info("=== END REPORT ===");

        return this;
    }
}
