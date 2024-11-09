package com.articulate.nlp;

import com.articulate.nlp.pipeline.Pipeline;
import com.articulate.nlp.pipeline.SentenceUtil;
import com.articulate.nlp.semRewrite.Literal;
import com.google.common.collect.Sets;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertTrue;

/**
 * Created by areed on 3/27/15.
 */
public class PartOfSpeechInfoTest extends IntegrationTestBase {

    private static final Pipeline pipeline = new Pipeline(true);

    /** *************************************************************
     */
    @Test
    public void PresentTense() {

        String input = "She works in a bank";
        Annotation document = pipeline.annotate(input);
        List<Literal> dependencyList = SentenceUtil.toDependenciesList(document);
        CoreMap lastSentence = SentenceUtil.getLastSentence(document);
        List<CoreLabel> lastSentenceTokens = lastSentence.get(CoreAnnotations.TokensAnnotation.class);
        Set<Literal> posInformation = Sets.newHashSet(SentenceUtil.findPOSInformation(lastSentenceTokens, dependencyList));

        assertTrue("posInformation should include tense(PRESENT, works-2) but was: " + posInformation, posInformation.contains(new Literal("tense(PRESENT, works-2)")));

    }

    /** *************************************************************
     */
    @Test
    public void PresentProgressiveTense() {

        String input = "She is working";
        Annotation document = pipeline.annotate(input);
        List<Literal> dependencyList = SentenceUtil.toDependenciesList(document);

        Set<Literal> posInformation = Sets.newHashSet(SentenceUtil.findPOSInformation(document.get(TokensAnnotation.class), dependencyList));

        assertTrue("posInformation should include tense(PRESENT, working-3) but was: " + posInformation,
                posInformation.contains(new Literal("tense(PRESENT, working-3)")));
        assertTrue("posInformation should include aspect(PROGRESSIVE, working-3) but was: " + posInformation,
                posInformation.contains(new Literal("aspect(PROGRESSIVE, working-3)")));

    }

    /** *************************************************************
     */
    @Test
    public void PastTense() {

        String input = "She lived in New York";
        Annotation document = pipeline.annotate(input);
        List<Literal> dependencyList = SentenceUtil.toDependenciesList(document);

        Set<Literal> posInformation = Sets.newHashSet(SentenceUtil.findPOSInformation(document.get(TokensAnnotation.class), dependencyList));

        assertTrue("posInformation should include tense(PAST, lived-2) but was: " + posInformation, posInformation.contains(new Literal("tense(PAST, lived-2)")));

    }

    /** *************************************************************
     */
    @Test
    public void PastProgressiveTense() {

        String input = "She was working when you called.";
        Annotation document = pipeline.annotate(input);
        List<Literal> dependencyList = SentenceUtil.toDependenciesList(document);

        Set<Literal> posInformation = Sets.newHashSet(SentenceUtil.findPOSInformation(document.get(TokensAnnotation.class), dependencyList));

        assertTrue("posInformation should include tense(PAST, working-3) but was: " + posInformation,
                posInformation.contains(new Literal("tense(PAST, working-3)")));
        assertTrue("posInformation should include aspect(PROGRESSIVE, working-3) but was: " + posInformation,
                posInformation.contains(new Literal("aspect(PROGRESSIVE, working-3)")));
        assertTrue("posInformation should include tense(PAST, called-6) but was: " + posInformation,
                posInformation.contains(new Literal("tense(PAST, called-6)")));

    }

    /** *************************************************************
     */
    @Test
    public void PresentPerfectTense() {

        String input = "She has finished the letter.";
        Annotation document = pipeline.annotate(input);
        List<Literal> dependencyList = SentenceUtil.toDependenciesList(document);

        Set<Literal> posInformation = Sets.newHashSet(SentenceUtil.findPOSInformation(document.get(TokensAnnotation.class), dependencyList));

        assertTrue("posInformation should include tense(PRESENT, finished-3) but was: " + posInformation,
                posInformation.contains(new Literal("tense(PRESENT, finished-3)")));
        assertTrue("posInformation should include aspect(PERFECT, finished-3) but was: " + posInformation,
                posInformation.contains(new Literal("aspect(PERFECT, finished-3)")));

    }

    /** *************************************************************
     */
    @Test
    public void PresentPerfectProgressiveTense() {

        String input = "She has been learning English.";
        Annotation document = pipeline.annotate(input);
        List<Literal> dependencyList = SentenceUtil.toDependenciesList(document);

        Set<Literal> posInformation = Sets.newHashSet(SentenceUtil.findPOSInformation(document.get(TokensAnnotation.class), dependencyList));

        assertTrue("posInformation should include tense(PRESENT, learning-4) but was: " + posInformation,
                posInformation.contains(new Literal("tense(PRESENT, learning-4)")));
        assertTrue("posInformation should include aspect(PROGRESSIVEPERFECT, learning-4) but was: " + posInformation,
                posInformation.contains(new Literal("aspect(PROGRESSIVEPERFECT, learning-4)")));

    }

