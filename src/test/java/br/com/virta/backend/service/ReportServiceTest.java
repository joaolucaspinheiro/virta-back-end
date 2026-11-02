package br.com.virta.backend.service;

import br.com.virta.backend.dto.TransactionResponseDTO;
import br.com.virta.backend.exception.BusinessException;
import br.com.virta.backend.model.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit tests for the CSV statement: period/category filters, totals and CSV-injection protection. */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final String EMAIL = "lucas@virta.com";
    private static final Long WALLET_ID = 1L;

    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private ReportService reportService;

    @Test
    void keepsOnlyTheChosenPeriodSortedByDateAndAddsTotals() {
        when(transactionService.list(EMAIL, WALLET_ID, null)).thenReturn(List.of(
                tx("2026-10-05", TransactionType.EXPENSE, "200", "Mercado", 2L, "Alimentação"),
                tx("2026-09-10", TransactionType.INCOME, "1000", "Pagamento", 1L, "Salário"),
                tx("2026-12-01", TransactionType.EXPENSE, "99", "Fora do período", 2L, "Alimentação")));

        String csv = reportService.statementCsv(EMAIL, WALLET_ID,
                YearMonth.of(2026, 9), YearMonth.of(2026, 11), null, null);

        assertThat(csv).startsWith("Data;Tipo;Categoria;Descrição;Valor;Lançado por\n");
        assertThat(csv).contains("10/09/2026;Receita;Salário;Pagamento;1000,00;Lucas");
        assertThat(csv).contains("05/10/2026;Despesa;Alimentação;Mercado;200,00;Lucas");
        assertThat(csv).doesNotContain("Fora do período");
        assertThat(csv.indexOf("10/09/2026")).isLessThan(csv.indexOf("05/10/2026"));
        assertThat(csv).contains("Total de receitas;1000,00");
        assertThat(csv).contains("Total de despesas;200,00");
        assertThat(csv).contains("Saldo do período;800,00");
    }

    @Test
    void filtersBySelectedCategories() {
        when(transactionService.list(EMAIL, WALLET_ID, null)).thenReturn(List.of(
                tx("2026-11-02", TransactionType.EXPENSE, "50", "Uber", 3L, "Transporte"),
                tx("2026-11-03", TransactionType.EXPENSE, "80", "Feira", 2L, "Alimentação"),
                tx("2026-11-04", TransactionType.EXPENSE, "10", "Sem categoria", null, null)));

        String csv = reportService.statementCsv(EMAIL, WALLET_ID,
                YearMonth.of(2026, 11), YearMonth.of(2026, 11), List.of(3L), null);

        assertThat(csv).contains("Uber");
        assertThat(csv).doesNotContain("Feira").doesNotContain("Sem categoria");
        assertThat(csv).contains("Total de despesas;50,00");
    }

    @Test
    void neutralizesFormulasAndQuotesSpecialCharacters() {
        when(transactionService.list(EMAIL, WALLET_ID, null)).thenReturn(List.of(
                tx("2026-11-02", TransactionType.EXPENSE, "1", "=HYPERLINK(\"http://x\")", null, null),
                tx("2026-11-03", TransactionType.EXPENSE, "1", "Mercado; feira", null, null)));

        String csv = reportService.statementCsv(EMAIL, WALLET_ID,
                YearMonth.of(2026, 11), YearMonth.of(2026, 11), null, null);

        // A leading ' stops Excel from running the cell as a formula; inner quotes are doubled.
        assertThat(csv).contains("\"'=HYPERLINK(\"\"http://x\"\")\"");
        // A ";" inside the text must not split the column.
        assertThat(csv).contains("\"Mercado; feira\"");
    }

    @Test
    void rejectsStartMonthAfterEndMonth() {
        assertThatThrownBy(() -> reportService.statementCsv(EMAIL, WALLET_ID,
                YearMonth.of(2026, 11), YearMonth.of(2026, 9), null, null))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(transactionService);
    }

    @Test
    void rejectsPeriodLongerThan36Months() {
        assertThatThrownBy(() -> reportService.statementCsv(EMAIL, WALLET_ID,
                YearMonth.of(2023, 1), YearMonth.of(2026, 1), null, null))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(transactionService);
    }

    private static TransactionResponseDTO tx(String date, TransactionType type, String amount,
                                             String description, Long categoryId, String categoryName) {
        return new TransactionResponseDTO(null, type, new BigDecimal(amount), description,
                LocalDate.parse(date), categoryId, categoryName, null, "Lucas", null);
    }
}
