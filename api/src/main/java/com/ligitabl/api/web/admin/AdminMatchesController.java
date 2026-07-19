package com.ligitabl.api.web.admin;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.web.shared.error.ErrorMapper;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.TeamRepo;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminMatchesController {

    private final HierarchyValidator hierarchyValidator;
    private final CompetitionDefaults competitionDefaults;
    private final MatchRepo matchRepo;
    private final RoundRepo roundRepo;
    private final TeamRepo teamRepo;

    private record PageContext(Competition competition, Season season, Round currentRound) {}

    @GetMapping("/matches")
    public String matchesPage(Model model, HttpServletResponse response) {
        log.info("GET /admin/matches");

        return resolveContext().fold(error -> handleError(error, model, response), ctx -> {
            model.addAttribute("pageTitle", "Admin · Matches");
            model.addAttribute("competitionName", ctx.competition().getName());
            model.addAttribute("seasonName", ctx.season().getName());
            model.addAttribute("currentRound", ctx.currentRound().getPosition());
            model.addAttribute("maxRounds", ctx.season().getMaxRounds());
            model.addAttribute("seasonInSetupMode", ctx.season().isInSetupMode());
            return "admin/matches";
        });
    }

    @GetMapping("/matches/data")
    @ResponseBody
    public ResponseEntity<AdminMatchesData> matchesData() {
        log.info("GET /admin/matches/data");

        return resolveContext()
                .fold(
                        error -> ResponseEntity.status(ErrorMapper.toHttpStatus(error))
                                .build(),
                        ctx -> ResponseEntity.ok(buildData(ctx)));
    }

    private Either<UseCaseError, PageContext> resolveContext() {
        return hierarchyValidator
                .resolveHierarchy(competitionDefaults.defaultCompetitionSlug())
                .map(ctx -> new PageContext(ctx.competition(), ctx.season(), ctx.round()));
    }

    private AdminMatchesData buildData(PageContext ctx) {
        List<Match> matches = matchRepo.findBySeasonId(ctx.season().getId());
        List<Round> rounds =
                roundRepo.findBySeasonIdOrderByPosition(ctx.season().getId());

        Set<UUID> teamIds = matches.stream()
                .flatMap(m -> Stream.of(m.getHomeTeamId(), m.getAwayTeamId()))
                .collect(Collectors.toSet());

        return new AdminMatchesData(
                ctx.currentRound().getPosition(),
                teamRepo.findAllByIds(teamIds).stream()
                        .map(AdminMatchesData.TeamEntry::from)
                        .toList(),
                rounds.stream().map(AdminMatchesData.RoundEntry::from).toList(),
                matches.stream().map(AdminMatchesData.MatchEntry::from).toList());
    }

    private String handleError(UseCaseError error, Model model, HttpServletResponse response) {
        response.setStatus(ErrorMapper.toHttpStatus(error));
        model.addAttribute("error", error.getMessage());
        model.addAttribute("pageTitle", "Admin · Matches");
        return "error";
    }
}
