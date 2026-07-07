package com.yowyob.kernel.i18n.application.service;

import com.yowyob.kernel.i18n.application.port.in.TranslateMessageUseCase;
import com.yowyob.kernel.i18n.application.port.out.MessageTranslationPort;

import java.util.Map;
import java.util.Optional;

/**
 * Application service implementing message translation.
 * Pure domain service — no Spring annotations, injectable via AutoConfiguration.
 *
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
public class TranslationService implements TranslateMessageUseCase {

    private MessageTranslationPort translationPort;

    public TranslationService(MessageTranslationPort translationPort) {
        this.translationPort = translationPort;
    }

    @Override
    public Optional<String> translate(String key, String language) {
        return translate(key, language, Map.of());
    }

    @Override
    public Optional<String> translate(String key, String language, Map<String, Object> parameters) {
        return translationPort.findByKeyAndLanguage(key, language)
                .map(message -> message.interpolate(parameters));
    }
}
