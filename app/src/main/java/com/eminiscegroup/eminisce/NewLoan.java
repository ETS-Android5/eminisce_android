package com.eminiscegroup.eminisce;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class NewLoan {

    @SerializedName("borrower")
    @Expose
    private String borrower;

    @SerializedName("book")
    @Expose
    private String book;

    @SerializedName("due_date")
    @Expose
    private String duedate;

    public NewLoan(String borrower, String book) {
        this.borrower = borrower;
        this.book = book;
    }

    public String getBorrower() {
        return borrower;
    }

    public String getBook() {
        return book;
    }

    public String getDuedate() {
        return duedate;
    }
}
