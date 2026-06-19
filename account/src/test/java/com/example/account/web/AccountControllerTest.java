package com.example.account.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.account.service.AccountService;
import com.example.account.service.AccountView;
import com.example.account.service.ClientNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AccountService accountService;

    @Test
    void list_returns200_withAccounts() throws Exception {
        UUID clientId = UUID.randomUUID();
        when(accountService.listAccounts(clientId)).thenReturn(List.of(
                new AccountView(UUID.randomUUID(), clientId, "KGS", new BigDecimal("1000.00"), "ACTIVE")));

        mvc.perform(get("/api/clients/{clientId}/accounts", clientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currency").value("KGS"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void list_returns404_whenClientUnknown() throws Exception {
        UUID clientId = UUID.randomUUID();
        when(accountService.listAccounts(any())).thenThrow(new ClientNotFoundException(clientId));

        mvc.perform(get("/api/clients/{clientId}/accounts", clientId))
                .andExpect(status().isNotFound());
    }
}
