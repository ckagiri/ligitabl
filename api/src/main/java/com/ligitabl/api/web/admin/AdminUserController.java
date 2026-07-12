package com.ligitabl.api.web.admin;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminUserController {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final List<Integer> PAGE_SIZE_OPTIONS = List.of(10, 25, 50, 100);

    private final UserRepo userRepo;

    @GetMapping("/users")
    public String usersPage(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            Model model,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {

        int pageSize = PAGE_SIZE_OPTIONS.contains(size) ? size : DEFAULT_PAGE_SIZE;
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * pageSize;

        List<User> users = userRepo.findAllPaged(offset, pageSize);
        long totalEntries = userRepo.countAll();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalEntries / pageSize));

        model.addAttribute("pageTitle", "Users");
        model.addAttribute("users", users);
        model.addAttribute("currentPage", safePage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalEntries", totalEntries);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("pageSizeOptions", PAGE_SIZE_OPTIONS);
        model.addAttribute("hasPreviousPage", safePage > 1);
        model.addAttribute("hasNextPage", safePage < totalPages);
        model.addAttribute("showingFrom", totalEntries > 0 ? offset + 1 : 0);
        model.addAttribute("showingTo", totalEntries > 0 ? Math.min(offset + pageSize, totalEntries) : 0);

        if (hxRequest != null && !hxRequest.isBlank()) {
            return "admin/users :: usersContent";
        }
        return "admin/users";
    }
}
