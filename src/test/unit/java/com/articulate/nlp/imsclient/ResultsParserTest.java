package com.articulate.nlp.imsclient;


import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import junit.framework.Assert;

/**
 * Unit test for ResultsParser.
 */
public class ResultsParserTest {
	
    @Test(expected=ParseException.class)    
    public void testNullResult() throws Exception {    		
    		ResultsParser p = new ResultsParser(null);
    		p.parse();
    }

    @Test
    public void testJustWordResult() throws Exception {    		
    		ResultsParser p = new ResultsParser("Only a word");
    		p.parse();
    }
    
    @Test
    public void testSimpleSentence() throws Exception {
    		String sentence= "I <x length=\"1 be%2:42:03::|0.19358397873187122 be%2:42:06::|0.09113971089550084 be%2:42:02::|0.08509705913189533 be%2:42:05::|0.11137983193556279 be%2:41:00::|0.08723928992165127 be%2:42:00::|0.10470784615104374 be%2:42:04::|0.07795942461122081 be%2:42:07::|0.0797854108033693 be%2:42:08::|0.08362633342144535 be%2:42:09::|0.08548111439643946\">am</x> <x length=\"1 interest%2:37:00::|0.35936362438964803 interest%2:42:00::|0.3200682317542027 interest%2:42:01::|0.3205681438561493\">interested</x> in the <x length=\"1 interest%1:09:00::|0.12016503873916862 interest%1:07:01::|0.1799600666027954 interest%1:07:02::|0.1198690665164905 interest%1:21:00::|0.25060956655121375 interest%1:04:01::|0.11567878920701233 interest%1:14:00::|0.12612466283670157 interest%1:21:03::|0.08759280954661784\">interest</x> rates of this <x length=\"1 bank%1:14:00::|0.24623754793140135 bank%1:17:00::|0.1641458183726022 bank%1:17:01::|0.2354607789849798 bank%1:14:01::|0.1651105420121604 bank%1:21:00::|0.18904531269885622\">bank</x> .";
    		ResultsParser p = new ResultsParser(sentence);
    		List<ParseResult> result = p.parse();
    		Assert.assertNotNull(result);
    		Assert.assertEquals(8, result.size());
    		Assert.assertEquals(new ParseResult("I", null), result.get(0));
         
    		List<TotalSense> senses = new ArrayList<>();
    		TotalSense sense1 = new TotalSense("be%2:42:03::", 0.19358397873187122);
    		senses.add(sense1);
    		TotalSense sense2 = new TotalSense("be%2:42:06::", 0.09113971089550084);
    		senses.add(sense2);
    		TotalSense sense3 = new TotalSense("be%2:42:02::", 0.08509705913189533);
    		senses.add(sense3);
    		TotalSense sense4 = new TotalSense("be%2:42:05::", 0.11137983193556279);
    		senses.add(sense4);
    		TotalSense sense5 = new TotalSense("be%2:41:00::", 0.08723928992165127);
    		senses.add(sense5);
    		TotalSense sense6 = new TotalSense("be%2:42:00::", 0.10470784615104374);
    		senses.add(sense6);
    		TotalSense sense7 = new TotalSense("be%2:42:04::", 0.07795942461122081);
    		senses.add(sense7);
    		TotalSense sense8 = new TotalSense("be%2:42:07::", 0.0797854108033693);
    		senses.add(sense8);
    		TotalSense sense9 = new TotalSense("be%2:42:08::", 0.08362633342144535);
    		senses.add(sense9);
    		TotalSense sense10 = new TotalSense("be%2:42:09::", 0.08548111439643946);
    		senses.add(sense10);
    		
    		List<List<TotalSense>> totalSenses = new ArrayList<>();
    		totalSenses.add(senses);    		
    		Assert.assertEquals(new ParseResult("am", totalSenses), result.get(1));

    		totalSenses = new ArrayList<>();
    		totalSenses.add(extractSenses("interest%2:37:00::|0.35936362438964803 interest%2:42:00::|0.3200682317542027 interest%2:42:01::|0.3205681438561493"));    		
    		Assert.assertEquals(new ParseResult("interested", totalSenses), result.get(2));
    		
    		Assert.assertEquals(new ParseResult("in the", null), result.get(3));
    		
    		senses = extractSenses("interest%1:09:00::|0.12016503873916862 interest%1:07:01::|0.1799600666027954 interest%1:07:02::|0.1198690665164905 interest%1:21:00::|0.25060956655121375 interest%1:04:01::|0.11567878920701233 interest%1:14:00::|0.12612466283670157 interest%1:21:03::|0.08759280954661784");
    		totalSenses = new ArrayList<>();
    		totalSenses.add(senses);    		
    		Assert.assertEquals(new ParseResult("interest", totalSenses), result.get(4));
    		
    		Assert.assertEquals(new ParseResult("rates of this", null), result.get(5));
    		
    		senses = extractSenses("bank%1:14:00::|0.24623754793140135 bank%1:17:00::|0.1641458183726022 bank%1:17:01::|0.2354607789849798 bank%1:14:01::|0.1651105420121604 bank%1:21:00::|0.18904531269885622");
    		totalSenses = new ArrayList<>();
    		totalSenses.add(senses);    		
    		Assert.assertEquals(new ParseResult("bank", totalSenses), result.get(6));

    		Assert.assertEquals(new ParseResult(".", null), result.get(7));
    }
    
    @Test
    public void testMultipleLength() throws Exception {
	    String sentence = "<x length=\"1 be%2:42:03::|0.19426835093763223 be%2:42:09::|0.08541156756412471\" length=\"2 be%2:42:03::|0.19426835093763223\">am</x>";
	    List<TotalSense> senses1 = extractSenses("be%2:42:03::|0.19426835093763223 be%2:42:09::|0.08541156756412471");
	    List<TotalSense> senses2 = extractSenses("be%2:42:03::|0.19426835093763223");
	    List<List<TotalSense>> senses = new ArrayList<>();
	    senses.add(senses1);
	    senses.add(senses2);

	    ResultsParser p = new ResultsParser(sentence);
		List<ParseResult> result = p.parse();
		Assert.assertNotNull(result);
		Assert.assertEquals(1, result.size());
		Assert.assertEquals(new ParseResult("am", senses), result.get(0));

	    
    }

	private List<TotalSense> extractSenses(String senses) {
		List<TotalSense> result = new ArrayList<>();
		String[] tokens = senses.split("\\s");
		for(String token : tokens) {
			result.add(extractSense(token));
		}
		return result;
	}

	private TotalSense extractSense(String token) {
		TotalSense sense = new TotalSense();
		int end = token.length();
		int senseProbabilitySeparator = token.indexOf('|');
		String word = token.substring(0, senseProbabilitySeparator);		
		double probability = Double.parseDouble(token.substring(senseProbabilitySeparator + 1, end));
		sense.setSense(word);
		sense.setProbability(probability);
		return sense;
	}

}
