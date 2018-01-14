package com.taisys.sc.securechat;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.taisys.sc.securechat.Application.App;
import com.taisys.sc.securechat.model.ChatMessage;
import com.taisys.sc.securechat.model.User;
import com.taisys.sc.securechat.util.MessagesAdapter;

import java.util.ArrayList;
import java.util.List;

public class ChatMessagesActivity extends AppCompatActivity {

    private RecyclerView mChatsRecyclerView;
    private LinearLayoutManager mLayoutManager;
    private EditText mMessageEditText;
    private ImageButton mSendImageButton;
    private DatabaseReference mMessagesDBRef;
    private DatabaseReference mUsersRef;
    private List<ChatMessage> mMessagesList = new ArrayList<>();
    private MessagesAdapter adapter = null;

    private String mReceiverId;
    private String mReceiverName;
    private String mReceiverImageUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_messages);


        //initialize the views
        mChatsRecyclerView = (RecyclerView)findViewById(R.id.messagesRecyclerView);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendImageButton = (ImageButton)findViewById(R.id.sendMessageImagebutton);
        mChatsRecyclerView.setHasFixedSize(true);
        // use a linear layout manager
        mLayoutManager = new LinearLayoutManager(this);
        mLayoutManager.setStackFromEnd(true);
        mChatsRecyclerView.setLayoutManager(mLayoutManager);

        //init Firebase
        mMessagesDBRef = FirebaseDatabase.getInstance().getReference().child("Messages");
        mUsersRef = FirebaseDatabase.getInstance().getReference().child("Users");

        //get receiverId from intent
        mReceiverId = getIntent().getStringExtra("USER_ID");

        /**listen to send message imagebutton click**/
        mSendImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = mMessageEditText.getText().toString();
                String senderId = FirebaseAuth.getInstance().getCurrentUser().getUid();


                if(message.isEmpty()){
                    Toast.makeText(ChatMessagesActivity.this, "You must enter a message", Toast.LENGTH_SHORT).show();
                }else {
                    //message is entered, send
                    sendMessageToFirebase(message, senderId, mReceiverId);
                }
            }
        });




    }

    @Override
    protected void onStart() {
        super.onStart();

        /**sets title bar with recepient name**/
        queryRecipientName(mReceiverId);

        /**Query and populate chat messages**/
        querymessagesBetweenThisUserAndClickedUser();

    }



    private void sendMessageToFirebase(String message, String senderId, String receiverId){
        //mMessagesList.clear();

        ChatMessage newMsg = new ChatMessage(message, senderId, receiverId);
        newMsg.setCreatedAt(System.currentTimeMillis());
        mMessagesDBRef.push().setValue(newMsg).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if(!task.isSuccessful()){
                    //error
                    Toast.makeText(ChatMessagesActivity.this, "Error: " + task.getException().getLocalizedMessage(), Toast.LENGTH_LONG).show();
                }else{
                    //Toast.makeText(ChatMessagesActivity.this, "Message sent successfully!", Toast.LENGTH_SHORT).show();
                    mMessageEditText.setText(null);
                    hideSoftKeyboard();
                }
            }
        });


    }

    public void hideSoftKeyboard() {
        if(getCurrentFocus()!=null) {
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    private void querymessagesBetweenThisUserAndClickedUser(){

        //mMessagesDBRef.addValueEventListener(new ValueEventListener() {
        mMessagesDBRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                mMessagesList.clear();
                /* 用下方的 addChildEventListener 取代
                                for(DataSnapshot snap: dataSnapshot.getChildren()){
                                    ChatMessage chatMessage = snap.getValue(ChatMessage.class);
                                    chatMessage.setDbKey(snap.getKey());

                                    if(chatMessage.getSenderId().equals(FirebaseAuth.getInstance().getCurrentUser().getUid()) && chatMessage.getReceiverId().equals(mReceiverId) || chatMessage.getSenderId().equals(mReceiverId) && chatMessage.getReceiverId().equals(FirebaseAuth.getInstance().getCurrentUser().getUid())){
                                        chatMessage.setSenderName(mReceiverName);
                                        chatMessage.setSenderImage(mReceiverImageUrl);
                                        Log.d("SecureChat", "mReceiverImageUrl=" + mReceiverImageUrl);
                                        mMessagesList.add(chatMessage);
                                    }

                                }
                                */

                /**populate messages**/
                populateMessagesRecyclerView();

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(ChatMessagesActivity.this, App.getContext().getResources().getString(R.string.msgFailedToRetrieveDataFromFirebase), Toast.LENGTH_SHORT).show();
            }
        });

        mMessagesDBRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snap, String prevChildKey) {
                ChatMessage chatMessage = snap.getValue(ChatMessage.class);
                chatMessage.setDbKey(snap.getKey());
                Log.d("SecureChat", "s=" + prevChildKey);

                if(chatMessage.getSenderId().equals(FirebaseAuth.getInstance().getCurrentUser().getUid()) && chatMessage.getReceiverId().equals(mReceiverId) || chatMessage.getSenderId().equals(mReceiverId) && chatMessage.getReceiverId().equals(FirebaseAuth.getInstance().getCurrentUser().getUid())){
                    chatMessage.setSenderName(mReceiverName);
                    chatMessage.setSenderImage(mReceiverImageUrl);
                    Log.d("SecureChat", "child event mReceiverImageUrl=" + mReceiverImageUrl);
                    mMessagesList.add(chatMessage);
                    mChatsRecyclerView.scrollToPosition(mMessagesList.size()-1);
                }

            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {

            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private void populateMessagesRecyclerView(){
        adapter = new MessagesAdapter(mMessagesList, this);
        mChatsRecyclerView.setAdapter(adapter);

    }

    private void queryRecipientName(final String receiverId){

        mUsersRef.child(receiverId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                User recepient = dataSnapshot.getValue(User.class);
                mReceiverName = recepient.getDisplayName();
                mReceiverImageUrl = recepient.getImage();

                try {
                    getSupportActionBar().setTitle(mReceiverName);
                    getActionBar().setTitle(mReceiverName);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

    }


}
