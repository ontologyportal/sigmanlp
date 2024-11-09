/*
Copyright 2014-2015 IPsoft

Author: Andrei Holub andrei.holub@ipsoft.com

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
package com.articulate.nlp.semRewrite.substitutor;

import com.articulate.sigma.*;
import com.articulate.sigma.utils.*;
import com.articulate.sigma.wordNet.WordNet;
import edu.stanford.nlp.ling.CoreLabel;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

public class ExternalSubstitutor extends SimpleSubstitutorStorage {

    public int tokenCounter = 1; // number of token being added

    public class Segmentation {
        public ArrayList<CoreLabelSequence> segments = new ArrayList<>();
        public ArrayList<String> types = new ArrayList<>();

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("segments: " + segments.toString());
            sb.append("types: " + types.toString());
            return sb.toString();
        }

        // a segment from string without token numbers
        public void addStringSegment(String s) {

            List<CoreLabel> al = new ArrayList<>();
            //System.out.println("Segmentation.addStringSegment: before " + s);
            String[] ss = StringUtil.removePunctuation(s).split(" ");
            //System.out.println("Segmentation.addStringSegment: after " + Arrays.toString(ss));
            for (String seg : ss) {
                CoreLabel cl = new CoreLabel();
                cl.setOriginalText(seg);
                cl.setValue(seg);
                cl.setIndex(tokenCounter);
                tokenCounter++;
                al.add(cl);
            }
            CoreLabelSequence cls = new CoreLabelSequence(al);
            segments.add(cls);
        }
    }

    // cache of remote system responses
    public static Map<String, Segmentation> cache = new HashMap<>();

    /*************************************************************
     */
    public ExternalSubstitutor(List<CoreLabel> labels) {

        initialize(labels);
    }

    /****************************************************************
     * {
     * "query": "blah blah blah",
     *      "annotatations": [
     *          {
     *              "types": [
     *                  "...",
     *                  ...
     *              ],
     *              "segmentation": [
     *                  "...",
     *                  ...
     *              ],
     *          }
     *      ],
     * }
     */
    private Segmentation parseNERJson(String json) {

        try {
            Segmentation result = new Segmentation();
            JSONParser parser = new JSONParser();
            Object obj = parser.parse(json);
            JSONObject jsonObject = (JSONObject) obj;
            System.out.println("ExternaSubstitutor.parseNERJson(): Reading JSON file: " + jsonObject);

            JSONArray anno = (JSONArray) jsonObject.get("annotatations");
            System.out.println("ExternalSubstitutor.parseNERJson(): annotations: " + anno);
            if (anno == null || anno.size() == 0 || anno.get(0) == null) {
                System.out.println("ExternalSubstitutor.parseNERJson(): bad JSON: " + json);
                throw new ParseException(0);
            }
            JSONObject msg = (JSONObject) anno.get(0);
            JSONArray typeObj = (JSONArray) msg.get("types");
            Iterator<String> iterator = typeObj.iterator();
            ArrayList<String> types = new ArrayList<String>();
            while (iterator.hasNext()) {
                result.types.add(iterator.next());
            }

            JSONArray msg2 = (JSONArray) msg.get("segmentation");
            Iterator<String> iterator2 = msg2.iterator();
            tokenCounter = 1;
            while (iterator2.hasNext()) {
                result.addStringSegment(iterator2.next());
            }
            return result;
        }
        catch (ParseException pe) {
            System.out.println("Error in ExternaSubstitutor.prepare(): parseException: " + json);
            pe.printStackTrace();
            return null;
        }
    }

    /* *************************************************************
     * Load a file consisting of previous responses from the remote NER
     * server.
     */
    private void loadCache() {

        if (cache.keySet().size() > 0)
            return;  // don't load the cache multiple times
        String resourcePath = System.getenv("SIGMA_HOME") + File.separator + "KBs" +
                File.separator + "nerCache.json";
        try {
            File f = new File(resourcePath);
            if (!f.exists())
                return;
            FileReader fr = new FileReader(f);
            System.out.println("ExternaSubstitutor.loadCache(): Reading JSON file: " + resourcePath);
            JSONParser parser = new JSONParser();
            Object obj = parser.parse(fr);
            JSONObject jsonObject = (JSONObject) obj;
            JSONArray msg = (JSONArray) jsonObject.get("cache");

            Iterator<JSONObject> iterator = msg.iterator();
            while (iterator.hasNext()) {
                JSONObject oneQuery = iterator.next();
                String query = (String) oneQuery.get("query");
                JSONArray types = (JSONArray) oneQuery.get("types");
                JSONArray segmentation = (JSONArray) oneQuery.get("segmentation");
                Segmentation seg = new Segmentation();
                Iterator<String> iterator2 = segmentation.iterator();
                tokenCounter = 1;
                while (iterator2.hasNext()) {
                    seg.addStringSegment(iterator2.next());
                }
                seg.types.addAll(types);
                cache.put(query, seg);
            }
        }
        catch (Exception e) {
            System.out.println("Parse exception reading: " + resourcePath);
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    /***************************************************************
     */
    private void saveCache() {

        String resourcePath = System.getenv("SIGMA_HOME") + File.separator + "KBs" +
                File.separator + "nerCache.json";
        try {
            FileWriter fw = new FileWriter(new File(resourcePath));
            System.out.println("Writing JSON file: " + resourcePath);
            JSONObject jsonObject = new JSONObject();
            JSONArray cacheJson = new JSONArray();

            for (String s : cache.keySet()) {
                JSONObject queryAndNER = new JSONObject();
                queryAndNER.put("query", s);

                Segmentation seg = cache.get(s);
                JSONArray seglist = new JSONArray();
                JSONArray typelist = new JSONArray();
                for (CoreLabelSequence segment : seg.segments) {
                    StringBuilder oneseg = new StringBuilder();
                    for (CoreLabel cl : segment.getLabels()) {
                        if (!StringUtil.emptyString(oneseg.toString()))
                            oneseg.append(" ");
                        oneseg.append(cl.value());
                    }
                    seglist.add(oneseg.toString());
                }
                for (String segment : seg.types) {
                    typelist.add(segment);
                }
                queryAndNER.put("segmentation", seglist);
                queryAndNER.put("types", typelist);
                cacheJson.add(queryAndNER);
            }
            jsonObject.put("cache", cacheJson);
            fw.write(jsonObject.toJSONString());
            fw.flush();
            fw.close();
        }
        catch (Exception e) {
            System.out.println("Exception writing: " + resourcePath);
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    /* *************************************************************
     * Collects information froma remote service about continuous noun
     * sequences like "Garry Bloom", "Tim Buk Tu"
     */
    private static String toURLSentence(List<CoreLabel> labels) {

        StringBuilder sb = new StringBuilder();
        for (CoreLabel cl : labels) {
            //System.out.println("Info in ExternalSubstitutor.toURLSentence(): label: " + cl);
            //System.out.println("Info in ExternalSubstitutor.toURLSentence(): label: " + cl.originalText());
            //System.out.println("Info in ExternalSubstitutor.toURLSentence(): label: " + cl.lemma());
            //System.out.println("Info in ExternalSubstitutor.toURLSentence(): label: " + cl.value());
            if (!sb.toString().equals(""))
                sb.append("+");
            sb.append(cl.value());
        }
        return sb.toString();
    }

    /* *************************************************************
     * convert a string consisting of space-delimited words into a
     * CoreLabelSequence
     */
    private static List<CoreLabel> stringToCoreLabelList(String s) {

        ArrayList<CoreLabel> al = new ArrayList<>();
        String[] splits = s.split(" ");
        for (String word : splits) {
            CoreLabel cl = new CoreLabel();
            cl.setValue(word);
            al.add(cl);
        }
        return al;
    }

    /* *************************************************************
     * convert a string consisting of space-delimited words into a
     * CoreLabelSequence
     */
    private static CoreLabelSequence stringToCoreLabelSeq(String s) {

        List<CoreLabel> al = stringToCoreLabelList(s);
        CoreLabelSequence result = new CoreLabelSequence(al);
        return result;
    }

    /* *************************************************************
     */
    private Map<CoreLabelSequence, CoreLabelSequence> segToCoreLabel(Segmentation seg) {

        Map<CoreLabelSequence, CoreLabelSequence> result = new HashMap<>();
        for (CoreLabelSequence cls : seg.segments) {
            CoreLabelSequence clsUpper = cls.toUpperCase();
            if (cls.size() > 1) {
                result.put(clsUpper,cls);
            }
        }
        return result;
    }

    /* *************************************************************
     * FIXME: A hack to use previous code for adding term formats to
     * WordNet
     * FIXME: Only gets used for multi words or certain types?
     */
    private void addNERtoWN(Segmentation seg) {

        KB kb = KBmanager.getMgr().getKB("SUMO");
        for (int i = 0; i < seg.segments.size(); i++) {
            CoreLabelSequence cls = seg.segments.get(i);
            CoreLabelSequence noPunc = cls.removePunctuation();
            System.out.println("Info in ExternalSubstitutor.addNERtoWN(): noPunc: " + noPunc);
            //if (s.contains(" ")) {
            String type = seg.types.get(i);
            if (!type.contains("UNKNOWN") && !type.startsWith("ACTIONS")) {
                Formula form = new Formula("(termFormat EnglishLanguage " + type + "\"" + cls.toString() + "\")");
                form.setSourceFile("ExternalNER.kif");
                String wnWord = noPunc.toWordNetID(); // replace space with underscore
                System.out.println("Info in ExternalSubstitutor.addNERtoWN(): wnWord: " + wnWord);
                String SUMOterm = Character.toUpperCase(wnWord.charAt(0)) + wnWord.substring(1);
                System.out.println("Info in ExternalSubstitutor.addNERtoWN(): SUMOterm: " + SUMOterm);
                //if (!WordNet.wn.caseMap.containsKey(wnWord.toUpperCase())) {
                    if (noPunc.size() > 1)
                        WordNet.wn.multiWords.addMultiWord(wnWord);
                    WordNet.wn.synsetFromTermFormat(form, wnWord, SUMOterm, kb);
                    String f = "(instance " + SUMOterm + " " + type + ")";
                    System.out.println("Info in ExternalSubstitutor.addNERtoWN(): formula: " + f);
                    kb.tell(f);
                //}
            }
        }
        kb.kbCache.buildCaches();
    }

    /* *************************************************************
     * Given a list of tokens from a single sentence, collects information from a remote
     * service about continuous noun sequences like "Garry Bloom",
     * "Tim Buk Tu"
     */
    private void initialize(List<CoreLabel> labels) {

        if (labels.get(labels.size()-1).toString().startsWith(".-"))
            labels.remove(labels.size()-1);
        System.out.println("Info in ExternalSubstitutor.initialize(): labels: " + labels);
        Segmentation seg = null;
        loadCache();
        String nerurl = "";
        try {
            String sentence = toURLSentence(labels);
            if (cache.containsKey(sentence)) {
                seg = cache.get(sentence);
                System.out.println("Info in ExternaSubstitutor.initialize(): Seg: " + seg);
            }
            else {
                System.out.println("Info in ExternalSubstitutor.initialize(): sentence: " + sentence);
                nerurl = System.getenv("NERURL") + "query=" + sentence + "&limit=1";
                System.out.println("Info in ExternalSubstitutor.initialize(): URL: " + nerurl);
                URL myURL = new URL(nerurl);

                URLConnection yc = myURL.openConnection();
                BufferedReader in = new BufferedReader(new InputStreamReader(
                        yc.getInputStream()));
                String JSONString = "";
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    System.out.println("Info in ExternalSubstitutor.initialize(): inputLine: " + inputLine);
                    JSONString += inputLine;
                }
                System.out.println("Info in ExternalSubstitutor.initialize(): JSON: " + JSONString);
                in.close();
                seg = parseNERJson(JSONString);
                if (seg == null)
                    return;
                System.out.println("Info in ExternaSubstitutor.initialize(): " + seg);
                cache.put(sentence,seg);
                saveCache();
            }
            addNERtoWN(seg);
        }
        catch (MalformedURLException e) {
            System.out.println("Error in ExternaSubstitutor.initialize(): new URL() failed: " + nerurl);
            e.printStackTrace();
            return;
        }
        catch (IOException e) {
            System.out.println("Error in ExternaSubstitutor.initialize(): IOException: " + nerurl);
            //e.printStackTrace();
            return;
        }
        Map<CoreLabelSequence, CoreLabelSequence> groupsFull = segToCoreLabel(seg);
        // create structures like {[UPTOWN-5, FUNK-6]=[Uptown-5, Funk-6], [APPLE-8, MUSIC-9]=[Apple-8, Music-9]}
        addGroups(groupsFull);
        System.out.println("Info in ExternaSubstitutor.initialize(): result: " + groupsFull);
    }

    /****************************************************************
     */
    public static void main(String[] args) throws IOException {

        ExternalSubstitutor es = new ExternalSubstitutor(stringToCoreLabelList("i want to watch the game of thrones"));
    }
}