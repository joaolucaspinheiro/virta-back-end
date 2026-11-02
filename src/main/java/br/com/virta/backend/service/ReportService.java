package br.com.virta.backend.service;

import br.com.virta.backend.dto.TransactionResponseDTO;
import br.com.virta.backend.exception.BusinessException;
import br.com.virta.backend.model.TransactionType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Service
public class ReportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final long MAX_MONTHS = 36;

    private final TransactionService transactionService;

    public ReportService(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * Statement for a period of months, in CSV (";" separator and decimal comma,
     * the pt-BR Excel standard). Optional filters: categories (any of them) and type.
     */
    public String statementCsv(String email, Long walletId, YearMonth from, YearMonth to,
                               List<Long> categoryIds, TransactionType type) {
        if (from.isAfter(to)) {
            throw new BusinessException("The start month must not be after the end month.");
        }
        if (ChronoUnit.MONTHS.between(from, to) >= MAX_MONTHS) {
            throw new BusinessException("The period can have at most " + MAX_MONTHS + " months.");
        }

        // list(...) already checks that the user is a member of the wallet.
        List<TransactionResponseDTO> transactions = transactionService.list(email, walletId, type).stream()
                .filter(t -> {
                    YearMonth month = YearMonth.from(t.date());
                    return !month.isBefore(from) && !month.isAfter(to);
                })
                .filter(t -> categoryIds == null || categoryIds.isEmpty() || categoryIds.contains(t.categoryId()))
                .sorted(Comparator.comparing(TransactionResponseDTO::date))
                .toList();

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        StringBuilder csv = new StringBuilder("Data;Tipo;Categoria;Descrição;Valor;Lançado por\n");
        for (TransactionResponseDTO t : transactions) {
            boolean isIncome = t.type() == TransactionType.INCOME;
            if (isIncome) {
                income = income.add(t.amount());
            } else {
                expense = expense.add(t.amount());
            }
            csv.append(t.date().format(DATE)).append(';')
                    .append(isIncome ? "Receita" : "Despesa").append(';')
                    .append(cell(t.categoryName())).append(';')
                    .append(cell(t.description())).append(';')
                    .append(money(t.amount())).append(';')
                    .append(cell(t.createdByName())).append('\n');
        }

        csv.append('\n')
                .append(";;;Total de receitas;").append(money(income)).append(";\n")
                .append(";;;Total de despesas;").append(money(expense)).append(";\n")
                .append(";;;Saldo do período;").append(money(income.subtract(expense))).append(";\n");
        return csv.toString();
    }

    /**
     * Escapes a cell: quotes, ";" and line breaks, and neutralizes formulas
     * (CSV injection, OWASP): =, +, -, @, tab and carriage return.
     */
    private static String cell(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(";") || v.contains("\"") || v.contains("\n")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
