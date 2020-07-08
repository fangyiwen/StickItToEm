package com.myexample.stickittoem;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.TextView;

public class ShowFCMActivity extends AppCompatActivity {
    private String sender, sticker;
    private TextView textView9;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_f_c_m);

        textView9 = findViewById(R.id.textView9);
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            sender = extras.getString("sender");
            sticker = extras.getString("sticker");
        }
        textView9.setText(sender + " sent you the emoji sticker: " + sticker);
    }
}
