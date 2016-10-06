/*
The MIT License (MIT)

Copyright (c) 2015 Naoyuki Kanezawa

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

Derived from: https://github.com/nkzawa/socket.io-android-chat
 */
package us.potatosaur.p0t4t0labs.potatoradio;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A chat fragment containing messages view and input form.
 */
public class ChatFragment extends Fragment implements Transceiver.TransceiverDataReceiver {

    private static final int REQUEST_LOGIN = 0;
    private static final String TAG = "ChatFragment";

    private static final int TYPING_TIMER_LENGTH = 600;

    private RecyclerView mMessagesView;
    private EditText mInputMessageView;
    private List<ChatMessage> mMessages = null;
    private RecyclerView.Adapter mAdapter;
    private String mUsername;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (mMessages == null) {
            mMessages = new ArrayList<ChatMessage>();
            mMessages.add(new ChatMessage.Builder(ChatMessage.TYPE_MESSAGE)
                    .username("TEST").message("DOES THS WERK").build());
        }
        mAdapter = new ChatMessageAdapter(getActivity(), mMessages);
        setHasOptionsMenu(true);

        Transceiver.addListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.chat_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mMessagesView = (RecyclerView) view.findViewById(R.id.messages);
        mMessagesView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mMessagesView.setAdapter(mAdapter);

        mInputMessageView = (EditText) view.findViewById(R.id.message_input);
        mInputMessageView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int id, KeyEvent event) {
                if (id == R.id.send || id == EditorInfo.IME_NULL) {
                    attemptSend();
                    return true;
                }
                return false;
            }
        });

        ImageButton sendButton = (ImageButton) view.findViewById(R.id.send_button);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptSend();
            }
        });

        if (mUsername == null) {
            mUsername = "DEFAULT-" + Float.toHexString(new Random().nextFloat()).substring(3, 6);
        }

        // DEBUG
        final Handler debugMsgHandler= new Handler();
        final Runnable debugMsgSender = new Runnable() {
            @Override
            public void run() {
                switch (new Random().nextInt(5)) {
                    case 0:
                        addMessage("Bob Joe", "What's going on!?!");
                        break;
                    case 1:
                        addLog("log msg");
                        break;
                    case 2:
                        addTyping("UNKNOWN");
                        scrollToBottom();
                        break;
                    case 3:
                        removeTyping("UNKNOWN");
                        break;
                    case 4:
                        addMessage("Frank", "Hello Hello!");
                        break;
                }
                debugMsgHandler.postDelayed(this, 2000);
            }
        };
        // TURN THIS ON FOR FAKE MESSAGES
        //debugMsgHandler.post(debugMsgSender);
    }

    @Override
    public void onPause() {
        super.onPause();
        InputMethodManager inputMethodManager = (InputMethodManager)  getActivity().getSystemService(Activity.INPUT_METHOD_SERVICE);
        IBinder token = getActivity().getCurrentFocus().getWindowToken();
        if (token != null)
            inputMethodManager.hideSoftInputFromWindow(token, 0);
    }

    /* This was the original intended way to start but we don't actually start this way
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (Activity.RESULT_OK != resultCode) {
            getActivity().finish();
            return;
        }

        mUsername = data.getStringExtra("username");
        int numUsers = data.getIntExtra("numUsers", 1);

        addLog(getResources().getString(R.string.chat_message_welcome));
        addParticipantsLog(numUsers);
    }*/

    public void addLog(String message) {
        mMessages.add(new ChatMessage.Builder(ChatMessage.TYPE_LOG)
                .message(message).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    public void addParticipantsLog(int numUsers) {
        addLog(getResources().getQuantityString(R.plurals.chat_message_participants, numUsers, numUsers));
    }

    public void addMessage(String username, String message) {
        mMessages.add(new ChatMessage.Builder(ChatMessage.TYPE_MESSAGE)
                .username(username).message(message).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    public void addTyping(String username) {
        mMessages.add(new ChatMessage.Builder(ChatMessage.TYPE_ACTION)
                .username(username).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    public void removeTyping(String username) {
        for (int i = mMessages.size() - 1; i >= 0; i--) {
            ChatMessage message = mMessages.get(i);
            if (message.getType() == ChatMessage.TYPE_ACTION && message.getUsername().equals(username)) {
                mMessages.remove(i);
                mAdapter.notifyItemRemoved(i);
            }
        }
    }

    private void attemptSend() {
        if (null == mUsername) return;

        String message = mInputMessageView.getText().toString().trim();
        if (TextUtils.isEmpty(message)) {
            mInputMessageView.requestFocus();
            return;
        }

        try {
            Transceiver.transmit(new PotatoPacket(PotatoPacket.DataType.MESSAGE, message));
        } catch (Exception e) {
            mInputMessageView.requestFocus();
            return;
        }

        mInputMessageView.setText("");
        addMessage(mUsername, message);

        // TODO: perform the sending message attempt.
        Log.d(TAG, "Send message " + message);
    }

    private void scrollToBottom() {
        mMessagesView.scrollToPosition(mAdapter.getItemCount() - 1);
    }

    @Override
    public void gotData(String data) {
        addMessage("RADIO", data);
    }
}
