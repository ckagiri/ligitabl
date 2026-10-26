package com.ligitabl.api.usecase.leaderboard.getuserdetail;

public class GetUserDetailNotFoundException extends RuntimeException {
    public GetUserDetailNotFoundException(String message) {
        super(message);
    }
}
