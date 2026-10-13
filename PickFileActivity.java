package com.mahmoh.clavier;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Écran invisible lancé depuis le clavier (icône trombone) :
 * 1) ouvre le sélecteur de fichiers de l'appareil,
 * 2) ouvre ensuite le menu de partage pour envoyer le ou les fichiers
 *    vers l'application voulue (WhatsApp, Telegram, Gmail, etc.).
 */
public class PickFileActivity extends Activity {

    private static final int REQ_PICK = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("*/*");
            pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            try {
                startActivityForResult(pick, REQ_PICK);
            } catch (Exception e) {
                Toast.makeText(this, "Sélecteur de fichiers indisponible", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK && resultCode == RESULT_OK && data != null) {
            ArrayList<Uri> uris = new ArrayList<>();
            ClipData cd = data.getClipData();
            if (cd != null) {
                for (int i = 0; i < cd.getItemCount(); i++) uris.add(cd.getItemAt(i).getUri());
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (!uris.isEmpty()) share(uris);
        }
        finish();
    }

    private void share(ArrayList<Uri> uris) {
        boolean single = uris.size() == 1;
        Intent send = new Intent(single ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE);
        String type = single ? getContentResolver().getType(uris.get(0)) : "*/*";
        send.setType(type == null ? "*/*" : type);
        if (single) {
            send.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        } else {
            send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        ClipData clip = ClipData.newRawUri(null, uris.get(0));
        for (int i = 1; i < uris.size(); i++) clip.addItem(new ClipData.Item(uris.get(i)));
        send.setClipData(clip);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Intent chooser = Intent.createChooser(send, "Envoyer le fichier vers…");
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(chooser);
        } catch (Exception e) {
            Toast.makeText(this, "Impossible d'ouvrir le menu de partage", Toast.LENGTH_LONG).show();
        }
    }
}
