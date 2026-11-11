package com.voicetxt.reader;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.provider.Settings;

/** System screen mirroring and in-app DLNA are separate routes, not connection states. */
final class CastMenu {
    static int dp(MainActivity a,float n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
    static GradientDrawable rounded(MainActivity a,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(a,radius));return d;}
    static TextView text(MainActivity a,String value,int size,int color){TextView t=new TextView(a);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    static Dialog sheet(MainActivity a,String heading,String subtitle,java.util.function.BiConsumer<Dialog,LinearLayout> fill){
        Dialog dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(a,22),dp(a,12),dp(a,22),dp(a,18));panel.setBackground(rounded(a,a.night?0xff24262d:Color.WHITE,24));
        View handle=new View(a);handle.setBackground(rounded(a,a.night?0xff555861:0xffdedfe3,3));LinearLayout.LayoutParams grip=new LinearLayout.LayoutParams(dp(a,32),dp(a,4));grip.gravity=Gravity.CENTER;grip.bottomMargin=dp(a,20);panel.addView(handle,grip);
        LinearLayout header=new LinearLayout(a);header.setGravity(Gravity.CENTER_VERTICAL);TextView title=text(a,heading,22,a.night?0xfff2f0eb:a.INK);title.setTypeface(null,Typeface.BOLD);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView close=text(a,"×",26,a.night?0xffc4c6cf:a.GRAY);close.setGravity(Gravity.CENTER);close.setContentDescription(a.getString(R.string.ui_11));header.addView(close,new LinearLayout.LayoutParams(dp(a,48),dp(a,48)));close.setOnClickListener(v->dialog.dismiss());panel.addView(header);
        TextView caption=text(a,subtitle,13,a.night?0xffb7bac4:a.GRAY);caption.setLineSpacing(dp(a,3),1);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(a,18);panel.addView(caption,cp);
        fill.accept(dialog,panel);ScrollView scroll=new ScrollView(a);scroll.setFillViewport(false);scroll.addView(panel);dialog.setContentView(scroll);dialog.setCanceledOnTouchOutside(true);
        Window window=dialog.getWindow();window.setBackgroundDrawableResource(android.R.color.transparent);window.setGravity(Gravity.BOTTOM);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.38f);dialog.show();
        int width=a.getResources().getDisplayMetrics().widthPixels;int height=a.getWindow().getDecorView().getHeight();int maxHeight=Math.max(dp(a,220),(int)(height*.88f));panel.measure(View.MeasureSpec.makeMeasureSpec(Math.min(width,dp(a,560)),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));window.setLayout(Math.min(width,dp(a,560)),Math.min(panel.getMeasuredHeight(),maxHeight));
        return dialog;
    }
    static void choice(MainActivity a,LinearLayout panel,String title,String subtitle,int icon,Runnable action){
        LinearLayout card=new LinearLayout(a);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(a,16),dp(a,18),dp(a,12),dp(a,18));card.setMinimumHeight(dp(a,98));card.setBackground(new RippleDrawable(ColorStateList.valueOf(a.night?0x22ffffff:0x11000000),rounded(a,a.night?0xff30333b:0xfff7f7f9,16),null));
        FrameLayout badge=new FrameLayout(a);badge.setBackground(rounded(a,a.night?0xff49322f:0xffffeae5,13));ImageView image=new ImageView(a);image.setImageResource(icon);image.setColorFilter(a.RED);badge.addView(image,new FrameLayout.LayoutParams(dp(a,25),dp(a,25),Gravity.CENTER));card.addView(badge,new LinearLayout.LayoutParams(dp(a,48),dp(a,48)));
        LinearLayout labels=new LinearLayout(a);labels.setOrientation(1);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(a,14),0,dp(a,8),0);card.addView(labels,lp);TextView heading=text(a,title,17,a.night?0xfff2f0eb:a.INK);heading.setTypeface(null,Typeface.BOLD);labels.addView(heading);TextView description=text(a,subtitle,13,a.night?0xffb7bac4:a.GRAY);description.setPadding(0,dp(a,6),0,0);description.setLineSpacing(dp(a,2),1);labels.addView(description);
        TextView arrow=text(a,"›",26,a.night?0xff9296a1:0xffb0b2bb);card.addView(arrow);LinearLayout.LayoutParams row=new LinearLayout.LayoutParams(-1,-2);row.bottomMargin=dp(a,12);panel.addView(card,row);card.setFocusable(true);card.setContentDescription(title+". "+subtitle);card.setOnClickListener(v->action.run());
    }
    static void show(MainActivity reader){
        sheet(reader,reader.getString(R.string.cast_sheet_title),reader.getString(R.string.cast_sheet_subtitle),(dialog,panel)->{
            choice(reader,panel,reader.getString(R.string.cast_mirror_short),reader.getString(R.string.cast_mirror_summary),R.drawable.reader_cast,()->{dialog.dismiss();mirrorHelp(reader);});
            String description=reader.cast!=null&&reader.cast.device!=null?reader.getString(R.string.cast_connected,reader.cast.device.name):reader.getString(R.string.cast_dlna_summary);
            choice(reader,panel,reader.getString(R.string.cast_dlna),description,R.drawable.player_order,()->{dialog.dismiss();if(reader.cast==null||reader.cast.closed)reader.cast=new DlnaCast(reader);reader.cast.show();});
        });
    }
    static void mirrorHelp(MainActivity reader){
        boolean switching=reader.cast!=null&&reader.cast.device!=null;
        sheet(reader,reader.getString(R.string.cast_mirror_short),reader.getString(R.string.cast_mirror_intro),(dialog,panel)->{
            String[] steps={reader.getString(R.string.cast_step_receiver),reader.getString(R.string.cast_step_select),reader.getString(R.string.cast_step_return)};
            for(int i=0;i<steps.length;i++){LinearLayout row=new LinearLayout(reader);row.setGravity(Gravity.CENTER_VERTICAL);TextView number=text(reader,String.valueOf(i+1),13,reader.RED);number.setGravity(Gravity.CENTER);number.setTypeface(null,Typeface.BOLD);number.setBackground(rounded(reader,reader.night?0xff49322f:0xffffeae5,16));row.addView(number,new LinearLayout.LayoutParams(dp(reader,30),dp(reader,30)));TextView step=text(reader,steps[i],15,reader.night?0xffe3e2de:reader.INK);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,-2,1);sp.setMargins(dp(reader,12),dp(reader,10),0,dp(reader,10));row.addView(step,sp);panel.addView(row);}
            TextView note=text(reader,reader.getString(switching?R.string.cast_switch_note:R.string.cast_mirror_note),13,reader.night?0xffb7bac4:reader.GRAY);note.setPadding(0,dp(reader,14),0,dp(reader,22));panel.addView(note);
            TextView connect=text(reader,reader.getString(R.string.cast_open_settings),16,Color.WHITE);connect.setTypeface(null,Typeface.BOLD);connect.setGravity(Gravity.CENTER);connect.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22ffffff),rounded(reader,reader.RED,14),null));panel.addView(connect,new LinearLayout.LayoutParams(-1,dp(reader,52)));connect.setOnClickListener(v->{dialog.dismiss();if(openSettings(reader)&&reader.cast!=null){reader.stop();reader.cast.close();reader.cast=null;if(reader.offline!=null)reader.offline.cast=null;}});
        });
    }
    static boolean openSettings(MainActivity reader){
        // OEMs expose different settings pages. Try the dedicated pages first.
        for(String action:new String[]{Settings.ACTION_CAST_SETTINGS,"android.settings.WIFI_DISPLAY_SETTINGS"}){
            try{reader.startActivity(new Intent(action));return true;}
            catch(ActivityNotFoundException|SecurityException unavailable){/* Try the next supported page. */}
        }
        new AlertDialog.Builder(reader).setTitle(R.string.cast_mirror)
                .setMessage(R.string.cast_mirror_unavailable)
                .setPositiveButton(android.R.string.ok,null).show();return false;
    }
    private CastMenu(){}
}
