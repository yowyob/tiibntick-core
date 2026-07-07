package com.yowyob.tiibntick.common.domain.result;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the Result monad.
 * Author: MANFOUO Braun
 */
class ResultTest {

    @Test
    void should_create_success_result() {
        Result<String> result = Result.success("hello");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue()).isEqualTo("hello");
    }

    @Test
    void should_create_failure_result() {
        Result<String> result = Result.failure("NOT_FOUND", "Resource not found");
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getFailure().get().errorCode()).isEqualTo("NOT_FOUND");
    }

    @Test
    void should_map_success_value() {
        Result<Integer> result = Result.success(5).map(n -> n * 2);
        assertThat(result.getValue()).isEqualTo(10);
    }

    @Test
    void should_propagate_failure_through_map() {
        Result<String> failure = Result.failure("ERR", "error");
        Result<Integer> mapped = failure.map(String::length);
        assertThat(mapped.isFailure()).isTrue();
    }

    @Test
    void should_return_default_on_failure() {
        String value = Result.<String>failure("E", "m").orElse("default");
        assertThat(value).isEqualTo("default");
    }
}
