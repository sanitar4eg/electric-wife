package org.edu.electricwife.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.api.client.util.Base64;
import com.google.api.client.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;


public class SpeechToTextActivity extends Activity {
    private TextView txtSpeechInput;
    private TextToSpeech tts;
    private final int REQ_CODE_SPEECH_INPUT = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speech_to_text);
        txtSpeechInput = findViewById(R.id.txtSpeechInput);
        Button btnSpeak = findViewById(R.id.btnSpeak);
        btnSpeak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                promptSpeechInput();
            }
        });
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = tts.setLanguage(Locale.US);
                    tts.setOnUtteranceProgressListener(mProgressListener);
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTS", "This Language is not supported");
                    }
                    int size = MainActivity.news.size();
                    if (0 == size) {
                        speak("Hello, you have no new messages", true);
                    } else if (size == 1) {
                        speak("Hello, you have " + size + "new message", true);
                    } else {
                        speak("Hello, you have " + size + "new messages", true);
                    }
                } else {
                    Log.e("TTS", "Initilization Failed!");
                }
            }
        });
    }

    /**
     * Showing google speech input dialog
     */
    private void promptSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
//        intent.putExtra(RecognizerIntent.EXTRA_WEB_SEARCH_ONLY, true);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                "Say something");
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(getApplicationContext(),
                    "Speech is not supported",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void speak(String text, Boolean needAnswer) {
        if (text != null) {
            Log.i(MainActivity.TAG, text);
            HashMap<String, String> hashTts = new HashMap<>();
            hashTts.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, needAnswer ? "needAnswer" : "id");

            tts.speak(text, TextToSpeech.QUEUE_FLUSH, hashTts);
        }
    }

    private UtteranceProgressListener mProgressListener = new UtteranceProgressListener() {
        @Override
        public void onStart(String utteranceId) {
        } // Do nothing

        @Override
        public void onError(String utteranceId) {
        } // Do nothing.

        @Override
        public void onDone(String utteranceId) {
            // end of synthesizing
            if (utteranceId.equals("needAnswer")) {
//                handleSpeech(((String) txtSpeechInput.getText()).toLowerCase(), false);
                promptSpeechInput();
            }
        }
    };

    private void handleSpeech(String text, Boolean beforeAnswer) {
        if (containsAtLeastOne(text, new String[]{
                "thanks", "thank you"
        })) {
            speak("You are welcome", false);
            return;
        }

        if (containsAtLeastOne(text, new String[]{
                "bye", "stop"
        })) {
            String answer = getRandomItem(new String[]{
                    "Goodbye", "Bye"
            });
            speak(answer, false);
            return;
        }

        if (containsAtLeastOne(text, new String[]{
                "read one", "read the one", "read one please", "one", "1"
        })) {
            String snippet = MainActivity.messages.get(0).getSnippet();
            int i = snippet.indexOf("2018");
            String data = i != -1 ? snippet.substring(0, i) : snippet;
            Log.i(TAG, data);
            speak(data, true);
        }

        if (beforeAnswer) {
//            speak(text, true);
        } else {
            promptSpeechInput();
        }
    }

    @Nullable
    private String getRandomItem(String[] arr) {
        HashSet<String> myHashSet = new HashSet<>(Arrays.asList(arr));
        int size = myHashSet.size();
        int item = new Random().nextInt(size); // In real life, the Random object should be rather more shared than this
        int i = 0;
        for (String obj : myHashSet) {
            if (i == item)
                return obj;
            i++;
        }
        return null;
    }

    public boolean containsAtLeastOne(String text, String[] arr) {
        HashSet<String> set = new HashSet<>(Arrays.asList(arr));

        for (String aSet : set) {
            if (text.contains(aSet)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Receiving speech input
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQ_CODE_SPEECH_INPUT: {
                if (resultCode == RESULT_OK && null != data) {

                    ArrayList<String> result = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

                    String userSpeech = result.get(0);
                    this.txtSpeechInput.setText(userSpeech);
                    handleSpeech(userSpeech.toLowerCase(), true);
                }
                break;
            }

        }
    }

    @Override
    public void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
