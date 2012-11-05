/**
 * @author yappy
 */
public class DMException extends Exception {

	private static final long serialVersionUID = 1L;

	public DMException() {
	}

	public DMException(String message) {
		super(message);
	}

	public DMException(Throwable cause) {
		super(cause);
	}

	public DMException(String message, Throwable cause) {
		super(message, cause);
	}

}
