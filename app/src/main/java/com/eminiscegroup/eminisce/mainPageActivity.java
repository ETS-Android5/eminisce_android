package com.eminiscegroup.eminisce;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.eminiscegroup.eminisce.R;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONObject;

import java.util.ArrayList;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;



public class mainPageActivity extends AppCompatActivity {

    public static final String book_info = "com.eminiscegroup.eminisce.info";
    public String postBarcode = "";
    Button scanButton;
    TextView errorView;
    EditText barcode;
    private Methods Methods;
    String duedate = "";
    ArrayList<String> barcodes = new ArrayList<String>();

    private String userID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags( WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN );

        setContentView(R.layout.activity_main2);
        userID = getIntent().getStringExtra("userid");
        ((TextView)findViewById(R.id.userID)).setText(userID);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(MainActivity.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        Methods = retrofit.create(Methods.class);
        //Intent intent = getIntent();
    }
    public void scanButton(View view) {
        IntentIntegrator intentIntegrator = new IntentIntegrator(
                mainPageActivity.this);
        intentIntegrator.setOrientationLocked(true);
        intentIntegrator.setCaptureActivity(Capture.class);
        intentIntegrator.initiateScan();
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
            addButton(null);
            barcode.setText("");
        }
        else
        {
            Toast toast = Toast.makeText(mainPageActivity.this, "Canceled scan.", Toast.LENGTH_SHORT);
            toast.show();
        }
    }
    private int booksProcessed = 0;
    /** Called when the user taps the button */
    public void confirmButton(View view) throws InterruptedException {
        if(barcodes.size() == 0)
        {
            Toast toast = Toast.makeText(mainPageActivity.this, "Please add some books!", Toast.LENGTH_SHORT);
            toast.show();
            return;
        }
        TextView textView = findViewById(R.id.book_view);

        booksProcessed = 0;
        for(int i = 0; i < barcodes.size(); i++)
        {
            postBarcode = barcodes.get(i);
            loanBook();
            //getBookData(); This just creates race condition...
        }
        //goToCheckoutPage();

    }

    public void nextButton(View view)
    {
        goToCheckoutPage();
    }
    //String allMessage = "";

    private void goToCheckoutPage()
    {
        Intent intent = new Intent(this, checkoutPageActivity.class);
        TextView textView = findViewById(R.id.book_view);
        String bookInfo = textView.getText().toString();
        intent.putExtra(book_info, bookInfo);
        intent.putExtra("userid", userID);

        //loanBook();
        startActivity(intent);
    }

    public void cancelButton(View view)
    {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    public void addButton(View view) {
        //Intent intent = new Intent(this, checkoutPageActivity.class);
        EditText editText = (EditText) findViewById(R.id.barcode_info);
        String tempBarcode = editText.getText().toString();
        if(tempBarcode.isEmpty()) {
            Toast toast = Toast.makeText(mainPageActivity.this, "Please type in a barcode!", Toast.LENGTH_SHORT);
            toast.show();
            return;
        }
        if(barcodes.contains(tempBarcode)) {
            Toast toast = Toast.makeText(mainPageActivity.this, "You already added this book!", Toast.LENGTH_SHORT);
            toast.show();
            return;
        }
        postBarcode = tempBarcode;
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
                if(response.code() != 200)
                {
                    String errorMsg;
                    if(response.code() == 404)
                    {
                        errorMsg = "Cannot find this book's information. Please check the barcode and try again!";
                    }
                    else {
                        try {
                            JSONObject errorJson = new JSONObject(response.errorBody().string());
                            errorMsg = errorJson.getString("error");
                        } catch (Exception e) {
                            errorMsg = e.getMessage();
                        }
                    }
                    AlertDialog.Builder alertDialog = new AlertDialog.Builder( mainPageActivity.this );
                    alertDialog.setTitle( "Error")
                            .setMessage( "Cannot retrieve book information from database.\nError code: " + response.code() + ".\nMessage: " + errorMsg)
                            .setCancelable( false )
                            .setNegativeButton( "CLOSE", (dialog, which) ->
                            {
                                dialog.dismiss();
                            }).create();
                    alertDialog.show();
                    return;
                }
                else {
                    Retrieve ids = response.body();

                    //for(Retrieve id: ids)
                    //{
                    String content = "";
                    
                    content += "Title: " + ids.getTitle() + "      " + "\n";
                    content += "Authors: " + ids.getAuthors() + "\n";
                    //FIND A WAY TO PUT THE COVER HERE!!!

                    textView.append(content);
                    textView.append(duedate);
                    // OK I see your clever trick here, you run getBookData() again to put the duedate in?
                    // It would be great if you just put every fcking thing in the "next" page instead
                    barcodes.add(postBarcode);
                    //}
                }
            }

            @Override
            public void onFailure(Call<Retrieve> call, Throwable t) {
                Toast toast = Toast.makeText(mainPageActivity.this, "Please check Internet connection! " /* + t.getMessage() */, Toast.LENGTH_LONG);
                toast.show();
                t.printStackTrace();
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
                if(response.code() != 201)
                {
                    String errorMsg;
                    try {
                        JSONObject errorJson = new JSONObject(response.errorBody().string());
                        errorMsg = errorJson.getString("error");
                        //errorMsg = errorJson.toString();
                    }
                    catch(Exception e)
                    {
                        errorMsg = e.getMessage();
                    }
                    AlertDialog.Builder alertDialog = new AlertDialog.Builder( mainPageActivity.this );
                    alertDialog.setTitle( "Error")
                            .setMessage( "Cannot process loan request.\nError code: " + response.code() + ".\nMessage: " + errorMsg)
                            .setCancelable( false )
                            .setNegativeButton( "CLOSE", (dialog, which) ->
                            {
                                dialog.dismiss();
                            }).create();
                    alertDialog.show();
                    return;
                }
                else {

                    //textView = findViewById(R.id.book_view);
                    NewLoan borrowResponse = response.body();
                    //String content = "";
                    duedate = "DueDate: " + borrowResponse.getDuedate() + "\n\n";
                    //getBookData();
                    //textView.setText(content);

                    booksProcessed ++;
                    if(booksProcessed == barcodes.size())
                    {
                        goToCheckoutPage();
                    }
                }
            }

            @Override
            public void onFailure(Call<NewLoan> call, Throwable t) {
                Toast toast = Toast.makeText(mainPageActivity.this, "Please check Internet connection! " /* + t.getMessage() */, Toast.LENGTH_LONG);
                toast.show();
                t.printStackTrace();
            }
        });
    }
}