package com.example.transfer.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.transfer.domain.Transfer;
import com.example.transfer.service.InvalidTransferException;
import com.example.transfer.service.TransferInProgressException;
import com.example.transfer.service.TransferNotFoundException;
import com.example.transfer.service.TransferService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransferController.class)
class TransferControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TransferService transferService;

    private static final String VALID_BODY = """
            {
              "idempotencyKey": "k-web",
              "fromAccountId": "11111111-1111-1111-1111-111111111111",
              "toAccountId":   "22222222-2222-2222-2222-222222222222",
              "amount": 100,
              "currency": "KGS"
            }
            """;

    @Test
    void create_returns201_withCompletedBody() throws Exception {
        Transfer completed = Transfer.pending("k-web",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                new BigDecimal("100"), "KGS");
        completed.markCompleted();
        when(transferService.create(any())).thenReturn(completed);

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.currency").value("KGS"));
    }

    @Test
    void create_invalidBody_returns400WithFieldErrors() throws Exception {
        String invalid = """
                {
                  "idempotencyKey": "k-bad",
                  "fromAccountId": "11111111-1111-1111-1111-111111111111",
                  "toAccountId":   "22222222-2222-2222-2222-222222222222",
                  "amount": -5,
                  "currency": "US"
                }
                """;

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists())
                .andExpect(jsonPath("$.errors.currency").exists());
    }

    @Test
    void create_downstreamUnavailable_returns503() throws Exception {
        when(transferService.create(any()))
                .thenThrow(new TransferInProgressException(UUID.randomUUID(), "fraud check temporarily unavailable"));

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void create_selfTransfer_returns400() throws Exception {
        when(transferService.create(any())).thenThrow(new InvalidTransferException("fromAccountId and toAccountId must differ"));

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_unknownId_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(transferService.getById(id)).thenThrow(new TransferNotFoundException(id));

        mvc.perform(get("/api/transfers/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_malformedJson_returns400_andSkipsService() throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verify(transferService, never()).create(any());
    }
}
