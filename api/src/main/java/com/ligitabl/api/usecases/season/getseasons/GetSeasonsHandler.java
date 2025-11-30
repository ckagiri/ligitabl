package com.ligitabl.api.usecases.season.getseasons;

import java.util.List;

import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.usecases.team.TeamDto;
import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.usecases.season.SeasonDto;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetSeasonsHandler implements GetSeasonsUseCase {

    private final SeasonRepo seasonRepo;

    @Override
    public Either<UseCaseError, List<SeasonDto>> execute(Void unused) {
        return Either.fromException(
                () -> SeasonDto.listOf(seasonRepo.findAll()),
                UseCaseErrors::fromException
        );
    }
}
