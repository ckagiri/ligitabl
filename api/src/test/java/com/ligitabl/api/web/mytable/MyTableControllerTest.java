package com.ligitabl.api.web.mytable;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Principal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MyTableControllerTest {

    private final MyTableController controller = new MyTableController();

    @Test
    @DisplayName("/my-table redirects guests to /my-table/guest")
    void myTable_redirectsGuestToGuestRoute() {
        String view = controller.myTable(null, null);

        assertThat(view).isEqualTo("redirect:/my-table/guest");
    }

    @Test
    @DisplayName("/my-table preserves round when redirecting guests")
    void myTable_preservesRoundWhenRedirectingGuest() {
        String view = controller.myTable(7, null);

        assertThat(view).isEqualTo("redirect:/my-table/guest?round=7");
    }

    @Test
    @DisplayName("/my-table forwards authenticated users to predictions/me")
    void myTable_forwardsAuthenticatedToMe() {
        Principal principal = () -> "user@example.com";

        String view = controller.myTable(null, principal);

        assertThat(view).isEqualTo("forward:/predictions/user/me");
    }

    @Test
    @DisplayName("/my-table/guest redirects authenticated users back to /my-table")
    void guestTable_redirectsAuthenticatedUsersToMyTable() {
        Principal principal = () -> "user@example.com";

        String view = controller.guestTable(null, principal);

        assertThat(view).isEqualTo("redirect:/my-table");
    }

    @Test
    @DisplayName("/my-table/guest preserves round when redirecting authenticated users to /my-table")
    void guestTable_preservesRoundWhenRedirectingAuthenticatedUsersToMyTable() {
        Principal principal = () -> "user@example.com";

        String view = controller.guestTable(12, principal);

        assertThat(view).isEqualTo("redirect:/my-table?round=12");
    }

    @Test
    @DisplayName("/my-table/guest forwards true guests to guest predictions")
    void guestTable_forwardsGuestUsers() {
        String view = controller.guestTable(null, null);

        assertThat(view).isEqualTo("forward:/predictions/user/guest");
    }

    @Test
    @DisplayName("/my-table/guest preserves round when forwarding true guests")
    void guestTable_preservesRoundWhenForwardingGuests() {
        String view = controller.guestTable(3, null);

        assertThat(view).isEqualTo("forward:/predictions/user/guest?round=3");
    }
}
