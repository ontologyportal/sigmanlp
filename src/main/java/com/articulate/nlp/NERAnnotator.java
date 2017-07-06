package com.articulate.nlp;

import com.articulate.sigma.*;
import com.articulate.nlp.pipeline.SentenceUtil;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.SpanAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.IntPair;

import java.util.*;

/**
 * This class marks NER strings and annotates with their SUMO
 * class membership and if a multi-word, their span .
 *
 * @author apease
 */

public class NERAnnotator implements Annotator {

    // Each CoreLabel in a multi-word string gets one that provides the entire
    // multi-word in WordNet format.  Individual words will still have their own
    // synsets, so later processing should check tokens for multi-word membership
    public static class NERAnnotation implements CoreAnnotation<String> {
        public Class<String> getType() {
            return String.class;
        }
    }

    public static class NERSUMOAnnotation implements CoreAnnotation<String> {
        public Class<String> getType() {
            return String.class;
        }
    }

    //spans are set to token numbers not array position index
    public static class NERSpanAnnotation  extends SpanAnnotation {
        public Class<IntPair> getType() {
            return IntPair.class;
        }
    }

    public static class NERTokenAnnotation implements CoreAnnotation<String> {
        public Class<String> getType() {
            return String.class;
        }
    }

    static final Requirement NERSUMO_REQUIREMENT = new Requirement("nersumo");

    /****************************************************************
     */
    public NERAnnotator(String name, Properties props) {

        KBmanager.getMgr().initializeOnce();
    }

    /****************************************************************
     * Long term, there's probably a way to deduce a more specific
     * class membership for a token, but for now we just map the
     * NER classes.  Note that Money, Percent, Date, Time will be complex
     * expressions rather than classes, and need different treatment
     * elsewhere.
     */
    public static String toSUMO(IntPair ip, List<CoreLabel> tokens) {

        String NERclass = tokens.get(ip.getSource()-1).ner();
        if (NERclass.equals("PERSON")) return "Human";
        else if (NERclass.equals("LOCATION")) return "GeographicArea";
        else if (NERclass.equals("ORGANIZATION")) return "Organization";
        return "";
    }

    /****************************************************************
     * Create a KIF variable token corresponding to each original
     * word separated by underscores and ending with the number of the
     * first token
     */
    public static String toToken(IntPair ip, List<CoreLabel> tokens) {

        StringBuffer result = new StringBuffer();
        //result.append("?");
        for (int i = ip.getSource() - 1; i < ip.getTarget(); i++) {
            if (i != ip.getSource() - 1)
                result.append("_");
            result.append(tokens.get(i).originalText());
        }
        result.append("-" + Integer.toString(ip.getSource()));
        return result.toString();
    }

    /****************************************************************
     * Set the NERSpanAnnotation of each token with the indices and
     * values of the entire span
     */
    public static void setSpan(int ptr, List<CoreLabel> tokens, int firstToken) {

        //spans are set to token numbers not array position index
        int last = ptr - 1;
        IntPair ip = new IntPair(firstToken, last);
        String sumo = toSUMO(ip,tokens);
        String tokStr = toToken(ip,tokens);
        System.out.println("NERAnnotator.annotate(): storing: " + tokStr + " " + ip + " " + sumo);
        for (int i = firstToken-1; i <= last-1; i++) {
            CoreLabel nertok = tokens.get(i);
            nertok.set(NERSpanAnnotation.class, ip);
            nertok.set(NERSUMOAnnotation.class, sumo);
            nertok.set(NERTokenAnnotation.class, tokStr);
        }
    }

