package com.voicetxt.reader;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.view.*;
import android.widget.FrameLayout;

/** App-window-only entry to the retained reader. Never requests overlay permission. */
final class PlaybackBubble implements Application.ActivityLifecycleCallbacks {
    static PlaybackBubble instance;
    final SharedPreferences prefs;
    Activity activity;FrameLayout decor;Bubble view;
    float relativeX,relativeY;
    final View.OnLayoutChangeListener resized=(v,l,t,r,b,ol,ot,or,ob)->{if(view!=null&&!view.dragging)view.place();};
    PlaybackBubble(Application app){prefs=app.getSharedPreferences("playback_bubble",0);relativeX=prefs.getFloat("x",1f);relativeY=prefs.getFloat("y",.76f);}
    static void install(Application app){if(instance==null){instance=new PlaybackBubble(app);app.registerActivityLifecycleCallbacks(instance);}}
    static void refresh(){if(instance!=null)instance.update();}
    void update(){
        if(activity==null||activity.isFinishing()||activity instanceof MainActivity||PlaybackService.reader()==null){remove();return;}
        if(view==null){View root=activity.getWindow().getDecorView();if(!(root instanceof FrameLayout))return;decor=(FrameLayout)root;view=new Bubble(activity);decor.addOnLayoutChangeListener(resized);decor.addView(view,new FrameLayout.LayoutParams(view.size,view.size));view.setElevation(view.dp(12));view.post(()->{if(view!=null)view.place();});}
        MainActivity reader=PlaybackService.reader();if(reader==null){remove();return;}
        view.state(reader.playing,reader.book);view.bringToFront();
    }
    void remove(){if(decor!=null)decor.removeOnLayoutChangeListener(resized);if(view!=null){view.removeCallbacks(view.tick);if(view.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)view.getParent()).removeView(view);}view=null;decor=null;}
    void open(){MainActivity reader=PlaybackService.reader();if(activity!=null&&reader!=null)activity.startActivity(new Intent(reader.getIntent()).setClass(activity,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
    @Override public void onActivityResumed(Activity a){if(activity!=a)remove();activity=a;update();}
    @Override public void onActivityPaused(Activity a){if(activity==a){remove();activity=null;}}
    @Override public void onActivityDestroyed(Activity a){if(activity==a){remove();activity=null;}}
    @Override public void onActivityCreated(Activity a,Bundle b){}
    @Override public void onActivityStarted(Activity a){}
    @Override public void onActivityStopped(Activity a){}
    @Override public void onActivitySaveInstanceState(Activity a,Bundle b){}

    final class Bubble extends View {
        final int size;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final Drawable icon;
        final RectF safe=new RectF();boolean playing,dragging;float downX,downY,originX,originY;
        final Runnable tick=new Runnable(){public void run(){if(view==Bubble.this&&playing&&isAttachedToWindow()){invalidate();postDelayed(this,160);}}};
        int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
        Bubble(Context context){super(context);size=dp(64);icon=context.getDrawable(R.mipmap.ic_launcher);setClickable(true);setFocusable(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);setOnClickListener(v->open());setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View v,Outline outline){outline.setOval(dp(4),dp(4),size-dp(4),size-dp(4));}});addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(!dragging)place();});}
        void state(boolean active,String book){boolean changed=playing!=active;playing=active;String status=getContext().getString(active?R.string.ui_74:R.string.ui_67);setContentDescription(book+" · "+status+" · "+getContext().getString(R.string.bubble_open));if(changed||!hasCallbacks(tick)){removeCallbacks(tick);if(playing)post(tick);}invalidate();}
        // API 26-compatible handler lookup; no animation continues after leaving the app.
        boolean hasCallbacks(Runnable task){return getHandler()!=null&&Build.VERSION.SDK_INT>=29&&getHandler().hasCallbacks(task);}
        void bounds(){if(decor==null)return;WindowInsets insets=decor.getRootWindowInsets();int left=0,right=0,top=0,bottom=0;if(insets!=null){left=insets.getSystemWindowInsetLeft();right=insets.getSystemWindowInsetRight();top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();if(Build.VERSION.SDK_INT>=28&&insets.getDisplayCutout()!=null){left=Math.max(left,insets.getDisplayCutout().getSafeInsetLeft());right=Math.max(right,insets.getDisplayCutout().getSafeInsetRight());top=Math.max(top,insets.getDisplayCutout().getSafeInsetTop());}}safe.set(left+dp(6),top+dp(6),Math.max(left+dp(6),decor.getWidth()-right-size-dp(6)),Math.max(top+dp(6),decor.getHeight()-bottom-size-dp(6)));}
        float clamp(float value,float min,float max){return Math.max(min,Math.min(max,value));}
        void place(){if(decor==null||decor.getWidth()==0)return;bounds();setX(safe.left+clamp(relativeX,0,1)*safe.width());setY(safe.top+clamp(relativeY,0,1)*safe.height());}
        void remember(){bounds();relativeX=safe.width()==0?1:clamp((getX()-safe.left)/safe.width(),0,1);relativeY=safe.height()==0?.76f:clamp((getY()-safe.top)/safe.height(),0,1);prefs.edit().putFloat("x",relativeX).putFloat("y",relativeY).apply();}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:downX=e.getRawX();downY=e.getRawY();originX=getX();originY=getY();dragging=false;setPressed(true);return true;
            case MotionEvent.ACTION_MOVE:float dx=e.getRawX()-downX,dy=e.getRawY()-downY;if(!dragging&&Math.hypot(dx,dy)>ViewConfiguration.get(getContext()).getScaledTouchSlop())dragging=true;if(dragging){bounds();setX(clamp(originX+dx,safe.left,safe.right));setY(clamp(originY+dy,safe.top,safe.bottom));getParent().requestDisallowInterceptTouchEvent(true);}return true;
            case MotionEvent.ACTION_UP:setPressed(false);if(dragging)remember();else performClick();dragging=false;return true;
            case MotionEvent.ACTION_CANCEL:setPressed(false);if(dragging)remember();dragging=false;return true;
        }return super.onTouchEvent(e);}
        @Override public boolean performClick(){super.performClick();return true;}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float mid=size/2f;paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);canvas.drawCircle(mid,mid,mid-dp(4),paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setColor(playing?0xffdf3021:0xffb4b6bc);canvas.drawCircle(mid,mid,mid-dp(5),paint);paint.setStyle(Paint.Style.FILL);
            canvas.save();Path clip=new Path();clip.addCircle(mid,mid,mid-dp(9),Path.Direction.CW);canvas.clipPath(clip);icon.setBounds(dp(9),dp(9),size-dp(9),size-dp(9));icon.draw(canvas);canvas.restore();
            float badgeX=size-dp(14),badgeY=size-dp(14);paint.setColor(Color.WHITE);canvas.drawCircle(badgeX,badgeY,dp(11),paint);paint.setColor(playing?0xffdf3021:0xff9b9ea5);canvas.drawCircle(badgeX,badgeY,dp(9),paint);paint.setColor(Color.WHITE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(dp(2));if(playing){double phase=SystemClock.uptimeMillis()/220.0;for(int i=0;i<3;i++){float h=dp(2)+(float)(.5+.5*Math.sin(phase+i*1.8))*dp(4);float x=badgeX+(i-1)*dp(4);canvas.drawLine(x,badgeY-h,x,badgeY+h,paint);}}else{canvas.drawLine(badgeX-dp(2),badgeY-dp(4),badgeX-dp(2),badgeY+dp(4),paint);canvas.drawLine(badgeX+dp(2),badgeY-dp(4),badgeX+dp(2),badgeY+dp(4),paint);}
        }
        @Override protected void onDetachedFromWindow(){removeCallbacks(tick);super.onDetachedFromWindow();}
    }
}
