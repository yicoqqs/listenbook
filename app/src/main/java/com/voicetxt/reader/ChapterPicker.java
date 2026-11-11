package com.voicetxt.reader;

import android.app.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;
import java.util.*;

/** Searchable chapter sheet; list positions always map to the original chapter index. */
final class ChapterPicker extends Dialog {
    interface Host {int current();void open(int index);}
    final Activity activity;final List<String> names;final Host host;final boolean night;
    final ArrayList<Integer> visible=new ArrayList<>();
    LinearLayout panel;EditText search;ListView list;TextView heading,empty;BaseAdapter adapter;
    ChapterPicker(Activity activity,List<String> names,boolean night,Host host){super(activity);this.activity=activity;this.names=new ArrayList<>(names);this.night=night;this.host=host;}
    int dp(float value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    int ink(){return night?0xffe5e4df:0xff41434a;}
    TextView text(String value,int size){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(ink());t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    @Override protected void onCreate(Bundle state){super.onCreate(state);panel=new LinearLayout(activity);panel.setOrientation(1);panel.setBackgroundColor(night?0xff24262d:Color.WHITE);panel.setFocusableInTouchMode(true);setContentView(panel);
        heading=text(activity.getString(R.string.chapter_count,names.size()),18);heading.setPadding(dp(20),0,dp(20),0);panel.addView(heading,new LinearLayout.LayoutParams(-1,dp(54)));divider();
        LinearLayout searchRow=new LinearLayout(activity);searchRow.setGravity(Gravity.CENTER_VERTICAL);searchRow.setPadding(dp(16),dp(8),dp(12),dp(8));panel.addView(searchRow,new LinearLayout.LayoutParams(-1,dp(56)));
        search=new EditText(activity);search.setSingleLine(true);search.setTextSize(15);search.setTextColor(ink());search.setHintTextColor(night?0xff92949c:0xff999ba1);search.setHint(activity.getString(R.string.ui_4));search.setContentDescription(activity.getString(R.string.ui_5));search.setPadding(dp(12),0,dp(12),0);GradientDrawable field=new GradientDrawable();field.setColor(night?0xff30333c:0xfff4f5f7);field.setCornerRadius(dp(20));search.setBackground(field);search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);searchRow.addView(search,new LinearLayout.LayoutParams(0,-1,1));
        TextView clear=text(activity.getString(R.string.ui_6),14);clear.setGravity(Gravity.CENTER);clear.setTextColor(0xffdf382c);searchRow.addView(clear,new LinearLayout.LayoutParams(dp(48),-1));clear.setOnClickListener(v->search.setText(""));divider();
        FrameLayout results=new FrameLayout(activity);panel.addView(results,new LinearLayout.LayoutParams(-1,0,1));list=new ListView(activity);list.setContentDescription(activity.getString(R.string.ui_7));list.setDivider(new android.graphics.drawable.ColorDrawable(night?0xff383b43:0xffeeeeee));list.setDividerHeight(dp(1));list.setCacheColorHint(Color.TRANSPARENT);list.setClipToPadding(false);results.addView(list,new FrameLayout.LayoutParams(-1,-1));empty=text(activity.getString(R.string.ui_8),15);empty.setGravity(Gravity.CENTER);results.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);
        adapter=new BaseAdapter(){public int getCount(){return visible.size();}public Integer getItem(int position){return visible.get(position);}public long getItemId(int position){return visible.get(position);}public boolean hasStableIds(){return true;}public View getView(int position,View recycled,ViewGroup parent){TextView row=recycled instanceof TextView?(TextView)recycled:text("",16);int chapter=visible.get(position);boolean current=chapter==host.current();row.setText(names.get(chapter));row.setPadding(dp(20),dp(16),dp(20),dp(16));row.setMinHeight(dp(60));row.setTextColor(current?0xffdf382c:ink());row.setTypeface(null,current?Typeface.BOLD:Typeface.NORMAL);row.setBackgroundColor(current?(night?0xff402d30:0xfffff1ef):Color.TRANSPARENT);row.setSelected(current);row.setContentDescription((current?activity.getString(R.string.ui_9):activity.getString(R.string.ui_10))+names.get(chapter));return row;}};list.setAdapter(adapter);
        list.setOnItemClickListener((parent,view,position,id)->{int index=visible.get(position);((InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);dismiss();host.open(index);});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){filter(s.toString());}public void afterTextChanged(Editable e){}});
        divider();TextView close=text(activity.getString(R.string.ui_11),18);close.setGravity(Gravity.CENTER);panel.addView(close,new LinearLayout.LayoutParams(-1,dp(52)));close.setOnClickListener(v->dismiss());filter("");panel.requestFocus();
        Window window=getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setGravity(Gravity.BOTTOM);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.4f);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);}
    }
    void divider(){View line=new View(activity);line.setBackgroundColor(night?0xff383b43:0xffeeeeee);panel.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));}
    void filter(String query){String q=query.trim().toLowerCase(Locale.ROOT);visible.clear();for(int i=0;i<names.size();i++)if(q.isEmpty()||names.get(i).toLowerCase(Locale.ROOT).contains(q)||String.valueOf(i+1).equals(q))visible.add(i);heading.setText(q.isEmpty()?activity.getString(R.string.chapter_count,names.size()):activity.getString(R.string.chapter_matches,names.size(),visible.size()));adapter.notifyDataSetChanged();if(!q.isEmpty())list.setSelection(0);else locateCurrent();}
    void locateCurrent(){int row=visible.indexOf(host.current());if(row>=0)list.post(()->list.setSelectionFromTop(row,0));}
    void refreshCurrent(){if(adapter!=null)adapter.notifyDataSetChanged();}
    @Override public void show(){super.show();Window window=getWindow();if(window!=null){int height=activity.getWindow().getDecorView().getHeight();if(height<=0)height=activity.getResources().getDisplayMetrics().heightPixels;window.setLayout(-1,(int)(height*.64f));}locateCurrent();}
}