    /****************************************************************
     * Mark all the multiwords in the text with their synset, sumo
     * term and the span of the multiword using tokens indexes (which
     * start at 0 and not token numbers, which start at 1)
     */
    public static void markTokens(List<CoreLabel> tokens) {

        String firstAnnotation = "O";
        int firstToken = 1;
        String NERtag = "";
        CoreLabel lastToken = null;
        for (CoreLabel token : tokens) {
            lastToken = token;
            //System.out.println("NERAnnotator.annotate() token: " + token);
            //System.out.println("NERAnnotator.annotate() index: " + token.index());
            NERtag = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
            //System.out.println("NERAnnotator.annotate() ner: " + NERtag);
            //System.out.println("NERAnnotator.annotate() firstAnnotation: " + firstAnnotation);
            //System.out.println("NERAnnotator.annotate() firstToken: " + firstToken);
            // String NERtag = token.ner();
            if (NERtag.equals("O") && firstAnnotation.equals("O")) { // continuous "other" tags
                //System.out.println("NERAnnotator.annotate() continuous other");
                firstAnnotation = NERtag;
                firstToken = token.index();
            }
            else {
                if (!NERtag.equals(firstAnnotation) && !NERtag.equals("O")) { // start of an NER
                    //System.out.println("NERAnnotator.annotate() start of NER");
                    firstAnnotation = NERtag;
                    firstToken = token.index();
                }
                else if (!NERtag.equals(firstAnnotation)) {  // end of an NER tag
                    //System.out.println("NERAnnotator.annotate() end of NER");
                    setSpan(token.index(),tokens,firstToken);
                    firstAnnotation = NERtag;
                    firstToken = token.index();
                }
            }
            //System.out.println("NERAnnotator.annotate(): loop: ------------------");
        }
        //System.out.println("NERAnnotator.annotate(): falling through with tag: " + NERtag);
        if (!NERtag.equals("O"))
            setSpan(tokens.size()+1,tokens,firstToken);
    }

    /****************************************************************
     * Mark all the multiwords in the text with their synset, sumo
     * term and the span of the multiword using tokens indexes (which
     * start at 0 and not token numbers, which start at 1)
     */
    public void annotate(Annotation annotation) {

        //System.out.println("NERAnnotator.annotate() ");
        if (! annotation.containsKey(CoreAnnotations.SentencesAnnotation.class))
            throw new RuntimeException("Unable to find sentences in " + annotation);
        List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
        for (CoreMap sentence : sentences) {
            List<CoreLabel> tokens = SentenceUtil.getLastSentence(annotation).get(CoreAnnotations.TokensAnnotation.class);
            markTokens(tokens);
        }
    }

    /****************************************************************
     */
    @Override
    public Set<Requirement> requires() {

        ArrayList<Requirement> al = new ArrayList<>();
        al.add(TOKENIZE_REQUIREMENT);
        al.add(SSPLIT_REQUIREMENT);
        al.add(LEMMA_REQUIREMENT);
        al.add(NER_REQUIREMENT);
        ArraySet<Requirement> result = new ArraySet<>();
        result.addAll(al);
        return result;
    }

    /****************************************************************
     */
    @Override
    public Set<Requirement> requirementsSatisfied() {

        return Collections.singleton(NERSUMO_REQUIREMENT);
    }

    /****************************************************************
     */
    public static void main(String[] args) {

        ArrayList<CoreLabel> al = new ArrayList<>();
        CoreLabel cl = new CoreLabel();
        cl.setValue("Juan");
        cl.setOriginalText("Juan");
        cl.setIndex(1);
        cl.set(CoreAnnotations.NamedEntityTagAnnotation.class,"PERSON");
        al.add(cl);

        cl = new CoreLabel();
        cl.setValue("Lopez");
        cl.setOriginalText("Lopez");
        cl.setIndex(2);
        cl.set(CoreAnnotations.NamedEntityTagAnnotation.class,"PERSON");
        al.add(cl);

        cl = new CoreLabel();
        cl.setValue("loves");
        cl.setOriginalText("loves");
        cl.setIndex(3);
        cl.set(CoreAnnotations.NamedEntityTagAnnotation.class,"O");
        al.add(cl);

        cl = new CoreLabel();
        cl.setValue("New");
        cl.setOriginalText("New");
        cl.setIndex(4);
        cl.set(CoreAnnotations.NamedEntityTagAnnotation.class,"LOCATION");
        al.add(cl);

        cl = new CoreLabel();
        cl.setValue("York");
        cl.setOriginalText("York");
        cl.setIndex(5);
        cl.set(CoreAnnotations.NamedEntityTagAnnotation.class,"LOCATION");
        al.add(cl);

        cl = new CoreLabel();
        cl.setValue("City");
        cl.setOriginalText("City");
        cl.setIndex(6);
        cl.set(CoreAnnotations.NamedEntityTagAnnotation.class,"LOCATION");
        al.add(cl);

        markTokens(al);
    }
}

