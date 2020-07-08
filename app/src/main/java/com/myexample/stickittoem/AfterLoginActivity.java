package com.myexample.stickittoem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class AfterLoginActivity extends AppCompatActivity {
    private static final String TAG = "AfterLoginActivity";
    private TextView textView4, textView5, textView11, textView12;
    private DatabaseReference mDatabase;
    private String username;
    private String deviceToken;
    private ArrayList<String> stickersArray;
    private GridView gridView;
    private String targetToken;
    private Button button3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_after_login);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            username = extras.getString("username");
            deviceToken = extras.getString("deviceToken");
        }

        textView4 = findViewById(R.id.textView4);
        textView5 = findViewById(R.id.textView5);
        textView11 = findViewById(R.id.textView11);
        textView12 = findViewById(R.id.textView12);
        stickersArray = new ArrayList<>();
        gridView = findViewById(R.id.gridView);
        button3 = findViewById(R.id.button3);
        mDatabase = FirebaseDatabase.getInstance().getReference();

        textView4.setText("Username: " + username);
        textView5.setText("No. of Sending Stickers: Loading...");

        // Forced to logout if a newer login occurs at another device
        mDatabase.child("users").child(username).child("token").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (!dataSnapshot.getValue(String.class).equals(deviceToken)) {
                    if (!(AfterLoginActivity.this).isFinishing()) {
                        showDialog();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });

        // Listen to update the number of sending stickers from database
        mDatabase.child("messages").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                long count = 0;
                for (DataSnapshot kv : dataSnapshot.getChildren()) {
                    if (kv.child("sender").getValue(String.class).equals(username)) {
                        count++;
                    }
                }
                textView5.setText("No. of Sending Stickers: " + count);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });

        stickersArray.add("Loading...");
        gridView.setAdapter(new ArrayAdapter<>(AfterLoginActivity.this, R.layout.simple_list_item_1, stickersArray));
        // Click to pick a sticker
        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // Get the selected emoji
                String selectedSticker = stickersArray.get(position);
                textView12.setText(selectedSticker);
            }
        });

        // Download stickers from database
        mDatabase.child("stickers").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Toast.makeText(AfterLoginActivity.this, "Stickers updated", Toast.LENGTH_SHORT).show();
                stickersArray.clear();
                for (DataSnapshot sticker : dataSnapshot.getChildren()) {
                    stickersArray.add(sticker.getKey());
                }
                gridView.setAdapter(new ArrayAdapter<>(AfterLoginActivity.this, R.layout.simple_list_item_1, stickersArray));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });

        // Get special notifications during the time when the username is previously dissociated with a token and get associated again
        mDatabase.child("messages").orderByKey().addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                ArrayList<String> keyArray = new ArrayList<>();
                for (DataSnapshot kv : dataSnapshot.getChildren()) {
                    if (kv.child("offline").getValue(boolean.class) && kv.child("receiver").getValue(String.class).equals(username)) {
                        String sender = kv.child("sender").getValue(String.class);
                        String sticker = kv.child("sticker").getValue(String.class);
                        sendNotification(sender, sticker);
                        keyArray.add(kv.getKey());
                    }
                }
                // Update the offline boolean label of the message from the database after being notified
                for (int i = 0; i < keyArray.size(); i++) {
                    mDatabase.child("messages").child(keyArray.get(i)).child("offline").setValue(false);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });
    }

    public void reset(View view) {
        textView12.setText("");
    }

    public void sendSticker(View view) {
        final EditText receiverEditText = findViewById(R.id.editText2);
        final String receiver = receiverEditText.getText().toString().trim();
        final String candidate = textView12.getText().toString();

        // Reset input EditText as empty after button click
        receiverEditText.setText("");
        if (receiver.equals("") || candidate.equals("")) {
            Toast.makeText(this, "Username and sticker can't be empty!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable button before sending is done
        button3.setEnabled(false);
        textView11.setText("Sending to " + receiver);

        // Check the existence of the receiver
        mDatabase.child("users").child(receiver).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // Check the existence of the receiver
                if (!dataSnapshot.exists()) {
                    Toast.makeText(AfterLoginActivity.this, "Receiver " + receiver + " does not exist!", Toast.LENGTH_SHORT).show();
                } else {
                    targetToken = dataSnapshot.child("token").getValue(String.class);

                    // Send and save message to the database
                    DatabaseReference pushMessagesRef = mDatabase.child("messages").push();
                    class Message {
                        public String receiver;
                        public String sender;
                        public String sticker;
                        public Map time;
                        public boolean offline;

                        public Message(String receiver, String sender, String sticker, Map time, boolean offline) {
                            this.receiver = receiver;
                            this.sender = sender;
                            this.sticker = sticker;
                            this.time = time;
                            this.offline = offline;
                        }
                    }
                    Map time = new HashMap();
                    time.put("timestamp", ServerValue.TIMESTAMP);

                    // Upload to the database
                    if (!targetToken.equals("offline")) {
                        // Send notification via FCM if the receiver is online
                        pushMessagesRef.setValue(new Message(receiver, username, candidate, time, false));
                        fcmSend(username, targetToken, candidate);
                    } else {
                        pushMessagesRef.setValue(new Message(receiver, username, candidate, time, true));
                    }

                }
                button3.setEnabled(true);
                textView11.setText("Click one emoji to pick. Click \"Reset\" to cancel.");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });
    }

    public void sendHistory(View view) {
        Intent intent = new Intent(this, SendHistoryActivity.class);
        intent.putExtra("username", username);
        intent.putExtra("deviceToken", deviceToken);
//        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    public void receiveHistory(View view) {
        Intent intent = new Intent(this, ReceiveHistoryActivity.class);
        intent.putExtra("username", username);
        intent.putExtra("deviceToken", deviceToken);
//        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    public void fcmSend(final String sender, final String targetToken, final String sticker) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                JSONObject jPayload = new JSONObject();
                JSONObject jNotification = new JSONObject();
                JSONObject jdata = new JSONObject();
                try {
                    final String SERVER_KEY = "key=AAAA7xASAtE:APA91bHBsTH0mTra96iS8XlxrITX-kBKHUYEu9VzifoT2OVpsouZYAlN1XRq-QcDvv7GU8urCY3jYoy-b-k9x7QVJodXfiVptFRgOn3SbIT7RR2jZxDis3898jF8S8FVxenIc1RaQMLM";
                    jNotification.put("title", "Emoji Sticker Received");
                    jNotification.put("body", sender + " sent you the emoji sticker: " + sticker);
                    jNotification.put("sound", "default");
                    jNotification.put("badge", "1");
                    jNotification.put("click_action", "OPEN_ACTIVITY_1");

                    jdata.put("sender", sender);
                    jdata.put("sticker", sticker);

                    jPayload.put("to", targetToken);
                    jPayload.put("priority", "high");
                    jPayload.put("notification", jNotification);
                    jPayload.put("data", jdata);

                    URL url = new URL("https://fcm.googleapis.com/fcm/send");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Authorization", SERVER_KEY);
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);

                    OutputStream outputStream = conn.getOutputStream();
                    outputStream.write(jPayload.toString().getBytes());
                    outputStream.close();

                    InputStream inputStream = conn.getInputStream();
                    final String resp = convertStreamToString(inputStream);
                    Handler h = new Handler(Looper.getMainLooper());

                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(AfterLoginActivity.this, resp, Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (JSONException | IOException e) {
                    Toast.makeText(AfterLoginActivity.this, "FCM failed", Toast.LENGTH_SHORT).show();
                }
            }
        }).start();
    }

    private String convertStreamToString(InputStream is) {
        Scanner s = new Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next().replace(",", ",\n") : "";
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

    // Go to the main activity after forced logged out
    protected void exit() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    public void sendNotification(String sender, String sticker) {
        String CHANNEL_ID = "Channel_StickItToEm";
        Intent intent = new Intent(this, ShowFCMActivity.class);
        intent.putExtra("sender", sender);
        intent.putExtra("sticker", sticker);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle("Emoji Sticker Received")
                .setContentText(sender + " sent you the emoji sticker: " + sticker)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        createNotificationChannel();

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void createNotificationChannel() {
        String CHANNEL_ID = "Channel_StickItToEm";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.app_name);
            String description = getString(R.string.app_name);
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}
