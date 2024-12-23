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

import com.articulate.nlp.semRewrite.Literal;
import com.articulate.sigma.wordNet.WSD;
import com.articulate.sigma.wordNet.WordNet;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DateAndNumbersGeneration {

	static enum DateComponent {
		DAY, MONTH, YEAR
	}

	public static boolean debug = false;
	
	static final Pattern HOUR_MINUTE_PATTERN = Pattern.compile("^T([0-9]{2}):([0-9]{2})$");
	static final Pattern HOUR_MINUTE_SECOND_PATTERN = Pattern.compile("^T([0-9]{2}):([0-9]{2}):([0-9]{2})$");
	static final Pattern YEAR_MONTH_TIME_PATTERN = Pattern.compile("^([0-9X]{4})(\\-[0-9]{2})?(\\-[0-9]{2})?T([0-9]{2}):([0-9]{2})(:[0-9]{2})?");
	static final Pattern POS_TAG_REMOVER = Pattern.compile("(\\/[A-Z]+)$");

	List<String> measureTerms = new ArrayList<String>();
	
	/** ***************************************************************
	 */
	public DateAndNumbersGeneration() {
	}
	
	/** ***************************************************************
	 * * Adds the time terms to SumoTerms list.
	 */
	private void generateSumoTimeTerms(List<TimeInfo> timesList, Utilities utilities) {

		for (TimeInfo times : timesList) {
			if ((times.getSecond() != null) || (times.getMinute() != null) || (times.getHour() != null)) {
				//StringBuilder timeFn = new StringBuilder();
				if (times.getSecond() != null) {
					utilities.sumoTerms.add(new Literal("second(" + "time-" + utilities.timeCount + "," +
                            times.getSecond() + ")"));
                    // times.getSecond() + "-" + times.getWordIndex() + ")");
				}
				if (times.getMinute() != null) {
					utilities.sumoTerms.add(new Literal("minute(" + "time-" + utilities.timeCount + "," +
							times.getMinute() + ")"));
				}
				if (times.getHour() != null) {
					utilities.sumoTerms.add(new Literal("hour(" + "time-" + utilities.timeCount + "," +
							times.getHour() + ")"));
				}
				String tokenRoot = utilities.populateRootWord(times.getWordIndex());
				if (tokenRoot != null) {
					utilities.sumoTerms.add(new Literal("time(" + tokenRoot + "," + "time-" + utilities.timeCount + ")"));
				}
				utilities.timeCount++;
			}
		}
	}
	
	/** ***************************************************************
	 */
	private String lemmatizeWord(IndexedWord measuredEntity) {
		
		String value = measuredEntity.value();
		if (!measuredEntity.tag().equals("NNP") || !measuredEntity.tag().equals("NNPS")) {
			value = measuredEntity.lemma();
		}
		return value;
	}

	/** ***************************************************************
	 * Generates a set of sumo terms corresponding to measure functions. Identifies the unit of measurement,
	 * value of measurement and entity of the measurement by performing a graph search.
	 * input: The tokens, count of measure, utility value.
	 */
	private void measureFn(Tokens token, int count, Utilities utilities) {

        if (utilities != null && debug)
            System.out.println("DateAndNumbersGeneration.measureFn(): utilities: " + utilities);
        if (token != null && debug)
            System.out.println("DateAndNumbersGeneration.measureFn(): token: " + token);
		IndexedWord tokenNode = utilities.StanfordDependencies.getNodeByIndex(token.getId());
		IndexedWord unitOfMeasurementNode = utilities.StanfordDependencies.getParent(tokenNode);
		IndexedWord measuredEntity = null;
		String posTagRemover = null;
		String unitOfMeasurementStr = "";
		String sumoUnitOfMeasure = "";
		List<String> visitedNodes = new ArrayList<String>();
		Matcher posTagRemoverMatcher = null;
		String measuredEntityStr = null;
		boolean flag = false;
		//int x = 0;
		// fix to remove comma in numbers
		if (token.getWord().contains(",")) {
			token.setWord(token.getWord().replaceAll(",", ""));
		}
		if (unitOfMeasurementNode != null) {
			//unitOfMeasurementStr = lemmatizeWord(unitOfMeasurementNode);
			unitOfMeasurementStr = unitOfMeasurementNode.word();
			measuredEntity = utilities.StanfordDependencies.getParent(unitOfMeasurementNode);
			visitedNodes.add(unitOfMeasurementNode.toString() + "-" + unitOfMeasurementNode.index());
		}
		if ((measuredEntity == null) && (unitOfMeasurementNode != null)) {
			for (SemanticGraphEdge e : utilities.StanfordDependencies.getOutEdgesSorted(unitOfMeasurementNode)) {
				if ((e.getRelation().toString().equals("nsubj")) || (e.getRelation().toString().equals("dobj"))) {
					measuredEntity = e.getDependent();
					flag = true;
					break;
				}
			}
		}
		else if ((measuredEntity == null) && (unitOfMeasurementNode == null)){
			return;
		}
		boolean changed = true;
		while (measuredEntity != null && !flag && changed) {
			measuredEntityStr = measuredEntity.value() + "-" + measuredEntity.index();
			if (debug) System.out.println("DateAndNumbersGeneration.measureFn(): measuredEntityStr: " + measuredEntityStr);
			if (!visitedNodes.contains(measuredEntityStr)) {
				visitedNodes.add(measuredEntityStr);
				changed = true;
			}
			else
			    changed = false;
			posTagRemoverMatcher = POS_TAG_REMOVER.matcher(measuredEntity.toString());
			if (posTagRemoverMatcher.find()) {
				posTagRemover = posTagRemoverMatcher.group(1);
				if (Utilities.nounTags.contains(posTagRemover)) {
					break;
				}
				//IndexedWord tempMeasuredEntity = StanfordDependencies.getParent(measuredEntity);
				if (utilities.StanfordDependencies.getParent(measuredEntity) == null) {
					Set<IndexedWord> childrenSet = utilities.StanfordDependencies.getChildren(measuredEntity);
					//which means it is unitOfMeasurementNode. Hence remove infinite looping condition
					if ((childrenSet.size() == 1)) {
						measuredEntity = unitOfMeasurementNode;
						//String lemmatizedWord = lemmatizeWord(measuredEntity);
						utilities.sumoTerms.add(new Literal("measure(" + measuredEntity.word() + "-" + measuredEntity.index() +
                                ", measure" + count + ")"));
						utilities.sumoTerms.add(new Literal("unit(measure" + count + ", "+ "memberCount" + ")"));
						utilities.sumoTerms.add(new Literal("value(measure" + count + ", " + token.getWord()+ ")"));
						utilities.sumoTerms.add(new Literal("valueToken(" + token.getWord() + "," + token.getWord() + "-" + token.getId() + ")"));
						flag = true;
						return;
					}
					IndexedWord measuredEntity_temp = null;
					for (IndexedWord child : childrenSet) {
						String childPosTagRemover = null;
						posTagRemoverMatcher = POS_TAG_REMOVER.matcher(child.toString());
						//childPosTagRemover = posTagRemoverMatcher.group(1);
						if (posTagRemoverMatcher.find()) {
							childPosTagRemover = posTagRemoverMatcher.group(1); 
						}
						if (debug) System.out.println("DateAndNumbersGeneration.measureFn(): visited: " + visitedNodes);
						if (debug) System.out.println("DateAndNumbersGeneration.measureFn(): noun tags: " + Utilities.nounTags);
						if (debug) System.out.println("DateAndNumbersGeneration.measureFn(): childPosTagRemover: " + childPosTagRemover);
						if (childPosTagRemover != null && !(visitedNodes.contains(child.toString() + "-" + child.index())) &&
								(Utilities.nounTags.contains(childPosTagRemover.replaceFirst("\\/", "")))){
							if ((utilities.StanfordDependencies.reln(measuredEntity, child) != null) &&
                                    (utilities.StanfordDependencies.reln(measuredEntity, child).getShortName().equals("nsubj"))) {
								measuredEntity = child;
								visitedNodes.add(child.toString() + "-" + child.index());
								flag = true;
								break;
							}
							measuredEntity_temp = child;
							visitedNodes.add(child.toString() + "-" + child.index());
						}
					}
					if (!flag) {
						measuredEntity = measuredEntity_temp;
						flag = true; 
					}
					
				}
				else {
					measuredEntity = utilities.StanfordDependencies.getParent(measuredEntity);
				}
			}
		}
		if (measuredEntity != null) {
			String lemmatizedWord = lemmatizeWord(measuredEntity);
			utilities.sumoTerms.add(new Literal("measure(" + lemmatizedWord + "-" + measuredEntity.index() + ", measure" + count + ")"));
		}
		sumoUnitOfMeasure = lemmatizeWord(unitOfMeasurementNode);
		sumoUnitOfMeasure = WSD.getBestDefaultSUMOsense(sumoUnitOfMeasure, 1);
		if ((sumoUnitOfMeasure != null) && (!sumoUnitOfMeasure.isEmpty())) {
			sumoUnitOfMeasure = sumoUnitOfMeasure.replaceAll("[^\\p{Alpha}\\p{Digit}]+","");
		}
		else {
			if ((measuredEntity != null) && (unitOfMeasurementStr.equals(measuredEntity.value()))) {
				unitOfMeasurementStr = "memberCount";
			}
			sumoUnitOfMeasure = unitOfMeasurementStr;
		}
		utilities.sumoTerms.add(new Literal("unit(measure" + count + ", "+ sumoUnitOfMeasure + ")"));
		utilities.sumoTerms.add(new Literal("value(measure" + count + ", " + token.getWord() + ")"));
		Literal l = new Literal();
		l.pred = "valueToken";
		l.setarg1(token.getWord());
		l.setarg2(token.getWord() + "-" + token.getId());
		utilities.sumoTerms.add(l);
		WordNet.wn.initOnce();
	}
	
	/** ***************************************************************
	 * Returns the measure terms added in the sumo list
	 * input: utility value
	 */
	public List<Literal> getMeasureTerms(Utilities utilities) {
		
		return utilities.sumoTerms;
	}
	
	/** ***************************************************************
	 * Sets the measure terms
	 * input: List of measure terms
	 */
	public void setMeasureTerms(List<String> measureTerms) {
		
		this.measureTerms = measureTerms;
	}
	
	/** ***************************************************************
	 * If a duplicate token is present in the tokens of datelist returns true
	 * input: Token value to compare and the list of tokens.
	 */
	private boolean checkTokenInList(Tokens token, List<Tokens> tempDateList) {
		
		for(Tokens tempToken : tempDateList) {
			if(tempToken.equals(token)) {
				return true;
			}
		}
		return false;
	}
	
	/** ***************************************************************
	 * Generates a set of sumo terms corresponding to time functions.  Uses the class TimeInfo to hold
	 * the required time data present in the tokens. Also does the job of grouping time set together from 
	 * the tokens. Foer eg. In sentence 3 hr, 30 min. the tokens 3 and 30 would be separate. This function 
	 * analyzes that 3 hour and 30 min would belong to same type object and groups them together.
	 * input: normalizedTokenIdMap, utility value, List of date tokens,date andDurationHandler,presentTimeToken and PreviousTimeToken
	 */
	private List<TimeInfo> processTime(List<String> tokenIdNormalizedTimeMap, Utilities utilities,
                                       List<Tokens> tempDateList, DatesAndDuration datesandDurationHandler,
                                       Tokens presentTimeToken, Tokens prevTimeToken) {

		List<TimeInfo> timesList = new ArrayList<TimeInfo>();
		for (String timeToken : tokenIdNormalizedTimeMap) {
			int id = Integer.valueOf(timeToken.split("@")[0]);
			String timeStr = timeToken.split("@")[1];
			Matcher hourMinPatternMatcher = HOUR_MINUTE_PATTERN.matcher(timeStr);
			Matcher hourMinSecPatternMatcher = HOUR_MINUTE_SECOND_PATTERN.matcher(timeStr);
			Matcher yearMonthTimePatternMatcher = YEAR_MONTH_TIME_PATTERN.matcher(timeStr);
			TimeInfo timeObj = new TimeInfo();
			if (hourMinPatternMatcher.find() && (utilities.StanfordDependencies.getNodeByIndexSafe(id)!=null)) {
				timeObj.setMinute(hourMinPatternMatcher.group(2));
				timeObj.setHour(hourMinPatternMatcher.group(1));
				timeObj.setWordIndex(id);
			} 
			else if (hourMinSecPatternMatcher.find() && (utilities.StanfordDependencies.getNodeByIndexSafe(id)!=null)) {
				timeObj.setMinute(hourMinSecPatternMatcher.group(2));
				timeObj.setHour(hourMinSecPatternMatcher.group(1));
				timeObj.setSecond(hourMinSecPatternMatcher.group(3));
				timeObj.setWordIndex(id);
			} 
			else if (yearMonthTimePatternMatcher.find() && (utilities.StanfordDependencies.getNodeByIndexSafe(id)!=null)) {
				String year = yearMonthTimePatternMatcher.group(1);
				int tokenCnt = new StanfordDateTimeExtractor().getTokenCount() + 1;
				if (!year.equals("XXXX")) {
					Tokens yearToken = new Tokens();
					yearToken.setWord(year);
					yearToken.setTokenType("YEAR");
					yearToken.setId(id);
					
					if(!checkTokenInList(yearToken, tempDateList)) {
						presentTimeToken = yearToken;
						datesandDurationHandler.mergeDates(yearToken, tempDateList, prevTimeToken, utilities);
						prevTimeToken = presentTimeToken;
					}
				}
				if (yearMonthTimePatternMatcher.group(2) != null) {
					String month = yearMonthTimePatternMatcher.group(2).replaceAll("\\-", "");
					Tokens monthToken = new Tokens();
					Tokens digitMonthToken = new Tokens();
					monthToken.setWord(month);
					monthToken.setId(id);
					monthToken.setTokenType("MONTH");
					
					digitMonthToken.setWord(Utilities.MONTHS.get(Integer.valueOf(month) - 1));
					digitMonthToken.setId(id);
					digitMonthToken.setTokenType("MONTH");
					
					if (!checkTokenInList(monthToken, tempDateList) && !checkTokenInList(digitMonthToken, tempDateList)) {
						presentTimeToken = digitMonthToken;
						datesandDurationHandler.mergeDates(digitMonthToken, tempDateList, prevTimeToken, utilities);
						prevTimeToken = presentTimeToken;
					}
				}
				if (yearMonthTimePatternMatcher.group(3) != null) {
					String day = yearMonthTimePatternMatcher.group(3).replaceAll("\\-", "");
					Tokens dayToken = new Tokens();
					dayToken.setWord(day);
					dayToken.setId(id);
					dayToken.setTokenType("DAYS");
					
					if(!checkTokenInList(dayToken, tempDateList)) {
						presentTimeToken = dayToken;
						datesandDurationHandler.mergeDates(dayToken, tempDateList, prevTimeToken, utilities);
						prevTimeToken = presentTimeToken;
					}
				}
				timeObj.setMinute(yearMonthTimePatternMatcher.group(5));
				timeObj.setHour(yearMonthTimePatternMatcher.group(4));
				timeObj.setWordIndex(id);
				if (yearMonthTimePatternMatcher.group(6) != null) {
					timeObj.setSecond(yearMonthTimePatternMatcher.group(6));
					timeObj.setWordIndex(id);
				}

			}
			if (!containsTimeInfo(timesList,timeObj)) {
				timesList.add(timeObj);
			}	
		}
		return timesList;
	}
	
	/** ***************************************************************
	 * Returns true if a timeInfo object is present in the list of timeInfo.
	 * input: List of timeInfo and timeInfo Object to be compared.
	 */
	public boolean containsTimeInfo(List<TimeInfo> timeList, TimeInfo timeObject) {
		
		for (TimeInfo t : timeList) {
			if (t.equals(timeObject)) {
				return true;
			}
		}
		return false;
	}

	/** ***************************************************************
	 * Categorizes the input into Date, Number, Duration and Time.
	 * Adds the necessary sumo terms from the respective functions and filter any duplicates if present.
	 * input: List of tokens, StanfordDateTimeExtractor object and a ClauseSubstitutor.
	 */
	public List<Literal> generateSumoTerms(List<Tokens> tokensList, StanfordDateTimeExtractor stanfordParser) {

		DatesAndDuration datesandDurationHandler = new DatesAndDuration();
		Utilities utilities = new Utilities();
		utilities.StanfordDependencies = stanfordParser.getDependencies();
		List<String> tokenIdNormalizedTimeMap = new ArrayList<String>();
		int numberCount = 1;
		List<Tokens> tempDateList = new ArrayList<>();
		Tokens presentDateToken = new Tokens();
		Tokens prevDateToken = null;
		Tokens presentTimeToken = new Tokens();
		Tokens prevTimeToken = null;
		Tokens numberToken = new Tokens();
		Tokens presentDurationToken = new Tokens();
		Tokens prevDurationToken = null;
		for (Tokens token : tokensList) {
			presentDateToken = token;
			presentDurationToken = token;
			switch(token.getNer()) {
				case "DATE"      : datesandDurationHandler.processDateToken(token, utilities, tempDateList, prevDateToken);
                                   prevDateToken = presentDateToken;
							       break;
				case "NUMBER":
				case "ORDINAL":
				case "PERCENT"   : measureFn(token,numberCount, utilities); ++numberCount;  //processNumber(token,stanfordParser.getDependencyList());
								   break;
				case "DURATION"  : numberToken = datesandDurationHandler.processDuration(token,utilities, prevDurationToken);
				                   if (numberToken != null) {
				                	   measureFn(numberToken, numberCount, utilities);
				                	   ++numberCount;
				                   }
				                   prevDurationToken = presentDurationToken;
				                   break;
				case "TIME"      : tokenIdNormalizedTimeMap.add(token.getId() + "@" + token.getNormalizedNer());
			}
		}
		List<TimeInfo> timesList = processTime(tokenIdNormalizedTimeMap,utilities, tempDateList, datesandDurationHandler, presentTimeToken, prevTimeToken);
		datesandDurationHandler.generateSumoDateTerms(utilities, tempDateList);
		datesandDurationHandler.processUnhandledDuration(utilities);
		generateSumoTimeTerms(timesList,utilities);
		utilities.filterSumoTerms();
		return utilities.sumoTerms;
	}
}
