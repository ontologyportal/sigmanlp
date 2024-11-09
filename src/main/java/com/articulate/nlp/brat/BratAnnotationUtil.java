package com.articulate.nlp.brat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.articulate.nlp.WSDAnnotator;
import com.articulate.nlp.semRewrite.Lexer;
import com.articulate.nlp.semRewrite.Literal;
import com.articulate.sigma.utils.StringUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.articulate.nlp.NERAnnotator;
import com.articulate.nlp.pipeline.Pipeline;
import com.articulate.nlp.pipeline.SentenceUtil;
import com.google.common.collect.ImmutableList;

import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;

/**
 * This code is copyright Infosys Ltd 2017.
 * This software is released under the GNU Public License v3.
 */

/** *************************************************************
 * 
 * @author Infosys LTD.
 * @author Adam Pease
 * contact apease@articulatesoftware.com
 *
 */
public class BratAnnotationUtil {

	public int termCount = 1;
	public int relationCount = 1;
	// mapping from term id to BratEntity
	public Map<String, BratEntity> bratEntitiesMap = new HashMap<>();
	// mapping from term name - index to term id
	public Map<String, String> termIdMap = new HashMap<>();

    /** ***************************************************************
     */
	public class BratEntity {

		private String name;
		private String id;
		private String type;
		private int start;
		private int end;

		public String getName() { return name; }

		public void setName(String name) { this.name = name; }

		public String getId() { return id; }

		public void setId(String id) { this.id = id; }

		public String getType() { return type; }

		public void setType(String type) { this.type = type; }

		public int getStart() { return start; }

		public void setStart(int start) { this.start = start; }

		public int getEnd() { return end; }

		public void setEnd(int end) { this.end = end; }
	}

    /** ***************************************************************
     */
	public class BratRelation {

		private String id;
		private String type;
		private String startTermId;
		private String endTermId;

		public String getId() { return id; }

		public void setId(String id) { this.id = id; }

		public String getType() { return type; }

		public void setType(String type) { this.type = type; }

		public String getStartTermId() { return startTermId; }

		public void setStartTermId(String startTermId) { this.startTermId = startTermId; }

		public String getEndTermId() { return endTermId; }

		public void setEndTermId(String endTermId) { this.endTermId = endTermId; }
	}

    /** ***************************************************************
     */
	private BratEntity addEntity(String type, String value, CoreLabel token) {

	    BratEntity be = new BratEntity();
		if (type != null && !type.isEmpty()) {
            be = new BratEntity();
            be.setId('T' + String.valueOf(termCount++));
            be.setType(type + ":" + value);
            be.setName(token.value());
            be.setStart(token.beginPosition());
            be.setEnd(token.endPosition());
            bratEntitiesMap.put(be.getId(), be);
		}
		return be;
	}

    /** ***************************************************************
     */
	@SuppressWarnings("unchecked")
	private JSONArray getJSONArrayOfBratEntities(List<BratEntity> bratEntities) {

		JSONArray result = new JSONArray();
		JSONArray currentEntity;
		for (BratEntity entity : bratEntities) {
			currentEntity = new JSONArray();
			currentEntity.add(entity.getId());
			currentEntity.add(entity.getType());
			JSONArray range = new JSONArray();
			JSONArray rangeInner = new JSONArray();
			rangeInner.add(entity.getStart());
			rangeInner.add(entity.getEnd());
			range.add(rangeInner);
			currentEntity.add(range);
			result.add(currentEntity);
		}
		return result;
	}

    /** ***************************************************************
     */
	@SuppressWarnings("unchecked")
	private JSONArray getJSONArrayOfBratRelations(List<BratRelation> bratRelations) {

		JSONArray result = new JSONArray();
		JSONArray currentRelation;
		for (BratRelation relation : bratRelations) {
			currentRelation = new JSONArray();
			currentRelation.add(relation.getId());
			currentRelation.add(relation.getType());
			JSONArray terms = new JSONArray();
			JSONArray term = new JSONArray();
			term.add("Start");
			term.add(relation.getStartTermId());
			terms.add(term);
			term = new JSONArray();
			term.add("End");
			term.add(relation.getEndTermId());
			terms.add(term);
			currentRelation.add(terms);
			result.add(currentRelation);
		}
		return result;
	}

