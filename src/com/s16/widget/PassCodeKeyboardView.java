package com.s16.widget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

import com.example.androidpasscode.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Region.Op;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

@SuppressWarnings("deprecation")
public class PassCodeKeyboardView extends View  {
	
	protected static final String TAG = PassCodeKeyboardView.class.getSimpleName(); 
	
	private static final int MSG_REPEAT = 3;
	private static final int MSG_LONGPRESS = 4;
	
	private static final int EDGE_LEFT = 0x01;
	private static final int EDGE_RIGHT = 0x02;
	private static final int EDGE_TOP = 0x04;
	private static final int EDGE_BOTTOM = 0x08;
	private static final int KEYCODE_DELETE = 67;
	public static final int KEYCODE_CLEAR = -3;
	
	private static final int[] KEY_DELETE = { KEYCODE_DELETE };
	
	private static final int REPEAT_INTERVAL = 50; // ~20 keys per second
	private static final int REPEAT_START_DELAY = 400;
	private static final int LONGPRESS_TIMEOUT = 800;
	
	/**
     * Listener for virtual keyboard events.
     */
    public interface OnKeyboardActionListener {
        
        /**
         * Called when the user presses a key. This is sent before the {@link #onKey} is called.
         * For keys that repeat, this is only called once.
         * @param primaryCode the unicode of the key being pressed. If the touch is not on a valid
         * key, the value will be zero.
         */
        void onPress(int primaryCode);
        
        /**
         * Called when the user releases a key. This is sent after the {@link #onKey} is called.
         * For keys that repeat, this is only called once.
         * @param primaryCode the code of the key that was released
         */
        void onRelease(int primaryCode);

        /**
         * Send a key press to the listener.
         * @param primaryCode this is the key that was pressed
         * @param keyCodes the codes for all the possible alternative keys
         * with the primary code being the first. If the primary key code is
         * a single character such as an alphabet or number or symbol, the alternatives
         * will include other characters that may be on the same key or adjacent keys.
         * These codes are useful to correct for accidental presses of a key adjacent to
         * the intended key.
         */
        void onKey(int primaryCode, int[] keyCodes);
        
        void onF1Key(int primaryCode);
        
        boolean onLongPress(int primaryCode);
    }
    
    private static class Key {
    	/** 
         * All the key codes (unicode or custom code) that this key could generate, zero'th 
         * being the most important.
         */
        public int[] codes;
        
        /** Label to display */
        public CharSequence label;
        
        /** Icon to display instead of a label. Icon takes precedence over a label */
        public Drawable icon;
        /** Width of the key, not including the gap */
        public int width;
        /** Height of the key, not including the gap */
        public int height;
        /** The horizontal gap before this key */
        public int gap;
        /** X coordinate of the key in the keyboard layout */
        public int x;
        /** Y coordinate of the key in the keyboard layout */
        public int y;
        /** The current pressed state of this key */
        public boolean pressed;
        /** Whether this key repeats itself when held down */
		public boolean repeatable;
		
        /** 
		 * Flags that specify the anchoring to edges of the keyboard for detecting touch events
		 * that are just out of the boundary of the key. This is a bit mask of 
		 * {@link Keyboard#EDGE_LEFT}, {@link Keyboard#EDGE_RIGHT}, {@link Keyboard#EDGE_TOP} and
		 * {@link Keyboard#EDGE_BOTTOM}.
		 */
		public int edgeFlags;
		
		private final static int[] KEY_STATE_NORMAL = {
		};
		
		private final static int[] KEY_STATE_PRESSED = {
			android.R.attr.state_pressed
		};
		
		/**
		 * Informs the key that it has been pressed, in case it needs to change its appearance or
		 * state.
		 * @see #onReleased(boolean)
		 */
		public void onPressed() {
			pressed = true;
		}
		
		/**
		 * Changes the pressed state of the key. If it is a sticky key, it will also change the
		 * toggled state of the key if the finger was release inside.
		 * @param inside whether the finger was released inside the key
		 * @see #onPressed()
		 */
		public void onReleased(boolean inside) {
			pressed = false;
		}
		
		/**
		 * Detects if a point falls inside this key.
		 * @param x the x-coordinate of the point 
		 * @param y the y-coordinate of the point
		 * @return whether or not the point falls inside the key. If the key is attached to an edge,
		 * it will assume that all points between the key and the edge are considered to be inside
		 * the key.
		 */
		public boolean isInside(int x, int y) {
			boolean leftEdge = (edgeFlags & EDGE_LEFT) > 0;
			boolean rightEdge = (edgeFlags & EDGE_RIGHT) > 0;
			boolean topEdge = (edgeFlags & EDGE_TOP) > 0;
			boolean bottomEdge = (edgeFlags & EDGE_BOTTOM) > 0;
			if ((x >= this.x || (leftEdge && x <= this.x + this.width)) 
					&& (x < this.x + this.width || (rightEdge && x >= this.x)) 
					&& (y >= this.y || (topEdge && y <= this.y + this.height))
					&& (y < this.y + this.height || (bottomEdge && y >= this.y))) {
				return true;
			} else {
				return false;
			}
		}
		
