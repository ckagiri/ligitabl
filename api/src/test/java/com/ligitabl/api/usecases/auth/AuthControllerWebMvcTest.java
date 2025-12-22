package com.ligitabl.api.usecases.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.usecases.auth.login.LoginController;
import com.ligitabl.api.usecases.auth.login.LoginResult;
import com.ligitabl.api.usecases.auth.login.LoginUseCase;

@WebMvcTest(LoginController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerWebMvcTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    LoginUseCase loginUseCase;

    @Test
    void login_returns_token() throws Exception {
        var resp = new LoginResult("token123");

        given(loginUseCase.execute(any())).willReturn(Either.right(resp));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("email", "admin@example.com", "password", "admin12345"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token123"));
    }
}
