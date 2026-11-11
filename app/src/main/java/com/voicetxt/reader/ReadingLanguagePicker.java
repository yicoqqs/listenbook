package com.voicetxt.reader;

import android.app.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Reading targets, separate from the interface locale. */
final class ReadingLanguagePicker extends Dialog {
    interface Host {String selected();boolean modelsReady();void select(String language);void help();default List<String> languages(){return Collections.emptyList();}default boolean canSelect(String language){return true;}}
    final Activity activity;final boolean night;final Host host;
    final int red=0xffdf382c;LinearLayout panel,body;final Map<String,View> choices=new LinkedHashMap<>();
    ReadingLanguagePicker(Activity activity,boolean night,Host host){super(activity);this.activity=activity;this.night=night;this.host=host;}
    int dp(float value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    int ink(){return night?0xffefede8:0xff292b30;}
    TextView text(String value,int size,int color){TextView view=new TextView(activity);view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    @Override protected void onCreate(Bundle state){super.onCreate(state);panel=new LinearLayout(activity);panel.setOrientation(1);GradientDrawable background=shape(night?0xff24262d:Color.WHITE,0);background.setCornerRadii(new float[]{dp(22),dp(22),dp(22),dp(22),0,0,0,0});panel.setBackground(background);panel.setClipToOutline(true);setContentView(panel);
        panel.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(0,0,0,insets.getSystemWindowInsetBottom());return insets;});
        View handle=new View(activity);handle.setBackground(shape(night?0xff555861:0xffdedee2,2));LinearLayout.LayoutParams grip=new LinearLayout.LayoutParams(dp(34),dp(4));grip.gravity=Gravity.CENTER_HORIZONTAL;grip.topMargin=dp(12);panel.addView(handle,grip);
        LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(22),dp(6),dp(10),0);panel.addView(header);TextView title=text(activity.getString(R.string.ui_18),21,ink());title.setTypeface(null,Typeface.BOLD);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView close=text("×",27,night?0xffb6b7bd:0xff92959d);close.setGravity(Gravity.CENTER);close.setContentDescription(activity.getString(R.string.ui_11));header.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));close.setOnClickListener(v->dismiss());
        TextView explanation=text(activity.getString(R.string.reading_translate_hint),12,night?0xffb4b5bd:0xff85858b);explanation.setPadding(dp(22),0,dp(22),dp(12));panel.addView(explanation);
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));body=new LinearLayout(activity);body.setOrientation(1);body.setPadding(dp(18),0,dp(18),dp(12));scroll.addView(body);
        refresh();
        TextView model=text(activity.getString(host.modelsReady()?R.string.hy_ready_hint:R.string.hy_missing_hint),12,night?0xffb9bcc4:0xff777c85);model.setPadding(dp(22),dp(4),dp(22),dp(14));panel.addView(model);
        View divider=new View(activity);divider.setBackgroundColor(night?0xff3a3d45:0xffeeeeef);panel.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        LinearLayout footer=new LinearLayout(activity);footer.setGravity(Gravity.CENTER_VERTICAL);footer.setPadding(dp(22),dp(4),dp(22),dp(8));panel.addView(footer);TextView help=text(activity.getString(R.string.ui_100)+" ›",14,red);help.setMinHeight(dp(48));footer.addView(help,new LinearLayout.LayoutParams(0,-2,1));help.setOnClickListener(v->{dismiss();host.help();});TextView attribution=text("HY-MT1.5",12,night?0xffaeb1bb:0xff85858b);attribution.setGravity(Gravity.CENTER);footer.addView(attribution,new LinearLayout.LayoutParams(dp(100),dp(24)));
        Window window=getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setGravity(Gravity.BOTTOM);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.38f);window.setNavigationBarColor(night?0xff24262d:Color.WHITE);}
    }
    void refresh(){if(body==null)return;body.removeAllViews();choices.clear();List<String> codes=new ArrayList<>();codes.add("");for(String value:host.languages()){String code=VoiceLanguages.canonical(value);if(!code.isEmpty()&&!codes.contains(code))codes.add(code);}
        for(int i=0;i<codes.size();i++){String code=codes.get(i);String title=code.isEmpty()?activity.getString(R.string.reading_original):VoiceLanguages.name(code,AppLanguage.locale(activity));String symbol=code.isEmpty()?"Aa":code.equals("zh")?"中":code.equals("ja")?"日":code.toUpperCase(Locale.ROOT);boolean chosen=code.equals(host.selected());LinearLayout card=new LinearLayout(activity);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(14),dp(14),dp(14),dp(14));card.setMinimumHeight(dp(86));GradientDrawable fill=shape(chosen?(night?0xff402d30:0xfffff1ef):(night?0xff2d3038:0xfff7f7f8),14);if(chosen)fill.setStroke(dp(1),red);card.setBackground(fill);card.setSelected(chosen);card.setFocusable(true);card.setContentDescription(title);LinearLayout.LayoutParams row=new LinearLayout.LayoutParams(-1,-2);row.topMargin=dp(9);body.addView(card,row);choices.put(code,card);
            TextView icon=text(symbol,19,chosen?red:night?0xffc9cbd1:0xff656a73);icon.setTypeface(null,Typeface.BOLD);icon.setGravity(Gravity.CENTER);icon.setBackground(shape(chosen?(night?0xff523136:0xffffe2dc):(night?0xff3b3e48:0xffeaecef),12));icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);card.addView(icon,new LinearLayout.LayoutParams(dp(42),dp(42)));
            LinearLayout words=new LinearLayout(activity);words.setOrientation(1);LinearLayout.LayoutParams wordsLp=new LinearLayout.LayoutParams(0,-2,1);wordsLp.setMarginStart(dp(13));wordsLp.setMarginEnd(dp(10));card.addView(words,wordsLp);TextView name=text(title,17,chosen?red:ink());name.setTypeface(null,Typeface.BOLD);words.addView(name);TextView subtitle=text(activity.getString(i==0?R.string.reading_original_hint:R.string.reading_translate_hint),12,night?0xffaeb1bb:0xff85858b);subtitle.setPadding(0,dp(5),0,0);words.addView(subtitle);
            TextView tick=text(chosen?"✓":"",14,Color.WHITE);tick.setGravity(Gravity.CENTER);GradientDrawable circle=shape(chosen?red:Color.TRANSPARENT,20);if(!chosen)circle.setStroke(dp(1),night?0xff626671:0xffd3d6dc);tick.setBackground(circle);tick.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);card.addView(tick,new LinearLayout.LayoutParams(dp(22),dp(22)));card.setOnClickListener(v->{if(!isShowing())return;if(code.equals(host.selected())){dismiss();return;}if(!host.canSelect(code))return;host.select(code);dismiss();});
        }
    }
    @Override public void show(){super.show();Window window=getWindow();if(window!=null){int height=activity.getWindow().getDecorView().getHeight();if(height<=0)height=activity.getResources().getDisplayMetrics().heightPixels;window.setLayout(-1,Math.min(dp(620),Math.round(height*.86f)));}panel.setTranslationY(dp(32));panel.setAlpha(0);panel.animate().translationY(0).alpha(1).setDuration(220).start();panel.requestApplyInsets();}
}
