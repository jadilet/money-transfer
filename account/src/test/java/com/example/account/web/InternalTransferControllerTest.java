package com.example.account.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.account.domain.InsufficientFundsException;
import com.example.account.service.AccountService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalTransferController.class)
class InternalTransferControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AccountService accountService;

    private static final String VALID_BODY = """
            {
              "transferId": "33333333-3333-3333-3333-333333333333",
              "fromAccountId": "11111111-1111-1111-1111-111111111111",
              "toAccountId": "22222222-2222-2222-2222-222222222222",
              "amount": 100,
              "currency": "KGS",
              "idempotencyKey": "k-1"
            }
            """;

    @Test
    void apply_returns200_onSuccess() throws Exception {
        mvc.perform(post("/internal/transfers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("APPLIED"));
    }

    @Test
    void apply_returns422_onInsufficientFunds() throws Exception {
        doThrow(new InsufficientFundsException(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                new BigDecimal("10"), new BigDecimal("100")))
                .when(accountService).applyTransfer(any());

        mvc.perform(post("/internal/transfers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void apply_returns400_onInvalidBody() throws Exception {
        String invalid = """
                {
                  "transferId": "33333333-3333-3333-3333-333333333333",
                  "fromAccountId": "11111111-1111-1111-1111-111111111111",
                  "toAccountId": "22222222-2222-2222-2222-222222222222",
                  "amount": -5,
                  "currency": "US",
                  "idempotencyKey": ""
                }
                """;
        mvc.perform(post("/internal/transfers").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
    }
}
