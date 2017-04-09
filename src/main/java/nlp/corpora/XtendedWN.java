package nlp.corpora;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;

/*
Copyright 2017 Articulate Software

Author: Adam Pease apease@articulatesoftware.com

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

Read the contents of eXtended WordNet http://www.hlt.utdallas.edu/~xwn/downloads.html

*/
public class XtendedWN {


    /***************************************************************
     */
    private static void readFile(String filename) {

        //ArrayList<String> result = new ArrayList<>();
        try {
            FileReader r = new FileReader(filename);
            LineNumberReader lr = new LineNumberReader(r);
            String line;
            while ((line = lr.readLine()) != null) {

            }
        }
        catch (IOException i) {
            System.out.println("Error in XtendedWN.readFile() reading file " + filename + ": " + i.getMessage());
            i.printStackTrace();
        }
    }

    /***************************************************************
     */
    public static void main(String[] args) {


    }
}
