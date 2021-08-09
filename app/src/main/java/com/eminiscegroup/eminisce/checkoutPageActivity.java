package com.eminiscegroup.eminisce;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.eminiscegroup.eminisce.R;

public class checkoutPageActivity extends AppCompatActivity {

    private String userID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout_page);

        // Get the Intent that started this activity and extract the string
        Intent intent = getIntent();
        String message = intent.getStringExtra(mainPageActivity.book_info);

        // Capture the layout's TextView and set the string as its text
        TextView textView = findViewById(R.id.book_view);
        textView.setText(message);

        userID = getIntent().getStringExtra("userid");
        ((TextView)findViewById(R.id.userID2)).setText(userID);
    }

    public void endButton(View view) {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }
}