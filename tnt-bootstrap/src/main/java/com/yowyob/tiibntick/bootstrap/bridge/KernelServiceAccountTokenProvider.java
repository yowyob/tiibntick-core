package com.yowyob.tiibntick.bootstrap.bridge;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Fournit le jeton du compte de service TiiBnTick auprès du noyau, et le garde en cache.
 *
 * <h2>Pourquoi ce fournisseur existe</h2>
 *
 * <p>Le worker de synchronisation des rôles appelait le noyau avec la seule clé d'API. Or la porte
 * {@code canManageIdentity} du noyau exige un {@code userId} non nul : elle refusait donc en
 * <b>403 avant même de regarder les permissions</b>, et les entrées de l'outbox finissaient
 * {@code DEAD} — les rôles du domaine (MEDIATOR, FREELANCER…) n'étaient jamais propagés.</p>
 *
 * <p>Le noyau attend un <b>utilisateur machine</b>, comme pour VerifID et la boutique. Ce
 * fournisseur ouvre donc une session avec les identifiants du compte {@code tnt-service} et
 * réutilise son jeton jusqu'à l'approche de l'expiration.</p>
 *
 * <p>Le cache est volontairement simple : un {@link AtomicReference}. Deux appels concurrents
 * peuvent se connecter deux fois au démarrage, ce qui est sans conséquence (le noyau émet deux
 * sessions valides) et évite un verrou sur un chemin réactif.</p>
 */
public class KernelServiceAccountTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(KernelServiceAccountTokenProvider.class);

    /** On renouvelle avant l'échéance : un jeton qui expire en vol coûterait un 401 et un retry. */
    private static final Duration MARGE_RENOUVELLEMENT = Duration.ofMinutes(5);

    private final WebClient kernelWebClient;
    private final String username;
    private final String password;
    private final UUID tenantId;
    private final AtomicReference<JetonEnCache> cache = new AtomicReference<>(null);

    public KernelServiceAccountTokenProvider(WebClient kernelWebClient, String username, String password,
            UUID tenantId) {
        this.kernelWebClient = kernelWebClient;
        this.username = username;
        this.password = password;
        this.tenantId = tenantId;
    }

    /** @return le jeton d'accès, ou un Mono vide si le compte de service n'est pas configuré. */
    public Mono<String> accessToken() {
        if (!estConfigure()) {
            return Mono.empty();
        }
        JetonEnCache courant = cache.get();
        if (courant != null && courant.estEncoreValide()) {
            return Mono.just(courant.accessToken());
        }
        return seConnecter();
    }

    public boolean estConfigure() {
        return username != null && !username.isBlank() && password != null && !password.isBlank();
    }

    private Mono<String> seConnecter() {
        return kernelWebClient.post()
                .uri("/api/auth/login")
                .header("X-Tenant-Id", tenantId.toString())
                .bodyValue(Map.of("principal", username, "password", password))
                .retrieve()
                .bodyToMono(ReponseConnexion.class)
                .flatMap(reponse -> {
                    String jeton = reponse.jetonAcces();
                    if (jeton == null || jeton.isBlank()) {
                        // Le noyau a répondu 200 sans jeton : MFA réclamé, ou compte non vérifié.
                        // On le dit clairement plutôt que de laisser un 403 inexpliqué plus loin.
                        log.error("Le noyau n'a pas renvoye de jeton pour le compte de service '{}'. "
                                + "Le compte doit etre actif, verifie et dispense de MFA.", username);
                        return Mono.empty();
                    }
                    long duree = reponse.dureeSecondes() > 0 ? reponse.dureeSecondes() : 3600L;
                    cache.set(new JetonEnCache(jeton, Instant.now().plusSeconds(duree)));
                    log.info("Session du compte de service '{}' ouverte aupres du noyau ({} s).", username, duree);
                    return Mono.just(jeton);
                })
                .onErrorResume(exception -> {
                    log.error("Connexion du compte de service '{}' au noyau impossible : {}",
                            username, exception.getMessage());
                    return Mono.empty();
                });
    }

    private record JetonEnCache(String accessToken, Instant expiration) {
        boolean estEncoreValide() {
            return Instant.now().isBefore(expiration.minus(MARGE_RENOUVELLEMENT));
        }
    }

    /**
     * Le noyau enveloppe sa reponse dans {@code ApiResponse}, d'ou le niveau {@code data}. On ne lit
     * que les deux champs utiles ; tout le reste du profil est ignore.
     */
    private record ReponseConnexion(Donnees data) {
        String jetonAcces() {
            return data == null ? null : data.accessToken();
        }

        long dureeSecondes() {
            return data == null ? 0L : data.expiresInSeconds();
        }

        private record Donnees(String accessToken, long expiresInSeconds) {
        }
    }
}
