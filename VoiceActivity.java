package com.mahmoh.clavier;

import android.app.Activity;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.os.Bundle;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Écran invisible lancé depuis le clavier (icône micro) :
 * ouvre la reconnaissance vocale du téléphone dans la langue du clavier,
 * puis renvoie le texte dicté au clavier, qui l'insère dans le champ.
 */
public class VoiceActivity extends Activity {

    private static final int REQ_VOICE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            String tag = getIntent().getStringExtra("lang");
            if (tag == null) tag = "fr-FR";
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            i.putExtra(RecognizerIntent.EXTRA_PROMPT, tag.startsWith("ar") ? "تكلّم الآن" : "Parlez maintenant");
            try {
                startActivityForResult(i, REQ_VOICE);
            } catch (Exception e) {
                Toast.makeText(this, "Reconnaissance vocale indisponible sur ce téléphone", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VOICE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty() && r.get(0) != null && !r.get(0).trim().isEmpty()) {
                ClavierService.deliverVoice(r.get(0).trim());
            }
        }
        finish();
    }
}
