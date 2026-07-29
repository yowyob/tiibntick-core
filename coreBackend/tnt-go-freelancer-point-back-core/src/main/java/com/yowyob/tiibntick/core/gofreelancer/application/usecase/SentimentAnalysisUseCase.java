package com.yowyob.tiibntick.core.gofreelancer.application.usecase;

import ai.djl.Application;
import ai.djl.ModelException;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import com.yowyob.tiibntick.core.gofreelancer.config.SentimentModelConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sentiment analysis service backed by the HuggingFace model
 * {@code nlptown/bert-base-multilingual-uncased-sentiment} loaded via DJL.
 *
 * <p>The model outputs a classification label in the form "N star(s)" (1-5),
 * which is parsed directly into the integer rating expected by
 * {@link com.yowyob.tiibntick.core.gofreelancer.application.service.EvaluationApplicationService}.
 *
 * <p>Model loading is performed asynchronously at startup so the application
 * is never blocked waiting for the ~400 MB download on first run.
 * Until the model is ready (or if loading fails), the keyword-based fallback
 * is used transparently — no error is surfaced to the caller.
 *
 * <p>The DJL cache directory is configurable via {@code tnt.sentiment.cache-dir}
 * so that a Docker volume can be mounted to persist the model across restarts.
 *
 * @author François-Charles ATANGA
 * @see SentimentModelConfig
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentimentAnalysisUseCase {

    // ── Keyword fallback (used while model loads or when DJL is disabled) ──

    private static final List<String> POSITIVE_WORDS = Arrays.asList(
            "excellent", "super", "bien", "parfait", "rapide", "courtois", "top",
            "génial", "merci", "pro", "professionnel", "recommande", "bon", "bonne",
            "satisfait", "ponctuel", "agréable", "efficace", "sérieux", "fiable"
    );

    private static final List<String> NEGATIVE_WORDS = Arrays.asList(
            "nul", "retard", "cassé", "mauvais", "lent", "désagréable", "catastrophique",
            "pire", "dommage", "incompétent", "déçu", "déception", "vol", "arnaque",
            "irresponsable", "impoli", "absent", "inexact", "abîmé", "endommagé"
    );

    // ── DJL state ──────────────────────────────────────────────────────────

    private final SentimentModelConfig config;

    private ZooModel<String, Classifications> model;
    private final AtomicBoolean modelReady = new AtomicBoolean(false);

    // ── Lifecycle ──────────────────────────────────────────────────────────

    /**
     * Loads the model asynchronously so the application starts without waiting.
     * Uses a bounded-elastic thread to avoid blocking the event loop.
     */
    @PostConstruct
    public void initModel() {
        if (!config.isEnabled()) {
            log.info("[Sentiment] DJL disabled — keyword fallback will be used for all requests.");
            return;
        }

        // Set DJL cache directory from config
        System.setProperty("DJL_CACHE_DIR", config.getCacheDir());

        Mono.fromRunnable(this::loadModel)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        ok -> {},
                        err -> log.warn("[Sentiment] Model failed to load — keyword fallback active. Cause: {}", err.getMessage())
                );
    }

    @PreDestroy
    public void closeModel() {
        if (model != null) {
            model.close();
            log.info("[Sentiment] DJL model closed.");
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Predicts a 1-5 star rating from a free-text comment.
     *
     * <p>Uses the DJL BERT model when ready, otherwise falls back to the
     * keyword scorer. Executes on a bounded-elastic scheduler to avoid
     * blocking the WebFlux event loop.
     *
     * @param comment the user's comment (any language, French recommended)
     * @return {@code Mono<Integer>} emitting a rating between 1 and 5,
     *         or empty if the comment is blank
     */
    public Mono<Integer> predictRating(String comment) {
        if (comment == null || comment.trim().isEmpty()) {
            return Mono.empty();
        }

        if (modelReady.get()) {
            return Mono.fromCallable(() -> predictWithDjl(comment))
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnSuccess(r -> log.info("[Sentiment/DJL] '{}' → {} stars", truncate(comment), r))
                    .onErrorResume(e -> {
                        log.warn("[Sentiment/DJL] Inference failed, using fallback. Cause: {}", e.getMessage());
                        return Mono.just(calculateKeywordScore(comment.toLowerCase()));
                    });
        }

        // Model not ready yet — use keyword fallback
        return Mono.fromCallable(() -> calculateKeywordScore(comment.toLowerCase()))
                .doOnSuccess(r -> log.info("[Sentiment/Keyword] '{}' → {} stars", truncate(comment), r));
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private void loadModel() {
        try {
            log.info("[Sentiment] Loading model '{}' via DJL (cache: {})...",
                    config.getModelName(), config.getCacheDir());

            Criteria<String, Classifications> criteria = Criteria.builder()
                    .optApplication(Application.NLP.SENTIMENT_ANALYSIS)
                    .setTypes(String.class, Classifications.class)
                    .optModelUrls("djl://ai.djl.huggingface.pytorch/" + config.getModelName())
                    .optEngine("PyTorch")
                    .build();

            model = criteria.loadModel();
            modelReady.set(true);
            log.info("[Sentiment] Model '{}' loaded and ready.", config.getModelName());

        } catch (IOException | ModelException e) {
            log.warn("[Sentiment] Could not load DJL model. Keyword fallback will be used. Cause: {}", e.getMessage());
        }
    }

    private int predictWithDjl(String comment) throws TranslateException {
        try (Predictor<String, Classifications> predictor = model.newPredictor()) {
            Classifications result = predictor.predict(comment);
            // Label format from nlptown model: "1 star", "2 stars", ... "5 stars"
            String topLabel = result.best().getClassName();
            return parseStarLabel(topLabel);
        }
    }

    /**
     * Parses "N star(s)" labels from nlptown model output into integer 1-5.
     * Falls back to keyword score if parsing fails.
     */
    private int parseStarLabel(String label) {
        try {
            // "1 star" → 1, "2 stars" → 2, ..., "5 stars" → 5
            String digit = label.trim().substring(0, 1);
            int rating = Integer.parseInt(digit);
            if (rating >= 1 && rating <= 5) return rating;
        } catch (Exception e) {
            log.warn("[Sentiment] Could not parse label '{}', using 3 as default.", label);
        }
        return 3;
    }

    private int calculateKeywordScore(String text) {
        int pos = 0;
        int neg = 0;
        for (String word : text.split("\\W+")) {
            if (POSITIVE_WORDS.contains(word)) pos++;
            else if (NEGATIVE_WORDS.contains(word)) neg++;
        }
        int score = 3 + pos - neg;
        score = Math.max(1, Math.min(5, score));
        if (pos > 0 && neg == 0 && score < 4) return 4;
        if (neg > 0 && pos == 0 && score > 2) return 2;
        return score;
    }

    private String truncate(String s) {
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }
}
