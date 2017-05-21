/******************************************************************************
 *
 * The MIT License (MIT)
 *
 * Copyright (c) Crossbar.io Technologies GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 ******************************************************************************/

package autobahn.demo.com.autobahndemo;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketConnectionHandler;
import de.tavendo.autobahn.WebSocketException;
import de.tavendo.autobahn.WebSocketOptions;


public class TestSuiteClientActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String PREFS_NAME = "AutobahnAndroidTestsuiteClient";
    private static final String TAG = "TestSuiteClientActivity";

    private SharedPreferences mSettings;

    private EditText mWsUri;
    private EditText mAgent;
    private TextView mStatusLine;
    private Button mStart;

    private int currentCase;
    private int lastCase;

    private WebSocketOptions mOptions;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_test_suite_client);

        mWsUri = (EditText) findViewById(R.id.wsuri);
        mAgent = (EditText) findViewById(R.id.agent);
        mStatusLine = (TextView) findViewById(R.id.statusline);

        mSettings = getSharedPreferences(PREFS_NAME, 0);
        loadPrefs();

        mStart = (Button) findViewById(R.id.start);
        mStart.setOnClickListener(this);

        mOptions = new WebSocketOptions();
        mOptions.setReceiveTextMessagesRaw(true);
        mOptions.setMaxMessagePayloadSize(16 * 1024 * 1024);
        mOptions.setMaxFramePayloadSize(16 * 1024 * 1024);
    }

    private void loadPrefs() {
        mWsUri.setText(mSettings.getString("wsuri", "ws://192.168.1.3:9001"));
        mAgent.setText(mSettings.getString("agent", "AutobahnAndroid"));
    }

    private void savePrefs() {
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putString("wsuri", mWsUri.getText().toString());
        editor.putString("agent", mAgent.getText().toString());
        editor.apply();
    }

    private void updateText(final TextView textView, final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textView.setText(text);
            }
        });
    }

    private void runTest() throws WebSocketException {
        final WebSocketConnection webSocket = new WebSocketConnection();
        webSocket.connect(mWsUri.getText() + "/runCase?case=" + currentCase + "&agent=" + mAgent.getText(),
                new WebSocketConnectionHandler() {

                    @Override
                    public void onRawTextMessage(byte[] payload) {
                        webSocket.sendRawTextMessage(payload);
                    }

                    @Override
                    public void onBinaryMessage(byte[] payload) {
                        webSocket.sendBinaryMessage(payload);
                    }

                    @Override
                    public void onOpen() {
                        updateText(mStatusLine, "Test case " + currentCase + "/" + lastCase + " started ..");
                    }

                    @Override
                    public void onClose(int code, String reason) {
                        mStatusLine.setText("Test case " + currentCase + "/" + lastCase + " finished.");
                        currentCase += 1;
                        processNext();
                    }
                }, mOptions);
    }

    private void updateReport() throws WebSocketException {
        WebSocketConnection webSocket = new WebSocketConnection();
        webSocket.connect(mWsUri.getText() + "/updateReports?agent=" + mAgent.getText(),
                new WebSocketConnectionHandler() {
                    @Override
                    public void onOpen() {
                        mStatusLine.setText("Updating test reports ..");
                    }

                    @Override
                    public void onClose(int code, String reason) {
                        mStatusLine.setText("Test reports updated. Finished.");
                        mStart.setEnabled(true);
                    }
                });
    }

    private void queryCaseCount() throws WebSocketException {
        final WebSocketConnection webSocket = new WebSocketConnection();
        webSocket.connect(mWsUri.getText() + "/getCaseCount", new WebSocketConnectionHandler() {

                    @Override
                    public void onOpen() {
                        savePrefs();
                    }

                    @Override
                    public void onTextMessage(String payload) {
                        Log.d(TAG,"onTextMessage" +payload);
                        try {
                            lastCase = Integer.parseInt(payload);
                        } catch (NumberFormatException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onClose(int code, String reason) {
                        mStatusLine.setText("Ok, will run " + lastCase + " cases.");
                        currentCase += 1;
                        processNext();
                    }
                });
    }

    private void processNext() {
        try {
            if (currentCase == 0) {
                queryCaseCount();
            } else if (currentCase <= lastCase) {
                runTest();
            } else {
                updateReport();
            }
        } catch (WebSocketException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.start:
                mStart.setEnabled(false);
                currentCase = 0;
                processNext();
                break;
        }
    }
}
