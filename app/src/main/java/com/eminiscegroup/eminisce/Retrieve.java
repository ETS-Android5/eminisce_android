package com.eminiscegroup.eminisce;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Retrieve {

    @SerializedName("id")
    @Expose
    private int id;
    @SerializedName("title")
    @Expose
    private String title;
    @SerializedName("authors")
    @Expose
    private String authors;
    @SerializedName("description")
    @Expose
    private String description;
    @SerializedName("cover")
    @Expose
    private String cover;
    @SerializedName("status")
    @Expose
    private String status;
    @SerializedName("barcode")
    @Expose
    private String barcode;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthors() {
        return authors;
    }

    public void setAuthors(String authors) {
        this.authors = authors;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCover() {
        return cover;
    }

    public void setCover(String cover) {
        this.cover = cover;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }
}