    /** ***************************************************************
     */
	private List<BratRelation> getBratRelations(String input, Annotation document) {

		List<CoreMap> sentences = document.get(SentencesAnnotation.class);
		List<BratRelation> result = new ArrayList<>();
		// traversing the sentences of the document
		for (CoreMap sentence : sentences) {
			List<Literal> dependencies = SentenceUtil.toDependenciesList(ImmutableList.of(sentence));
			BratRelation bratRelation;
			String startTermId = null;
			String endTermId = null;
			for (Literal lit : dependencies) {
                startTermId = termIdMap.get(lit.arg1);
                endTermId = termIdMap.get(lit.arg2);
				//startTermId = termIdMap.get(dep.substring(dep.indexOf('(') + 1, dep.indexOf(',')).trim());
				//endTermId = termIdMap.get(dep.substring(dep.indexOf(',') + 1, dep.indexOf(')')).trim());
				if (startTermId == null || endTermId == null) {
					continue;
				}
				bratRelation = new BratRelation();
				bratRelation.setId('R' + String.valueOf(relationCount++));
                bratRelation.setType(lit.pred);
				//bratRelation.setType(dep.substring(0, dep.indexOf('(')).trim());
				bratRelation.setStartTermId(startTermId);
				bratRelation.setEndTermId(endTermId);
				result.add(bratRelation);
			}
		}
		return result;
	}

