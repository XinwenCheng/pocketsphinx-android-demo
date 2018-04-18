/* ====================================================================
 * Copyright (c) 2014 Alpha Cephei Inc.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY ALPHA CEPHEI INC. ``AS IS'' AND
 * ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL CARNEGIE MELLON UNIVERSITY
 * NOR ITS EMPLOYEES BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ====================================================================
 */

package edu.cmu.pocketsphinx.demo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Locale;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;

/**
 * @author Unknown yet.
 */
public class MainActivity extends Activity implements RecognitionListener {

    private static final String TAG = "^_^" + MainActivity.class.getSimpleName();

    /**
     * Named searches allow to quickly reconfigure the decoder
     */
    private static final String CORPUS = "corpus";

    /**
     * Used to handle permission request
     */
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    private SpeechRecognizer recognizer;
    private TextView tvRecognition;
    private TextView tvResult;
    private ScrollView svResult;

    @Override
    public void onCreate(Bundle state) {
        Log.d(TAG, "onCreate()");
        super.onCreate(state);

        setContentView(R.layout.main);
        initView();

        // Check if user has given permission to record audio
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
            return;
        }
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
        new SetupTask(this).execute();
    }

    private void initView() {
        tvRecognition = findViewById(R.id.tvRecognition);
        tvResult = findViewById(R.id.tvResult);
        svResult = findViewById(R.id.svResult);
    }

    private static class SetupTask extends AsyncTask<Void, Void, Exception> {
        WeakReference<MainActivity> activityReference;

        SetupTask(MainActivity activity) {
            this.activityReference = new WeakReference<>(activity);
        }

        @Override
        protected Exception doInBackground(Void... params) {
            try {
                Assets assets = new Assets(activityReference.get());
                File assetDir = assets.syncAssets();
                activityReference.get().setupRecognizer(assetDir);
            } catch (IOException e) {
                return e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Exception e) {
            if (e != null) {
                Log.e(TAG, e.getMessage(), e);
                return;
            }

            Log.i(TAG, "开始录音");
            activityReference.get().restartListening();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Recognizer initialization is a time-consuming and it involves IO,
                // so we execute it in async task
                new SetupTask(this).execute();
            } else {
                finish();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (recognizer != null) {
            recognizer.cancel();
            recognizer.shutdown();
        }
    }

    /**
     * In partial result we get quick updates about current hypothesis. In
     * keyword spotting mode we can react here, in other modes we need to wait
     * for final result in onResult.
     */
    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null) {
            return;
        }

        String text = hypothesis.getHypstr();
        Log.d(TAG, "onPartialResult() hypothesis: " + text);
        tvRecognition.setText(text);
    }

    /**
     * This callback is called when we stop the recognizer.
     */
    @Override
    public void onResult(Hypothesis hypothesis) {
        Log.w(TAG, String.format(Locale.getDefault(), "hypothesis == null: %B", hypothesis == null));

        tvRecognition.setText("");

        if (hypothesis == null) {
            Toast.makeText(this, "没听懂。", Toast.LENGTH_SHORT).show();
            return;
        }

        String text = hypothesis.getHypstr();
        Log.i(TAG, "onResult() hypothesis: " + text);

        if (TextUtils.isEmpty(text)) {
            return;
        }

        if (TextUtils.isEmpty(tvResult.getText())) {
            tvResult.setText(text);
        } else {
            tvResult.setText(String.format(Locale.getDefault(), "%1$s\n%2$s", tvResult.getText(), text));
        }

        svResult.fullScroll(ScrollView.FOCUS_DOWN);
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    /**
     * We stop recognizer here to get a final result
     */
    @Override
    public void onEndOfSpeech() {
        Log.d(TAG, "onEndOfSpeech() recognizer.getSearchName(): " + recognizer.getSearchName());
        restartListening();
    }

    private void restartListening() {
        Log.w(TAG, "restartListening()");
        recognizer.stop();
        recognizer.startListening(CORPUS, 10000);
    }

    private void setupRecognizer(File assetsDir) throws IOException {
        Log.i(TAG, "setupRecognizer()");

        // The recognizer can be configured to perform multiple searches
        // of different kind and switch between them

        recognizer = SpeechRecognizerSetup.defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))
//                .setRawLogDir(assetsDir) // To disable logging of raw audio comment out this call (takes a lot of space on the device)
                .getRecognizer();
        recognizer.addListener(this);

        /* In your application you might not need to add all those searches.
          They are added here for demonstration. You can leave just one.
         */

        // Create keyword-activation search.
//        recognizer.addKeyphraseSearch(KWS_SEARCH, KEYPHRASE);

        // Create grammar-based search for selection between demos
//        recognizer.addGrammarSearch(CORPUS, new File(assetsDir, "free-talk.gram"));
        recognizer.addNgramSearch(CORPUS, new File(assetsDir, "corpus.lm.bin"));
    }

    @Override
    public void onError(Exception error) {
        Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
    }

    @Override
    public void onTimeout() {
        restartListening();
    }
}
