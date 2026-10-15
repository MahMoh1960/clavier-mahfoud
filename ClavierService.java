package com.mahmoh.clavier;

import android.os.Bundle;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.StateListDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Clavier système FR (AZERTY) / AR avec barre de suggestions de mots et rangée de navigation :
 * effacer en arrière / en avant, curseur gauche / droite, haut / bas, début / fin de ligne.
 * Dictionnaires intégrés (hors connexion) + apprentissage local des mots tapés.
 */
public class ClavierService extends InputMethodService {

    // ---------- Types de touches ----------
    private static final int T_CHAR = 0, T_SPACE = 1, T_BKSP = 2, T_DEL = 3, T_LEFT = 4, T_RIGHT = 5,
            T_UP = 6, T_DOWN = 7, T_HOME = 8, T_END = 9, T_SHIFT = 10, T_SYM = 11, T_LANG = 12, T_ENTER = 13;

    private static final int LANG_FR = 0, LANG_AR = 1;
    // Lettres françaises : minuscules plus grandes (elles paraissent petites), majuscules un peu moins
    private static final float LETTER_FR_LOWER = 25, LETTER_FR_UPPER = 22;
    private static final int S_NORMAL = 0, S_FN = 1, S_NAV = 2, S_ACCENT = 3;

    // ---------- Couleurs (thème sombre proche de Gboard) ----------
    private static final int C_BG = 0xFF000000;     // noir pur
    private static final int C_KEY = 0xFF141414;    // touches : presque noir
    private static final int C_FN = 0xFF0A0A0A;     // touches de fonction : noir très sombre
    private static final int C_NAV = 0xFF0E0E0E;    // rangée de navigation
    private static final int C_ACCENT = 0xFF8AB4F8;
    private static final int C_TEXT = 0xFFD4A017;   // jaune foncé (lettres, chiffres, icônes)
    private static final int C_DIM = 0xFFB8901A;    // jaune foncé atténué (nom de la langue)
    private static final int C_ON_ACCENT = 0xFF000000;

