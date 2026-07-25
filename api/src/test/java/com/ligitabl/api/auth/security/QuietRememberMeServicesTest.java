package com.ligitabl.api.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.rememberme.CookieTheftException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class QuietRememberMeServicesTest {

    private final RememberMeServices delegate = mock(RememberMeServices.class);
    private final QuietRememberMeServices services = new QuietRememberMeServices(delegate);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);

    @Test
    void autoLoginPassesThroughOnSuccess() {
        Authentication auth = new UsernamePasswordAuthenticationToken("alice", "n/a");
        when(delegate.autoLogin(request, response)).thenReturn(auth);

        assertThat(services.autoLogin(request, response)).isSameAs(auth);
        verify(delegate, never()).loginFail(any(), any());
    }

    @Test
    void autoLoginSwallowsCookieTheftExceptionAndClearsCookie() {
        when(delegate.autoLogin(request, response)).thenThrow(new CookieTheftException("mismatch"));

        Authentication result = services.autoLogin(request, response);

        assertThat(result).isNull();
        verify(delegate).loginFail(request, response);
    }

    @Test
    void loginFailDelegates() {
        services.loginFail(request, response);
        verify(delegate).loginFail(request, response);
    }

    @Test
    void loginSuccessDelegates() {
        Authentication auth = new UsernamePasswordAuthenticationToken("alice", "n/a");
        services.loginSuccess(request, response, auth);
        verify(delegate).loginSuccess(request, response, auth);
    }
}
