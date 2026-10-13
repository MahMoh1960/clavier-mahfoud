package com.mahmoh.clavier;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Petit écran d'accueil : activation du clavier et zone de test. */
public class MainActivity extends Activity {

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(20), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setText("Clavier FR / عربي");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(title);

        TextView steps = new TextView(this);
        steps.setText("1. Activez « Clavier FR-AR » dans la liste des claviers.\n"
                + "2. Choisissez-le comme clavier actif.\n"
                + "3. Touchez la zone de test ci-dessous.");
        steps.setTextSize(16);
        steps.setPadding(0, dp(12), 0, dp(12));
        col.addView(steps);

        Button b1 = new Button(this);
        b1.setText("1. Activer le clavier");
        b1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            }
        });
        col.addView(b1);

        Button b2 = new Button(this);
        b2.setText("2. Choisir le clavier");
        b2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showInputMethodPicker();
            }
        });
        col.addView(b2);

        EditText test = new EditText(this);
        test.setHint("Zone de test…");
        test.setMinLines(6);
        test.setGravity(Gravity.TOP | Gravity.START);
        test.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(16);
        col.addView(test, tlp);

        TextView help = new TextView(this);
        help.setText("Rangée du haut :\n"
                + "• Effacer en arrière / en avant\n"
                + "• ← → : déplacer le curseur\n"
                + "• ↑ ↓ : monter / descendre d'une ligne\n"
                + "• |← →| : début / fin de ligne\n\n"
                + "Maintenez une flèche ou une touche d'effacement pour la répéter. "
                + "Maintenez une lettre pour obtenir ses accents.\n"
                + "La touche ع / FR change de langue ; ⇧ (deux fois) verrouille les majuscules.");
        help.setTextSize(14);
        help.setPadding(0, dp(16), 0, 0);
        col.addView(help);

        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        setContentView(sv);
    }
}
