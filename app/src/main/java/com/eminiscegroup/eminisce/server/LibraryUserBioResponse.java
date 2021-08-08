package com.eminiscegroup.eminisce.server;


import com.google.gson.annotations.SerializedName;

public class LibraryUserBioResponse {

    @SerializedName("user")
    private String user;

    @SerializedName("fingerprint")
    private String fingerprint_b64;

    @SerializedName("face_front")
    private String face_b64;

    public String getUser() {
        return user;
    }

    public String getFingerprint_b64() {
        return fingerprint_b64;
    }

    public String getFace_b64() {
        return face_b64;
    }
}
