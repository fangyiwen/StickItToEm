package com.myexample.stickittoem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

public class MainActivity extends AppCompatActivity {

    private EditText usernameEditText;
    private String deviceToken;
    private DatabaseReference mDatabase;
    private String username;
    private TextView textView10, textView13, textView14;
    Button button, button6, button7;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        usernameEditText = findViewById(R.id.editText);
        textView10 = findViewById(R.id.textView10);
        textView13 = findViewById(R.id.textView13);
        textView14 = findViewById(R.id.textView14);
        button = findViewById(R.id.button);
        button6 = findViewById(R.id.button6);
        button7 = findViewById(R.id.button7);

        // Get Instance ID token
        FirebaseInstanceId.getInstance().getInstanceId()
                .addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
                    @Override
                    public void onComplete(@NonNull Task<InstanceIdResult> task) {
                        if (!task.isSuccessful()) {
                            Toast.makeText(MainActivity.this, "getInstanceId failed", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        String token = task.getResult().getToken();
                        deviceToken = token;

                        // Toast the token
                        String msg = getString(R.string.msg_token_fmt, token);
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                    }
                });

        // Retrieve an instance of database using reference the location
        mDatabase = FirebaseDatabase.getInstance().getReference();

    }

    // Login the user
    public void login(View view) {
        if (deviceToken == null) {
            Toast.makeText(this, "Get device token failed", Toast.LENGTH_SHORT).show();
            return;
        }

        username = usernameEditText.getText().toString().trim();
        // Reset input EditText as empty after button click
        usernameEditText.setText("");
        // Check empty input
        if (username.equals("")) {
            Toast.makeText(this, "Username can't be empty!", Toast.LENGTH_SHORT).show();
            return;
        }

        textView10.setText(username + " is logging in...");
        // Disable "Login" before login is done
        button.setEnabled(false);

        // Update new token associated with username
        mDatabase.child("users").child(username).child("token").setValue(deviceToken);

        // Check the same token but different username, set them as offline
        mDatabase.child("users").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot kv : dataSnapshot.getChildren()) {
                    if (kv.child("token").getValue(String.class).equals(deviceToken) && !kv.getKey().equals(username)) {
                        mDatabase.child("users").child((kv.getKey())).child("token").setValue("offline");
                    }
                }

                // Start new activity
                Intent intent = new Intent(MainActivity.this, AfterLoginActivity.class);
                intent.putExtra("username", username);
                intent.putExtra("deviceToken", deviceToken);
                // Reset button and textView
                button.setEnabled(true);
                textView10.setText("");

                startActivity(intent);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });
    }

    // Check the username associated with this device
    public void checkAssociation(View view) {
        button6.setEnabled(false);
        textView13.setText("Loading...");
        // Check the username associated with this device token
        mDatabase.child("users").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                String result = "No username";
                for (DataSnapshot kv : dataSnapshot.getChildren()) {
                    if (kv.child("token").getValue(String.class).equals(deviceToken)) {
                        result = kv.getKey();
                        break;
                    }
                }
                textView13.setText(result + " is associated with this device.");
                button6.setEnabled(true);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });
    }

    // Dissociated any username with this device
    public void dissociation(View view) {
        button7.setEnabled(false);
        textView14.setText("Loading...");
        // Check the username associated with this device token
        mDatabase.child("users").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                String result = "No username";
                for (DataSnapshot kv : dataSnapshot.getChildren()) {
                    if (kv.child("token").getValue(String.class).equals(deviceToken)) {
                        result = kv.getKey();
                        mDatabase.child("users").child(kv.getKey()).child("token").setValue("offline");
                        break;
                    }
                }
                textView14.setText(result + " is dissociated with this device.");
                button7.setEnabled(true);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });
    }
}
