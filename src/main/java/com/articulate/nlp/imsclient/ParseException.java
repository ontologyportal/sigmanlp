package com.articulate.nlp.imsclient;

public class ParseException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    /**
     * Constructor for ParseException.
     * 
     * @param message String
     */
    public ParseException(final String message) {
        super(message);
    }

    /**
     * Constructor for ParseException.
     * @param message String
     * @param cause Exception
     */
    public ParseException(final String message, final Exception cause) {
        super(message, cause);
    }

}
