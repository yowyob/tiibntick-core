package com.yowyob.tiibntick.core.gofreelancer.adapter;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Announcement;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L2 — the gofp announcement mirror and the need's stored state are gone from the code: nothing
 * maps, writes or reads the {@code announcements} / {@code announcement_subscriptions} tables on the
 * announcement path (migrations excepted), and the need's status, delivery and courier are not columns.
 */
@DisplayName("Plus de miroir local d'annonce")
class AnnouncementMirrorRemovalTest {

    private static final Path MAIN = Path.of("src/main/java/com/yowyob/tiibntick/core/gofreelancer");

    private static List<Path> sources() throws IOException {
        try (Stream<Path> s = Files.walk(MAIN)) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    @DisplayName("Announcement n'est plus une entité persistée")
    void announcementIsNotMapped() {
        assertThat(Announcement.class.isAnnotationPresent(Table.class)).isFalse();
    }

    @Test
    @DisplayName("aucun dépôt R2DBC ni SQL n'écrit ou ne lit la table announcements")
    void noRepositoryOrSqlOnTheMirror() throws IOException {
        Pattern repo = Pattern.compile("Repository<\\s*Announcement\\s*,");
        Pattern sql = Pattern.compile("(?i)(insert\\s+into|update|delete\\s+from|from|join)\\s+announcements\\b");
        for (Path p : sources()) {
            String code = Files.readString(p);
            assertThat(repo.matcher(code).find()).as(p.toString()).isFalse();
            assertThat(sql.matcher(code).find()).as(p.toString()).isFalse();
        }
    }

    @Test
    @DisplayName("le service d'annonce ne lit plus announcement_subscriptions ni le miroir")
    void announcementServiceReadsTheCoreOnly() throws IOException {
        String code = Files.readString(MAIN.resolve("application/service/AnnouncementApplicationService.java"));
        assertThat(code).doesNotContain("AnnouncementSubscriptionRepository")
                .doesNotContain("softMirrorLocal")
                .doesNotContain("enrichFromLocalMirror")
                .doesNotContain("softMirrorAssigned")
                .doesNotContain("IAnnouncementRepository");
    }

    @Test
    @DisplayName("statut, livraison et livreur du besoin ne sont pas des colonnes")
    void needStateIsDerived() throws NoSuchFieldException {
        for (String field : List.of("status", "deliveryId", "assignedFreelancerId")) {
            assertThat(DeliveryNeed.class.getDeclaredField(field).isAnnotationPresent(Transient.class))
                    .as(field).isTrue();
        }
    }
}
