package com.articulate.nlp.imsclient;

import java.util.ArrayList;
import java.util.List;

public class ResultsParser {
	private String result;
	
	public ResultsParser(final String result) {
		this.result = result;
	}
	
	public List<ParseResult> parse() throws ParseException {
		
	/**
	 * Take a token: could be 
	 * 			    : word / (part of word)
	 *              : x<
 	 *              : length="<total>
 	 *              : sense-class|probability
 	 *              : sense-class|probability"
 	 *              : sense-class|probability">word</x>
 	 *              
	 */
		
//<x length="1 be%2:42:03::|0.19426835093763223 be%2:42:09::|0.08541156756412471" length="2 be%2:42:03::|0.19426835093763223 be%2:42:09::|0.08541156756412471"></x>		
	/**
	 * Some rules:
	 * 			: If token is: <x => Means legal sense started
	 * 			: Inside legal sense the token could be:
 	 *              : length="<total> => Means new element in total
 	 *              : sense-class|probability
 	 *              : sense-class|probability" => Means end element in total
 	 *              : sense-class|probability">word</x> => Means end legal sense 
 	 *          :When we are not in legal sense, we are inside a word
	 */
	if(result == null || result.isEmpty()) {
		throw new ParseException("No result to parse");
	}
	String[] tokens = result.split("\\s");
	boolean isLegalSense = false;
	List<TotalSense> senses = null;
	List<List<TotalSense>> totalSensesForAWord = null;
	ParseResult wordSenseResult = null;
	List<ParseResult> result = new ArrayList<>();
	StringBuilder wordBuf = new StringBuilder();
	
	for (String token : tokens) {
		if(token.equals("<x")) {
			if(isLegalSense) {
				throw new ParseException("Encountered unclosed x<, could not parse.");
			}
			isLegalSense = true;		//Start of legal sense
			totalSensesForAWord = new ArrayList<>(); //Initialize total senses (senses for a word)
			wordSenseResult = new ParseResult(); //Initialize parse result
			
			if(!wordBuf.toString().isEmpty()) { //Previous word did not have legal sense.
				wordSenseResult.setWord(wordBuf.toString());
				wordBuf = new StringBuilder();
				result.add(wordSenseResult);
				wordSenseResult = new ParseResult();				
			}
		} else if(isLegalSense) {
			if(token.startsWith("length=\"")) { //Start of a new total sense, within total senses
				senses = new ArrayList<>(); //Start of total sense
			} else if(token.endsWith("</x>")) {
				int end = token.lastIndexOf('"');
				TotalSense sense = extractSense(token, end);
				senses.add(sense);
				totalSensesForAWord.add(senses);
				wordSenseResult.setTotals(totalSensesForAWord);
				wordSenseResult.setWord(token.substring(end + 2, token.indexOf("</x>"))); //+2 because it is something like ..">word</x>
				result.add(wordSenseResult);				
				wordSenseResult = new ParseResult();
				wordBuf = new StringBuilder(); //Next word starts here		
				isLegalSense = false;
			} else {
				boolean endOfTotalSense = token.endsWith("\""); 
				int endToken =  endOfTotalSense ? token.length() - 1 : token.length();
				TotalSense sense = extractSense(token, endToken);
				senses.add(sense);
				if(endOfTotalSense) {
					totalSensesForAWord.add(senses);
					senses = new ArrayList<>(); 
				}
			}
		} else {
			if(!wordBuf.toString().isEmpty()) {
				wordBuf.append(" ");
			}
			wordBuf.append(token);
		}		
		System.out.println(token);
	}
	
	if(!wordBuf.toString().isEmpty()) { //Last word did not have legal sense.
		wordSenseResult = new ParseResult();
		wordSenseResult.setWord(wordBuf.toString());
		result.add(wordSenseResult);
	}
	return result;	
	}

	private TotalSense extractSense(String token, int end) throws ParseException {
		TotalSense sense = new TotalSense();
		int senseProbabilitySeparator = token.indexOf('|');
		if(senseProbabilitySeparator == -1) {
			throw new ParseException("Missing |, could not parse: "+token);					
		}
		String word = token.substring(0, senseProbabilitySeparator);		
		double probability = Double.parseDouble(token.substring(senseProbabilitySeparator + 1, end));
		sense.setSense(word);
		sense.setProbability(probability);
		return sense;
	}
	

}
