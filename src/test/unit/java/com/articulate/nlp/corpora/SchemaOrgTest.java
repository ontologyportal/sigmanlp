package com.articulate.nlp.corpora;

import com.articulate.nlp.UnitTestBase;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Created by apease on 6/26/18.
 */
public class SchemaOrgTest extends UnitTestBase {

    /****************************************************************
     */
    @Test
    public void testNumber1()   {

        String s = SchemaOrg.removeNonNumeric("$61,250");
        System.out.println("testNumber1(): " + s);
        assertEquals("61250",s);
    }

    /****************************************************************
     */
    @Test
    public void testNumber2()   {

        String s = SchemaOrg.removeNonNumeric("$61,250.37");
        System.out.println("testNumber2(): " + s);
        assertEquals("61250.37",s);
    }

    /****************************************************************
     */
    @Test
    public void testNumber3()   {

        String s = SchemaOrg.removeNonNumeric("$61,250.37");
        System.out.println("testNumber3(): " + s);
        Float f = Float.parseFloat(s);
        System.out.println("testNumber3(): as float: " + f);
        assertEquals(61,250.37f,f);
    }

    /****************************************************************
     */
    @Test
    public void testNumber4()   {

        String s = SchemaOrg.removeNonNumeric("$250.37");
        System.out.println("testNumber3(): " + s);
        Float f = Float.parseFloat(s);
        System.out.println("testNumber3(): as float: " + f);
        assertEquals(250.37f,(float) f,0.001f);
    }

    /****************************************************************
     */
    @Test
    public void testNumber5()   {

        String s = SchemaOrg.removeNonNumeric("$250,345,678.37");
        System.out.println("testNumber3(): " + s);
        Float f = Float.parseFloat(s);
        System.out.println("testNumber3(): as float: " + f);
        assertEquals(250345678.37f,(float) f,0.001f);
    }
}
