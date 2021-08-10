package com.eminiscegroup.eminisce;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.eminiscegroup.eminisce.R;

import java.util.HashMap;
import java.util.Map;

public class checkoutPageActivity extends AppCompatActivity {

    private String userID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags( WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN );

        setContentView(R.layout.activity_checkout_page);

        // Get the Intent that started this activity and extract the string
        Intent intent = getIntent();

        // Capture the layout's TextView and set the string as its text


        userID = getIntent().getStringExtra("userid");

        HashMap<Integer, String> loanInfoAll = (HashMap<Integer, String>) getIntent().getSerializableExtra("loan_info");
        String loanInfoStr = "";
        for (Map.Entry<Integer, String> entry : loanInfoAll.entrySet()) {
            loanInfoStr += (entry.getValue() + "\n");
        }

        TextView textView = findViewById(R.id.book_view);
        textView.setText(loanInfoStr);

        ((TextView)findViewById(R.id.userID2)).setText(userID);
    }

    public void endButton(View view) {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }
}