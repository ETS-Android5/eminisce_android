package com.eminiscegroup.eminisce.server;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;

import java.util.List;

public interface JsonBioApi {

    @Headers({
            "Authorization: Token 4784af0576766ff014bcfa76d86366c81479da1d",
    })
    @GET("api/libraryusersbio")
    Call<List<LibraryUserBioResponse>> getBios();

}
