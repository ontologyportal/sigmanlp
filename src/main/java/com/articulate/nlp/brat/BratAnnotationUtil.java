package com.articulate.nlp.brat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * This software is released under the GNU Public License.
 */
/**
 * 
 * @author mohit.gupta
 *
 */
public class BratAnnotationUtil {

	public int termCount = 1;
	public int relationCount = 1;
	public Annotation document = null;
	// mapping from term id to BratEntity
	public Map<String, BratEntity> bratEntitiesMap = new HashMap<>();
	// mapping from term name - index to term id
	public Map<String, String> termIdMap = new HashMap<>();

	public class BratEntity {
		private String name;
		private String id;
		private String type;
		private int start;
		private int end;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public int getStart() {
			return start;
		}

		public void setStart(int start) {
			this.start = start;
		}

		public int getEnd() {
			return end;
		}

		public void setEnd(int end) {
			this.end = end;
		}

	}

	public class BratRelation {
		private String id;
		private String type;
		private String startTermId;
		private String endTermId;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public String getStartTermId() {
			return startTermId;
		}

		public void setStartTermId(String startTermId) {
			this.startTermId = startTermId;
		}

		public String getEndTermId() {
			return endTermId;
		}

		public void setEndTermId(String endTermId) {
			this.endTermId = endTermId;
		}

	}

	private BratEntity addEntity(String type, BratEntity entity, CoreLabel token, List<BratEntity> result) {
		if (type != null && !type.isEmpty()) {
			if (entity == null) {
				entity = new BratEntity();
				entity.setType(type);
				entity.setStart(token.beginPosition());
				entity.setEnd(token.endPosition());
				entity.setId('T' + String.valueOf(termCount++));
				entity.setName(token.get(TextAnnotation.class));
				bratEntitiesMap.put(entity.getId(), entity);
				result.add(entity);
			} else {
				if (type.equals(entity.getType())) {
					entity.setEnd(token.endPosition());
					entity.setName(entity.getName() + " " + token.get(TextAnnotation.class));
				} else {
					entity = new BratEntity();
					entity.setType(type);
					entity.setStart(token.beginPosition());
					entity.setEnd(token.endPosition());
					entity.setId('T' + String.valueOf(termCount++));
					entity.setName(token.get(TextAnnotation.class));
					bratEntitiesMap.put(entity.getId(), entity);
					result.add(entity);
				}
			}
		}
		return entity;
	}

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

	private List<BratRelation> getBratRelations(String input) {
		if (document == null)
			document = Pipeline.toAnnotation(input);

		List<CoreMap> sentences = document.get(SentencesAnnotation.class);
		List<BratRelation> result = new ArrayList<>();
		// traversing the sentences of the document
		for (CoreMap sentence : sentences) {
			List<String> dependencies = SentenceUtil.toDependenciesList(ImmutableList.of(sentence));
			BratRelation bratRelation;
			String startTermId = null;
			String endTermId = null;
			for (String dep : dependencies) {
				startTermId = termIdMap.get(dep.substring(dep.indexOf('(') + 1, dep.indexOf(',')).trim());
				endTermId = termIdMap.get(dep.substring(dep.indexOf(',') + 1, dep.indexOf(')')).trim());
				if (startTermId == null || endTermId == null) {
					continue;
				}
				bratRelation = new BratRelation();
				bratRelation.setId('R' + String.valueOf(relationCount++));
				bratRelation.setType(dep.substring(0, dep.indexOf('(')).trim());
				bratRelation.setStartTermId(startTermId);
				bratRelation.setEndTermId(endTermId);
				result.add(bratRelation);
			}
		}
		return result;
	}

	private List<BratEntity> getBratEntities(String input) {
		document = Pipeline.toAnnotation(input);
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
				if (!ner.equalsIgnoreCase("O")) {
					nerEntity = addEntity(ner, nerEntity, token, result);
				}
				// NER SUMO annotation of the token
				String sumo = token.get(NERAnnotator.NERSUMOAnnotation.class);
				sumoEntity = addEntity(sumo, sumoEntity, token, result);
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

	/**
	 * 
	 * @param input
	 *            Text to be annotated
	 * @return JSON String to be used as docData for brat annotation
	 */
	@SuppressWarnings("unchecked")
	public String getBratAnnotations(String input) {
		JSONObject result = new JSONObject();
		result.put("text", input);
		List<BratEntity> bratEntities = getBratEntities(input);
		JSONArray entitiesArray = getJSONArrayOfBratEntities(bratEntities);
		result.put("entities", entitiesArray);
		List<BratRelation> bratRelations = getBratRelations(input);
		JSONArray relationsArray = getJSONArrayOfBratRelations(bratRelations);
		result.put("relations", relationsArray);
		return result.toJSONString();
	}

}
