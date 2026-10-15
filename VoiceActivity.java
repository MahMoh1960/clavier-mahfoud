package com.mahmoh.clavier;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Écran invisible : demande une seule fois l'autorisation du micro,
 * puis relance la dictée continue du clavier.
 */
public class VoiceActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 7);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
            ClavierService.micPermissionGranted();
        } else {
            Toast.makeText(this, "Autorisation du micro refusée", Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
