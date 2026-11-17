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
    /**
     * La session, mise en cache ET partagée.
     *
     * <p>{@code cacheInvalidateIf} fait deux choses, et les deux sont nécessaires : il garde le
     * jeton jusqu'à son renouvellement, et il <strong>réunit les abonnements concurrents sur une
     * seule exécution</strong>. Sans ce second point, chaque appel ouvrait sa propre session :
     * rejouer vingt entrées de la file déclenchait vingt connexions simultanées, chacune avec son
     * hachage de mot de passe à vérifier côté noyau. Aucune n'avait le temps d'aboutir dans le
     * budget de l'appel qui la portait, le cache ne se remplissait donc jamais, et le
     * disjoncteur finissait par s'ouvrir — en masquant la cause derrière des
     * {@code CallNotPermittedException}.</p>
     */
    private final Mono<JetonEnCache> session = Mono.defer(this::ouvrirSession)
            // Jamais vide : cacheInvalidateIf LÈVE sur une source vide
            // (« cacheInvalidateWhen expects a value, source completed empty »), et une ouverture
            // de session refusée complète précisément à vide. Sans cette sentinelle, un simple
            // 401 du noyau se transformait en erreur de transport, comptée par le disjoncteur,
            // qui s'ouvrait et masquait le refus d'origine : les journaux ne montraient plus que
            // des CallNotPermittedException, sans dire que l'identifiant était rejeté.
            .defaultIfEmpty(JetonEnCache.ECHEC)
            .cacheInvalidateIf(JetonEnCache::doitEtreRenouvele);

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
        // Mono vide sur la sentinelle : l'appel part sans en-tête d'autorisation et le worker
        // réessaiera, ce qui vaut mieux qu'une erreur de transport imputée au noyau.
        return session.flatMap(jeton -> Mono.justOrEmpty(jeton.accessToken()));
    }

    public boolean estConfigure() {
        return username != null && !username.isBlank() && password != null && !password.isBlank();
    }

    private Mono<JetonEnCache> ouvrirSession() {
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
                    log.info("Session du compte de service '{}' ouverte aupres du noyau ({} s).", username, duree);
                    return Mono.just(new JetonEnCache(jeton, Instant.now().plusSeconds(duree)));
                })
                .onErrorResume(exception -> {
                    log.error("Connexion du compte de service '{}' au noyau impossible : {}",
                            username, exception.getMessage());
                    return Mono.empty();
                });
    }

    private record JetonEnCache(String accessToken, Instant expiration) {

        /** Échec d'ouverture : déjà périmée, donc réessayée au prochain abonnement. */
        private static final JetonEnCache ECHEC = new JetonEnCache(null, Instant.EPOCH);

        /** Vrai quand le jeton doit être réouvert : le cache partagé s'invalide alors seul. */
        boolean doitEtreRenouvele() {
            return !Instant.now().isBefore(expiration.minus(MARGE_RENOUVELLEMENT));
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
