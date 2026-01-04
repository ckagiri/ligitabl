package com.ligitabl.api.usecases.competition.getcompetitionbyslug;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.NotFoundError;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.api.usecases.competition.CompetitionDto;
import com.ligitabl.api.usecases.shared.HierarchyValidator;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;

class GetCompetitionBySlugUseCaseTest {

    @Mock
    private RequestValidator requestValidator;

    @Mock
    private HierarchyValidator hierarchyValidator;

    private GetCompetitionBySlugUseCase getCompetitionBySlugUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getCompetitionBySlugUseCase = new GetCompetitionBySlugUseCase(hierarchyValidator, requestValidator);
    }

    @Test
    void shouldReturnCompetitionWhenFound() {
        var competition = Competition.builder()
                .id(UUID.randomUUID())
                .name("Premier League")
                .slug(CompetitionSlug.of("premier-league"))
                .code("PL")
                .build();

        given(requestValidator.validate(any(GetCompetitionBySlugQuery.class)))
                .willAnswer(invocation -> Either.right(invocation.getArgument(0)));

        given(hierarchyValidator.validateCompetition("premier-league")).willReturn(Either.right(competition));

        Either<UseCaseError, CompetitionDto> result =
                getCompetitionBySlugUseCase.execute(new GetCompetitionBySlugQuery("premier-league"));
        assertThat(result.isRight()).isTrue();
        CompetitionDto dto = result.get();
        assertThat(dto.getName()).isEqualTo("Premier League");
        assertThat(dto.getSlug()).isEqualTo("premier-league");
        assertThat(dto.getCode()).isEqualTo("PL");
    }

    @Test
    void shouldReturnErrorWhenCompetitionNotFound() {
        given(requestValidator.validate(any(GetCompetitionBySlugQuery.class)))
                .willAnswer(invocation -> Either.right(invocation.getArgument(0)));

        given(hierarchyValidator.validateCompetition("unknown"))
                .willReturn(Either.left(new NotFoundError("Competition", "slug", "unknown")));

        Either<UseCaseError, CompetitionDto> result =
                getCompetitionBySlugUseCase.execute(new GetCompetitionBySlugQuery("unknown"));

        assertThat(result.isLeft()).isTrue();
    }
}
