package br.com.virta.backend.service;

import br.com.virta.backend.dto.AssistantMessageDTO;
import br.com.virta.backend.dto.AssistantReplyDTO;
import br.com.virta.backend.dto.WalletResponseDTO;
import br.com.virta.backend.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Chat assistant: sends the conversation to Gemini and, when Gemini asks for a tool,
 * runs it and sends the result back, until Gemini answers with text.
 */
@Service
public class AssistantService {

    /** Safety limit: at most 5 tool rounds per message, so a confused model can't loop forever. */
    private static final int MAX_TOOL_ROUNDS = 5;
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final GeminiClient gemini;
    private final AssistantTools tools;
    private final WalletService walletService;

    public AssistantService(GeminiClient gemini, AssistantTools tools, WalletService walletService) {
        this.gemini = gemini;
        this.tools = tools;
        this.walletService = walletService;
    }

    public AssistantReplyDTO chat(String email, Long walletId, List<AssistantMessageDTO> history) {
        if (history.isEmpty() || !"user".equals(history.get(history.size() - 1).role())) {
            throw new BusinessException("The last message must come from the user.");
        }
        // Also checks that the user is a member of the wallet.
        WalletResponseDTO wallet = walletService.get(email, walletId);
        String instructions = instructions(wallet);

        List<Map<String, Object>> contents = new ArrayList<>();
        for (AssistantMessageDTO message : history) {
            contents.add(Map.of("role", message.role(), "parts", List.of(Map.of("text", message.text()))));
        }

        List<String> actions = new ArrayList<>();
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            List<Map<String, Object>> parts = gemini.generate(instructions, contents, tools.declarations());
            List<Map<String, Object>> calls = parts.stream()
                    .filter(part -> part.get("functionCall") != null)
                    .toList();

            if (calls.isEmpty()) {
                return new AssistantReplyDTO(textOf(parts), actions);
            }

            // 1) Gemini's turn, exactly as it came (keeps hidden fields such as thought signatures).
            contents.add(Map.of("role", "model", "parts", parts));

            // 2) Run every tool it asked for and answer with the results.
            List<Map<String, Object>> responses = new ArrayList<>();
            for (Map<String, Object> part : calls) {
                Map<String, Object> call = asMap(part.get("functionCall"));
                String name = String.valueOf(call.get("name"));
                Map<String, Object> result = tools.execute(name, asMap(call.get("args")), email, walletId);
                if (Boolean.TRUE.equals(result.get("created"))) {
                    actions.add(name);
                }
                responses.add(Map.of("functionResponse", Map.of("name", name, "response", result)));
            }
            contents.add(Map.of("role", "user", "parts", responses));
        }
        return new AssistantReplyDTO("Não consegui concluir o pedido. Pode reformular?", actions);
    }

    private String instructions(WalletResponseDTO wallet) {
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        return """
                Você é o assistente financeiro do Virta, um app de finanças pessoais e compartilhadas.
                Hoje é %s, %s (formato ISO: %s).
                A carteira aberta se chama "%s" e o papel do usuário nela é %s.

                Regras:
                - Responda sempre em português do Brasil, de forma curta e simpática.
                - Use as ferramentas para consultar ou registrar dados. Nunca invente valores.
                - Valores são em reais; escreva como R$ 1.234,56.
                - Converta datas como "ontem" ou "dia 5" para YYYY-MM-DD usando a data de hoje.
                - Se faltar algo essencial para registrar (o valor, ou se é receita ou despesa), pergunte antes.
                - Se uma ferramenta devolver "error", explique o problema com palavras simples.
                - Fale apenas sobre as finanças do usuário neste app.
                """.formatted(
                today.getDayOfWeek().getDisplayName(TextStyle.FULL, PT_BR),
                today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                today,
                wallet.name(),
                wallet.role());
    }

    private static String textOf(List<Map<String, Object>> parts) {
        String text = parts.stream()
                .map(part -> part.get("text"))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.joining())
                .trim();
        return text.isEmpty() ? "Pronto!" : text;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}