package br.com.virta.backend.service;

import br.com.virta.backend.dto.CategoryRequestDTO;
import br.com.virta.backend.dto.CategoryResponseDTO;
import br.com.virta.backend.dto.DashboardSummaryResponseDTO;
import br.com.virta.backend.dto.TransactionRequestDTO;
import br.com.virta.backend.dto.TransactionResponseDTO;
import br.com.virta.backend.model.TransactionType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AssistantTools {

    private final CategoryService categoryService;
    private final TransactionService transactionService;
    private final Validator validator;

    public AssistantTools(CategoryService categoryService,
                          TransactionService transactionService,
                          Validator validator) {
        this.categoryService = categoryService;
        this.transactionService = transactionService;
        this.validator = validator;
    }

    /** The "menu" sent to Gemini: what each tool does and which arguments it takes. */
    public List<Map<String, Object>> declarations() {
        return List.of(
                tool("create_category",
                        "Creates a new category for the user. Use it when the user asks to create a category.",
                        Map.of(
                                "name", param("string", "Category name, e.g. Academia"),
                                "type", enumParam("INCOME for money coming in, EXPENSE for money going out",
                                        "INCOME", "EXPENSE")),
                        List.of("name", "type")),
                tool("create_transaction",
                        "Records an income or an expense in the current wallet. Use it when the user "
                                + "says they spent, paid, received or earned money.",
                        Map.of(
                                "type", enumParam("INCOME for money received, EXPENSE for money spent",
                                        "INCOME", "EXPENSE"),
                                "amount", param("number", "Positive amount in BRL, e.g. 45.90"),
                                "description", param("string", "Short description, e.g. Mercado"),
                                "date", param("string", "Date as YYYY-MM-DD. Omit it for today."),
                                "categoryName", param("string",
                                        "Name of one of the user's existing categories. Omit it if none fits.")),
                        List.of("type", "amount", "description")),
                tool("get_summary",
                        "Returns totals (income, expense, balance), totals per category and per month "
                                + "for the current wallet. Use it to answer questions like 'how much did I spend'.",
                        Map.of(
                                "startDate", param("string", "First day as YYYY-MM-DD. Omit both dates for all time."),
                                "endDate", param("string", "Last day as YYYY-MM-DD."),
                                "type", enumParam("Only INCOME or only EXPENSE. Omit it for both.",
                                        "INCOME", "EXPENSE")),
                        List.of()),
                tool("list_recent_transactions",
                        "Lists the most recent transactions of the current wallet, newest first.",
                        Map.of("limit", param("integer", "How many to return, from 1 to 20. Default 10.")),
                        List.of())
        );
    }

    /** Runs the tool Gemini asked for. Errors go back to Gemini, which explains them to the user. */
    public Map<String, Object> execute(String name, Map<String, Object> args, String email, Long walletId) {
        try {
            return switch (name) {
                case "create_category" -> createCategory(email, args);
                case "create_transaction" -> createTransaction(email, walletId, args);
                case "get_summary" -> getSummary(email, walletId, args);
                case "list_recent_transactions" -> listRecentTransactions(email, walletId, args);
                default -> Map.of("error", "Unknown tool: " + name);
            };
        } catch (Exception e) {
            return Map.of("error", e.getMessage() == null ? "Unexpected error." : e.getMessage());
        }
    }

    private Map<String, Object> createCategory(String email, Map<String, Object> args) {
        CategoryRequestDTO dto = new CategoryRequestDTO(text(args, "name"), type(args), null, null);
        validate(dto);
        CategoryResponseDTO created = categoryService.create(email, dto);
        return Map.of("created", true, "id", created.id(), "name", created.name(), "type", created.type().name());
    }

    private Map<String, Object> createTransaction(String email, Long walletId, Map<String, Object> args) {
        TransactionType type = type(args);
        Long categoryId = categoryId(email, type, text(args, "categoryName"));
        LocalDate date = args.get("date") == null ? today() : date(args, "date");

        TransactionRequestDTO dto = new TransactionRequestDTO(
                type, decimal(args, "amount"), text(args, "description"), date, categoryId);
        validate(dto);
        TransactionResponseDTO created = transactionService.create(email, walletId, dto);
        return Map.of("created", true, "id", created.id(), "type", created.type().name(),
                "amount", created.amount(), "date", created.date().toString(),
                "category", created.categoryName() == null ? "none" : created.categoryName());
    }

    /** Finds one of the user's categories by name (ignoring case); null when no name was given. */
    private Long categoryId(String email, TransactionType type, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        List<CategoryResponseDTO> categories = categoryService.list(email, type);
        return categories.stream()
                .filter(c -> c.name().equalsIgnoreCase(name))
                .map(CategoryResponseDTO::id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Category '" + name + "' not found. Existing "
                        + type + " categories: " + categories.stream().map(CategoryResponseDTO::name).toList()));
    }

    private Map<String, Object> getSummary(String email, Long walletId, Map<String, Object> args) {
        LocalDate start = date(args, "startDate");
        LocalDate end = date(args, "endDate");
        if (start != null && end == null) {
            end = today();
        }
        TransactionType type = args.get("type") == null ? null : type(args);

        DashboardSummaryResponseDTO s = transactionService.summary(email, walletId, start, end, null, type);
        // Only what the answer needs: no ids, colors or names of who created each transaction (LGPD).
        return Map.of(
                "totalIncome", s.totalIncome(),
                "totalExpense", s.totalExpense(),
                "balance", s.balance(),
                "transactionCount", s.transactionCount(),
                "byCategory", s.byCategory().stream()
                        .map(c -> Map.of("category", c.categoryName(), "total", c.total()))
                        .toList(),
                "byMonth", s.byMonth().stream()
                        .map(m -> Map.of("month", m.month(), "income", m.income(), "expense", m.expense()))
                        .toList());
    }

    private Map<String, Object> listRecentTransactions(String email, Long walletId, Map<String, Object> args) {
        long requested = args.get("limit") == null ? 10 : Math.round(Double.parseDouble(args.get("limit").toString()));
        long limit = Math.max(1, Math.min(20, requested)); // never trust the size the AI asks for

        List<Map<String, Object>> items = transactionService.list(email, walletId, null).stream()
                .sorted(Comparator.comparing(TransactionResponseDTO::date).reversed())
                .limit(limit)
                .map(t -> Map.<String, Object>of(
                        "date", t.date().toString(),
                        "type", t.type().name(),
                        "amount", t.amount(),
                        "description", t.description() == null ? "" : t.description(),
                        "category", t.categoryName() == null ? "none" : t.categoryName()))
                .toList();
        return Map.of("transactions", items);
    }

    // ---------- helpers ----------

    /**
     * Calling a service directly skips the @Valid of the controllers, so the tools
     * validate the DTO themselves: the AI goes through the same checks as the API.
     */
    private void validate(Object dto) {
        Set<ConstraintViolation<Object>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining("; ")));
        }
    }

    private static TransactionType type(Map<String, Object> args) {
        String value = text(args, "type");
        if (value == null) {
            throw new IllegalArgumentException("type is required (INCOME or EXPENSE).");
        }
        return TransactionType.valueOf(value.toUpperCase());
    }

    private static String text(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? null : value.toString().trim();
    }

    private static BigDecimal decimal(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? null : new BigDecimal(value.toString());
    }

    private static LocalDate date(Map<String, Object> args, String key) {
        String value = text(args, key);
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    /** Today in Brazil: the server (Render) runs in UTC, where 10 p.m. here is already tomorrow. */
    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    }

    private static Map<String, Object> tool(String name, String description,
                                            Map<String, Object> properties, List<String> required) {
        return Map.of("name", name, "description", description,
                "parameters", Map.of("type", "object", "properties", properties, "required", required));
    }

    private static Map<String, Object> param(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    private static Map<String, Object> enumParam(String description, String... values) {
        return Map.of("type", "string", "description", description, "enum", List.of(values));
    }
}