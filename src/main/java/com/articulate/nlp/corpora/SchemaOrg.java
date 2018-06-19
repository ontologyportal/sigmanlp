package com.articulate.nlp.corpora;

import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.nlp.semRewrite.NPtype;
import com.articulate.sigma.AVPair;
import com.articulate.sigma.KB;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.StringUtil;
import edu.stanford.nlp.ling.CoreLabel;

import java.io.FileReader;
import java.io.LineNumberReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by apease on 6/13/18.
 */
public class SchemaOrg {

    // <_:noded8ceb1a23d14863c1ee9bf9ea0ac4b90 <http://schema.org/Product/name> "\n          \n          Big Wave Surfing: Extreme Technology Development, Management, Marketing & Investing\n        "@en
    // _:node40b4438ffe5a14f9f3c6f56245ce73e <http://schema.org/Offer/price> "$19.96"@en
    // _:node9813aa56f85f2e30eabede6ede19e23c <http://schema.org/Offer/priceCurrency> "USD"@en-us
    // _:node7315ca4da77173fa81d82c51cc3d8b <http://schema.org/Offer/category> "Storage & Tools > Tools & Navigation > Father's Day Sale > Gifts Under "@en
    // weight, width, height, depth
    // a map of node IDs to the relation value pairs it has
    // Since there's a lot of data, only keep 10 nodes and the associated statements at a time
    public static HashMap<String,HashMap<String,String>> nodes = new HashMap<>();

    private static final int maxQueue = 10;

    // a count of how many items have been compiled so far.  Used to trigger
    // the periodic summary of price averages for SUMO types which in progress.
    private static int numItems = 0;

    // Maintain a queue of the most recent referenced nodes, on the assumption
    // that they are grouped nearby in the file.  When the queue reaches maxQueue
    // elements, pop it, get all statements for that node, print them, and
    // remove them from the nodes HashMap
    private static Queue<String> stmtQ = new LinkedList<String>();

    // A list of prices for each SUMO type, normalized to integers
    private static HashMap<String,ArrayList<Integer>> prices = new HashMap<>();

    /** ***************************************************************
     * Compute min, max and average price for each category and
     * print them.
     */
    private static void printPriceCategoryData() {

        System.out.println();
        System.out.println("==========================");
        for (String sumo : prices.keySet()) {
            int min = 100000;
            int max = 0;
            int total = 0;
            ArrayList<Integer> theList = prices.get(sumo);
            for (Integer i : theList) {
                if (i < min)
                    min = i;
                if (i > max)
                    max = i;
                total = total + i;
            }
            float avg = ((float) total / ((float) theList.size() * (float) 100.0));
            System.out.println("term, min, max, avg: " + sumo + "\t" + min + "\t" + max + "\t" + avg);
        }
        System.out.println("==========================");
        System.out.println();
    }

    /** ***************************************************************
     * Attempt to find the type of the product with the class NPtype and
     * add the price for that type to the list of prices.  Convert a
     * few known currencies.  This assumes that a check has been performed
     * to ensure there is already price and currency data.
     */
    private static void addPrice(String name, HashMap<String,String> statements) {

        String sumo = NPtype.findProductType(name);
        NPtype.heads = new HashSet<CoreLabel>();
        if (StringUtil.emptyString(sumo))
            return;
        Integer price = null;
        try {
            price = new Integer( Math.round(Float.parseFloat(statements.get("price")) * (float) 100.0) );
        }
        catch (NumberFormatException nfe) {
            System.out.println("Bad price format for " + name + " : " + statements.get("price"));
            return;
        }
        ArrayList<Integer> priceList = null;
        if (!prices.keySet().contains(sumo)) {
            priceList = new ArrayList<>();
            prices.put(sumo,priceList);
        }
        else
            priceList = prices.get(sumo);
        priceList.add(price);
    }

    /** ***************************************************************
     * Products have offers and offers have prices.  Print the price
     * for the offer for the product id.  Note that if there are multiple
     * offers, only the latest one in the file will have been recorded.
     * Find the type of the product name and add its price to the set
     * of prices for the type. Convert currency if known
     */
    private static String printPrices(String id, String name, String prettyID) {

        StringBuffer sb = new StringBuffer();
        HashMap<String,String> statements = nodes.get(id);
        String offerId = statements.get("offer");
        statements = nodes.get(offerId); // now the statements are for the offer, not the node!
        if (statements != null && statements.keySet().contains("price")) {
            sb.append(prettyID + "\tprice\t" + statements.get("price") + "\n");
        }
        if (statements != null && statements.keySet().contains("currency")) {
            sb.append(prettyID + "\tcurrency\t" + statements.get("currency") + "\n");
        }
        if (statements != null && statements.keySet().contains("price") && statements.keySet().contains("currency"))
            addPrice(name,statements);
        return sb.toString();
    }