    // ---------- Dispositions ----------
    private static final String[] NUMS = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};

    private static final String[][] FR_ROWS = {
            {"a", "z", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"q", "s", "d", "f", "g", "h", "j", "k", "l", "m"},
            {"w", "x", "c", "v", "b", "n", "'"}
    };

    private static final String[][] AR_ROWS = {
            {"ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج"},
            {"ش", "س", "ي", "ب", "ل", "ا", "ت", "ن", "م", "ك", "ط"},
            {"ء", "ؤ", "ر", "ى", "ة", "و", "ز", "ظ", "د", "ذ"}
    };

    // Tatweel pour afficher les signes diacritiques seuls ("étiquette|texte produit")
    private static final String M = "ـ";
    private static final String[][] AR_SHIFT_ROWS = {
            {M + "ً|ً", M + "ٌ|ٌ", M + "ٍ|ٍ", M + "َ|َ",
                    M + "ُ|ُ", M + "ِ|ِ", M + "ّ|ّ", M + "ْ|ْ",
                    "ـ", M + "ٰ|ٰ", "؟"},
            {"أ", "إ", "آ", "ئ", "ؤ", "ء", "ٱ", "«", "»", "،", "؛"},
            {"!", "(", ")", "-", ":", "/", "\"", "'", "%", "+"}
    };

    private static final String[][] SYM_ROWS = {
            {"@", "#", "$", "%", "&", "-", "+", "(", ")", "/"},
            {"*", "\"", "'", ":", ";", "!", "?", "_", "=", "«"},
            {"€", "£", "<", ">", "[", "]", "{", "}", "\\", "»"}
    };

    // ---------- Caractères alternatifs (appui long) ----------
    private static final Map<String, String[]> ALT_FR = new HashMap<>();
    private static final Map<String, String[]> ALT_AR = new HashMap<>();
    private static final Map<String, String[]> ALT_SYM = new HashMap<>();

    static {
        ALT_FR.put("a", new String[]{"à", "â", "ä", "æ"});
        ALT_FR.put("e", new String[]{"é", "è", "ê", "ë"});
        ALT_FR.put("i", new String[]{"î", "ï"});
        ALT_FR.put("o", new String[]{"ô", "ö", "œ"});
        ALT_FR.put("u", new String[]{"ù", "û", "ü"});
        ALT_FR.put("c", new String[]{"ç"});
        ALT_FR.put("y", new String[]{"ÿ"});
        ALT_FR.put("0", new String[]{"°"});
        ALT_FR.put(".", new String[]{"?", "!", ":", ";", "…"});
        ALT_FR.put(",", new String[]{";", ":"});

        ALT_AR.put("ا", new String[]{"أ", "إ", "آ", "ٱ"});
        ALT_AR.put("و", new String[]{"ؤ"});
        ALT_AR.put("ي", new String[]{"ئ", "ى"});
        ALT_AR.put("ه", new String[]{"ة"});
        ALT_AR.put("1", new String[]{"١"});
        ALT_AR.put("2", new String[]{"٢"});
        ALT_AR.put("3", new String[]{"٣"});
        ALT_AR.put("4", new String[]{"٤"});
        ALT_AR.put("5", new String[]{"٥"});
        ALT_AR.put("6", new String[]{"٦"});
        ALT_AR.put("7", new String[]{"٧"});
        ALT_AR.put("8", new String[]{"٨"});
        ALT_AR.put("9", new String[]{"٩"});
        ALT_AR.put("0", new String[]{"٠"});
        ALT_AR.put(".", new String[]{"؟", "!", ":", "…"});
        ALT_AR.put("،", new String[]{",", "؛", ";"});

        ALT_SYM.put("/", new String[]{"|", "\\"});
        ALT_SYM.put("-", new String[]{"–", "—"});
        ALT_SYM.put("=", new String[]{"≠", "≈", "~"});
        ALT_SYM.put("*", new String[]{"^", "°", "•"});
        ALT_SYM.put("<", new String[]{"≤", "«"});
        ALT_SYM.put(">", new String[]{"≥", "»"});
        ALT_SYM.put("?", new String[]{"¿"});
        ALT_SYM.put("!", new String[]{"¡"});
    }

    // ---------- État ----------
    private int lang = LANG_FR;
    private boolean symbols = false;
    private int shift = 0;               // 0 = minuscules, 1 = une majuscule, 2 = verrouillé
    private long lastShiftTap = 0;
    private LinearLayout root;
    private Key shiftKey;
    private final List<Key> keys = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PopupWindow popup;
    private float density = 1f;

    // ---------- Suggestions de mots ----------
    /** Mis à true par l'écran d'accueil pour effacer les mots appris. */
    public static volatile boolean clearLearnedRequested = false;

    // ---------- Dictée vocale continue ----------
    private static volatile ClavierService instance = null;
    private android.speech.SpeechRecognizer recognizer;
    private boolean listening = false;
    private ImageView micView;

    /** Appelé par VoiceActivity quand l'autorisation du micro vient d'être accordée. */
    public static void micPermissionGranted() {
        final ClavierService s = instance;
        if (s != null) {
            s.handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    s.beginListening();
                }
            }, 400);
        }
    }

    private void startVoice() {
        if (listening) {
            stopListening();
            return;
        }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Intent i = new Intent(this, VoiceActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(i);
            } catch (Exception ignored) {
            }
            return;
        }
        beginListening();
    }

    private void beginListening() {
        if (listening) return;
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
            android.widget.Toast.makeText(this, "Reconnaissance vocale indisponible sur ce téléphone",
                    android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        try {
            recognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new android.speech.RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float rmsdB) { }
                @Override public void onBufferReceived(byte[] buffer) { }
                @Override public void onEndOfSpeech() { }
                @Override public void onPartialResults(Bundle partialResults) { }
                @Override public void onEvent(int eventType, Bundle params) { }

                @Override
                public void onResults(Bundle results) {
                    java.util.ArrayList<String> r =
                            results.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
                    if (r != null && !r.isEmpty() && r.get(0) != null) commitVoice(r.get(0).trim());
                    relisten(150);
                }

                @Override
                public void onError(int error) {
                    if (error == android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        stopListening();
                        return;
                    }
                    if (error == android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY && recognizer != null) {
                        recognizer.cancel();
                        relisten(600);
                        return;
                    }
                    relisten(250);
                }
            });
        } catch (Exception e) {
            recognizer = null;
            return;
        }
        listening = true;
        updateMicTint();
        listenOnce();
    }

    private void relisten(long delayMs) {
        if (!listening) return;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                listenOnce();
            }
        }, delayMs);
    }

    private void listenOnce() {
        if (!listening || recognizer == null) return;
        String tag = (lang == LANG_FR) ? "fr-FR" : "ar";
        Intent i = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, tag);
        i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag);
        i.putExtra(android.speech.RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        i.putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        i.putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        i.putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 4000L);
        i.putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 4000L);
        i.putExtra("android.speech.extra.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", 8000L);
        try {
            recognizer.startListening(i);
        } catch (Exception e) {
            relisten(600);
        }
    }

    private void stopListening() {
        listening = false;
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) {
            try {
                recognizer.cancel();
                recognizer.destroy();
            } catch (Exception ignored) {
            }
            recognizer = null;
        }
        updateMicTint();
    }

    private void updateMicTint() {
        if (micView != null) {
            micView.setImageTintList(ColorStateList.valueOf(listening ? 0xFFFF5252 : C_TEXT));
        }
    }

    private void commitVoice(String t) {
        if (t.isEmpty()) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence before = ic.getTextBeforeCursor(1, 0);
        String out = t;
        if (before != null && before.length() == 1 && !Character.isWhitespace(before.charAt(0))) out = " " + t;
        ic.commitText(out, 1);
        scheduleSuggestions();
    }

    private void addMicButton(LinearLayout bar) {
        ImageView mic = new ImageView(this);
        micView = mic;
        mic.setImageResource(R.drawable.ic_mic);
        mic.setImageTintList(ColorStateList.valueOf(listening ? 0xFFFF5252 : C_TEXT));
        mic.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mic.setPadding(dp(8), dp(4), dp(8), dp(4));
        mic.setBackground(keyBackground(C_BG));
        mic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                startVoice();
            }
        });
        bar.addView(mic, new LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private static final int SUG_DELAY_MS = 40;
    private final TextView[] sugViews = new TextView[3];
    private final String[] sugs = new String[3];
    private boolean suggestOn = true;
    private boolean toolsOpen = false;
    private volatile Dict dictFr, dictAr;
    private final Map<String, Integer> learnedFr = new HashMap<>();
    private final Map<String, Integer> learnedAr = new HashMap<>();
    private final Map<String, String> foldCache = new HashMap<>();
    private int learnedChanges = 0;

    private final Runnable updateSugRunnable = new Runnable() {
        @Override
        public void run() {
            updateSuggestions();
        }
    };

    /** Dictionnaire classé par fréquence décroissante ; « folded » = sans accents ni voyelles brèves. */
    private static final class Dict {
        final String[] words;
        final String[] folded;

        Dict(List<String> list) {
            words = list.toArray(new String[0]);
            folded = new String[words.length];
            for (int i = 0; i < words.length; i++) folded[i] = fold(words[i]);
        }
    }

    // ---------- Une touche ----------
    private final class Key {
        final int type;
        final float weight;
        int style = S_NORMAL;
        float textSp = 20;
        boolean dim;
        boolean isLetter;
        boolean mirror;
        int icon;
        String nLabel = "", nOut = "", sLabel = "", sOut = "";
        View view;
        TextView tv;
        ImageView iv;

        Key(int type, float weight) {
            this.type = type;
            this.weight = weight;
        }

        String label() {
            return (isLetter && shift != 0) ? sLabel : nLabel;
        }

        String out() {
            return (isLetter && shift != 0) ? sOut : nOut;
        }
    }

    // =====================================================================
    //  Cycle de vie
    // =====================================================================

    @Override
    public void onCreate() {
        super.onCreate();
        density = getResources().getDisplayMetrics().density;
        lang = getSharedPreferences("clavier", MODE_PRIVATE).getInt("lang", LANG_FR);
        toolsOpen = getSharedPreferences("clavier", MODE_PRIVATE).getBoolean("tools_open", false);
        loadLearned();
        new Thread(new Runnable() {
            @Override
            public void run() {
                dictFr = loadDict("fr_words.txt");
                dictAr = loadDict("ar_words.txt");
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateSuggestions();
                    }
                });
            }
        }).start();
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                  int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);
        scheduleSuggestions();
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override
    public View onCreateInputView() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        root.setBackgroundColor(C_BG);
        root.setPadding(dp(2), dp(2), dp(2), dp(6));
        build();
        return root;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        instance = this;
        if (clearLearnedRequested) {
            clearLearned();
            clearLearnedRequested = false;
        }
        suggestOn = !isSensitive(info);
        if (!restarting) {
            symbols = false;
            shift = 0;
        }
        build();
        autoShift();
        scheduleSuggestions();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        if (listening) stopListening();
        dismissPopup();
        handler.removeCallbacksAndMessages(null);
        saveLearned();
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onWindowHidden() {
        dismissPopup();
        handler.removeCallbacksAndMessages(null);
        super.onWindowHidden();
    }

    @Override
    public void onDestroy() {
        dismissPopup();
        handler.removeCallbacksAndMessages(null);
        saveLearned();
        super.onDestroy();
    }

    // =====================================================================
    //  Construction du clavier
    // =====================================================================

    private void build() {
        if (root == null) return;
        dismissPopup();
        handler.removeCallbacksAndMessages(null);
        root.removeAllViews();
        keys.clear();
        shiftKey = null;

        // 0) Barre de suggestions de mots
        addSuggestionBar();

        // 1) Rangée de navigation
        List<Key> nav = new ArrayList<>();
        nav.add(icon(T_BKSP, R.drawable.ic_backspace, 1f, S_NAV));
        Key del = icon(T_DEL, R.drawable.ic_backspace, 1f, S_NAV);
        del.mirror = true;
        nav.add(del);
        nav.add(fn(T_LEFT, "←", 1f, S_NAV, 23));
        nav.add(fn(T_RIGHT, "→", 1f, S_NAV, 23));
        nav.add(fn(T_UP, "↑", 1f, S_NAV, 23));
        nav.add(fn(T_DOWN, "↓", 1f, S_NAV, 23));
        nav.add(fn(T_HOME, "|←", 1f, S_NAV, 18));
        nav.add(fn(T_END, "→|", 1f, S_NAV, 18));
        addRow(nav, 34);

        // 2) Rangée des chiffres
        List<Key> nums = new ArrayList<>();
        for (String d : NUMS) nums.add(ch(d, null, false, 21));
        addRow(nums, 40);

        // 3) Lettres ou symboles
        if (symbols) {
            for (int r = 0; r < 3; r++) {
                List<Key> row = new ArrayList<>();
                for (String s : SYM_ROWS[r]) row.add(ch(s, null, false, 23));
                if (r == 2) row.add(icon(T_BKSP, R.drawable.ic_backspace, 1.5f, S_FN));
                addRow(row, 40);
            }
        } else {
            String[][] rows = (lang == LANG_FR) ? FR_ROWS : AR_ROWS;
            for (int r = 0; r < 3; r++) {
                List<Key> row = new ArrayList<>();
                if (r == 2) {
                    shiftKey = icon(T_SHIFT, R.drawable.ic_shift, 1.4f, S_FN);
                    row.add(shiftKey);
                }
                for (int i = 0; i < rows[r].length; i++) {
                    String sh = (lang == LANG_AR) ? AR_SHIFT_ROWS[r][i] : null;
                    row.add(ch(rows[r][i], sh, true, lang == LANG_FR ? LETTER_FR_LOWER : 23));
                }
                if (r == 2) row.add(icon(T_BKSP, R.drawable.ic_backspace, 1.4f, S_FN));
                addRow(row, 40);
            }
        }

        // 4) Rangée du bas
        List<Key> bottom = new ArrayList<>();
        bottom.add(fn(T_SYM,
                symbols ? (lang == LANG_FR ? "ABC" : "أبج") : "?123",
                1.4f, S_FN, 16));
        bottom.add(fn(T_LANG, lang == LANG_FR ? "ع" : "FR", 1.1f, S_FN, 19));
        bottom.add(ch(lang == LANG_FR ? "," : "،", null, false, 25));
        Key space = fn(T_SPACE,
                lang == LANG_FR ? "Français" : "العربية",
                4.0f, S_NORMAL, 14);
        space.dim = true;
        bottom.add(space);
        bottom.add(ch(".", null, false, 25));
        String el = enterLabel();
        bottom.add(el == null
                ? icon(T_ENTER, R.drawable.ic_enter, 1.5f, S_ACCENT)
                : fn(T_ENTER, el, 1.5f, S_ACCENT, 20));
        addRow(bottom, 40);

        refreshLabels();
    }

    private Key ch(String spec, String shiftSpec, boolean letter, float sp) {
        Key k = new Key(T_CHAR, 1f);
        String[] p = parse(spec);
        k.nLabel = p[0];
        k.nOut = p[1];
        k.isLetter = letter;
        if (letter) {
            String[] q = (shiftSpec != null)
                    ? parse(shiftSpec)
                    : new String[]{p[0].toUpperCase(Locale.FRENCH), p[1].toUpperCase(Locale.FRENCH)};
            k.sLabel = q[0];
            k.sOut = q[1];
        }
        k.textSp = sp;
        return k;
    }

    private static String[] parse(String s) {
        int i = s.indexOf('|');
        if (i > 0) return new String[]{s.substring(0, i), s.substring(i + 1)};
        return new String[]{s, s};
    }

    private Key fn(int type, String label, float w, int style, float sp) {
        Key k = new Key(type, w);
        k.nLabel = label;
        k.style = style;
        k.textSp = sp;
        return k;
    }

    private Key icon(int type, int res, float w, int style) {
        Key k = new Key(type, w);
        k.icon = res;
        k.style = style;
        return k;
    }

    private void addRow(List<Key> list, int heightDp) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        for (Key k : list) {
            View v = makeView(k);
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, k.weight);
            lp.setMargins(0, 0, 0, 0);
            r.addView(v, lp);
            keys.add(k);
        }
        root.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)));
    }

    private View makeView(Key k) {
        int bg = k.style == S_ACCENT ? C_ACCENT : k.style == S_NAV ? C_NAV : k.style == S_FN ? C_FN : C_KEY;
        int fg = k.style == S_ACCENT ? C_ON_ACCENT : k.style == S_NAV ? C_ACCENT : C_TEXT;
        if (k.dim) fg = C_DIM;
        View v;
        if (k.icon != 0) {
            ImageView iv = new ImageView(this);
            iv.setImageResource(k.icon);
            iv.setImageTintList(ColorStateList.valueOf(fg));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setPadding(dp(9), dp(7), dp(9), dp(7));
            if (k.mirror) iv.setScaleX(-1f);
            k.iv = iv;
            v = iv;
        } else {
            TextView tv = new TextView(this);
            tv.setGravity(Gravity.CENTER);
            tv.setTextColor(fg);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, k.textSp);
            tv.setSingleLine(true);
            tv.setText(k.label());
            k.tv = tv;
            v = tv;
        }
        v.setBackground(new InsetDrawable(keyBackground(bg), dp(2)));
        v.setOnTouchListener(new KeyTouch(k));
        k.view = v;
        return v;
    }

    private void refreshLabels() {
        for (Key k : keys) {
            if (k.isLetter && k.tv != null) {
                k.tv.setText(k.label());
                if (lang == LANG_FR) {
                    k.tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, shift != 0 ? LETTER_FR_UPPER : LETTER_FR_LOWER);
                }
            }
        }
        if (shiftKey != null && shiftKey.iv != null) {
            int res = shift == 0 ? R.drawable.ic_shift : (shift == 1 ? R.drawable.ic_shift_on : R.drawable.ic_caps);
            shiftKey.iv.setImageResource(res);
            shiftKey.iv.setImageTintList(ColorStateList.valueOf(shift == 0 ? C_TEXT : C_ACCENT));
        }
    }

    private String enterLabel() {
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei == null || (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return null;
        switch (ei.imeOptions & EditorInfo.IME_MASK_ACTION) {
            case EditorInfo.IME_ACTION_GO:
                return "→";
            case EditorInfo.IME_ACTION_NEXT:
                return "→|";
            case EditorInfo.IME_ACTION_DONE:
                return "✓";
            case EditorInfo.IME_ACTION_SEARCH:
                return "🔍";
            default:
                return null;
        }
    }

    // =====================================================================
    //  Apparence
    // =====================================================================

    private int dp(float v) {
        return Math.round(v * density);
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(6));
        return g;
    }

    private StateListDrawable keyBackground(int color) {
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, rounded(lighten(color)));
        sl.addState(new int[]{}, rounded(color));
        return sl;
    }

    private static int lighten(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        r += (255 - r) / 4;
        g += (255 - g) / 4;
        b += (255 - b) / 4;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // =====================================================================
    //  Gestion des touches
    // =====================================================================

    private static boolean isRepeatable(Key k) {
        switch (k.type) {
            case T_BKSP:
            case T_DEL:
            case T_LEFT:
            case T_RIGHT:
            case T_UP:
            case T_DOWN:
                return true;
            default:
                return false;
        }
    }

    private final class KeyTouch implements View.OnTouchListener {
        private final Key key;
        private boolean longDone;

        private final Runnable longPress = new Runnable() {
            @Override
            public void run() {
                longDone = true;
                showAlternates(key);
            }
        };

        private final Runnable repeat = new Runnable() {
            @Override
            public void run() {
                performKey(key);
                handler.postDelayed(this, 45);
            }
        };

        KeyTouch(Key key) {
            this.key = key;
        }

        private void cancelTimers() {
            handler.removeCallbacks(longPress);
            handler.removeCallbacks(repeat);
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            int slop = dp(12);
            boolean inside = e.getX() >= -slop && e.getX() <= v.getWidth() + slop
                    && e.getY() >= -slop && e.getY() <= v.getHeight() + slop;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    longDone = false;
                    if (isRepeatable(key)) {
                        performKey(key);
                        handler.postDelayed(repeat, 380);
                    } else if (alternatesFor(key) != null) {
                        handler.postDelayed(longPress, 380);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!inside) {
                        v.setPressed(false);
                        cancelTimers();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    v.setPressed(false);
                    cancelTimers();
                    if (!isRepeatable(key) && !longDone && inside) performKey(key);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    cancelTimers();
                    return true;
                default:
                    return true;
            }
        }
    }

    private void performKey(Key k) {
        switch (k.type) {
            case T_CHAR:
                commit(k.out());
                break;
            case T_SPACE:
                commit(" ");
                break;
            case T_BKSP:
                sendKey(KeyEvent.KEYCODE_DEL);
                autoShift();
                break;
            case T_DEL:
                sendKey(KeyEvent.KEYCODE_FORWARD_DEL);
                break;
            case T_LEFT:
                sendKey(KeyEvent.KEYCODE_DPAD_LEFT);
                break;
            case T_RIGHT:
                sendKey(KeyEvent.KEYCODE_DPAD_RIGHT);
                break;
            case T_UP:
                sendKey(KeyEvent.KEYCODE_DPAD_UP);
                break;
            case T_DOWN:
                sendKey(KeyEvent.KEYCODE_DPAD_DOWN);
                break;
            case T_HOME:
                sendKey(KeyEvent.KEYCODE_MOVE_HOME);
                break;
            case T_END:
                sendKey(KeyEvent.KEYCODE_MOVE_END);
                break;
            case T_SHIFT:
                toggleShift();
                break;
            case T_SYM:
                symbols = !symbols;
                rebuildLater();
                break;
            case T_LANG:
                switchLang();
                break;
            case T_ENTER:
                handleEnter();
                break;
            default:
                break;
        }
        scheduleSuggestions();
    }

    private void commit(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || s == null || s.isEmpty()) return;
        // Apprentissage : un mot est terminé quand on tape un espace ou une ponctuation
        if (suggestOn && s.length() == 1 && !Character.isLetter(s.charAt(0))) {
            learnWord(wordBeforeCursor());
        }
        ic.commitText(s, 1);
        if (shift == 1) setShift(0);
        autoShift();
    }

    private void sendKey(int code) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        long now = SystemClock.uptimeMillis();
        int flags = KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE;
        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags));
        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags));
    }

    private void handleEnter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        EditorInfo ei = getCurrentInputEditorInfo();
        int action = (ei == null) ? EditorInfo.IME_ACTION_NONE : (ei.imeOptions & EditorInfo.IME_MASK_ACTION);
        boolean noAction = ei == null
                || (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
                || action == EditorInfo.IME_ACTION_NONE
                || action == EditorInfo.IME_ACTION_UNSPECIFIED;
        if (noAction) {
            sendKey(KeyEvent.KEYCODE_ENTER);
        } else {
            ic.performEditorAction(action);
        }
        autoShift();
    }

    // ---------- Majuscules ----------

    private void setShift(int s) {
        shift = s;
        refreshLabels();
    }

    private void toggleShift() {
        long now = SystemClock.uptimeMillis();
        if (shift == 1 && now - lastShiftTap < 400) {
            setShift(2);
        } else if (shift == 0) {
            setShift(1);
        } else {
            setShift(0);
        }
        lastShiftTap = now;
    }

    /** Majuscule automatique en début de phrase (français uniquement). */
    private void autoShift() {
        if (lang != LANG_FR || symbols || shift != 0) return;
        EditorInfo ei = getCurrentInputEditorInfo();
        InputConnection ic = getCurrentInputConnection();
        if (ei == null || ic == null || ei.inputType == 0) return;
        if (ic.getCursorCapsMode(ei.inputType) != 0) setShift(1);
    }

    // ---------- Langue / disposition ----------

    private void switchLang() {
        lang = (lang == LANG_FR) ? LANG_AR : LANG_FR;
        getSharedPreferences("clavier", MODE_PRIVATE).edit().putInt("lang", lang).apply();
        symbols = false;
        shift = 0;
        rebuildLater();
    }

    private void rebuildLater() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                build();
                autoShift();
                scheduleSuggestions();
            }
        });
    }

    // =====================================================================
    //  Appui long : caractères alternatifs (accents, etc.)
    // =====================================================================

    private String[] alternatesFor(Key k) {
        if (k.type != T_CHAR) return null;
        String out = k.out();
        boolean up = lang == LANG_FR && k.isLetter && shift != 0;
        String lookup = up ? out.toLowerCase(Locale.FRENCH) : out;
        String[] a = null;
        if (symbols) a = ALT_SYM.get(lookup);
        if (a == null) a = (lang == LANG_FR ? ALT_FR : ALT_AR).get(lookup);
        if (a == null) return null;
        if (!up) return a;
        String[] res = new String[a.length];
        for (int i = 0; i < a.length; i++) res[i] = a[i].toUpperCase(Locale.FRENCH);
        return res;
    }

    private void showAlternates(Key k) {
        final String[] alts = alternatesFor(k);
        if (alts == null || k.view == null || root == null) return;
        dismissPopup();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        box.setPadding(dp(4), dp(4), dp(4), dp(4));
        box.setBackground(rounded(0xFF000000));

        for (final String a : alts) {
            TextView t = new TextView(this);
            t.setText(a);
            t.setTextColor(C_TEXT);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            t.setGravity(Gravity.CENTER);
            t.setBackground(keyBackground(C_KEY));
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    commit(a);
                    dismissPopup();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(42), dp(48));
            lp.setMargins(dp(2), 0, dp(2), 0);
            box.addView(t, lp);
        }

        box.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int w = box.getMeasuredWidth();
        int h = box.getMeasuredHeight();
        int[] loc = new int[2];
        k.view.getLocationOnScreen(loc);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int x = loc[0] + k.view.getWidth() / 2 - w / 2;
        x = Math.max(dp(2), Math.min(x, screenW - w - dp(2)));
        int y = Math.max(0, loc[1] - h - dp(6));

        popup = new PopupWindow(box, w, h, false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.showAtLocation(root, Gravity.NO_GRAVITY, x, y);
    }

    private void dismissPopup() {
        if (popup != null) {
            try {
                popup.dismiss();
            } catch (Exception ignored) {
            }
            popup = null;
        }
    }

    // =====================================================================
    //  Barre de suggestions de mots
    // =====================================================================

    private void addSuggestionBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        // Bouton à gauche : bascule entre suggestions de mots et outils (copier, coller...)
        ImageView toggle = new ImageView(this);
        toggle.setImageResource(R.drawable.ic_chevron);
        toggle.setImageTintList(ColorStateList.valueOf(C_ACCENT));
        toggle.setScaleType(ImageView.ScaleType.FIT_CENTER);
        toggle.setPadding(dp(5), dp(5), dp(5), dp(5));
        if (toolsOpen) toggle.setScaleX(-1f);
        toggle.setBackground(keyBackground(C_BG));
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                toolsOpen = !toolsOpen;
                getSharedPreferences("clavier", MODE_PRIVATE).edit().putBoolean("tools_open", toolsOpen).apply();
                rebuildLater();
            }
        });
        bar.addView(toggle, new LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.MATCH_PARENT));

        if (toolsOpen) {
            addToolButtons(bar);
            addMicButton(bar);
            root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));
            sugViews[0] = null;
            sugViews[1] = null;
            sugViews[2] = null;
            return;
        }

        for (int i = 0; i < 3; i++) {
            if (i > 0) {
                View div = new View(this);
                div.setBackgroundColor(0xFF2E2E2E);
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(1), dp(20));
                dl.gravity = Gravity.CENTER_VERTICAL;
                bar.addView(div, dl);
            }
            final int idx = i;
            TextView tv = new TextView(this);
            tv.setGravity(Gravity.CENTER);
            tv.setTextColor(C_TEXT);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setPadding(dp(6), 0, dp(6), 0);
            if (i == 1) tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setBackground(keyBackground(C_BG));
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applySuggestion(idx);
                }
            });
            bar.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            sugViews[i] = tv;
            sugs[i] = null;
        }
        addMicButton(bar);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));
    }

    private static final int ACTION_ATTACH = -1;
    private static final int ACTION_CLEAR_ALL = -2;

    /** Outils : copier, coller, couper, tout sélectionner, envoyer un fichier (trombone). */
    private void addToolButtons(LinearLayout bar) {
        // De gauche à droite : fichier, effacer, sélectionner, couper, copier, coller.
        // Lu depuis l'extrême droite : coller, copier, couper, sélectionner, effacer, fichier.
        final int[] icons = {R.drawable.ic_attach, R.drawable.ic_clear_all, R.drawable.ic_select_all,
                R.drawable.ic_cut, R.drawable.ic_copy, R.drawable.ic_paste};
        final int[] actions = {ACTION_ATTACH, ACTION_CLEAR_ALL, android.R.id.selectAll,
                android.R.id.cut, android.R.id.copy, android.R.id.paste};
        for (int i = 0; i < icons.length; i++) {
            final int action = actions[i];
            ImageView b = new ImageView(this);
            b.setImageResource(icons[i]);
            b.setImageTintList(ColorStateList.valueOf(C_TEXT));
            b.setScaleType(ImageView.ScaleType.FIT_CENTER);
            b.setPadding(dp(6), dp(3), dp(6), dp(3));
            b.setBackground(keyBackground(C_BG));
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    if (action == ACTION_ATTACH) {
                        Intent i = new Intent(ClavierService.this, PickFileActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                        return;
                    }
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) {
                        if (action == ACTION_CLEAR_ALL) {
                            clearAllText(ic);
                        } else {
                            ic.performContextMenuAction(action);
                        }
                    }
                    scheduleSuggestions();
                }
            });
            bar.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
    }

    /** Efface tout le texte du champ en cours (avant et après le curseur). */
    private void clearAllText(InputConnection ic) {
        ic.beginBatchEdit();
        ic.performContextMenuAction(android.R.id.selectAll);
        ic.commitText("", 1);
        ic.deleteSurroundingText(100000, 100000);
        ic.endBatchEdit();
        if (lang == LANG_FR && shift == 0) autoShift();
    }

    private void scheduleSuggestions() {
        handler.removeCallbacks(updateSugRunnable);
        handler.postDelayed(updateSugRunnable, SUG_DELAY_MS);
    }

    private void updateSuggestions() {
        if (sugViews[0] == null) return;
        String[] res = new String[3];
        if (suggestOn) computeSuggestions(wordBeforeCursor(), res);
        // Disposition façon Gboard : meilleure suggestion au centre
        sugs[1] = res[0];
        sugs[0] = res[1];
        sugs[2] = res[2];
        for (int i = 0; i < 3; i++) {
            if (sugViews[i] != null) sugViews[i].setText(sugs[i] == null ? "" : sugs[i]);
        }
    }

    /** Mot en cours de saisie (lettres juste avant le curseur). */
    private String wordBeforeCursor() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return "";
        CharSequence t = ic.getTextBeforeCursor(32, 0);
        if (t == null) return "";
        int end = t.length();
        int i = end;
        while (i > 0) {
            char c = t.charAt(i - 1);
            if (Character.isLetter(c) || Character.getType(c) == Character.NON_SPACING_MARK) i--;
            else break;
        }
        return t.subSequence(i, end).toString();
    }

    private void computeSuggestions(String prefix, String[] out) {
        Dict d = (lang == LANG_FR) ? dictFr : dictAr;
        Map<String, Integer> learned = (lang == LANG_FR) ? learnedFr : learnedAr;
        String fp = fold(prefix);
        boolean cap = !prefix.isEmpty() && Character.isUpperCase(prefix.charAt(0));
        List<String> res = new ArrayList<>();

        // 1) Mots appris (au moins 2 fois), les plus utilisés d'abord (2 places maximum)
        final Map<String, Integer> counts = learned;
        List<String> mine = new ArrayList<>();
        for (Map.Entry<String, Integer> e : learned.entrySet()) {
            if (e.getValue() >= 2 && (fp.isEmpty() || foldCached(e.getKey()).startsWith(fp))) {
                mine.add(e.getKey());
            }
        }
        Collections.sort(mine, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return counts.get(b) - counts.get(a);
            }
        });
        for (String w : mine) {
            if (res.size() >= 2) break;
            res.add(w);
        }

        // 2) Dictionnaire intégré, par fréquence
        if (d != null) {
            for (int i = 0; i < d.words.length && res.size() < 3; i++) {
                if (fp.isEmpty() || d.folded[i].startsWith(fp)) {
                    String w = d.words[i];
                    if (!containsIgnoreCase(res, w)) res.add(w);
                }
            }
        }

        for (int i = 0; i < 3 && i < res.size(); i++) {
            String w = res.get(i);
            if (cap && !w.isEmpty()) w = w.substring(0, 1).toUpperCase(Locale.FRENCH) + w.substring(1);
            out[i] = w;
        }
    }

    private static boolean containsIgnoreCase(List<String> list, String w) {
        for (String s : list) if (s.equalsIgnoreCase(w)) return true;
        return false;
    }

    private void applySuggestion(int idx) {
        String w = sugs[idx];
        if (w == null || w.isEmpty()) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        String prefix = wordBeforeCursor();
        ic.beginBatchEdit();
        if (!prefix.isEmpty()) ic.deleteSurroundingText(prefix.length(), 0);
        ic.commitText(w + " ", 1);
        ic.endBatchEdit();
        learnWord(w);
        if (shift == 1) setShift(0);
        autoShift();
        scheduleSuggestions();
    }

    /** Pas de suggestions ni d'apprentissage dans les mots de passe, e-mails, adresses web, nombres. */
    private static boolean isSensitive(EditorInfo ei) {
        if (ei == null) return true;
        int cls = ei.inputType & InputType.TYPE_MASK_CLASS;
        int var = ei.inputType & InputType.TYPE_MASK_VARIATION;
        if (cls == InputType.TYPE_CLASS_TEXT) {
            return var == InputType.TYPE_TEXT_VARIATION_PASSWORD
                    || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                    || var == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    || var == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                    || var == InputType.TYPE_TEXT_VARIATION_URI
                    || (ei.inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0;
        }
        return true; // nombre, téléphone, date/heure, autre
    }

    // ---------- Normalisation (sans accents, voyelles brèves arabes, variantes de lettres) ----------

    private static String fold(String s) {
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        StringBuilder b = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (Character.getType(c) == Character.NON_SPACING_MARK || c == 'ـ') continue;
            if (c == 'ٱ') c = 'ا';       // ٱ -> ا
            else if (c == 'ى') c = 'ي';  // ى -> ي
            else if (c == 'ة') c = 'ه';  // ة -> ه
            b.append(c);
        }
        return b.toString();
    }

    private String foldCached(String w) {
        String f = foldCache.get(w);
        if (f == null) {
            f = fold(w);
            if (foldCache.size() > 6000) foldCache.clear();
            foldCache.put(w, f);
        }
        return f;
    }

    // ---------- Dictionnaires intégrés ----------

    private Dict loadDict(String assetName) {
        List<String> list = new ArrayList<>(40000);
        try {
            InputStream in = getAssets().open(assetName);
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) list.add(line);
            }
            br.close();
        } catch (Exception ignored) {
        }
        return new Dict(list);
    }

    // ---------- Mots appris (stockés uniquement sur le téléphone) ----------

    private void learnWord(String w) {
        if (!suggestOn || w == null) return;
        String k = w.toLowerCase(Locale.ROOT);
        if (k.length() < 3 || k.length() > 30) return;
        Map<String, Integer> m = (lang == LANG_FR) ? learnedFr : learnedAr;
        Integer c = m.get(k);
        m.put(k, c == null ? 1 : Math.min(c + 1, 1000));
        if (m.size() > 4000) {
            Iterator<Map.Entry<String, Integer>> it = m.entrySet().iterator();
            while (it.hasNext()) {
                if (it.next().getValue() <= 1) it.remove();
            }
        }
        if (++learnedChanges >= 20) saveLearned();
    }

    private void loadLearned() {
        readLearned(new File(getFilesDir(), "appris_fr.txt"), learnedFr);
        readLearned(new File(getFilesDir(), "appris_ar.txt"), learnedAr);
    }

    private void readLearned(File f, Map<String, Integer> m) {
        if (!f.exists()) return;
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                int t = line.lastIndexOf('\t');
                if (t <= 0) continue;
                try {
                    m.put(line.substring(0, t), Integer.parseInt(line.substring(t + 1).trim()));
                } catch (NumberFormatException ignored) {
                }
            }
            br.close();
        } catch (Exception ignored) {
        }
    }

    private void saveLearned() {
        if (learnedChanges == 0) return;
        writeLearned(new File(getFilesDir(), "appris_fr.txt"), learnedFr);
        writeLearned(new File(getFilesDir(), "appris_ar.txt"), learnedAr);
        learnedChanges = 0;
    }

    private void writeLearned(File f, Map<String, Integer> m) {
        try {
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), "UTF-8"));
            for (Map.Entry<String, Integer> e : m.entrySet()) {
                bw.write(e.getKey() + "\t" + e.getValue() + "\n");
            }
            bw.close();
        } catch (Exception ignored) {
        }
    }

    private void clearLearned() {
        learnedFr.clear();
        learnedAr.clear();
        foldCache.clear();
        learnedChanges = 0;
        new File(getFilesDir(), "appris_fr.txt").delete();
        new File(getFilesDir(), "appris_ar.txt").delete();
    }
}
