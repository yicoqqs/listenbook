package com.voicetxt.reader;
import android.content.Context;
import java.util.Locale;
/** Presentation locale supplied by Android. No in-app language preference. */
final class AppLanguage {
    static Locale locale(Context context){return context.getResources().getConfiguration().getLocales().get(0);}
}
