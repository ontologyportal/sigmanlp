package com.articulate.nlp.semRewrite;

import com.articulate.nlp.IntegrationTestBase;
import com.articulate.sigma.KBmanager;
import edu.stanford.nlp.ling.CoreLabel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Created by apease on 6/14/18.
 */
public class NPtypeTest extends IntegrationTestBase {

    /****************************************************************
     */
    @Before
    public void setUpInterpreter() throws IOException {

        Interpreter interp = new Interpreter();
        KBmanager.getMgr().initializeOnce();
        interp.initOnce();
    }

    /****************************************************************
     */
    @After
    public void cleanup() {

        NPtype.heads = new HashSet<CoreLabel>();
    }

    /****************************************************************
     */
    @Test
    public void testSkort()   {

        NPtype.findProductType("ladies' green pleated skirt");
    }

    /****************************************************************
     */
    @Test
    public void testSentSkirt() {

        String type = NPtype.findProductType("ladies' green pleated skirt");
        assertEquals("Dress", type);
    }

    /****************************************************************
     */
    @Test
    public void testSentHeadphone() {

        String type = NPtype.findProductType("Merkury retro headphones - black.");
        assertEquals("Earphone",type);
    }

    /****************************************************************
     */
    @Test
    public void testSentJersey() {

        String type = NPtype.findProductType("mid weight 2-button custom baseball jerseys - closeout sale");
        assertEquals("Shirt",type);
    }

    /****************************************************************
     */
    @Test
    public void testSentScanner() {

        String type = NPtype.findProductType("USB mini business card scanner (silver)");
        assertEquals("ComputerInputDevice",type);
    }

    /****************************************************************
     */
    @Test
    public void testSentJacket() {

        String type = NPtype.findProductType("\n\t\ttri mountain bridget women's lightweight jacket\n\t");
        assertEquals("Coat",type);
    }

    /****************************************************************
     */
    @Test
    public void testSentAM() {

        String type = NPtype.findProductType("Idect X1i TWIN-B Digital Cordless Designer Phone Twin Pack with Answering Machine");
        assertEquals("Telephone",type);
    }

    /****************************************************************
     */
    @Test
    public void testSentDesktop() {

        String type = NPtype.findProductType("Gateway/DX4860-UR32P desktop");
        assertEquals("Computer",type);
    }

    /****************************************************************
         */
    @Test
    public void testSentCamera() {

        String type = NPtype.findProductType("Clover CW8800 2.4 GHz. Wireless Camera System - CCTV camera - color - audio");
        assertEquals("Camera",type);
    }

    /****************************************************************
     */
    @Test
    public void testSentVacuum() {

        String type = NPtype.findProductType("Sealey Power Tools Sealey Vacuum Cleaner Industrial 30ltr 1400W/230V Stainless Bin");
        assertEquals("VacuumCleaner",type);
    }

    /****************************************************************
     */
    @Test
    public void testSentFence() {

        String type = NPtype.findProductType("picket fence");
        assertEquals("Fence",type);
    }
}