    /** *************************************************************
     */
    @Test
    public void PastPerfectTense() {

        String input = "They had already met.";
        Annotation document = pipeline.annotate(input);
        List<Literal> dependencyList = SentenceUtil.toDependenciesList(document);

        Set<Literal> posInformation = Sets.newHashSet(SentenceUtil.findPOSInformation(document.get(TokensAnnotation.class), dependencyList));

        assertTrue("posInformation should include tense(PAST, met-4) but was: " + posInformation,
                posInformation.contains(new Literal("tense(PAST, met-4)")));
        assertTrue("posInformation should include aspect(PERFECT, met-4) but was: " + posInformation,
                posInformation.contains(new Literal("aspect(PERFECT, met-4)")));

    }

    /** *************************************************************
     */
    @Test
    public void PastPerfectProgressiveTense() {

        String input = "She had been living in Germany";
        Annotation document = pipeline.annotate(input);
        List<Literal> dependencyList = SentenceUtil.toDependenciesList(document);

        Set<Literal> posInformation = Sets.newHashSet(SentenceUtil.findPOSInformation(document.get(TokensAnnotation.class), dependencyList));

        assertTrue("posInformation should include tense(PAST, living-4) but was: " + posInformation,
                posInformation.contains(new Literal("tense(PAST, living-4)")));
        assertTrue("posInformation should include aspect(PROGRESSIVEPERFECT, living-4) but was: " + posInformation,
                posInformation.contains(new Literal("aspect(PROGRESSIVEPERFECT, living-4)")));

    }

    /** *************************************************************
     */
    @Test
    public void FutureTense() {

        String input = "They will see Jane tomorrow.";
        Annotation document = pipeline.annotate(input);
        List<Literal> dependencyList = SentenceUtil.toDependenciesList(document);

        Set<Literal> posInformation = Sets.newHashSet(SentenceUtil.findPOSInformation(document.get(TokensAnnotation.class), dependencyList));

        assertTrue("posInformation should include tense(FUTURE, see-3) but was: " + posInformation,
                posInformation.contains(new Literal("tense(FUTURE, see-3)")));

    }

    /** *************************************************************
     */
    @Test
    public void FutureProgressiveTense() {

        String input = "They will be dancing at the party.";
        Annotation document = pipeline.annotate(input);
        List<Literal> dependencyList = SentenceUtil.toDependenciesList(document);

        Set<Literal> posInformation = Sets.newHashSet(SentenceUtil.findPOSInformation(document.get(TokensAnnotation.class), dependencyList));

        assertTrue("posInformation should include tense(FUTURE, dancing-4) but was: " + posInformation,
                posInformation.contains(new Literal("tense(FUTURE, dancing-4)")));
        assertTrue("posInformation should include aspect(PROGRESSIVE, dancing-4) but was: " + posInformation,
                posInformation.contains(new Literal("aspect(PROGRESSIVE, dancing-4)")));

    }

    /** *************************************************************
     */
    @Test
    public void FuturePerfectTense() {

        String input = "They will have met Dora by Friday.";
        Annotation document = pipeline.annotate(input);
        List<Literal> dependencyList = SentenceUtil.toDependenciesList(document);

        Set<Literal> posInformation = Sets.newHashSet(SentenceUtil.findPOSInformation(document.get(TokensAnnotation.class), dependencyList));

        assertTrue("posInformation should include tense(FUTURE, met-4) but was: " + posInformation,
                posInformation.contains(new Literal("tense(FUTURE, met-4)")));
        assertTrue("posInformation should include aspect(PERFECT, met-4) but was: " + posInformation,
                posInformation.contains(new Literal("aspect(PERFECT, met-4)")));

    }

    /** *************************************************************
     */
    @Test
    public void FutureProgressivePerfectTense() {

        String input = "They will have been working there for 10 years.";
        Annotation document = pipeline.annotate(input);
        List<Literal> dependencyList = SentenceUtil.toDependenciesList(document);

        Set<Literal> posInformation = Sets.newHashSet(SentenceUtil.findPOSInformation(document.get(TokensAnnotation.class), dependencyList));

        assertTrue("posInformation should include tense(FUTURE, working-5) but was: " + posInformation,
                posInformation.contains(new Literal("tense(FUTURE, working-5)")));
        assertTrue("posInformation should include aspect(PROGRESSIVEPERFECT, working-5) but was: " + posInformation,
                posInformation.contains(new Literal("aspect(PROGRESSIVEPERFECT, working-5)")));

    }

    /** *************************************************************
     */
    @Test
    public void FutureProgressivePerfectWithInBetweenWordsTense() {

        String input = "They will have certainly been working there for 10 years.";
        Annotation document = pipeline.annotate(input);
        List<Literal> dependencyList = SentenceUtil.toDependenciesList(document);

        Set<Literal> posInformation = Sets.newHashSet(SentenceUtil.findPOSInformation(document.get(TokensAnnotation.class), dependencyList));

        assertTrue("posInformation should include tense(FUTURE, working-6) but was: " + posInformation,
                posInformation.contains(new Literal("tense(FUTURE, working-6)")));
        assertTrue("posInformation should include aspect(PROGRESSIVEPERFECT, working-6) but was: " + posInformation,
                posInformation.contains(new Literal("aspect(PROGRESSIVEPERFECT, working-6)")));

    }

}