		/**
		 * Returns the square of the distance between the center of the key and the given point.
		 * @param x the x-coordinate of the point
		 * @param y the y-coordinate of the point
		 * @return the square of the distance of the point from the center of the key
		 */
		public int squaredDistanceFrom(int x, int y) {
			int xDist = this.x + width / 2 - x;
			int yDist = this.y + height / 2 - y;
			return xDist * xDist + yDist * yDist;
		}
		
		/**
		 * Returns the drawable state for the key, based on the current state and type of the key.
		 * @return the drawable state of the key.
		 * @see android.graphics.drawable.StateListDrawable#setState(int[])
		 */
		public int[] getCurrentDrawableState() {
			int[] states = KEY_STATE_NORMAL;
			if (pressed) {
				states = KEY_STATE_PRESSED;
			}
			return states;
		}
    }
    
    private class Keyboard {
    	
    	private final String[] KEYS_LABELS = new String[] { "1", "2", "3", "4", "5", "6", "7", "8", "9", "DEL", "0", "Clear" }; 
    	private final int[] KEYS_CODES = new int[] { 8, 9, 10, 11, 12, 13, 14, 15, 16, KEYCODE_DELETE, 7, KEYCODE_CLEAR };
    	
    	/** Number of key widths from current touch point to search for nearest keys. */
    	private float SEARCH_DISTANCE = 1.4f;
    	
    	/** Horizontal gap default for all rows */
    	private int mDefaultHorizontalGap;
    	
    	/** Default key width */
    	private int mDefaultWidth;
    	/** Default key height */
    	private int mDefaultHeight;
    	/** Default gap between rows */
    	private int mDefaultVerticalGap;
    	/** Width of the screen available to fit the keyboard */
    	private int mDisplayWidth;
    	
    	/** Current key width, while loading the keyboard */
    	private int mKeyWidth;
    	
    	/** Current key height, while loading the keyboard */
    	private int mKeyHeight;
    	
    	/** Total height of the keyboard, including the padding and keys */
    	private int mTotalHeight;
    	
    	/** 
    	 * Total width of the keyboard, including left side gaps and keys, but not any gaps on the
    	 * right side.
    	 */
    	private int mTotalWidth;
    	
    	/** List of keys in this keyboard */
    	private List<Key> mKeys;
    	
    	private static final int GRID_WIDTH = 3;
    	private static final int GRID_HEIGHT = 4;
    	private static final int GRID_SIZE = GRID_WIDTH * GRID_HEIGHT;
    	private int mCellWidth;
    	private int mCellHeight;
    	private int[][] mGridNeighbors;
    	private int mProximityThreshold;
    	
    	public Keyboard(Context context) {
    		DisplayMetrics dm = context.getResources().getDisplayMetrics();
    		mDisplayWidth = dm.widthPixels;
    		mDefaultHorizontalGap = 0;
    		mDefaultWidth = mDisplayWidth / GRID_WIDTH;
    		mDefaultVerticalGap = 0;
    		mDefaultHeight = mDefaultWidth;
    		
    		mProximityThreshold = (int) (mDefaultWidth * SEARCH_DISTANCE);
    		mProximityThreshold = mProximityThreshold * mProximityThreshold; // Square it for comparison
    		
    		mKeys = new ArrayList<Key>();
    		mKeyHeight = mDefaultHeight;
    		mKeyWidth = mDefaultWidth;
    		
    		createKeys();
    	}
    	
    	private void createKeys() {
    		int x = 0;
    		int y = 0;
    		int column = 0;
    		mTotalWidth = 0;
    		
    		for(int i=0;i<KEYS_CODES.length;i++) {
    			if (column >= GRID_WIDTH) {
    				x = 0;
    				y += mDefaultVerticalGap + mKeyHeight;
    				column = 0;
    			}
    			final Key key = new Key();
    			if (i == 0) {
    				key.edgeFlags = EDGE_LEFT | EDGE_TOP;
    			} else if (i == (GRID_WIDTH - 1)) {
    				key.edgeFlags = EDGE_RIGHT | EDGE_TOP;
    			} else if (i == ((GRID_HEIGHT * GRID_WIDTH) - GRID_WIDTH)) {
    				key.edgeFlags = EDGE_LEFT | EDGE_BOTTOM;
    			} else if (i == (KEYS_CODES.length - 1)) {
    				key.edgeFlags = EDGE_RIGHT | EDGE_BOTTOM;
    			}
    			
    			key.x = x;
    			key.y = y;
    			key.width = mKeyWidth;
    			key.height = mKeyHeight;
    			key.gap = mDefaultHorizontalGap;
    			key.label = KEYS_LABELS[i];
    			key.codes = new int[] { KEYS_CODES[i] };
    			key.repeatable = (KEYS_CODES[i] == KEYCODE_DELETE);
    			column++;
    			x += key.width + key.gap;
    			mKeys.add(key);
    			if (x > mTotalWidth) {
    				mTotalWidth = x;
    			}
    		}
    		mTotalHeight = y + mDefaultHeight; 
    	}
    	
