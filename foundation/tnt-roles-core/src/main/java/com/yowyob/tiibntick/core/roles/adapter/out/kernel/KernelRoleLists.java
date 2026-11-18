package com.yowyob.tiibntick.core.roles.adapter.out.kernel;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Lecture des listes rendues par les endpoints de rôles du noyau.
 *
 * <p>Ces endpoints répondent sous <strong>deux</strong> formes : un tableau nu pour
 * {@code GET /api/roles}, dont le contrôleur déclare un {@code Flux} sans enveloppe, et
 * l'enveloppe {@code {success, data}} ailleurs. Un lecteur qui n'admet qu'une seule des deux rend
 * une liste vide devant l'autre.</p>
 *
 * <p>Ce module en avait deux : l'un tolérant, du côté des attributions, l'autre strict et muet, du
 * côté du provisionnement. Le second lisait un tableau nu comme un échec, le réduisait à une liste
 * vide, et le satellite concluait qu'un rôle pourtant créé « n'était pas provisionné » — trente
 * entrées de file bloquées sur des rôles qui existaient. Le lecteur tolérant portait déjà
 * l'avertissement, « une erreur avalée ici se lirait comme un rôle manquant », mais la correction
 * n'avait pas gagné l'autre chemin. D'où ce lecteur unique : deux lecteurs pour un même endpoint,
 * c'est la prochaine divergence qui attend.</p>
 *
 * <p>Les erreurs HTTP et de syntaxe <strong>se propagent</strong>. Une liste vide et un appel en
 * échec ne disent pas la même chose, et les confondre est ce qui a rendu ce défaut long à voir.</p>
 */
final class KernelRoleLists {

    private static final ObjectMapper LENIENT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private KernelRoleLists() {
    }

    static <T> Flux<T> readList(WebClient.ResponseSpec responseSpec, Class<T> itemType) {
        return readBody(responseSpec)
                .flatMapMany(body -> {
                    JsonNode items = body.isArray() ? body : body.path("data");
                    if (!items.isArray()) {
                        return Flux.empty();
                    }
                    List<T> lus = new ArrayList<>();
                    items.forEach(item -> lus.add(LENIENT_MAPPER.convertValue(item, itemType)));
                    return Flux.fromIterable(lus);
                });
    }

    /** Lit le corps brut et l'analyse ici, pour que le résultat ne dépende pas des codecs du client. */
    private static Mono<JsonNode> readBody(WebClient.ResponseSpec responseSpec) {
        return responseSpec.bodyToMono(String.class).flatMap(raw -> {
            if (raw == null || raw.isBlank()) {
                return Mono.empty();
            }
            try {
                return Mono.just(LENIENT_MAPPER.readTree(raw));
            } catch (Exception malforme) {
                return Mono.error(malforme);
            }
        });
    }
}
