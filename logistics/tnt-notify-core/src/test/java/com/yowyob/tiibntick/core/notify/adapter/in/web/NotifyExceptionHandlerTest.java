package com.yowyob.tiibntick.core.notify.adapter.in.web;

import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.KernelContextMissingException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class NotifyExceptionHandlerTest {

    @Test
    void missingKernelContext_isAServiceUnavailable_notAStateConflict() {
        var ex = new KernelContextMissingException("No organization for the Kernel notification call");
        StepVerifier.create(new NotifyExceptionHandler().handleKernelContextMissing(ex))
                .assertNext(pd -> {
                    assertThat(pd.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
                    assertThat(pd.getDetail()).contains("organization");
                }).verifyComplete();
    }
}
