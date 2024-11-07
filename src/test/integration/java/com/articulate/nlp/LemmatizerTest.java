package com.articulate.nlp;

import com.articulate.nlp.pipeline.Pipeline;
import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.nlp.semRewrite.Literal;
import com.google.common.collect.ImmutableList;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Created by areed on 3/30/15.
 */
public class LemmatizerTest {

    Pipeline p = new Pipeline();

    @Test
    public void testLemmatization() {
        String input = "I had a car.";

        Annotation document = p.annotate(input);
        List<CoreLabel> labels = document.get(CoreAnnotations.TokensAnnotation.class);

        List<Literal> results = ImmutableList.of(new Literal("test(had-2,anything-0)"),
                new Literal("testing(PAST,had-2)"), new Literal("testing2(had-2,ANYTHING)"));

        List<Literal> actual = Interpreter.lemmatizeResults(results, labels);

        Literal[] expected = {
                new Literal("test(have-2,anything-0)"),
                new Literal("testing(PAST,have-2)"),
                new Literal("testing2(have-2,ANYTHING)")
        };

        assertThat(actual, hasItems(expected));

    }

}
