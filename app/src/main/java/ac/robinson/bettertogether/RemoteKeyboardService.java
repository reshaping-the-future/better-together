package ac.robinson.bettertogether;

import android.inputmethodservice.InputMethodService;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import ac.robinson.bettertogether.event.BroadcastMessage;
import ac.robinson.bettertogether.event.MessageReceivedEvent;

// see: https://android.googlesource.com/platform/development/+/master/samples/SoftKeyboard
public class RemoteKeyboardService extends InputMethodService {

	private static final String TAG = "RemoteKeyboardService";

	private InputMethodManager mInputMethodManager;
	private StringBuilder mComposing = new StringBuilder();

	/**
	 * Main initialization of the input method component. Be sure to call to super class.
	 */
	@Override
	public void onCreate() {
		super.onCreate();
		mInputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
	}

	/**
	 * This is the point where you can do all of your UI initialization. It is called after creation and any configuration
	 * change.
	 */
	@Override
	public void onInitializeInterface() {
	}

	/**
	 * Called by the framework when your view for creating input needs to be generated. This will be called the first time your
	 * input method is displayed, and every time it needs to be re-created such as due to a configuration change.
	 */
	@Override
	public View onCreateInputView() {
		TextView inputView = (TextView) getLayoutInflater().inflate(R.layout.remote_keyboard, null);
		inputView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Log.d(TAG, "Disconnect keyboard here if necessary");
			}
		});
		mComposing.setLength(0);
		mComposing.append(getCurrentInputConnection().getExtractedText(new ExtractedTextRequest(), 0).text);
		return inputView;
	}

	/**
	 * Called by the framework when your view for showing candidates needs to be generated, like {@link #onCreateInputView}.
	 */
	@Override
	public View onCreateCandidatesView() {
		return null; // no candidates - all remote
	}

	/**
	 * This is the main point where we do our initialization of the input method to begin operating on an application. At this
	 * point we have been bound to the client, and are now receiving all of the detailed information about the target of our
	 * edits.
	 */
	@Override
	public void onStartInput(EditorInfo attribute, boolean restarting) {
		super.onStartInput(attribute, restarting);
		Log.d(TAG, "Starting remote keyboard input (restarting: " + restarting + ")");
		if (!EventBus.getDefault().isRegistered(this)) {
			EventBus.getDefault().register(this); // register for EventBus events
		}

		// TODO: forward this state to the controlling keyboard
		//// Reset our state.  We want to do this even if restarting, because
		//// the underlying state of the text editor could have changed in any way.
		//mComposing.setLength(0);
		//updateCandidates();
		//
		//if (!restarting) {
		//	// Clear shift states.
		//	mMetaState = 0;
		//}
		//
		//mPredictionOn = false;
		//mCompletionOn = false;
		//mCompletions = null;
		//
		//// We are now going to initialize our state based on the type of
		//// text being edited.
		//switch (attribute.inputType & InputType.TYPE_MASK_CLASS) {
		//	case InputType.TYPE_CLASS_NUMBER:
		//	case InputType.TYPE_CLASS_DATETIME:
		//		// Numbers and dates default to the symbols keyboard, with
		//		// no extra features.
		//		mCurKeyboard = mSymbolsKeyboard;
		//		break;
		//
		//	case InputType.TYPE_CLASS_PHONE:
		//		// Phones will also default to the symbols keyboard, though
		//		// often you will want to have a dedicated phone keyboard.
		//		mCurKeyboard = mSymbolsKeyboard;
		//		break;
		//
		//	case InputType.TYPE_CLASS_TEXT:
		//		// This is general text editing.  We will default to the
		//		// normal alphabetic keyboard, and assume that we should
		//		// be doing predictive text (showing candidates as the
		//		// user types).
		//		mCurKeyboard = mQwertyKeyboard;
		//		mPredictionOn = true;
		//
		//		// We now look for a few special variations of text that will
		//		// modify our behavior.
		//		int variation = attribute.inputType & InputType.TYPE_MASK_VARIATION;
		//		if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD || variation == InputType
		//				.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
		//			// Do not display predictions / what the user is typing
		//			// when they are entering a password.
		//			mPredictionOn = false;
		//		}
		//
		//		if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || variation == InputType.TYPE_TEXT_VARIATION_URI
		//				|| variation == InputType.TYPE_TEXT_VARIATION_FILTER) {
		//			// Our predictions are not useful for e-mail addresses
		//			// or URIs.
		//			mPredictionOn = false;
		//		}
		//
		//		if ((attribute.inputType & InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
		//			// If this is an auto-complete text view, then our predictions
		//			// will not be shown and instead we will allow the editor
		//			// to supply their own.  We only show the editor's
		//			// candidates when in fullscreen mode, otherwise relying
		//			// own it displaying its own UI.
		//			mPredictionOn = false;
		//			mCompletionOn = isFullscreenMode();
		//		}
		//
		//		// We also want to look at the current state of the editor
		//		// to decide whether our alphabetic keyboard should start out
		//		// shifted.
		//		updateShiftKeyState(attribute);
		//		break;
		//
		//	default:
		//		// For all unknown input types, default to the alphabetic
		//		// keyboard with no special features.
		//		mCurKeyboard = mQwertyKeyboard;
		//		updateShiftKeyState(attribute);
		//}
		//
		//// Update the label on the enter key, depending on what the application
		//// says it will do.
		//mCurKeyboard.setImeOptions(getResources(), attribute.imeOptions);
	}

	/**
	 * This is called when the user is done editing a field.  We can use this to reset our state.
	 */
	@Override
	public void onFinishInput() {
		super.onFinishInput();
		Log.d(TAG, "Finishing remote keyboard input");
		EventBus.getDefault().unregister(this);

		// TODO: switch back to default keyboard when we exit? (need to prompt on start each time if so)
		//List<InputMethodInfo> inputMethodInfos = mInputMethodManager.getInputMethodList();
		//String keyboardName = RemoteKeyboardService.class.getName();
		//for (InputMethodInfo inputMethodInfo : inputMethodInfos) {
		//	Log.d(TAG, inputMethodInfo.getId());
		//	if (inputMethodInfo.getId().startsWith("com.android.inputmethod")) {
		//		// mInputMethodManager.switchInputMethod(inputMethodInfo.getId());
		//		mInputMethodManager.setInputMethod(getWindow().getWindow().getAttributes().token, inputMethodInfo.getId());
		//		break;
		//	}
		//}
		//
		//InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		//imm.showInputMethodPicker();
	}

	@Override
	public void onStartInputView(EditorInfo attribute, boolean restarting) {
		super.onStartInputView(attribute, restarting);
	}

	@Override
	public void onCurrentInputMethodSubtypeChanged(InputMethodSubtype subtype) {
		super.onCurrentInputMethodSubtypeChanged(subtype);
	}

	/**
	 * Deal with the editor reporting movement of its cursor.
	 */
	@Override
	public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd, int candidatesStart, int
			candidatesEnd) {
		super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
		//// If the current selection in the text view changes, we should
		//// clear whatever candidate text we have.
		//if (mComposing.length() > 0 && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
		//	mComposing.setLength(0);
		//	updateCandidates();
		//	InputConnection ic = getCurrentInputConnection();
		//	if (ic != null) {
		//		ic.finishComposingText();
		//	}
		//}
	}

	// TODO: can we communicate with the keyboard another way? (e.g., handler?)
	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onMessage(MessageReceivedEvent event) {
		BroadcastMessage message = event.mMessage;
		switch (message.mType) {
			case KEYBOARD:
				if (message.hasExtras()) {
					int[] extras = message.getExtras();
					getCurrentInputConnection().setSelection(0, mComposing.length());
					mComposing.replace(extras[0], extras[0] + extras[1], message.mMessage);
					getCurrentInputConnection().commitText(mComposing, mComposing.length());
				}
				break;

			default:
				break;
		}
	}
}
