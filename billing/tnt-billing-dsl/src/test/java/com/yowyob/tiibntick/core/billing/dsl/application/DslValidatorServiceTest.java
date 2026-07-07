package com.yowyob.tiibntick.core.billing.dsl.application;

import com.yowyob.tiibntick.core.billing.dsl.application.service.DslAccessValidator;
import com.yowyob.tiibntick.core.billing.dsl.application.service.DslCompilerService;
import com.yowyob.tiibntick.core.billing.dsl.application.service.DslValidatorService;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.ValidationError;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.lexer.DslLexer;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.parser.DslParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DslValidatorService}.
 *
 * @author MANFOUO Braun
 */
@DisplayName("DslValidatorService")
class DslValidatorServiceTest {

    private DslValidatorService validatorService;

    @BeforeEach
    void setUp() {
        DslLexer lexer = new DslLexer();
        DslParser parser = new DslParser();
        DslCompilerService compiler = new DslCompilerService(lexer, parser);
        DslAccessValidator accessValidator = new DslAccessValidator();
        validatorService = new DslValidatorService(compiler, accessValidator);
    }

    @Test
    @DisplayName("valid condition should return empty error list")
    void testValidCondition() {
        List<ValidationError> errors = validatorService.validateCondition("weight <= 5 AND distance <= 10");
        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("blank condition should return validation error")
    void testBlankCondition() {
        List<ValidationError> errors = validatorService.validateCondition("");
        assertThat(errors).isNotEmpty();
    }

    @Test
    @DisplayName("invalid syntax should return validation error")
    void testInvalidSyntax() {
        List<ValidationError> errors = validatorService.validateCondition("weight <=");
        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0).getMessage()).isNotBlank();
    }

    @Test
    @DisplayName("valid action SET_BASE should return no errors")
    void testValidAction() {
        List<ValidationError> errors = validatorService.validateAction("SET_BASE 1000 XAF");
        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("blank action should return validation error")
    void testBlankAction() {
        List<ValidationError> errors = validatorService.validateAction("");
        assertThat(errors).isNotEmpty();
    }

    @Test
    @DisplayName("complex valid condition with IN and BETWEEN returns no errors")
    void testComplexValidCondition() {
        List<ValidationError> errors = validatorService.validateCondition(
                "packageType IN [FRAGILE, ELECTRONICS] AND distance BETWEEN 5 AND 20");
        assertThat(errors).isEmpty();
    }
}
