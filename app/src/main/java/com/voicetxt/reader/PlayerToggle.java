package com.voicetxt.reader;
import android.content.Context;import android.graphics.*;import android.widget.TextView;
/** Geometry stays centered independently of the phone's selected text font. */
final class PlayerToggle extends TextView {
    final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    PlayerToggle(Context context){super(context);}
    @Override protected void onDraw(Canvas canvas){float density=getResources().getDisplayMetrics().density,cx=getWidth()/2f,cy=getHeight()/2f;paint.setColor(getCurrentTextColor());if("Ⅱ".contentEquals(getText())){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3*density);paint.setStrokeCap(Paint.Cap.ROUND);canvas.drawLine(cx-5*density,cy-9*density,cx-5*density,cy+9*density,paint);canvas.drawLine(cx+5*density,cy-9*density,cx+5*density,cy+9*density,paint);}else{paint.setStyle(Paint.Style.FILL);Path triangle=new Path();triangle.moveTo(cx-5*density,cy-10*density);triangle.lineTo(cx+10*density,cy);triangle.lineTo(cx-5*density,cy+10*density);triangle.close();canvas.drawPath(triangle,paint);}}
}
