package com.example.androidpasscode;

import com.example.androidpasscode.fragments.PasscodeComfirmFragment;
import com.example.androidpasscode.fragments.PasscodeFragment;
import com.s16.widget.FragmentSwitcher;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.support.v13.app.FragmentPagerAdapter;
import android.view.Menu;

public class MainActivity extends Activity {

	public class PagePagerAdapter extends FragmentPagerAdapter {

		private static final int S_COUNT = 2;
		private Fragment[] mFragments;
		
		public PagePagerAdapter(FragmentManager fm) {
			super(fm);
			
			mFragments = new Fragment[S_COUNT];
			mFragments[0] = new PasscodeFragment();
			mFragments[1] = new PasscodeComfirmFragment();
		}

		@Override
		public Fragment getItem(int position) {
			return mFragments[position];
		}

		@Override
		public int getCount() {
			return S_COUNT;
		}
		
	}
	
	private FragmentSwitcher mSwitcher;
	private PagePagerAdapter mPagerAdapter;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		mPagerAdapter = new PagePagerAdapter(getFragmentManager());
		mSwitcher = (FragmentSwitcher)findViewById(R.id.fragmentSwitcher);
		mSwitcher.setAdapter(mPagerAdapter);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		return false;
	}
	
	public void setPageIndex(int index) {
		if (mSwitcher != null) {
			mSwitcher.setCurrentItem(index);
		}
	}
}
