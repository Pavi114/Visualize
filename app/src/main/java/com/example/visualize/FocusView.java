package com.example.visualize;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public class FocusView extends View {
    private Paint transperantBlack;
    private Paint transperant;
    private Path path = new Path();
    public FocusView(Context context) {
        super(context);
        init_paints();
    }

    public FocusView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init_paints();
    }

    public FocusView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init_paints();
    }

    private void init_paints(){
        transperant = new Paint();
        transperant.setColor(Color.TRANSPARENT);
        transperant.setStrokeWidth(10);

        transperantBlack = new Paint();
        transperantBlack.setColor(Color.TRANSPARENT);
        transperantBlack.setStrokeWidth(10);
    }

    @Override
    protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        path.reset();

        path.addRect(getLeft() + (getRight() - getLeft())/15,
                      getTop() + (getBottom() - getTop())/4,
                      getRight() - (getRight() - getLeft())/15,
                      getBottom() - (getBottom() - getTop())/3,
                       Path.Direction.CW
                     );
        path.setFillType(Path.FillType.INVERSE_EVEN_ODD);
        canvas.drawRect(getLeft() + (getRight() - getLeft())/15,
                         getTop() + (getBottom() - getTop())/4,
                         getRight() - (getRight() - getLeft())/15,
                         getBottom() - (getBottom() - getTop())/3,
                          transperant
                       );
        canvas.drawPath(path, transperantBlack);
        canvas.clipPath(path);
        canvas.drawColor(Color.parseColor("#A4000000"));
    }

}
