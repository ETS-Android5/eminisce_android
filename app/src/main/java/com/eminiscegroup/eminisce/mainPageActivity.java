package com.eminiscegroup.eminisce;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.eminiscegroup.eminisce.R;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.ArrayList;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;



public class mainPageActivity extends AppCompatActivity {

    public static final String book_info = "com.eminiscegroup.eminisce.info";
    public int userID = 3;
    public String postBarcode = "";
    Button scanButton;
    TextView errorView;
    EditText barcode;
    private Methods Methods;
    private static String BASE_URL = "https://eminisce.herokuapp.com/";
    String duedate = "";
    ArrayList<String> barcodes = new ArrayList<String>();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        Methods = retrofit.create(Methods.class);
        //Intent intent = getIntent();
    }
    public void scanButton(View view) {
        scanButton = findViewById(R.id.scanButton);

        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                IntentIntegrator intentIntegrator = new IntentIntegrator(
                        mainPageActivity.this);
                intentIntegrator.setOrientationLocked(true);
                intentIntegrator.setCaptureActivity(Capture.class);
                intentIntegrator.initiateScan();
            }
        });


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable @org.jetbrains.annotations.Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        barcode = findViewById(R.id.barcode_info);
        errorView = findViewById(R.id.error_view);
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult intentResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if(intentResult.getContents() != null)
        {
            barcode.setText(intentResult.getContents());
        }
        else
        {
            errorView.setText("Error");
        }
    }
    /** Called when the user taps the button */
    public void confirmButton(View view) throws InterruptedException {
        TextView textView = findViewById(R.id.book_view);

        textView.setText("");
        for(int i = 0; i < barcodes.size(); i++)
        {
            postBarcode = barcodes.get(i);
            loanBook();
            getBookData();
        }

    }

    public void nextButton(View view)
    {
        Intent intent = new Intent(this, checkoutPageActivity.class);
        TextView textView = findViewById(R.id.book_view);
        String bookInfo = textView.getText().toString();
        intent.putExtra(book_info, bookInfo);

        //loanBook();
        startActivity(intent);
    }
    //String allMessage = "";

    public void cancelButton(View view)
    {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    public void addButton(View view) {
        //Intent intent = new Intent(this, checkoutPageActivity.class);
        EditText editText = (EditText) findViewById(R.id.barcode_info);
        String tempBarcode = editText.getText().toString();
        postBarcode = tempBarcode;
        barcodes.add(tempBarcode);

        getBookData();

    }

    private void getBookData()
    {
        Call<Retrieve> call = Methods.getIdData(postBarcode);
        TextView textView = findViewById(R.id.book_view);
        errorView = findViewById(R.id.error_view);
        call.enqueue(new Callback<Retrieve>() {
            @Override
            public void onResponse(Call<Retrieve> call, Response<Retrieve> response) {
                if(!response.isSuccessful())
                {
                    errorView.setText("Code: " + response.code());
                    return;
                }
                else {
                    Retrieve ids = response.body();

                    //for(Retrieve id: ids)
                    //{
                    String content = "";
                    
                    content += "Title: " + ids.getTitle() + "      " + "\n";
                    content += "Authors: " + ids.getAuthors() + "\n";

                    textView.append(content);
                    textView.append(duedate);
                    //}
                }
            }

            @Override
            public void onFailure(Call<Retrieve> call, Throwable t) {
                errorView.setText(t.getMessage());
            }
        });
    }

    private void loanBook()
    {
        NewLoan borrow = new NewLoan(userID, postBarcode);
        TextView textView = findViewById(R.id.book_view);
        errorView = findViewById(R.id.error_view);
        Call<NewLoan> call = Methods.borrowBook(borrow);

        call.enqueue(new Callback<NewLoan>() {
            @Override
            public void onResponse(Call<NewLoan> call, Response<NewLoan> response) {


                if(!response.isSuccessful())
                {
                    String message = "";
                    message += "Code: " + response.code() + "\n";
                    message += "Book is currently unavailable" + "\n";
                    //message += "Error: " + borrowResponse.getError();
                    errorView.setText(message);
                    return;
                }
                else {

                    //textView = findViewById(R.id.book_view);
                    NewLoan borrowResponse = response.body();
                    //String content = "";
                    duedate = "DueDate: " + borrowResponse.getDuedate() + "\n\n";
                    //getBookData();
                    //textView.setText(content);
                }
            }

            @Override
            public void onFailure(Call<NewLoan> call, Throwable t) {
                errorView.setText(t.getMessage());
            }
        });
    }
}