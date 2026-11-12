package br.com.virta.backend.service;

import br.com.virta.backend.dto.CategoryRequestDTO;
import br.com.virta.backend.dto.CategoryResponseDTO;
import br.com.virta.backend.model.TransactionType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

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
                        List.of("name", "type"))
        );
    }

    /** Runs the tool Gemini asked for. Errors go back to Gemini, which explains them to the user. */
    public Map<String, Object> execute(String name, Map<String, Object> args, String email, Long walletId) {
        try {
            return switch (name) {
                case "create_category" -> createCategory(email, args);
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