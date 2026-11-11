package com.yowyob.kernel.i18n.application.service;

import com.yowyob.kernel.i18n.application.port.in.TranslateMessageUseCase;
import com.yowyob.kernel.i18n.application.port.out.MessageTranslationPort;

import com.yowyob.kernel.i18n.domain.format.MessageFormatter;
import com.yowyob.kernel.i18n.domain.model.MessageVariant;

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
        return translate(key, language, parameters, MessageVariant.DEFAULT);
    }

    @Override
    public Optional<String> translate(String key, String language, Map<String, Object> parameters, String variant) {
        String v = MessageVariant.normalize(variant);
        // Plain text keeps using the two-argument lookup: stores (and test doubles) that know no
        // variants implement only that one.
        var found = MessageVariant.DEFAULT.equals(v)
                ? translationPort.findByKeyAndLanguage(key, language)
                : translationPort.findByKeyAndLanguage(key, language, v);
        return found.map(message -> MessageFormatter.format(message.content(), language, parameters));
    }
}
