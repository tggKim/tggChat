package com.tgg.chat.common.messaging.stomp;

import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;
import com.tgg.chat.exception.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
@Slf4j
public class StompMessageExceptionAdvice {

    @MessageExceptionHandler(ErrorException.class)
    @SendToUser(value = "/queue/errors", broadcast = false)
    protected ErrorResponse handleErrorException(ErrorException e) {
        ErrorCode errorCode = e.getErrorCode();

        log.warn("[ErrorException] code={}, status={}, message={}",
                errorCode.getCode(),
                errorCode.getStatus().value(),
                errorCode.getMessage());

        return ErrorResponse.of(errorCode);
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(value = "/queue/errors", broadcast = false)
    protected ErrorResponse handleException(Exception e) {
        log.error("[Unhandled Exception]", e);

        return ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR);
    }

}
