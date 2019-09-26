package com.dicio.dicio_android.io.graphical.render;

import android.content.Context;
import android.text.Html;
import android.text.Layout;
import android.text.Spannable;
import android.text.style.ClickableSpan;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.appcompat.widget.AppCompatTextView;

public class HtmlTextView extends AppCompatTextView {
    public HtmlTextView(Context context) {
        super(context);
    }

    public HtmlTextView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean clicked = false;

        if (event.getAction() == MotionEvent.ACTION_UP) {
            int x = (int) event.getX();
            int y = (int) event.getY();

            x -= getTotalPaddingLeft();
            y -= getTotalPaddingTop();

            x += getScrollX();
            y += getScrollY();

            Layout layout = getLayout();
            int offset = layout.getOffsetForHorizontal(layout.getLineForVertical(y), x);

            Spannable spannable = (Spannable) getText();
            ClickableSpan[] links = spannable.getSpans(offset, offset, ClickableSpan.class);

            if (links.length != 0) {
                ClickableSpan link = links[0];
                link.onClick(this);
                clicked = true;
            }
        }

        if (clicked) {
            event.setAction(MotionEvent.ACTION_CANCEL);
            return super.onTouchEvent(event);
        } else {
            return super.onTouchEvent(event);
        }
    }

    public void setHtmlText(String htmlText) {
        super.setText(Html.fromHtml(htmlText), BufferType.SPANNABLE);
    }
}