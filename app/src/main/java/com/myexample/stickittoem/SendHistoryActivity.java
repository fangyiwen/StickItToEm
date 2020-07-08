package com.myexample.stickittoem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class SendHistoryActivity extends AppCompatActivity {
    private String username, deviceToken;
    private DatabaseReference mDatabase;
    private TextView textView3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_history);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            username = extras.getString("username");
            deviceToken = extras.getString("deviceToken");
        }

        textView3 = findViewById(R.id.textView3);
        textView3.setText("Loading...");
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Forced to logout if a newer login occurs at another device
        mDatabase.child("users").child(username).child("token").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (!dataSnapshot.getValue(String.class).equals(deviceToken)) {
                    if (!(SendHistoryActivity.this).isFinishing()) {
                        showDialog();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });

        // Get sending data from database
        mDatabase.child("messages").orderByKey().addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                String result = "";
                for (DataSnapshot kv : dataSnapshot.getChildren()) {
                    if (kv.child("sender").getValue(String.class).equals(username)) {
                        String time = timestampToDate(kv.child("time").child("timestamp").getValue(Long.class));
                        String receiver = kv.child("receiver").getValue(String.class);
                        String sticker = kv.child("sticker").getValue(String.class);
                        result = "< " + time + " | " + receiver + " | " + sticker + " >\n\n" + result;
                    }
                }
                result = ("Displaying Format = < Time | Receiver | Sticker >\n\n" + result).trim();
                textView3.setText(result);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });
    }

    // Format datetime
    private String timestampToDate(long timestamp) {
        Date date = new Date(timestamp);
        SimpleDateFormat myFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        myFormat.setTimeZone(TimeZone.getTimeZone("GMT-7"));
        return myFormat.format(date);
    }

    // Forced to logout
    public void showDialog() {
        AlertDialog.Builder myDialog = new AlertDialog.Builder(this);
        myDialog.setTitle("Logout soon");
        myDialog.setIcon(R.mipmap.ic_launcher_round);
        myDialog.setMessage("You will be logged out since you are logging from other device.");
        myDialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                exit();
            }
        });
        myDialog.create().show();
    }

    protected void exit() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }
}
