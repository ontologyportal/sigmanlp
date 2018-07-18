package com.articulate.nlp.corpora;

import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.nlp.semRewrite.NPtype;
import com.articulate.sigma.AVPair;
import com.articulate.sigma.KB;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.StringUtil;
import edu.stanford.nlp.ling.CoreLabel;

import java.io.File;
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
    public static HashMap<String,HashMap<String,ArrayList<String>>> nodes = new HashMap<>();

    private static int maxQueue = 100;

    // a count of how many items have been compiled so far.  Used to trigger
    // the periodic summary of price averages for SUMO types which in progress.
    private static int numItems = 0;

    // Maintain a queue of the most recent referenced nodes, on the assumption
    // that they are grouped nearby in the file.  When the queue reaches maxQueue
    // elements, pop it, get all statements for that node, print them, and
    // remove them from the nodes HashMap
    private static Queue<String> stmtQ = new LinkedList<String>();

    // A list of prices for each SUMO type, normalized to integers
    private static HashMap<String,ArrayList<Float>> prices = new HashMap<>();

    private static KB kb = null;

    /** ***************************************************************
     */
    private static String getNodeName(String nodeId) {

        HashMap<String,ArrayList<String>> rels = nodes.get(nodeId);
        if (rels == null)
            return "";
        if (rels.size() == 0)
            return "";
        if (rels.get("name") == null)
            return "";
        if (rels.get("name").size() == 0)
            return "";
        return rels.get("name").get(0);
    }

    /** ***************************************************************
     * Compute min, max and average price for each category and
     * print them.
     */
    private static void printPriceCategoryData() {

        System.out.println();
        System.out.println("==========================");
        for (String sumo : prices.keySet()) {
            float min = 100000;
            float max = 0;
            float total = 0;
            ArrayList<Float> theList = prices.get(sumo);
            for (Float i : theList) {
                if (i < min)
                    min = i;
                if (i > max)
                    max = i;
                total = total + i;
            }
            float avg = ((float) total / ((float) theList.size()));
            System.out.println("(defaultMinimumMeasure " + sumo + " (MeasureFn USDollar " + min + "))");
            System.out.println("(defaultMaximumMeasure " + sumo + " (MeasureFn USDollar " + max + "))");
            System.out.println("(defaultMeasure " + sumo + " (MeasureFn USDollar " + avg + "))");
        }
        System.out.println("==========================");
        System.out.println();
    }

    /** ***************************************************************
     * Attempt to find the type of the product with the class NPtype and
     * add the price for that type to the list of prices.  Convert a
     * few known currencies.  This assumes that a check has been performed
     * to ensure there is already price and currency data.
     * Note that only instances or subclasses of Object will be saved
     */
    private static void addPrice(String name, HashMap<String,ArrayList<String>> statements) {

        System.out.println("addPrice(): for " + name);
        String sumo = NPtype.findProductType(name);
        System.out.println("addPrice(): SUMO: " + sumo);
        NPtype.heads = new HashSet<CoreLabel>();
        if (StringUtil.emptyString(sumo))
            return;
        if (!kb.isInstanceOf(sumo,"Object") && !kb.isSubclass(sumo,"Object"))
            return;
        Float price = null;
        try {
            ArrayList<String> priceList = statements.get("price");
            for (String priceStr : priceList) {
                price = Float.parseFloat(priceStr);
                ArrayList<Float> pricesForItem = null;
                if (!prices.keySet().contains(sumo)) {
                    pricesForItem = new ArrayList<>();
                    prices.put(sumo,pricesForItem);
                }
                else
                    pricesForItem = prices.get(sumo);
                pricesForItem.add(price);
            }
        }
        catch (NumberFormatException nfe) {
            System.out.println("Error in addPrice(): Bad price format for " + name + " : " + statements.get("price"));
            return;
        }

    }

    /** ***************************************************************
     * Products have offers and offers have prices.  Print the price
     * for the offer for the product id.  Note that if there are multiple
     * offers, only the latest one in the file will have been recorded.
     * Find the type of the product name and add its price to the set
     * of prices for the type. Convert currency if known

    private static String printPrices(String id, String name, String prettyID) {

        StringBuffer sb = new StringBuffer();
        HashMap<String,ArrayList<String>> statements = nodes.get(id);
        ArrayList<String> offerId = statements.get("offer");
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
*/
    /** ***************************************************************
     * If the queue is full, pop one node off.  Only print its facts
     * if it at least has a name and a price.  Increment the item count
     * if a node is printed.
     * @return whether the id was the "head" of a subgraph that starts with
     * a "name" relation
     */
    private static boolean handleQueue(String id) {

        //System.out.println("handleQueue(): " + id);
        if (!stmtQ.contains(id))
            stmtQ.add(id);

        String nextID = stmtQ.peek();
        String prettyID = nextID; // but will get replaced, if possible with a pretty id below
        HashMap<String, ArrayList<String>> statements = nodes.get(nextID);
        ArrayList<String> names;
        boolean priceFound = false;
        //System.out.println("handleQueue():id:  " + nextID);
        if (statements == null)
            return false;
        String name = "";
        if (statements.keySet().contains("names")) {
            names = statements.get("names");
            //System.out.println("handleQueue():names:  " + names);
            if (names != null) {
                name = names.get(0);
                prettyID = StringUtil.toCamelCase(name);
            }
            ArrayList<String> offers = statements.get("offer");
            //System.out.println("handleQueue():offers:  " + offers);
            if (offers != null) {
                for (String offerID : offers) {
                    //System.out.println("handleQueue():offerID:  " + offerID);
                    HashMap<String, ArrayList<String>> statsOffer = nodes.get(offerID);
                    //System.out.println("handleQueue():offer statements:  " + statsOffer);
                    if (statsOffer != null) {
                        if (statsOffer.keySet().contains("price"))
                            addPrice(name,statsOffer);
                        for (String rel : statsOffer.keySet()) {
                            if (rel.equals("price")) {
                                ArrayList<String> prices = statsOffer.get(rel);
                                for (String price : prices) {
                                    System.out.println(prettyID + "\t" + rel + "\t" + price);
                                    priceFound = true;
                                }
                            }
                        }
                    }
                    nodes.remove(offerID);
                    stmtQ.remove(offerID);
                }
            }
            stmtQ.remove(nextID);
            nodes.remove(nextID);
            if (!priceFound)
                System.out.println("handleQueue(): no prices found for:  " + nextID);
            return true;
        }
        if (!priceFound)
            System.out.println("handleQueue(): no prices found for:  " + nextID);
        return false;
    }

    /** ***************************************************************
     * Add the triple to nodes.  If it's not an "offer", add the id to
     * the queue.
     */
    private static void updateNodes(String id, String relation, String value) {

        //System.out.println("updateNodes() " + nodes);
        if (!stmtQ.contains(id))
            stmtQ.add(id);
        if (StringUtil.emptyString(id) || StringUtil.emptyString(relation) || StringUtil.emptyString(value))
            return;
        HashMap<String,ArrayList<String>> nodeVals = nodes.get(id);
        if (nodeVals == null) {
            nodeVals = new HashMap<>();
            nodes.put(id, nodeVals);
        }
        ArrayList<String> relVals = nodeVals.get(relation);
        if (nodeVals.get(relation) == null) {
            relVals = new ArrayList<>();
        }
        nodeVals.put(relation,relVals);
        relVals.add(value);
        //System.out.println("updateNodes(): " + id + "\t" + relation + "\t" + value);
        //if (!relation.equals("offer"))
        if (stmtQ.size() > 100) {
            boolean itsAName = handleQueue(stmtQ.peek());
            if (!itsAName)
                stmtQ.remove();
        }
        //handleQueue(id);
    }

    /** ***************************************************************
     * Remove non numeric character from a price
     */
    public static String removeNonNumeric(String s) {

        Pattern p = Pattern.compile("[^\\d]*(\\d+(,\\d{3})*(\\.\\d{2})?)[^\\d]*");
        Matcher m = p.matcher(s);
        if (m.matches()) {
            String result = m.group(1);
            result = result.replaceAll(",","");
            return result;
        }
        else
            return "";
    }

    /** ***************************************************************
     */
    public static void loadContents() {

        Interpreter interp = new Interpreter();
        KBmanager.getMgr().initializeOnce();
        interp.initOnce();
        String filename = System.getenv("CORPORA") + File.separator + "Schema.org" +
              //  File.separator + "Product-small.nq";
                File.separator + "ProductSchemaPart.nq";
        try {
            FileReader r = new FileReader(filename);
            LineNumberReader lr = new LineNumberReader(r);
            String line;
            while ((line = lr.readLine()) != null) {
                System.out.println(lr.getLineNumber());
                if (lr.getLineNumber() % 99 == 0 && prices.keySet().size() > 0)
                    printPriceCategoryData();
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
                //System.out.println("loadContents(): " + nodeId + "\t" + relation + "\t" + value);
                if (relation.equals("<http://schema.org/Product/name>")) {
                    relation = "names";
                    updateNodes(nodeId, relation, value);
                }
                else if (relation.equals("<http://schema.org/Offer/price>")) {
                    relation = "price";
                    value = removeNonNumeric(value);
                    String name = getNodeName(nodeId);
                    //System.out.println("Price: " + value + "\t" + name);
                    updateNodes(nodeId, relation, value);
                }
                else if (relation.equals("<http://schema.org/Offer/priceCurrency>")) {
                    relation = "currency";
                    updateNodes(nodeId, relation, value);
                }
                else if (relation.equals("<http://schema.org/Product/offers>")) {
                    relation = "offer";
                    updateNodes(nodeId, relation,value );
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
        maxQueue = 0;
        while (stmtQ.size() > 0) {
            boolean itsAName = handleQueue(stmtQ.peek());
            if (!itsAName)
                stmtQ.remove();
        }
        printPriceCategoryData();
    }

    /***************************************************************
     */
    public static void main(String[] args) {

        KBmanager.getMgr().initializeOnce();
        kb = KBmanager.getMgr().getKB("SUMO");
        NPtype.init();
        loadContents();
    }
}
