package br.com.virta.backend.service;

import br.com.virta.backend.event.TransactionChangedEvent;
import jakarta.transaction.Transaction;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Avisa em tempo real (SSE) quem está com uma carteira aberta.
 * Um SseEmitter é UMA conexão aberta com UM navegador.
 * Pense num "cano" que sai do servidor e chega numa aba do navegador:
 * o servidor pode mandar mensagens por ele a qualquer momento.
 */
@Service
public class WalletEventService {

    private final Map<Long, List<SseEmitter>> emittersByWallet = new ConcurrentHashMap<>();


    public SseEmitter subscribe(Long walletId) {
        List<SseEmitter> emitters = emittersByWallet.computeIfAbsent(walletId, id -> new CopyOnWriteArrayList<>());

        SseEmitter emitter = new SseEmitter(0L); // 0L = sem tempo limite
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        return emitter;
    }

    public void publish(Long walletId, String eventName) {
        List<SseEmitter> emitters = emittersByWallet.get(walletId);
        if (emitters == null) {
            return; // ninguém está com essa carteira aberta
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(walletId));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void onTransactionChanged(TransactionChangedEvent event){
            publish(event.walletId(), "transaction-changed");

    }
}
