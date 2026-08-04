package br.com.virta.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/** Aggregated financial summary for a wallet's dashboard. */
public record DashboardSummaryResponseDTO(
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal balance,
        long transactionCount,
        List<CategorySummary> byCategory,
        List<MonthSummary> byMonth
) {
    public record CategorySummary(Long categoryId, String categoryName, String categoryColor, BigDecimal total) {}

    public record MonthSummary(String month, BigDecimal income, BigDecimal expense) {}
}
