package edu.indiana.dlib.hsi;
/**
 * An exception class for Hsi activities.  I hate documenting boilerplate.
 * @author bdwheele
 *
 */
public class HsiException extends Exception {
	static final long serialVersionUID = 7374712325114989901L;
	public HsiException(String string) {
		super(string);
	}
	public HsiException(Exception e) {
		super(e);
	}
}