    	final void resize(int newWidth, int newHeight) {
    		if (newWidth <= 0 || newHeight <= 0) return;
    		if (mTotalWidth <= newWidth && mTotalHeight <= newHeight) return;  // it already fits
    		
    		mKeyHeight = (newHeight / GRID_HEIGHT) - mDefaultVerticalGap;
    		mKeyWidth = (newWidth / GRID_WIDTH) - mDefaultHorizontalGap;
    		mProximityThreshold = (int) (mKeyWidth * SEARCH_DISTANCE);
    		mProximityThreshold = mProximityThreshold * mProximityThreshold; // Square it for comparison
    		
    		int x = 0;
    		int y = 0;
    		int column = 0;
    		for(int i=0;i<KEYS_CODES.length;i++) {
    			if (column >= GRID_WIDTH) {
    				x = 0;
    				y += mDefaultVerticalGap + mKeyHeight;
    				column = 0;
    			}
    			
    			Key key = mKeys.get(i);
    			key.x = x;
    			key.y = y;
    			key.width = mKeyWidth;
    			key.height = mKeyHeight;
    			
    			column++;
    			x += key.width + key.gap;
    			if (x > mTotalWidth) {
    				mTotalWidth = x;
    			}
    		}
    		
    		mTotalHeight = newHeight;
    		mTotalWidth = newWidth;
    		mGridNeighbors = null;
    	}
    	
    	private void computeNearestNeighbors() {
    		// Round-up so we don't have any pixels outside the grid
    		mCellWidth = (getMinWidth() + GRID_WIDTH - 1) / GRID_WIDTH;
    		mCellHeight = (getHeight() + GRID_HEIGHT - 1) / GRID_HEIGHT;
    		mGridNeighbors = new int[GRID_SIZE][];
    		int[] indices = new int[mKeys.size()];
    		final int gridWidth = GRID_WIDTH * mCellWidth;
    		final int gridHeight = GRID_HEIGHT * mCellHeight;
    		for (int x = 0; x < gridWidth; x += mCellWidth) {
    			for (int y = 0; y < gridHeight; y += mCellHeight) {
    				int count = 0;
    				for (int i = 0; i < mKeys.size(); i++) {
    					final Key key = mKeys.get(i);
    					if (key.squaredDistanceFrom(x, y) < mProximityThreshold ||
    							key.squaredDistanceFrom(x + mCellWidth - 1, y) < mProximityThreshold ||
    							key.squaredDistanceFrom(x + mCellWidth - 1, y + mCellHeight - 1) 
    								< mProximityThreshold ||
    							key.squaredDistanceFrom(x, y + mCellHeight - 1) < mProximityThreshold) {
    						indices[count++] = i;
    					}
    				}
    				int [] cell = new int[count];
    				System.arraycopy(indices, 0, cell, 0, count);
    				mGridNeighbors[(y / mCellHeight) * GRID_WIDTH + (x / mCellWidth)] = cell;
    			}
    		}
    	}
    	
    	public int[] getNearestKeys(int x, int y) {
    		if (mGridNeighbors == null) computeNearestNeighbors();
    		if (x >= 0 && x < getMinWidth() && y >= 0 && y < getHeight()) {
    			int index = (y / mCellHeight) * GRID_WIDTH + (x / mCellWidth);
    			if (index < GRID_SIZE) {
    				return mGridNeighbors[index];
    			}
    		}
    		return new int[0];
    	}
    	
    	public List<Key> getKeys() {
    		return mKeys;
    	}
    	
    	void setHorizontalGap(int gap) {
    		mDefaultHorizontalGap = gap;
    	}
    	
    	protected void setVerticalGap(int gap) {
    		mDefaultVerticalGap = gap;
    	}
    	
    	public int getMinWidth() {
    		return mTotalWidth;
    	}
    	
    	public int getHeight() {
    		return mTotalHeight;
    	}
    	
    	private void setF1Key(CharSequence label, int[] codes) {
    		if (mKeys != null) {
    			mKeys.get(KEYS_CODES.length - 1).label = label;
    			mKeys.get(KEYS_CODES.length - 1).codes = codes;
    		}
    	}
    }
    
