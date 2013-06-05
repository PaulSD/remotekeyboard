package de.onyxbits.remotekeyboard;

import net.wimpi.telnetd.io.TerminalIO;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.Toast;

/**
 * Models a key event sent from the remote keyboard via the telnet protocol and
 * provides a method for replaying that input on the UI thread (possibly
 * translating it from telnet to android semantics).
 * 
 * @author patrick
 * 
 */
class InputAction implements Runnable {

	public static final String TAG = "InputAction";

	/**
	 * A control character (anything thats not printable)
	 */
	protected int control;
	
	/**
	 * Printable text to insert (anything thats not a control character).
	 */
	protected String printable;

	/**
	 * For sending raw key presses to the editor
	 */
	protected RemoteKeyboardService myService;

	public InputAction() {
	}

	// @Override
	public void run() {
		Log.d(TAG, "" + control);
		InputConnection con = myService.getCurrentInputConnection();
		if (con == null) {
			return;
		}

		switch (control) {
			case Sequencer.UNKNOWN: {
				String s = myService.getResources().getString(
						R.string.err_esc_unsupported);
				Toast.makeText(myService, s, Toast.LENGTH_SHORT).show();
				break;
			}
			// FIXME: Dirty hack! Most telnet clients send DELETE instead of BACKSPACE
			// when the physical backspace key is pressed. This is usually
			// configurable but difficult to explain. So instead of explaining the
			// issue, we just make both symbols semantically equivalent.
			case TerminalIO.DEL:
			case TerminalIO.DELETE:
			case TerminalIO.BACKSPACE: {
				typeKey(con,KeyEvent.KEYCODE_DEL);
				break;
			}
			case TerminalIO.ENTER:
			case '\n': {
				// NOTE: Workaround for a bug in the telnet library! It should return
				// ENTER, but does return LF.
				typeKey(con,KeyEvent.KEYCODE_ENTER);
				break;
			}
			case Sequencer.CURSOR_LEFT: {
				typeKey(con,KeyEvent.KEYCODE_DPAD_LEFT);
				break;
			}
			case Sequencer.CURSOR_RIGHT: {
				typeKey(con,KeyEvent.KEYCODE_DPAD_RIGHT);
				break;
			}
			case Sequencer.CURSOR_UP: {
				typeKey(con,KeyEvent.KEYCODE_DPAD_UP);
				break;
			}
			case Sequencer.CURSOR_DOWN: {
				typeKey(con,KeyEvent.KEYCODE_DPAD_DOWN);
				break;
			}
			case TerminalIO.TABULATOR: {
				typeKey(con,KeyEvent.KEYCODE_TAB);
				break;
			}

			case Sequencer.HOME: {
				con.setSelection(0,0);
				break;
			}
			case Sequencer.INSERT: {
				// Dunno what to do with this one, yet.
				break;
			}
			case Sequencer.DELETE: {
				con.deleteSurroundingText(0, 1);
				break;
			}
			case Sequencer.END: {
				ExtractedText txt = con.getExtractedText(new ExtractedTextRequest(), 0);
				if (txt != null) {
					con.setSelection(txt.text.length(),txt.text.length());
				}
				break;
			}

			// Hacky time! Redefine the semantics of CTRL-A to select-all
			case TerminalIO.COLORINIT: {
				ExtractedText text = con
						.getExtractedText(new ExtractedTextRequest(), 0);
				try {
					con.setSelection(0, text.text.length());
				}
				catch (NullPointerException e) {
					// Potentially, text or text.text can be null
				}
				break;
			}

			// Hacky time! Redefine the semantics of ASCII ETX (CTRL-C) to copy
			case 3: {
				con.performContextMenuAction(android.R.id.copy);
				break;
			}

			// Hacky time! Redefine the semantics of ASCII SYN (CTRL-V) to paste
			case 22: {
				con.performContextMenuAction(android.R.id.paste);
				break;
			}

			// Hacky time! Redefine the semantics of ASCII CAN (CTRL-X) to cut
			case 24: {
				con.performContextMenuAction(android.R.id.cut);
				break;
			}
			
			// Hacky time! Redefine the semantics of ASCII FF (CTRL-L) to Send
			case 12: {
				con.performEditorAction(EditorInfo.IME_ACTION_SEND);
				break;
			}

			default: {
				if (printable!=null) {
					con.commitText(printable, printable.length());
				}
			}
		}
	}
	
	/**
	 * Send an down/up event
	 * @con conenction to sent with
	 * @param key keycode
	 */
	private void typeKey(InputConnection con, int key) {
		con.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,key));
		con.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,key));
	}
}
