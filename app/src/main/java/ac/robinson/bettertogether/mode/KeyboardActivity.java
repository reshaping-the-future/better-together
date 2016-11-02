package ac.robinson.bettertogether.mode;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import ac.robinson.bettertogether.R;
import ac.robinson.bettertogether.event.BroadcastMessage;
import ac.robinson.bettertogether.hotspot.BaseHotspotActivity;

public class KeyboardActivity extends BaseHotspotActivity {

	private EditText mKeyboardText;
	private TextView mFooter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initialiseViewAndToolbar(R.layout.mode_keyboard, false);

		mKeyboardText = (EditText) findViewById(R.id.keyboard_text);
		mFooter = (TextView) findViewById(R.id.footer);
		mFooter.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				// show the keyboard whenever the text view is touched
				// TODO: can this be improved?
				((InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE)).toggleSoftInput(0, InputMethodManager
						.HIDE_IMPLICIT_ONLY);
				//mKeyboardText.clearFocus();
				mKeyboardText.requestFocus();
				return true; // so that events don't pass through to the EditText
			}
		});

		// TODO: prompt enabling the remote keyboard on the client side
	}

	@Override
	protected void onPause() {
		super.onPause();
		mKeyboardText.removeTextChangedListener(mTextWatcher);
	}

	@Override
	protected void onResume() {
		super.onResume();
		mKeyboardText.addTextChangedListener(mTextWatcher);
	}

	private TextWatcher mTextWatcher = new TextWatcher() {
		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
			String changedPart = s.toString().substring(start, start + count);
			BroadcastMessage message = new BroadcastMessage(BroadcastMessage.Type.KEYBOARD, changedPart);
			int[] extras = new int[]{start, before};
			message.setExtras(extras);
			sendBroadcastMessage(message);
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			// nothing to do
		}

		@Override
		public void afterTextChanged(Editable s) {
			// nothing to do
		}
	};

	@Override
	public void onBroadcastMessageReceived(BroadcastMessage message) {
	}
}