    /** ***************************************************************
     * If the queue is full, pop one node off.  Only print its facts
     * if it at least has a name and a price.  Increment the item count
     * if a node is printed.
     */
    private static void handleQueue(String id) {

        if (!stmtQ.contains(id))
            stmtQ.add(id);
        if (stmtQ.size() > maxQueue) {
            numItems++;
            if (numItems > 100) {
                printPriceCategoryData();
                numItems = 0;
            }
            String nextID = stmtQ.peek();
            String prettyID = nextID; // but will get replaced, if possible with a pretty id below
            HashMap<String,String> statements = nodes.get(nextID);
            String name = nextID;
            if (statements.keySet().contains("names")) {
                name = statements.get("names");
                prettyID = StringUtil.toCamelCase(name);
            }
            if (statements.keySet().contains("names")) {
                String prices = printPrices(id,name,prettyID);
                if (!StringUtil.emptyString(prices) && !StringUtil.emptyString(name)) {
                    for (String rel : statements.keySet()) {
                        if (!rel.equals("offer"))
                            System.out.println(prettyID + "\t" + rel + "\t" + statements.get(rel));
                    }
                    System.out.println(prices);
                }
            }
            stmtQ.remove(nextID);
            nodes.remove(nextID);
        }
    }

    /** ***************************************************************
     * Add the triple to nodes.  If it's not an "offer", add the id to
     * the queue.
     */
    private static void updateNodes(String id, String relation, String value) {

        if (StringUtil.emptyString(id) || StringUtil.emptyString(relation) || StringUtil.emptyString(value))
            return;
        HashMap<String,String> nodeVals = nodes.get(id);
        if (nodeVals == null) {
            nodeVals = new HashMap<>();
            nodes.put(id, nodeVals);
        }
        nodeVals.put(relation,value);
        if (!relation.equals("offer"))
            handleQueue(id);
    }

    /** ***************************************************************
     * Remove non numeric character from a price
     */
    private static String removeNonNumeric(String s) {

        Pattern p = Pattern.compile("[^\\d]*(\\d+(,\\d{3})*(\\.\\d{2})?)[^\\d]*");
        Matcher m = p.matcher(s);
        if (m.matches())
            return m.group(1);
        else
            return "";
    }

    /** ***************************************************************
     */
    public static void loadContents() {

        Interpreter interp = new Interpreter();
        KBmanager.getMgr().initializeOnce();
        interp.initOnce();
        String filename = "ProductSchemaPart.nq";
        try {
            FileReader r = new FileReader(filename);
            LineNumberReader lr = new LineNumberReader(r);
            String line;
            int column = 0;
            boolean inColmap = true;
            while ((line = lr.readLine()) != null) {
                int firstSpace = line.indexOf(" ");
                int secondSpace = line.indexOf(" ",firstSpace + 1);
                int thirdSpace = line.indexOf(" ",secondSpace + 1);
                if (firstSpace == -1 || secondSpace == -1)
                    continue;
                String nodeId = line.substring(0,line.indexOf(" "));
                String relation = line.substring(firstSpace + 1,secondSpace);
                int endQuote = line.indexOf("\"",secondSpace + 2);
                String value = "";
                if (endQuote != -1)
                    value = line.substring(secondSpace + 2, endQuote);
                else
                    value = line.substring(secondSpace + 1, thirdSpace);
                if (relation.equals("<http://schema.org/Product/name>")) {
                    relation = "names";
                    updateNodes(nodeId, relation, value);
                }
                else if (relation.equals("<http://schema.org/Offer/price>")) {
                    relation = "price";
                    value = removeNonNumeric(value);
                    updateNodes(nodeId, relation, value);
                }
                else if (relation.equals("<http://schema.org/Offer/priceCurrency>")) {
                    relation = "currency";
                    updateNodes(nodeId, relation, value);
                }
                else if (relation.equals("<http://schema.org/Product/offers>")) {
                    relation = "offer";
                    updateNodes(nodeId, relation, value);
                }
                else if (relation.equals("<http://schema.org/Offer/category>")) {
                    relation = "type";
                    updateNodes(nodeId, relation, value);
                }
                else if (relation.equals("<http://schema.org/Product/weight>")) {
                    relation = "weight";
                    updateNodes(nodeId, relation, value);
                }
                else if (relation.equals("<http://schema.org/Product/width>")) {
                    relation = "width";
                    updateNodes(nodeId, relation, value);
                }
                else if (relation.equals("<http://schema.org/Product/height>")) {
                    relation = "height";
                    updateNodes(nodeId, relation, value);
                }
                else if (relation.equals("<http://schema.org/Product/depth>")) {
                    relation = "depth";
                    updateNodes(nodeId, relation, value);
                }
            }
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    /***************************************************************
     */
    public static void main(String[] args) {

        loadContents();
    }
}
