import java.math.BigDecimal;
import java.text.NumberFormat;

public class Market {
    public final String from;
    public final String to;
    public final String exchange;
    private BigDecimal rate;

    public Market(String from, String to, String exchange) {
        this.from = from;
        this.to = to;
        this.exchange = exchange;
    }

    public Market setRate(BigDecimal rate) {
        this.rate = rate;
        return this;
    }

    @Override
    public String toString() {

        return "Market{" +
                "from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", exchange='" + exchange + '\'' +
                ", rate=" + NumberFormat.getCurrencyInstance().format(rate) +
                '}';
    }
}
