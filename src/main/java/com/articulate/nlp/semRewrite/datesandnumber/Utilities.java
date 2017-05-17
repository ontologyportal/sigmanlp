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

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;

import java.util.*;
import java.util.regex.Pattern;

public class Utilities {
    
	//HashMap<Integer,String> dateMap = new LinkedHashMap<Integer,String>();
	
	public static final List<String> MONTHS = new ArrayList<String>(Arrays.asList("january",
			"february","march","april","may","june","july","august",
			"september","october","november","december"));
	public static final List<String> DAYS = new ArrayList<String>(Arrays.asList("monday",
			"tuesday","wednesday","thursday","friday","saturday","sunday"));
	public static final List<String> VerbTags = new ArrayList<String>(Arrays.asList("VB",
			"VBD","VBG","VBN","VBP","VBZ"));
	public static final List<String> nounTags = new ArrayList<String>(Arrays.asList("NN","NNS","NNP","NNPS","/NN","/NNS","/NNP", "/NNPS"));
	
	public static final Pattern sumoTermPattern = Pattern.compile("^([a-zA-Z]+)\\(([a-zA-Z\\-0-9]+)(\\s)?,(\\s)?([a-zA-Z(\\-)?0-9]+)\\)");
	public static final Pattern cnfPattern = Pattern.compile("^([a-zA-Z]+)\\((.*(\\-)?[0-9]*)(\\s)?,(\\s)?(.*(\\-)?[0-9]*)\\)");
	
	public static final List<String> datesAndNumbersPredicates = new ArrayList<String>(Arrays.asList("time","day","month"));
	
	public static final List<String> stopWords = new ArrayList<String>(Arrays.asList("of",",","-"));
	
	List<String> sumoTerms = new LinkedList<String>();
	List<DateInfo> datesList = new LinkedList<DateInfo>();
	SemanticGraph StanfordDependencies;
	List<String> lemmatizedResults = new ArrayList<>();
	HashMap<Integer, String> lemmaWordMap = new HashMap<>();
	int timeCount = 1;
	
	
	/** ***************************************************************
     */
	public boolean containsIndexWord(String word) {

		for (String verbTag: VerbTags) {
			if (verbTag.contains(word)) {
				return true;
			}
		}
		return false;
	}

	/** ***************************************************************
     */
	public String populateRootWord(int wordIndex) {

		IndexedWord tempParent = StanfordDependencies.getNodeByIndex(wordIndex);
		while (!tempParent.equals(StanfordDependencies.getFirstRoot())) {
			tempParent = StanfordDependencies.getParent(tempParent);
			if (containsIndexWord(tempParent.tag())) {
				return tempParent.word()+"-"+tempParent.index();
			}
		}
		return null;
	}
		
	/** ***************************************************************
     */
	public void filterSumoTerms() {
		
		Set<String> hashsetList = new HashSet<String>(sumoTerms);
		sumoTerms.clear();
		sumoTerms.addAll(hashsetList);
		//List<String> removableList = new ArrayList<String>();
		Set<String> removableSumoTerms = new HashSet<String>();
		for (DateInfo d : datesList) {
			if (d.isDuration()) {
				//removableList.add("time-"+d.getTimeCount());
				for(String sumoTerm : sumoTerms) {
					if(sumoTerm.matches("^time\\(.*,time-"+d.getTimeCount()+"\\)$")) {
						removableSumoTerms.add(sumoTerm);
					}
				}
			}
		}
	    sumoTerms.removeAll(removableSumoTerms);
	}
}