    private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case MSG_REPEAT:
					if (repeatKey()) {
						Message repeat = Message.obtain(this, MSG_REPEAT);
						sendMessageDelayed(repeat, REPEAT_INTERVAL);                        
					}
					break;
				case MSG_LONGPRESS:
					onLongPress((MotionEvent) msg.obj);
					break;
			}
		}
	};
    
    private static final int NOT_A_KEY = -1;
    
    private int mLabelTextSize;
    private int mKeyTextSize;
    private int mKeyTextColor;
    private float mShadowRadius;
    private int mShadowColor;
    
    private boolean mProximityCorrectOn;
    
    private Paint mPaint;
    private Rect mPadding;
    
    private int mHorizontalGap;
    private int mVerticalGap;
    
    private int mVerticalCorrection;
    private int mProximityThreshold;
    
    private static int MAX_NEARBY_KEYS = 12;
	private int[] mDistances = new int[MAX_NEARBY_KEYS];
	
	private int mCurrentKeyIndex = NOT_A_KEY;
	private int mCurrentKey = NOT_A_KEY;
	private int mRepeatKeyIndex = NOT_A_KEY;
	private int mLastKey;
	private long mDownTime;
	private long mLastKeyTime;
	private long mCurrentKeyTime;
	private long mLastMoveTime;
	private int mLastCodeX;
	private int mLastCodeY;
	private int mLastX;
	private int mLastY;
	private boolean mAbortKey;
	
	// For multi-tap
	private int mLastSentIndex;
	private int mTapCount;
	private long mLastTapTime;
	private boolean mInMultiTap;
	private static final int MULTITAP_INTERVAL = 800; // milliseconds

    private Rect mClipRegion = new Rect(0, 0, 0, 0);
    /** Whether the keyboard bitmap needs to be redrawn before it's blitted. **/
    private boolean mDrawPending;
    /** The dirty region in the keyboard bitmap */
    private Rect mDirtyRect = new Rect();
    /** The keyboard bitmap for faster updates */
    private Bitmap mBuffer;
    /** The canvas for the above mutable keyboard bitmap */
    private Canvas mCanvas;
    
    private Key mInvalidatedKey;
    private Keyboard mKeyboard;
    private Drawable mKeyBackground;
    
    private CharSequence mF1KeyLabel;
    private int[] mF1KeyCodes;
    
    private Key[] mKeys;
    
    private List<OnKeyboardActionListener> mKeyboardActionListener;
    
    public PassCodeKeyboardView(Context context, AttributeSet attrs) {
    	this(context, attrs, R.attr.keyboardViewStyle);
    }

	public PassCodeKeyboardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PassCodeKeyboardView, defStyle, R.style.PassCodeKeyboardView);
        int keyTextSize = 0;
        
        mKeyBackground = a.getDrawable(R.styleable.PassCodeKeyboardView_keyBackground);
        mVerticalCorrection = a.getDimensionPixelOffset(R.styleable.PassCodeKeyboardView_verticalCorrection, 0);
        mHorizontalGap = a.getDimensionPixelOffset(R.styleable.PassCodeKeyboardView_horizontalGap, 0);
        mVerticalGap = a.getDimensionPixelOffset(R.styleable.PassCodeKeyboardView_verticalGap, 0);
        mKeyTextSize = a.getDimensionPixelSize(R.styleable.PassCodeKeyboardView_keyTextSize, 18);
        mKeyTextColor = a.getColor(R.styleable.PassCodeKeyboardView_keyTextColor, 0xFF000000);
        mLabelTextSize = a.getDimensionPixelSize(R.styleable.PassCodeKeyboardView_labelTextSize, 14);
        mShadowColor = a.getColor(R.styleable.PassCodeKeyboardView_shadowColor, 0);
        mShadowRadius = a.getFloat(R.styleable.PassCodeKeyboardView_shadowRadius, 0f);
        mF1KeyLabel = a.getText(R.styleable.PassCodeKeyboardView_f1KeyLabel);
        
        TypedValue f1CodesValue = new TypedValue();
        a.getValue(R.styleable.PassCodeKeyboardView_f1KeyCodes, f1CodesValue);
        if (f1CodesValue.type == TypedValue.TYPE_INT_DEC 
                || f1CodesValue.type == TypedValue.TYPE_INT_HEX) {
        	mF1KeyCodes = new int[] { f1CodesValue.data };
        } else if (f1CodesValue.type == TypedValue.TYPE_STRING) {
        	mF1KeyCodes = parseCSV(f1CodesValue.string.toString());
        }
        
        a.recycle();
        
        if (mF1KeyCodes == null && !TextUtils.isEmpty(mF1KeyLabel)) {
        	mF1KeyCodes = new int[] { mF1KeyLabel.charAt(0) };
        }
        
        mPaint = new Paint();
		mPaint.setAntiAlias(true);
		mPaint.setTextSize(keyTextSize);
		mPaint.setTextAlign(Align.CENTER);
		mPadding = new Rect(0, 0, 0, 0);
		if (mKeyBackground == null) {
			mKeyBackground = context.getResources().getDrawable(R.drawable.btn_keyboard_key_ics);
		}
		mKeyBackground.getPadding(mPadding);
		
		mKeyboardActionListener = new ArrayList<OnKeyboardActionListener>();
		createKeyboard();
    }
    
    private int[] parseCSV(String value) {
        int count = 0;
        int lastIndex = 0;
        if (value.length() > 0) {
            count++;
            while ((lastIndex = value.indexOf(",", lastIndex + 1)) > 0) {
                count++;
            }
        }
        int[] values = new int[count];
        count = 0;
        StringTokenizer st = new StringTokenizer(value, ",");
        while (st.hasMoreTokens()) {
            try {
                values[count++] = Integer.parseInt(st.nextToken());
            } catch (NumberFormatException nfe) {
                Log.e(TAG, "Error parsing keycodes " + value);
            }
        }
        return values;
    }
    
    private void createKeyboard() {
    	mHandler.removeMessages(MSG_REPEAT);
		mHandler.removeMessages(MSG_LONGPRESS);
		
    	mKeyboard = new Keyboard(getContext());
    	mKeyboard.setF1Key(mF1KeyLabel, mF1KeyCodes);
    	mKeyboard.setHorizontalGap(mHorizontalGap);
    	mKeyboard.setVerticalGap(mVerticalGap);
    	List<Key> keys = mKeyboard.getKeys();
		mKeys = keys.toArray(new Key[keys.size()]);
		requestLayout();
		// Release buffer, just in case the new keyboard has a different size. 
		// It will be reallocated on the next draw.
		mBuffer = null;
		invalidateAllKeys();
		computeProximityThreshold();
    }
    
    public void addOnKeyboardActionListener(OnKeyboardActionListener listener) {
        mKeyboardActionListener.add(listener);
    }
    
    public boolean removeOnKeyboardActionListener(OnKeyboardActionListener listener) {
    	return mKeyboardActionListener.remove(listener);
    }

    /**
	 * When enabled, calls to {@link OnKeyboardActionListener#onKey} will include key
	 * codes for adjacent keys.  When disabled, only the primary key code will be
	 * reported.
	 * @param enabled whether or not the proximity correction is enabled
	 */
	public void setProximityCorrectionEnabled(boolean enabled) {
		mProximityCorrectOn = enabled;
	}
	/**
	 * Returns true if proximity correction is enabled.
	 */
	public boolean isProximityCorrectionEnabled() {
		return mProximityCorrectOn;
	}
    
	public void setF1Key(CharSequence label, int[] codes) {
		mF1KeyLabel = label;
		if (!TextUtils.isEmpty(mF1KeyLabel) && (codes == null || codes.length == 0)) {
			mF1KeyCodes = new int[] { mF1KeyLabel.charAt(0) };
		} else {
			mF1KeyCodes = codes;
		}
		if (mKeyboard != null) {
			mKeyboard.setF1Key(mF1KeyLabel, mF1KeyCodes);
		}
		if (mKeys != null) {
			mKeys[mKeys.length - 1].label = mF1KeyLabel;
			mKeys[mKeys.length - 1].codes = mF1KeyCodes;
			invalidateAllKeys();
		} 
	}
	
    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    	// Round up a little
		if (mKeyboard == null) {
			setMeasuredDimension(getPaddingLeft() + getPaddingRight(), getPaddingTop() + getPaddingBottom());
		} else {
			int width = mKeyboard.getMinWidth() + getPaddingLeft() + getPaddingRight();
			if (MeasureSpec.getSize(widthMeasureSpec) < width + 10) {
				width = MeasureSpec.getSize(widthMeasureSpec);
			}
			setMeasuredDimension(width, MeasureSpec.getSize(heightMeasureSpec));
		}
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    	super.onSizeChanged(w, h, oldw, oldh);
    	if (mKeyboard != null) {
        	mKeyboard.resize(w - (getPaddingLeft() + getPaddingRight()), h - (getPaddingTop() + getPaddingBottom()));
        }
    	// Release the buffer, if any and it will be reallocated on the next draw
        mBuffer = null;
    }
    
    @Override
    public void onDraw(Canvas canvas) {
    	super.onDraw(canvas);
		if (mDrawPending || mBuffer == null) {
			onBufferDraw();
		}
		canvas.drawBitmap(mBuffer, 0, 0, null);
    }
    
    private void onBufferDraw() {
    	if (mBuffer == null) {
			mBuffer = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
			mCanvas = new Canvas(mBuffer);
			invalidateAllKeys();
		}
		final Canvas canvas = mCanvas;
		canvas.clipRect(mDirtyRect, Op.REPLACE);
		
		final Paint paint = mPaint;
		final Drawable keyBackground = mKeyBackground;
		final Rect clipRegion = mClipRegion;
		final Rect padding = mPadding;
		final int kbdPaddingLeft = getPaddingLeft();
		final int kbdPaddingTop = getPaddingTop();
		final Key[] keys = mKeys;
		final Key invalidKey = mInvalidatedKey;
		paint.setAlpha(255);
		paint.setColor(mKeyTextColor);
		boolean drawSingleKey = false;
		if (invalidKey != null && canvas.getClipBounds(clipRegion)) {
		  // Is clipRegion completely contained within the invalidated key?
		  if (invalidKey.x + kbdPaddingLeft - 1 <= clipRegion.left &&
				  invalidKey.y + kbdPaddingTop - 1 <= clipRegion.top &&
				  invalidKey.x + invalidKey.width + kbdPaddingLeft + 1 >= clipRegion.right &&
				  invalidKey.y + invalidKey.height + kbdPaddingTop + 1 >= clipRegion.bottom) {
			  drawSingleKey = true;
		  }
		}
		canvas.drawColor(0x00000000, PorterDuff.Mode.CLEAR);
		final int keyCount = keys.length;
		for (int i = 0; i < keyCount; i++) {
			final Key key = keys[i];
			if (drawSingleKey && invalidKey != key) {
				continue;
			}
			int[] drawableState = key.getCurrentDrawableState();
			keyBackground.setState(drawableState);
			
			String label = key.label == null? null : key.label.toString();
			
			final Rect bounds = keyBackground.getBounds();
			if (key.width != bounds.right || 
					key.height != bounds.bottom) {
				keyBackground.setBounds(0, 0, key.width, key.height);
			}
			canvas.translate(key.x + kbdPaddingLeft, key.y + kbdPaddingTop);
			keyBackground.draw(canvas);
			
			if (label != null) {
				// For characters, use large font. For labels like "Done", use small font.
				if (label.length() > 1 && key.codes.length < 2) {
					paint.setTextSize(mLabelTextSize);
					paint.setTypeface(Typeface.DEFAULT_BOLD);
				} else {
					paint.setTextSize(mKeyTextSize);
					paint.setTypeface(Typeface.DEFAULT);
				}
				// Draw a drop shadow for the text
				paint.setShadowLayer(mShadowRadius, 0, 0, mShadowColor);
				// Draw the text
				canvas.drawText(label,
					(key.width - padding.left - padding.right) / 2
							+ padding.left,
					(key.height - padding.top - padding.bottom) / 2
							+ (paint.getTextSize() - paint.descent()) / 2 + padding.top,
					paint);
				// Turn off drop shadow
				paint.setShadowLayer(0, 0, 0, 0);
			} else if (key.icon != null) {
				final int drawableX = (key.width - padding.left - padding.right 
								- key.icon.getIntrinsicWidth()) / 2 + padding.left;
				final int drawableY = (key.height - padding.top - padding.bottom 
						- key.icon.getIntrinsicHeight()) / 2 + padding.top;
				canvas.translate(drawableX, drawableY);
				key.icon.setBounds(0, 0, 
						key.icon.getIntrinsicWidth(), key.icon.getIntrinsicHeight());
				key.icon.draw(canvas);
				canvas.translate(-drawableX, -drawableY);
			}
			canvas.translate(-key.x - kbdPaddingLeft, -key.y - kbdPaddingTop);
		}
		mInvalidatedKey = null;
		
		mDrawPending = false;
		mDirtyRect.setEmpty();
    }

	@Override
	public void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		closing();
	}
	
	public void closing() {
		mHandler.removeMessages(MSG_REPEAT);
		mHandler.removeMessages(MSG_LONGPRESS);
		
		mBuffer = null;
		mCanvas = null;
	}
	
	private boolean onLongPress(MotionEvent me) {
		if (mCurrentKey < 0 || mCurrentKey >= mKeys.length) {
			return false;
		}
		Key key = mKeys[mCurrentKey];
		boolean result = onLongPress(key);
		if (result) {
			mAbortKey = true;
			updateKeyState(NOT_A_KEY);
		}
		return result;
	}
	
	protected boolean onLongPress(Key popupKey) {
		boolean result = false;
		if (popupKey != null && popupKey.codes != null && popupKey.codes.length > 0) {
			int primaryCode = popupKey.codes[0];
			if (mKeyboardActionListener != null) {
				for(OnKeyboardActionListener listener : mKeyboardActionListener) {
					if (listener != null) {
						result = result || listener.onLongPress(primaryCode);
					}
				}	
			}	
		}
		return result;
	}
	
	protected void sendActionPress(int primaryCode) {
		if (mKeyboardActionListener != null) {
			for(OnKeyboardActionListener listener : mKeyboardActionListener) {
				if (listener != null) {
					listener.onPress(primaryCode);
				}
			}	
		}
	}
	
	protected void sendActionRelease(int primaryCode) {
		if (mKeyboardActionListener != null) {
			for(OnKeyboardActionListener listener : mKeyboardActionListener) {
				if (listener != null) {
					listener.onRelease(primaryCode);
				}
			}	
		}
	}
	
	protected void sendActionKey(int primaryCode, int[] keyCodes) {
		if (mKeyboardActionListener != null) {
			for(OnKeyboardActionListener listener : mKeyboardActionListener) {
				if (listener != null) {
					listener.onKey(primaryCode, keyCodes);
				}
			}	
		}
	}
	
	protected void sendActionF1Key(int primaryCode) {
		if (mKeyboardActionListener != null) {
			for(OnKeyboardActionListener listener : mKeyboardActionListener) {
				if (listener != null) {
					listener.onF1Key(primaryCode);
				}
			}	
		}
	}
	
	@Override
    public boolean onTouchEvent(MotionEvent me) {
		int touchX = (int) me.getX() - getPaddingLeft();
		int touchY = (int) me.getY() + mVerticalCorrection - getPaddingTop();
		int action = me.getAction();
		long eventTime = me.getEventTime();
		int keyIndex = getKeyIndices(touchX, touchY, null);
		
		switch (action) {
			case MotionEvent.ACTION_DOWN:
				mAbortKey = false;
				mLastCodeX = touchX;
				mLastCodeY = touchY;
				mLastKeyTime = 0;
				mCurrentKeyTime = 0;
				mLastKey = NOT_A_KEY;
				mCurrentKey = keyIndex;
				mDownTime = me.getEventTime();
				mLastMoveTime = mDownTime;
				checkMultiTap(eventTime, keyIndex);
				sendActionPress(keyIndex != NOT_A_KEY ? mKeys[keyIndex].codes[0] : 0);
				if (mCurrentKey >= 0 && mKeys[mCurrentKey].repeatable) {
					mRepeatKeyIndex = mCurrentKey;
					repeatKey();
					Message msg = mHandler.obtainMessage(MSG_REPEAT);
					mHandler.sendMessageDelayed(msg, REPEAT_START_DELAY);
				}
				if (mCurrentKey != NOT_A_KEY) {
					Message msg = mHandler.obtainMessage(MSG_LONGPRESS, me);
					mHandler.sendMessageDelayed(msg, LONGPRESS_TIMEOUT);
				}
				updateKeyState(keyIndex);
				break;
			case MotionEvent.ACTION_MOVE:
				boolean continueLongPress = false;
				if (keyIndex != NOT_A_KEY) {
					if (mCurrentKey == NOT_A_KEY) {
						mCurrentKey = keyIndex;
						mCurrentKeyTime = eventTime - mDownTime;
					} else {
						if (keyIndex == mCurrentKey) {
							mCurrentKeyTime += eventTime - mLastMoveTime;
							continueLongPress = true;
						} else {
							resetMultiTap();
							mLastKey = mCurrentKey;
							mLastCodeX = mLastX;
							mLastCodeY = mLastY;
							mLastKeyTime =
									mCurrentKeyTime + eventTime - mLastMoveTime;
							mCurrentKey = keyIndex;
							mCurrentKeyTime = 0;
						}
					}
					if (keyIndex != mRepeatKeyIndex) {
						mHandler.removeMessages(MSG_REPEAT);
						mRepeatKeyIndex = NOT_A_KEY;
					}
				}
				if (!continueLongPress) {
					// Cancel old longpress
					mHandler.removeMessages(MSG_LONGPRESS);
					// Start new longpress if key has changed
					if (keyIndex != NOT_A_KEY) {
						Message msg = mHandler.obtainMessage(MSG_LONGPRESS, me);
						mHandler.sendMessageDelayed(msg, LONGPRESS_TIMEOUT);
					}
				}
				updateKeyState(keyIndex);
				break;
			case MotionEvent.ACTION_UP:
				mHandler.removeMessages(MSG_REPEAT);
				mHandler.removeMessages(MSG_LONGPRESS);
				if (keyIndex != mCurrentKey) {
					resetMultiTap();
					mLastKey = mCurrentKey;
					mLastKeyTime = mCurrentKeyTime + eventTime - mLastMoveTime;
					mCurrentKey = keyIndex;
					mCurrentKeyTime = 0;
				}
				if (mCurrentKeyTime < mLastKeyTime && mLastKey != NOT_A_KEY) {
					mCurrentKey = mLastKey;
					touchX = mLastCodeX;
					touchY = mLastCodeY;
				}
				updateKeyState(NOT_A_KEY);
				// If we're not on a repeating key (which sends on a DOWN event)
				if (mRepeatKeyIndex == NOT_A_KEY && !mAbortKey) {
					detectAndSendKey(touchX, touchY, eventTime);
				}
				invalidateKey(keyIndex);
				mRepeatKeyIndex = NOT_A_KEY;
				break;
			default:
				break;
		}
		
		mLastX = touchX;
		mLastY = touchY;
		return true;
	}
	
	private int getKeyIndices(int x, int y, int[] allKeys) {
		final Key[] keys = mKeys;
		int primaryIndex = NOT_A_KEY;
		int closestKey = NOT_A_KEY;
		int closestKeyDist = mProximityThreshold + 1;
		java.util.Arrays.fill(mDistances, Integer.MAX_VALUE);
		int [] nearestKeyIndices = mKeyboard.getNearestKeys(x, y);
		final int keyCount = nearestKeyIndices.length;
		
		for (int i = 0; i < keyCount; i++) {
			final Key key = keys[nearestKeyIndices[i]];
			int dist = 0;
			boolean isInside = key.isInside(x,y);
			if (((mProximityCorrectOn 
					&& (dist = key.squaredDistanceFrom(x, y)) < mProximityThreshold) 
					|| isInside)
					&& key.codes[0] > 32) {
				// Find insertion point
				final int nCodes = key.codes.length;
				if (dist < closestKeyDist) {
					closestKeyDist = dist;
					closestKey = nearestKeyIndices[i];
				}
				
				if (allKeys == null) continue;
				
				for (int j = 0; j < mDistances.length; j++) {
					if (mDistances[j] > dist) {
						// Make space for nCodes codes
						System.arraycopy(mDistances, j, mDistances, j + nCodes,
								mDistances.length - j - nCodes);
						System.arraycopy(allKeys, j, allKeys, j + nCodes,
								allKeys.length - j - nCodes);
						for (int c = 0; c < nCodes; c++) {
							allKeys[j + c] = key.codes[c];
							mDistances[j + c] = dist;
						}
						break;
					}
				}
			}
			
			if (isInside) {
				primaryIndex = nearestKeyIndices[i];
			}
		}
		if (primaryIndex == NOT_A_KEY) {
			primaryIndex = closestKey;
		}
		return primaryIndex;
	}
	
	/**
	 * Compute the average distance between adjacent keys (horizontally and vertically)
	 * and square it to get the proximity threshold. We use a square here and in computing
	 * the touch distance from a key's center to avoid taking a square root.
	 */
	private void computeProximityThreshold() {
		final Key[] keys = mKeys;
		if (keys == null) return;
		int length = keys.length;
		int dimensionSum = 0;
		for (int i = 0; i < length; i++) {
			Key key = keys[i];
			dimensionSum += Math.min(key.width, key.height) + key.gap;
		}
		if (dimensionSum < 0 || length == 0) return;
		mProximityThreshold = (int) (dimensionSum * 1.4f / length);
		mProximityThreshold *= mProximityThreshold; // Square it
	}

	public void invalidateAllKeys() {
		mDirtyRect.union(0, 0, getWidth(), getHeight());
		mDrawPending = true;
		invalidate();
	}
	/**
	 * Invalidates a key so that it will be redrawn on the next repaint. Use this method if only
	 * one key is changing it's content. Any changes that affect the position or size of the key
	 * may not be honored.
	 * @param keyIndex the index of the key in the attached {@link Keyboard}.
	 * @see #invalidateAllKeys
	 */
	public void invalidateKey(int keyIndex) {
		if (mKeys == null) return;
		if (keyIndex < 0 || keyIndex >= mKeys.length) {
			return;
		}
		final Key key = mKeys[keyIndex];
		mInvalidatedKey = key;
		mDirtyRect.union(key.x + getPaddingLeft(), key.y + getPaddingTop(), 
				key.x + key.width + getPaddingLeft(), key.y + key.height + getPaddingTop());
		onBufferDraw();
		invalidate(key.x + getPaddingLeft(), key.y + getPaddingTop(), 
				key.x + key.width + getPaddingLeft(), key.y + key.height + getPaddingTop());
	}
	
	private boolean repeatKey() {
		Key key = mKeys[mRepeatKeyIndex];
		detectAndSendKey(key.x, key.y, mLastTapTime);
		return true;
	}
	
	private void updateKeyState(int keyIndex) {
		int oldKeyIndex = mCurrentKeyIndex;
		mCurrentKeyIndex = keyIndex;
		// Release the old key and press the new key
		final Key[] keys = mKeys;
		if (oldKeyIndex != mCurrentKeyIndex) {
			if (oldKeyIndex != NOT_A_KEY && keys.length > oldKeyIndex) {
				keys[oldKeyIndex].onReleased(mCurrentKeyIndex == NOT_A_KEY);
				invalidateKey(oldKeyIndex);
			}
			if (mCurrentKeyIndex != NOT_A_KEY && keys.length > mCurrentKeyIndex) {
				keys[mCurrentKeyIndex].onPressed();
				invalidateKey(mCurrentKeyIndex);
			}
		}
	}
	
	private boolean isF1KeyCode(int primaryCode) {
		if (mF1KeyCodes != null && mF1KeyCodes.length > 0)
			return mF1KeyCodes[0] == primaryCode;
		return false;
	}
	
	private void detectAndSendKey(int x, int y, long eventTime) {
		int index = mCurrentKey;
		if (index != NOT_A_KEY && index < mKeys.length) {
			final Key key = mKeys[index];
			if (mKeyboardActionListener != null) {
				int code = key.codes[0];
				Log.i(TAG, "detectAndSendKey, code="+code);
				//TextEntryState.keyPressedAt(key, x, y);
				int[] codes = new int[MAX_NEARBY_KEYS];
				Arrays.fill(codes, NOT_A_KEY);
				getKeyIndices(x, y, codes);
				if (isF1KeyCode(code)) {
					sendActionF1Key(code);
				}
				// Multi-tap
				if (mInMultiTap) {
					if (mTapCount != -1) {
						sendActionKey(KEYCODE_DELETE, KEY_DELETE);
					} else {
						mTapCount = 0;
					}
					code = key.codes[mTapCount];
				}
				sendActionKey(code, codes);
				sendActionRelease(code);
			}
			mLastSentIndex = index;
			mLastTapTime = eventTime;
		}
	}
	
	private void resetMultiTap() {
		mLastSentIndex = NOT_A_KEY;
		mTapCount = 0;
		mLastTapTime = -1;
		mInMultiTap = false;
	}
	
	private void checkMultiTap(long eventTime, int keyIndex) {
		if (keyIndex == NOT_A_KEY) return;
		Key key = mKeys[keyIndex];
		if (key.codes.length > 1) {
			mInMultiTap = true;
			if (eventTime < mLastTapTime + MULTITAP_INTERVAL
					&& keyIndex == mLastSentIndex) {
				mTapCount = (mTapCount + 1) % key.codes.length;
				return;
			} else {
				mTapCount = -1;
				return;
			}
		}
		if (eventTime > mLastTapTime + MULTITAP_INTERVAL || keyIndex != mLastSentIndex) {
			resetMultiTap();
		}
	}
}
