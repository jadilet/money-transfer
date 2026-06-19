package com.example.account.web;

import com.example.account.domain.AccountNotActiveException;
import com.example.account.domain.InsufficientFundsException;
import com.example.account.service.AccountNotFoundException;
import com.example.account.service.ClientNotFoundException;
import com.example.account.service.CurrencyMismatchException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    /** Business declines on the money-move: the request is well-formed but cannot be applied. */
    @ExceptionHandler({
            InsufficientFundsException.class,
            AccountNotActiveException.class,
            CurrencyMismatchException.class,
            AccountNotFoundException.class
    })
    public ProblemDetail handleDeclined(RuntimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setProperty("code", declineCode(ex));
        return problem;
    }

    private String declineCode(RuntimeException ex) {
        if (ex instanceof InsufficientFundsException) {
            return "INSUFFICIENT_FUNDS";
        }
        if (ex instanceof AccountNotActiveException) {
            return "ACCOUNT_NOT_ACTIVE";
        }
        if (ex instanceof CurrencyMismatchException) {
            return "CURRENCY_MISMATCH";
        }
        if (ex instanceof AccountNotFoundException) {
            return "ACCOUNT_NOT_FOUND";
        }
        return "DECLINED";
    }

    @ExceptionHandler(ClientNotFoundException.class)
    public ProblemDetail handleClientNotFound(ClientNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fieldError -> fieldError.getField(),
                        fieldError -> fieldError.getDefaultMessage() == null ? "invalid" : fieldError.getDefaultMessage(),
                        (a, b) -> a,
                        LinkedHashMap::new));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }
}
