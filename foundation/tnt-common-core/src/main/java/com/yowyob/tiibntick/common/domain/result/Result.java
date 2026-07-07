package com.yowyob.tiibntick.common.domain.result;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A discriminated union representing either a successful value of type {@code T}
 * or a failure described by a {@link TntFailure}.
 *
 * <p>Use {@code Result} to propagate business rule violations and expected errors
 * through the call stack without relying on exceptions. This is especially important
 * in reactive pipelines (Project Reactor) where exception-based error handling
 * disrupts the monadic composition.
 *
 * <p>Usage example:
 * <pre>{@code
 * Result<Mission> result = missionService.assign(missionId, delivererId);
 *
 * result
 *   .onSuccess(mission -> log.info("Assigned: {}", mission.getId()))
 *   .onFailure(failure -> log.warn("Failed: {}", failure.message()));
 *
 * // Map success value
 * Result<MissionDto> dto = result.map(missionMapper::toDto);
 *
 * // Flat-map in a chain
 * Result<Invoice> invoice = result.flatMap(m -> invoiceService.createFor(m));
 * }</pre>
 *
 * Author: MANFOUO Braun
 *
 * @param <T> type of the success value
 */
public sealed interface Result<T> permits Result.Success, Result.Failure {

    // ── Factory methods ────────────────────────────────────────────────────

    /**
     * Creates a successful result wrapping {@code value}.
     *
     * @param value must not be null
     */
    static <T> Result<T> success(T value) {
        return new Success<>(Objects.requireNonNull(value, "Success value must not be null"));
    }

    /**
     * Creates a failure result from a {@link TntFailure}.
     */
    static <T> Result<T> failure(TntFailure failure) {
        return new Failure<>(Objects.requireNonNull(failure, "Failure must not be null"));
    }

    /**
     * Creates a failure result from an error code and human-readable message.
     */
    static <T> Result<T> failure(String errorCode, String message) {
        return failure(TntFailure.of(errorCode, message));
    }

    /**
     * Creates a failure result from a caught exception.
     */
    static <T> Result<T> failure(String errorCode, Throwable cause) {
        return failure(TntFailure.ofException(errorCode, cause));
    }

    // ── Query methods ──────────────────────────────────────────────────────

    /** Returns {@code true} if this result is a success. */
    boolean isSuccess();

    /** Returns {@code true} if this result is a failure. */
    default boolean isFailure() {
        return !isSuccess();
    }

    /**
     * Returns the success value.
     *
     * @throws NoSuchElementException if this is a failure
     */
    T getValue();

    /**
     * Returns the failure, or empty if this is a success.
     */
    Optional<TntFailure> getFailure();

    // ── Transformation ────────────────────────────────────────────────────

    /**
     * Maps the success value using {@code mapper}. If this is a failure, returns the same failure
     * typed to {@code U}.
     */
    <U> Result<U> map(Function<? super T, ? extends U> mapper);

    /**
     * Flat-maps the success value to another {@code Result}. If this is a failure, propagates it.
     */
    <U> Result<U> flatMap(Function<? super T, ? extends Result<U>> mapper);

    /**
     * Returns the success value or a supplied default.
     */
    T orElse(T defaultValue);

    /**
     * Returns the success value or the result of {@code supplier}.
     */
    T orElseGet(Supplier<? extends T> supplier);

    /**
     * Executes {@code consumer} if this result is a success. Returns {@code this} for chaining.
     */
    Result<T> onSuccess(Consumer<? super T> consumer);

    /**
     * Executes {@code consumer} if this result is a failure. Returns {@code this} for chaining.
     */
    Result<T> onFailure(Consumer<TntFailure> consumer);

    // ── Implementations ───────────────────────────────────────────────────

    record Success<T>(T value) implements Result<T> {

        public Success {
            Objects.requireNonNull(value, "Success value must not be null");
        }

        @Override public boolean isSuccess()                     { return true; }
        @Override public T getValue()                             { return value; }
        @Override public Optional<TntFailure> getFailure()       { return Optional.empty(); }

        @Override
        public <U> Result<U> map(Function<? super T, ? extends U> mapper) {
            return Result.success(mapper.apply(value));
        }

        @Override
        public <U> Result<U> flatMap(Function<? super T, ? extends Result<U>> mapper) {
            return mapper.apply(value);
        }

        @Override public T orElse(T defaultValue)                { return value; }
        @Override public T orElseGet(Supplier<? extends T> s)    { return value; }

        @Override
        public Result<T> onSuccess(Consumer<? super T> consumer) {
            consumer.accept(value);
            return this;
        }

        @Override
        public Result<T> onFailure(Consumer<TntFailure> consumer) { return this; }
    }

    record Failure<T>(TntFailure failure) implements Result<T> {

        public Failure {
            Objects.requireNonNull(failure, "Failure must not be null");
        }

        @Override public boolean isSuccess()                     { return false; }

        @Override
        public T getValue() {
            throw new NoSuchElementException("Result is a failure: " + failure.errorCode());
        }

        @Override public Optional<TntFailure> getFailure()       { return Optional.of(failure); }

        @Override
        @SuppressWarnings("unchecked")
        public <U> Result<U> map(Function<? super T, ? extends U> mapper) {
            return (Result<U>) this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <U> Result<U> flatMap(Function<? super T, ? extends Result<U>> mapper) {
            return (Result<U>) this;
        }

        @Override public T orElse(T defaultValue)                { return defaultValue; }
        @Override public T orElseGet(Supplier<? extends T> s)    { return s.get(); }

        @Override
        public Result<T> onSuccess(Consumer<? super T> consumer) { return this; }

        @Override
        public Result<T> onFailure(Consumer<TntFailure> consumer) {
            consumer.accept(failure);
            return this;
        }
    }
}
