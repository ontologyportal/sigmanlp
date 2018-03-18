package com.articulate.nlp.semRewrite.datesandnumber;

/*
Copyright 2014-2015 IPsoft

Author: Nagaraj Bhat nagaraj.bhat@ipsoft.com
        Rashmi Rao

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program ; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston,
MA  02111-1307 USA 
*/

import com.articulate.nlp.pipeline.Pipeline;
import com.articulate.nlp.pipeline.SentenceUtil;
import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.nlp.semRewrite.Literal;
import com.articulate.sigma.KBmanager;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;

import java.util.ArrayList;
import java.util.List;

public class InterpretNumerics {

    private static Pipeline p = null; //new Pipeline(false);

    /** ***************************************************************
	 * Returns a list of SUO-KIF statements, each corresponding to a date/time/measure found in the input
	 * natural language string.
	 * @param input: The natural language string.
     * @return List of SUO-KIF statements, each date/time/measures are obtained from parser.
     */
	public static List<Literal> getSumoTerms(String input) {

        if (p == null) {
            System.out.println("Error in InterpretNumerics.getSumoTerms(): null pipeline");
            return null;
        }
        Annotation annotation = p.annotate(input);
        CoreMap lastSentence = SentenceUtil.getLastSentence(annotation);
		StanfordDateTimeExtractor sde = new StanfordDateTimeExtractor();
        List<Tokens> tokenList = new ArrayList<Tokens>();
		sde.populateParserInfo(lastSentence,tokenList);
		DateAndNumbersGeneration generator = new DateAndNumbersGeneration();
		return generator.generateSumoTerms(tokenList, sde);
	}

	/** ***************************************************************
	 */
	public static void main(String[] args) {

		KBmanager.getMgr().initializeOnce();
        Interpreter interp = new Interpreter();
        try {
            interp.initialize();
        }
        catch (Exception e) {
            e.printStackTrace();
            System.out.println(e.getMessage());
        }
        //pipe = new Pipeline(true);
        //Annotation annotation;
        //annotation = new Annotation(inputSentence);

        System.out.println("-------------------------InterpretNumerics.main()-------------------------");
        String input = "John was killed on 8/15/2014 at 3:45 PM.";
        System.out.println(input);
        System.out.println(interp.interpretSingle(input));

        System.out.println("-------------------------");
        input = "As of 2012, sweet oranges accounted for approximately 70 percent of citrus production.";
        System.out.println(input);
        System.out.println(interp.interpretSingle(input));
        System.out.println("-------------------------");
        input = "The standard goal of sigma is to achieve precision to 4.5 standard deviations above or below the mean.";
        System.out.println(input);
        System.out.println(interp.interpretSingle(input));
        System.out.println("-------------------------");
        input = "Taj Mahal attracts some 3000000 people a year for visit.";
        System.out.println(input);
        System.out.println(interp.interpretSingle(input));
        System.out.println("-------------------------");
        input = "In 2014, Fiat owned 90% of Ferrari.";
        System.out.println(input);
        System.out.println(interp.interpretSingle(input));
        System.out.println("-------------------------");
        input = "John killed Mary on 31 March and also in July 1995 by travelling back in time.";
        System.out.println(input);
        System.out.println(interp.interpretSingle(input));

        System.out.println("-------------------------");
        input = "The $200,000 and $60,000 in the first and fourth charges were not lost";
        System.out.println(input);
        System.out.println(interp.interpretSingle(input));

        System.out.println("-------------------------");
        input = "Of the 11 counts, the applicant was convicted of 9 counts (namely, the 3rd charge to the 11th charge).";
        System.out.println(input);
        System.out.println(interp.interpretSingle(input));

        System.out.println("-------------------------");
        input = "The total amount of monies involved in Charges 1 and 2 was approximately HK$2.7 million.";
        System.out.println(input);
        System.out.println(interp.interpretSingle(input));

    }
}
