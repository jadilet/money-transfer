package com.example.ledger.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ledger.service.LedgerAccountNotFoundException;
import com.example.ledger.service.LedgerService;
import com.example.ledger.service.LedgerViews;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LedgerController.class)
class LedgerControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LedgerService ledgerService;

    @Test
    void balance_returns200() throws Exception {
        UUID ref = UUID.randomUUID();
        when(ledgerService.balanceOf(ref)).thenReturn(new LedgerViews.Balance(ref, "KGS", new BigDecimal("-100.0000")));

        mvc.perform(get("/api/ledger-accounts/{ref}/balance", ref))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("KGS"))
                .andExpect(jsonPath("$.balance").value(-100.0000));
    }

    @Test
    void balance_unknownAccount_returns404() throws Exception {
        UUID ref = UUID.randomUUID();
        when(ledgerService.balanceOf(any())).thenThrow(new LedgerAccountNotFoundException(ref));

        mvc.perform(get("/api/ledger-accounts/{ref}/balance", ref))
                .andExpect(status().isNotFound());
    }
}
