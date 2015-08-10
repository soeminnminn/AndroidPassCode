package com.s16.widget;

import java.security.InvalidParameterException;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

public class PassCodeView extends EditText {
	
	public interface OnCompleteListener {
		public void onComplete(View view, Editable s);
	}
	
	private static final int DEFAULT_BACKGROUND_COLOR = 0xffffffff;
	private static final int DEFAULT_BORDER_COLOR = 0xff000000;
	private static final int DEFAULT_CHAR_COLOR = 0xff000000;
	
	private static final int DEFAULT_PIN_COUNT = 4;
	private static final int DEFAULT_DIVIDER_WIDTH = 10;
	
	private TextWatcher mTextWatcher = new TextWatcher() {
		
		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
		}
		
		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			
		}
		
		@Override
		public void afterTextChanged(Editable s) {
			if (s != null && s.length() == mPinCount) {
				if (mOnCompleteListener != null) {
					mOnCompleteListener.onComplete(PassCodeView.this, s);
				}
			}
		}
	};
	
	private PassCodeKeyboardView.OnKeyboardActionListener mKeyboardActionListener = new PassCodeKeyboardView.OnKeyboardActionListener() {
		
		@Override
		public void onRelease(int primaryCode) {}
		
		@Override
		public void onPress(int primaryCode) {}
		
		@Override
		public void onKey(int primaryCode, int[] keyCodes) {
			long eventTime = System.currentTimeMillis();
			KeyEvent event = new KeyEvent(eventTime, eventTime,
					KeyEvent.ACTION_DOWN, primaryCode, 0, 0, 0, 0,
					KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE);

			dispatchKeyEvent(event);
		}
		
		@Override
		public void onF1Key(int code) {
			if (code == PassCodeKeyboardView.KEYCODE_CLEAR)
				setText("");
		}
		
		@Override
		public boolean onLongPress(int primaryCode) {
			return false;
		}
	};
	
	private int mPinCount = DEFAULT_PIN_COUNT;
	private int mDividerWidth;
	private Paint mBackgroundPaint;
	private Paint mBorderPaint;
	private Paint mCharPaint;
	private OnCompleteListener mOnCompleteListener;
	private PassCodeKeyboardView mKeyboardView;
	
	public PassCodeView(Context context) {
		super(context);
        init();
    }

    public PassCodeView(Context context, AttributeSet attrs) {
    	super(context, attrs);
        init();
    }

    public PassCodeView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }
    
    private void init() {
    	mBackgroundPaint = new Paint();
        mBackgroundPaint.setAntiAlias(true);
        mBackgroundPaint.setStyle(Paint.Style.FILL);
        mBackgroundPaint.setColor(DEFAULT_BACKGROUND_COLOR);
        
        mBorderPaint = new Paint();
        mBorderPaint.setAntiAlias(true);
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setColor(DEFAULT_BORDER_COLOR);
        
        mCharPaint = new Paint();
        mCharPaint.setAntiAlias(true);
        mCharPaint.setStyle(Paint.Style.FILL);
        mCharPaint.setColor(DEFAULT_CHAR_COLOR);
        
        setBackground(null);
        
        mDividerWidth = getDimensionDip(DEFAULT_DIVIDER_WIDTH);
        updateFilter();
        addTextChangedListener(mTextWatcher);
    }
    
    protected int getDimensionDip(int value) {
    	DisplayMetrics dm = getResources().getDisplayMetrics();
		int result = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, dm);
		if (result == 0) {
			result = (int)(value * dm.density);
		}
		return result;
	}
    
    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
    	super.onFocusChanged(focused, direction, previouslyFocusedRect);
    	if (!isInEditMode() && focused) {
    		showSoftKeyboard(false);	
    	}
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
    	if (isInEditMode()) {
    		return super.onTouchEvent(event);
    	}
    	int action = event.getActionMasked();
    	if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP) {
    		showSoftKeyboard(true);
    	} else if (action == MotionEvent.ACTION_CANCEL) {
    		hideSoftKeyboard();
    	}
    	return true;
    	//return super.onTouchEvent(event);
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    	if (isInEditMode()) {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
			return;
		}
    	
    	super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
    
	@Override
    protected void onDraw(Canvas canvas) {
		if (isInEditMode()) {
			super.onDraw(canvas);
			return;
		}
		//super.onDraw(canvas);
    	drawPasscode(canvas);
    }
    
    protected void drawPasscode(Canvas canvas) {
    	RectF bounds = new RectF(canvas.getClipBounds());
    	if (mPinCount < 1) return;
    	if (bounds.width() == 0 || bounds.height() == 0) return;
    	
    	float height = bounds.height();
    	if (height < 10) return;
    	float width = height + mDividerWidth;
    	float totalWidth = (width * mPinCount) - mDividerWidth;
    	float x = bounds.left + ((bounds.width() - totalWidth) * 0.5f);
    	float size = Math.min(width, height);
    	
    	int charCount = getText() != null ? getText().length() : 0; 
    	RectF rect = null;
    	for(int i=0; i<mPinCount; i++) {
    		rect = new RectF(x, bounds.top, x + size, bounds.bottom);
    		canvas.drawRect(rect, mBackgroundPaint);
    		canvas.drawRect(rect, mBorderPaint);
    		
    		if (charCount > i) {
    			float radius = size / 8;
    			canvas.drawCircle(rect.centerX(), rect.centerY(), radius, mCharPaint);
    		}
    		
    		x += width;
    	}
    }
    
    private void showSoftKeyboard(boolean doFocus) {
    	if (mKeyboardView != null) return;
	    InputMethodManager inputMethodManager = (InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
	    if (doFocus) this.requestFocus();
	    inputMethodManager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
	}
    
    private void hideSoftKeyboard() {
    	if (mKeyboardView != null) return;
    	InputMethodManager inputMethodManager = (InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(this.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
    }
    
    private void updateFilter() {
    	setFilters(new InputFilter[] { new InputFilter.LengthFilter(mPinCount) });
    }
    
    public boolean isPassCodeComplete() {
    	CharSequence text = getText();
    	return (text != null && text.length() == mPinCount);
    }
    
    public void setPinCount(int count) {
    	if (count < 1) {
    		throw new InvalidParameterException();
    	}
    	if (mPinCount != count) {
    		mPinCount = count;
    		updateFilter();
    		invalidate();
    	}
    }
    
    public void setOnCompleteListener(OnCompleteListener listener) {
    	mOnCompleteListener = listener;
    }
    
    public void setKeyboardView(PassCodeKeyboardView keyboardView) {
    	mKeyboardView = keyboardView;
    	if (mKeyboardView != null) {
    		setFocusable(false);
    		mKeyboardView.addOnKeyboardActionListener(mKeyboardActionListener);
    	}
    }
}
