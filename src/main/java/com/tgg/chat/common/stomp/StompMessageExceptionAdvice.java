package com.tgg.chat.common.stomp;

import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;
import com.tgg.chat.exception.ErrorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class StompMessageExceptionAdvice {

    @MessageExceptionHandler(ErrorException.class)
    @SendToUser("/errors")
    protected ResponseEntity<ErrorResponse> handleErrorException(ErrorException e) {

        ErrorCode errorCode = e.getErrorCode();
        String errorMessage = e.getMessage();

        log.warn("[ErrorException] code={}, status={}, message={}",
                errorCode.getCode(),
                errorCode.getStatus().value(),
                errorMessage);

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode));

    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/errors")
    protected ResponseEntity<ErrorResponse> handleException(Exception e) {

        log.error("[Unhandled Exception]", e);

        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));

    }

}
