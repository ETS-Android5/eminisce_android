package com.eminiscegroup.eminisce;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface Methods {

    @Headers("Authorization: Token 4784af0576766ff014bcfa76d86366c81479da1d")
    @GET("api/books/{barcode}/")
    Call<Retrieve> getIdData(@Path("barcode") String barcodeID);

    @Headers("Authorization: Token 4784af0576766ff014bcfa76d86366c81479da1d")
    @POST("api/loans/new_loan")
    Call<NewLoan> borrowBook(@Body NewLoan newLoan);

    /*
    @Headers("Authorization: Token 4784af0576766ff014bcfa76d86366c81479da1d")
    @GET("api/books//")
    Call<FingerprintData> getFpData(@Path("fingerprint"));

    */

}
