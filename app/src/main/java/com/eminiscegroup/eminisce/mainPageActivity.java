package com.eminiscegroup.eminisce;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.eminiscegroup.eminisce.R;
import com.eminiscegroup.eminisce.rfid.RFIDListener;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;

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
    private int booksProcessed = 0;

    private String userID;

    private HashMap<Integer, String> loanInfo = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags( WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


        //Display user's ID on the top right of the page
        setContentView(R.layout.activity_main2);
        userID = getIntent().getStringExtra("userid");
        ((TextView)findViewById(R.id.userID)).setText(userID);

        // We use Retrofit to call Django Rest Framework API
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(MainActivity.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        Methods = retrofit.create(Methods.class);

        barcode = findViewById(R.id.barcode_info);

        // Start listening to RFID scanner (simulation)
        RFIDListener rfidListener = new RFIDListener(new RFIDListener.RFIDCallback() {
            @Override
            public void onRFIDReceive(String msg) {
                // Run on UI Thread is required or the app will crash
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast toast = Toast.makeText(mainPageActivity.this, "Scanned " + msg, Toast.LENGTH_SHORT);
                        toast.show();
                        barcode.setText(msg);
                        addButton(null);
                        barcode.setText("");
                    }
                });
            }

            @Override
            public void onRFIDConnected(String address) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast toast = Toast.makeText(mainPageActivity.this, "New RFID Scanner connected from " + address, Toast.LENGTH_SHORT);
                        toast.show();
                    }
                });
            }
        });

        try {
            rfidListener.startListening();
        }
        catch(Exception e)
        {
            Toast toast = Toast.makeText(mainPageActivity.this, "Failed to start connection to RFID scanner.", Toast.LENGTH_SHORT);
            toast.show();
            e.printStackTrace();
        }
    }

    //To start up barcode/OR Code scanner through the camera
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

        errorView = findViewById(R.id.error_view);
        super.onActivityResult(requestCode, resultCode, data);

        //retrieving data from the barcode/QR code scanner and display the information of the book
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


    /** Called when the user taps the button */
    //Process the borrowing of books which are scanned by the user
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

        }

    }

    public void nextButton(View view)
    {
        goToCheckoutPage();
    }

    //Proceed to the Checkout page after borrowing the book
    //Bring the book loan information and user id to the checkout page using HashMap
    private void goToCheckoutPage()
    {
        Intent intent = new Intent(this, checkoutPageActivity.class);
        TextView textView = findViewById(R.id.book_view);
        intent.putExtra("loan_info", loanInfo);
        intent.putExtra("userid", userID);

        startActivity(intent);
    }

    //Ending the current session and bringing the application back to the authentication page
    //Users have to re-authenticate themseleves after pressing the cancel button
    public void cancelButton(View view)
    {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    //This is to display the book info of the book scanned
    //To retrieve the book info, getBookData() is called
    public void addButton(View view) {
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

    //This function is to retrieve the book info using the book id added by the user
    private void getBookData()
    {
        //calling the database to get data of the particular book id
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

                    String content = "";

                    //Displaying Title and Authors
                    content += "Title: " + ids.getTitle() + "      " + "\n";
                    content += "Authors: " + ids.getAuthors() + "\n";

                    textView.append(content + "\n");
                    textView.append(duedate);

                    barcodes.add(postBarcode);

                    //Putting the info here so that it can be also displayed in the checkout page
                    loanInfo.put(ids.getId(), content);
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

    //This function is called to loan the books and get the due date of the loaned books
    private void loanBook()
    {
        //Calling the database to process the loan of the book to the particular user
        NewLoan borrow = new NewLoan(userID, postBarcode);
        TextView textView = findViewById(R.id.book_view);
        errorView = findViewById(R.id.error_view);
        Call<NewLoan> call = Methods.borrowBook(borrow);

        call.enqueue(new Callback<NewLoan>() {
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onResponse(Call<NewLoan> call, Response<NewLoan> response) {
                if(response.code() != 201)
                {
                    String errorMsg;
                    try {
                        JSONObject errorJson = new JSONObject(response.errorBody().string());
                        errorMsg = errorJson.getString("error");
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
                    NewLoan borrowResponse = response.body();

                    //To save the book duedate for each book into loanInfo so that it can be displayed in the checkout page
                    String responseBook = borrowResponse.getBook();
                    try {
                        // Convert the received String to Date
                        SimpleDateFormat strToDate = new SimpleDateFormat(
                                "yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.US);
                        strToDate.setTimeZone(TimeZone.getTimeZone("UTC"));
                        // Then convert the Date to a more readable String again
                        SimpleDateFormat dateToStr = new SimpleDateFormat(
                                "MMMM d, yyyy HH:mm:ss", Locale.ENGLISH);
                        strToDate.setTimeZone(TimeZone.getDefault());

                        String parsedDate = dateToStr.format(strToDate.parse(borrowResponse.getDuedate()));
                        String newInfo = loanInfo.get(Integer.parseInt(responseBook)) + "Please return book before " +  parsedDate + "\n";
                        loanInfo.replace(Integer.parseInt(responseBook), newInfo);
                    }
                    catch(Exception e)
                    {
                        //If there is an error, an error message is shown and book loan is not processed
                        Toast toast = Toast.makeText(mainPageActivity.this, "Error while processing loan request. Please check the log.",
                                Toast.LENGTH_LONG);
                        toast.show();
                        e.printStackTrace();
                        return;
                    }

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