    /** ***************************************************************
     */
	private List<BratEntity> getBratEntities(String input, Annotation document) {

		List<CoreMap> sentences = document.get(SentencesAnnotation.class);
		List<BratEntity> result = new ArrayList<>();
		// traversing the sentences of the document
		for (CoreMap sentence : sentences) {
			BratEntity nerEntity = null;
			BratEntity sumoEntity = null;
			BratEntity posEntity = null;

			// traversing the words in the current sentence
			for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
				// NER label of the token
				String ner = token.get(NamedEntityTagAnnotation.class);
				if (!StringUtil.emptyString(ner) && !ner.equalsIgnoreCase("O")) {
				    System.out.println("getBratEntities(): ner: " + ner);
					nerEntity = addEntity("NER",token.get(NamedEntityTagAnnotation.class),token);
					result.add(nerEntity);
                    bratEntitiesMap.put(nerEntity.getId(), nerEntity);
                    termIdMap.put(nerEntity.getName() + "-" + token.index(), nerEntity.getId());
				}
				// NER SUMO annotation of the token
				String sumo = token.get(NERAnnotator.NERSUMOAnnotation.class);
				if (StringUtil.emptyString(sumo))
				    sumo = token.get(WSDAnnotator.SUMOAnnotation.class);
                System.out.println("getBratEntities(): sumo: " + sumo);
                if (!StringUtil.emptyString(sumo)) {
                    sumoEntity = addEntity("SUMO", sumo, token);
                    result.add(sumoEntity);
                    bratEntitiesMap.put(sumoEntity.getId(), sumoEntity);
                    termIdMap.put(sumoEntity.getName() + "-" + token.index(), sumoEntity.getId());
                }
				// POS tag of the token
				String pos = token.get(PartOfSpeechAnnotation.class);
				posEntity = new BratEntity();
				posEntity.setType(pos);
				posEntity.setStart(token.beginPosition());
				posEntity.setEnd(token.endPosition());
				posEntity.setId('T' + String.valueOf(termCount++));
				posEntity.setName(token.get(TextAnnotation.class));
				result.add(posEntity);
				bratEntitiesMap.put(posEntity.getId(), posEntity);
				termIdMap.put(posEntity.getName() + "-" + token.index(), posEntity.getId());
			}
		}
		return result;
	}

    /** ***************************************************************
     * @param input
     *            Text to be annotated
     * @return JSON String to be used as docData for brat annotation
     */
    @SuppressWarnings("unchecked")
    public JSONObject getBratAnnotationsJSON(String input, Annotation document) {

        JSONObject result = new JSONObject();
        result.put("text", input);
        List<BratEntity> bratEntities = getBratEntities(input, document);
        JSONArray entitiesArray = getJSONArrayOfBratEntities(bratEntities);
        result.put("entities", entitiesArray);
        List<BratRelation> bratRelations = getBratRelations(input, document);
        JSONArray relationsArray = getJSONArrayOfBratRelations(bratRelations);
        result.put("relations", relationsArray);
        return result;
    }

    /** ***************************************************************
	 * @param input
	 *            Text to be annotated
	 * @return JSON String to be used as docData for brat annotation
	 */
	@SuppressWarnings("unchecked")
	public String getBratAnnotations(String input, Annotation document) {

		return getBratAnnotationsJSON(input,document).toJSONString();
	}

    /** ***************************************************************
     */
    public static String toBratStandoff(List<BratRelation> rels, List<BratEntity> entities) {

        StringBuilder sb = new StringBuilder();

        for (BratEntity be : entities) {
            sb.append(be.getId() + "\t" + be.getType() + " " + be.getStart() +
                    " " + be.getEnd() + "\t" + be.getName() + "\n");
        }
        for (BratRelation br : rels) {
            sb.append(br.getId() + "\t" + br.getType() + " Arg1:" + br.getStartTermId() +
                    " Arg2:" + br.getEndTermId() + "\n");
        }
        return sb.toString();
    }

    /** ***************************************************************
     */
	@SuppressWarnings("unchecked")
	public String getBratAnnotations(List<Literal> dependencies) {

		JSONObject result = new JSONObject();
		String[] tokens = getTokensFromDependencies(dependencies);
		String sentence = getSentenceFromTokens(tokens);
		result.put("text", sentence);
		List<BratEntity> bratEntities = getBratEntitiesForDependencyParse(tokens);
		JSONArray entitiesArray = getJSONArrayOfBratEntities(bratEntities);
		result.put("entities", entitiesArray);
		List<BratRelation> bratRelations = getBratRelationsForDependencyParse(dependencies);
		JSONArray relationsArray = getJSONArrayOfBratRelations(bratRelations);
		result.put("relations", relationsArray);
        System.out.println("BratAnnotationUtil.getBratAnnotations(): " + result.toJSONString());
        return result.toJSONString();
	}

    /** ***************************************************************
     */
	private List<BratRelation> getBratRelationsForDependencyParse(List<Literal> dependencies) {

		List<BratRelation> result = new ArrayList<>();
		BratRelation bratRelation;
		String startTermId = null;
		String endTermId = null;
		for (Literal lit : dependencies) {
			startTermId = termIdMap.get(lit.arg1); //dep.substring(dep.indexOf('(') + 1, dep.indexOf(',')).trim());
			endTermId = termIdMap.get(lit.arg2); //dep.substring(dep.indexOf(',') + 1, dep.indexOf(')')).trim());
			if (startTermId == null || endTermId == null) {
				continue;
			}
			bratRelation = new BratRelation();
			bratRelation.setId('R' + String.valueOf(relationCount++));
			bratRelation.setType(lit.pred); //dep.substring(0, dep.indexOf('(')).trim());
			bratRelation.setStartTermId(startTermId);
			bratRelation.setEndTermId(endTermId);
			result.add(bratRelation);

		}
		return result;
	}

    /** ***************************************************************
     */
	private List<BratEntity> getBratEntitiesForDependencyParse(String[] tokens) {

		List<BratEntity> entities = new ArrayList<>();
		BratEntity bratEntity;
		int position = 0;
		for (int i = 1; i < tokens.length; i++) {
			bratEntity = new BratEntity();
			String tok = Literal.tokenOnly(tokens[i]);
			if (StringUtil.emptyString(tok)) {
			    System.out.println("Error in BratAnnotationUtil.getBratEntitiesForDependencyParse(): empty token for: " + tokens[i]);
                continue;
            }
			bratEntity.setName(tok); // tokens[i].substring(0, tokens[i].indexOf('-')));
			bratEntity.setType("");
			bratEntity.setId('T' + String.valueOf(termCount++));
			bratEntity.setStart(position);
			bratEntity.setEnd(position + bratEntity.getName().length());
			position += (bratEntity.getName().length() + 1);
			termIdMap.put(tokens[i], bratEntity.getId());
			entities.add(bratEntity);
		}
		return entities;
	}

    /** ***************************************************************
     */
	private String getSentenceFromTokens(String[] tokens) {

		StringBuilder sentence = new StringBuilder();
		for (int i = 1; i < tokens.length; i++) {
		    sentence.append(Literal.tokenOnly(tokens[i]));
			    // sentence.append(tokens[i].substring(0, tokens[i].indexOf('-')));
			sentence.append(" ");
		}
		return sentence.toString().trim();
	}

    /** ***************************************************************
     * Ignore tokens that are not part of the dependency parse but
     * are introduced by semantic rewriting preprocessing and therefore
     * lack a token number
     */
	private String[] getTokensFromDependencies(List<Literal> dependencies) {

		String first;
		String second;
		Set<String> tokens = new HashSet<>();
		int max = 0;
		for (Literal lit : dependencies) {
			//first = dep.substring(dep.indexOf('(') + 1, dep.indexOf(',')).trim();
			//second = dep.substring(dep.indexOf(',') + 1, dep.indexOf(')')).trim();
			tokens.add(lit.arg1); //tokens.add(first);
			tokens.add(lit.arg2); //tokens.add(second);
			max = Math.max(max,Literal.tokenNum(lit.arg1));
                    // max = Math.max(max, Integer.valueOf(first.substring(first.indexOf('-') + 1)));
            max = Math.max(max,Literal.tokenNum(lit.arg2));
			        //max = Math.max(max, Integer.valueOf(second.substring(second.indexOf('-') + 1)));
		}
		String[] result = new String[max + 1];
		for (String str : tokens) {
		    int tokNum = Literal.tokenNum(str);
		    if (tokNum > 0) {
                result[Literal.tokenNum(str)] = str;
                //System.out.println("BratAnnotationUtil.getTokensFromDependencies(): adding token: " + str);
            }
			//result[Integer.valueOf(str.substring(str.indexOf('-') + 1))] = str;
		}
		return result;
	}

	/** ***************************************************************
	 */
	public static void main(String[] args) {

		BratAnnotationUtil brt = new BratAnnotationUtil();
		String sentence = "The problem is that the vehicle in question was indeed not on the street in Hong Kong.";
		sentence = "Takayasu arteritis (TA) is an inflammatory process frequently associated with stenosis and obliteration of the aorta and its primary branches.";
		Pipeline pipeline = new Pipeline(true);
		Annotation document = pipeline.annotate(sentence);
		List<CoreMap> sentences = document.get(SentencesAnnotation.class);
		List<Literal> dependencies = SentenceUtil.toDependenciesList(ImmutableList.of(sentences.get(0)));
		System.out.println(brt.getBratAnnotations(dependencies));
		brt.termCount = 1;
		brt.relationCount = 1;
		brt.termIdMap = new HashMap<>();
		//System.out.println("main(): annotations: " + brt.getBratAnnotations(sentence, document));
		////System.out.println();
       // System.out.println("main(): json: " + brt.getBratAnnotationsJSON(sentence, document));
        //System.out.println();
        List<BratEntity> bratEntities = brt.getBratEntities(sentence,document);
        List<BratRelation> bratRelations = brt.getBratRelations(sentence,document);
        System.out.println("main(): standoff: " + toBratStandoff(bratRelations,bratEntities));
	}
}


