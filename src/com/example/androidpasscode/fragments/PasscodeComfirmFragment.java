package com.example.androidpasscode.fragments;

import com.example.androidpasscode.R;
import com.s16.widget.PassCodeKeyboardView;
import com.s16.widget.PassCodeView;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

public class PasscodeComfirmFragment extends Fragment {

	private PassCodeKeyboardView.OnKeyboardActionListener mKeyboardActionListener = new PassCodeKeyboardView.OnKeyboardActionListener() {
		
		@Override
		public void onRelease(int primaryCode) {
		}
		
		@Override
		public void onPress(int primaryCode) {
		}
		
		@Override
		public boolean onLongPress(int primaryCode) {
			return false;
		}
		
		@Override
		public void onKey(int primaryCode, int[] keyCodes) {
		}
		
		@Override
		public void onF1Key(int primaryCode) {
			if (primaryCode == F1KEY_CODE) {
				onAccept();
			}
		}
	};
	
	private static int F1KEY_CODE = 0x1000;
	private PassCodeView mPassCodeView;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		ViewGroup rootView = (ViewGroup)inflater.inflate(R.layout.fragment_confirm_passcode, container, false);
		
		mPassCodeView = (PassCodeView)rootView.findViewById(R.id.passcodeView);
		
		ViewGroup frameKeyboard = (ViewGroup)rootView.findViewById(R.id.frameKeyboard);
		PassCodeKeyboardView keyboardView = (PassCodeKeyboardView)inflater.inflate(R.layout.layout_keyboard, frameKeyboard, false);
		
		CharSequence f1Label = getResources().getString(android.R.string.ok);
		keyboardView.setF1Key(f1Label, new int[] { F1KEY_CODE });
		keyboardView.addOnKeyboardActionListener(mKeyboardActionListener);
		
		frameKeyboard.addView(keyboardView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
		mPassCodeView.setKeyboardView(keyboardView);
		
		return rootView;
	}
	
	private void onAccept() {
		if (mPassCodeView != null && mPassCodeView.isPassCodeComplete()) {
			String passcode = getActivity().getIntent().getStringExtra("passcode");
			if (passcode != null && passcode.equals(mPassCodeView.getText().toString())) {
				Toast.makeText(getActivity(), R.string.message_passcode_set, Toast.LENGTH_LONG).show();
			} else {
				Toast.makeText(getActivity(), R.string.message_passcode_not_match, Toast.LENGTH_LONG).show();
			}
			
		} else {
			Toast.makeText(getActivity(), R.string.message_passcode_empty, Toast.LENGTH_LONG).show();
		}
	}
}
