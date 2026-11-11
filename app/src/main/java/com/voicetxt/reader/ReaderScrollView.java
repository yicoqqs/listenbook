package com.voicetxt.reader;

import android.content.Context;
import android.view.*;
import android.widget.ScrollView;

/** Distinguishes manual scrolling/fling from playback-driven scrolling. */
final class ReaderScrollView extends ScrollView {
    interface Listener {void movement();void released();}
    final Listener listener;final int slop;
    boolean touching,dragging,manualMotion;float downX,downY;
    ReaderScrollView(Context context,Listener listener){super(context);this.listener=listener;slop=ViewConfiguration.get(context).getScaledTouchSlop();}
    void endManualMotion(){manualMotion=false;dragging=false;}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:touching=true;dragging=false;downX=event.getX();downY=event.getY();break;
            case MotionEvent.ACTION_MOVE:float dx=Math.abs(event.getX()-downX),dy=Math.abs(event.getY()-downY);if(dy>slop&&dy>dx){dragging=true;manualMotion=true;listener.movement();}break;
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:touching=false;if(dragging){manualMotion=true;listener.movement();}dragging=false;listener.released();break;
        }
        return super.dispatchTouchEvent(event);
    }
    @Override protected void onScrollChanged(int x,int y,int oldX,int oldY){super.onScrollChanged(x,y,oldX,oldY);if(listener!=null&&y!=oldY&&manualMotion)listener.movement();}
    @Override public boolean onGenericMotionEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_SCROLL){manualMotion=true;listener.movement();}return super.onGenericMotionEvent(event);}
    @Override public boolean executeKeyEvent(KeyEvent event){boolean handled=super.executeKeyEvent(event);if(handled&&event.getAction()==KeyEvent.ACTION_DOWN){manualMotion=true;listener.movement();}return handled;}
}
